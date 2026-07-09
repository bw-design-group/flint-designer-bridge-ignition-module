package dev.bwdesigngroup.flint.designer.lsp;

import com.inductiveautomation.ignition.common.ImmutableBytes;
import com.inductiveautomation.ignition.common.resourcecollection.Resource;
import com.inductiveautomation.ignition.common.resourcecollection.ResourcePath;
import com.inductiveautomation.ignition.common.resourcecollection.ResourceType;
import com.inductiveautomation.ignition.common.script.PackageTreeNode;
import com.inductiveautomation.ignition.common.script.ScriptManager;
import com.inductiveautomation.ignition.common.script.typing.CompletionDescriptor;
import com.inductiveautomation.ignition.designer.model.DesignerContext;
import com.inductiveautomation.ignition.designer.project.DesignableProject;
import dev.bwdesigngroup.flint.common.FlintConstants;
import dev.bwdesigngroup.flint.common.protocol.methods.lsp.CompletionItem;
import dev.bwdesigngroup.flint.common.protocol.methods.lsp.CompletionResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service that provides code completion using Ignition's ScriptManager. Leverages the built-in
 * getHintsTree() API to provide accurate, scope-aware completions.
 */
public class CompletionService {
    private static final Logger logger =
            LoggerFactory.getLogger("flint.Designer.CompletionService");

    // ResourceType for project Python scripts
    private static final ResourceType SCRIPT_PYTHON_TYPE =
            new ResourceType("ignition", "script-python");

    // system.perspective functions (not in standard hintsTree, added manually)
    private static final String[][] PERSPECTIVE_FUNCTIONS = {
        {
            "alterLogging",
            "(remoteLoggingEnabled, [level], [sessionId], [pageId])",
            "Alter logging for a Perspective session"
        },
        {
            "authenticationChallenge",
            "([sessionId], [pageId], [idp], [forceAuth], [payload])",
            "Present an authentication challenge to the user"
        },
        {"closeDock", "(id, [sessionId], [pageId])", "Close a docked view"},
        {"closePage", "([sessionId], [pageId])", "Close a Perspective page"},
        {"closePopup", "(id, [sessionId], [pageId])", "Close a popup view"},
        {"closeSession", "([sessionId])", "Close a Perspective session"},
        {
            "download",
            "(filename, data, [contentType], [sessionId], [pageId])",
            "Download a file to the client"
        },
        {
            "getSessionInfo",
            "([sessionId], [usernameFilter], [projectFilter])",
            "Get info about Perspective sessions"
        },
        {
            "isAuthorized",
            "(isAllOf, securityLevels, [sessionId], [pageId])",
            "Check if user is authorized"
        },
        {
            "login",
            "([sessionId], [pageId], [forceAuth], [payload])",
            "Present login dialog to user"
        },
        {"logout", "([sessionId], [pageId])", "Log out the current user"},
        {
            "navigate",
            "(page, [url], [view], [params], [sessionId], [pageId], [newTab])",
            "Navigate to a page or URL"
        },
        {"openDock", "(id, viewPath, [params], [sessionId], [pageId])", "Open a docked view"},
        {
            "openPopup",
            "(id, view, [params], [title], [position], [showCloseIcon], [draggable], [resizable], [modal], [overlayDismiss], [sessionId], [pageId], [viewportBound])",
            "Open a popup view"
        },
        {
            "print",
            "(fileName, [showDialog], [sessionId], [pageId])",
            "Print the current page to PDF"
        },
        {"refresh", "([sessionId], [pageId])", "Refresh the current page"},
        {
            "sendMessage",
            "(messageType, payload, [scope], [sessionId], [pageId])",
            "Send a message to Perspective client(s)"
        },
        {"setTheme", "(name, [sessionId], [pageId])", "Set the theme for a session"},
        {"toggleDock", "(id, viewPath, [params], [sessionId], [pageId])", "Toggle a docked view"},
        {
            "togglePopup",
            "(id, view, [params], [title], [sessionId], [pageId])",
            "Toggle a popup view"
        },
        {"vibrateDevice", "(duration, [sessionId], [pageId])", "Vibrate the mobile device"}
    };

    private final DesignerContext context;

    // Cache for hints tree (short TTL for responsiveness to changes)
    private PackageTreeNode hintsTreeCache;
    private long lastCacheTime = 0;
    private static final long CACHE_TTL_MS = 1000; // 1 second cache - short for quick updates

    // Cache for project script names (top-level only)
    private Set<String> projectScriptNamesCache;
    private long lastScriptCacheTime = 0;

    // Cache for all script paths (full paths like "Test12/Test123")
    private Set<String> allScriptPathsCache;
    private long lastAllPathsCacheTime = 0;

    // Cache for project script functions (moduleName -> list of functions)
    private Map<String, List<ScriptFunction>> projectScriptFunctionsCache;
    private long lastFunctionCacheTime = 0;

    // Regex pattern for Python function definitions
    // Matches: def functionName(params):
    private static final Pattern PYTHON_DEF_PATTERN =
            Pattern.compile("^def\\s+(\\w+)\\s*\\(([^)]*)\\)\\s*:", Pattern.MULTILINE);

    // Regex pattern for Python docstrings (triple-quoted strings)
    private static final Pattern DOCSTRING_PATTERN =
            Pattern.compile(
                    "^\\s*(?:\"\"\"([\\s\\S]*?)\"\"\"|'''([\\s\\S]*?)''')", Pattern.MULTILINE);

    /** Class to hold parsed function info from project scripts. */
    private static class ScriptFunction {
        final String name;
        final String signature;
        final String params;
        final String docstring;

        ScriptFunction(String name, String params, String docstring) {
            this.name = name;
            this.params = params;
            this.signature = name + "(" + params + ")";
            this.docstring = docstring;
        }
    }

    public CompletionService(DesignerContext context) {
        this.context = context;
        this.hintsTreeCache = null;
        this.projectScriptNamesCache = new HashSet<>();
        this.allScriptPathsCache = new HashSet<>();
        this.projectScriptFunctionsCache = new HashMap<>();
    }

    /**
     * Gets completion items for the given prefix.
     *
     * @param prefix The module path prefix (e.g., "system.tag" or "")
     * @return CompletionResult containing matching items
     */
    public CompletionResult getCompletions(String prefix) {
        CompletionResult result = new CompletionResult();

        try {
            PackageTreeNode hintsTree = getHintsTree();

            if (hintsTree == null) {
                logger.debug("No hints available from ScriptManager");
                return result;
            }

            // Normalize prefix (case-insensitive matching)
            String normalizedPrefix = prefix != null ? prefix.trim() : "";
            String normalizedPrefixLower = normalizedPrefix.toLowerCase();

            logger.info(
                    "Getting completions for prefix: '{}', hintsTree children: {}",
                    normalizedPrefix,
                    hintsTree.children() != null ? hintsTree.children().keySet() : "null");

            // At root level (empty prefix), return all top-level keys from hintsTree
            if (normalizedPrefix.isEmpty()) {
                // Add system modules from hints tree
                if (hintsTree.children() != null) {
                    for (String key : hintsTree.children().keySet()) {
                        CompletionItem item = new CompletionItem();
                        item.setLabel(key);
                        item.setKind(FlintConstants.COMPLETION_KIND_MODULE);
                        item.setDetail("Module");
                        item.setDocumentation("Ignition scripting module: " + key);
                        item.setInsertText(key);
                        item.setInsertTextFormat(1); // Plain text
                        item.setSortText(key.toLowerCase());
                        item.setFilterText(key);
                        result.addItem(item);
                    }
                }

                // Add project script modules
                Set<String> projectScripts = getProjectScriptNames();
                for (String scriptName : projectScripts) {
                    CompletionItem item = new CompletionItem();
                    item.setLabel(scriptName);
                    item.setKind(FlintConstants.COMPLETION_KIND_MODULE);
                    item.setDetail("Project Script");
                    item.setDocumentation("Project library script: " + scriptName);
                    item.setInsertText(scriptName);
                    item.setInsertTextFormat(1); // Plain text
                    item.setSortText("z_" + scriptName.toLowerCase()); // Sort after system modules
                    item.setFilterText(scriptName);
                    result.addItem(item);
                }

                int childrenCount = hintsTree.children() != null ? hintsTree.children().size() : 0;
                logger.info(
                        "Root level completions: {} items ({} system, {} project scripts)",
                        result.getItems().size(),
                        childrenCount,
                        projectScripts.size());
                return result;
            }

            // For non-empty prefix, navigate the tree to find the right node
            PackageTreeNode targetNode = navigateTree(hintsTree, normalizedPrefix);

            if (targetNode != null) {
                // Add child modules (sub-packages)
                if (targetNode.children() != null) {
                    for (String childKey : targetNode.children().keySet()) {
                        // Check if this child has further children or just methods
                        PackageTreeNode childNode = targetNode.children().get(childKey);
                        boolean hasChildren =
                                childNode.children() != null && !childNode.children().isEmpty();
                        boolean hasMethods =
                                childNode.methods() != null && !childNode.methods().isEmpty();

                        CompletionItem item = new CompletionItem();
                        item.setLabel(childKey);

                        if (hasMethods && !hasChildren) {
                            // Leaf node with methods only - could still be a module
                            item.setKind(FlintConstants.COMPLETION_KIND_MODULE);
                        } else {
                            item.setKind(FlintConstants.COMPLETION_KIND_MODULE);
                        }

                        item.setDetail("Module");
                        item.setDocumentation(
                                "Ignition scripting module: " + normalizedPrefix + "." + childKey);
                        item.setInsertText(childKey);
                        item.setInsertTextFormat(1); // Plain text
                        item.setSortText(childKey.toLowerCase());
                        item.setFilterText(childKey);

                        // Skip duplicates
                        String childKeyLower = childKey.toLowerCase();
                        if (result.getItems().stream()
                                .noneMatch(i -> i.getLabel().toLowerCase().equals(childKeyLower))) {
                            result.addItem(item);
                        }
                    }
                }

                // Add methods from the target node
                if (targetNode.methods() != null) {
                    for (CompletionDescriptor.Method method : targetNode.methods()) {
                        String methodName = method.getName();
                        if (methodName == null || methodName.isEmpty()) {
                            continue;
                        }

                        // Skip duplicates
                        String methodNameLower = methodName.toLowerCase();
                        if (result.getItems().stream()
                                .anyMatch(
                                        i -> i.getLabel().toLowerCase().equals(methodNameLower))) {
                            continue;
                        }

                        String fullPath = normalizedPrefix + "." + methodName;
                        CompletionItem item = createCompletionItem(method, methodName, fullPath);
                        result.addItem(item);
                    }
                }

                // Add attributes from the target node
                if (targetNode.attributes() != null) {
                    for (CompletionDescriptor.Attribute attr : targetNode.attributes()) {
                        String attrName = attr.getName();
                        if (attrName == null || attrName.isEmpty()) {
                            continue;
                        }

                        // Skip duplicates
                        String attrNameLower = attrName.toLowerCase();
                        if (result.getItems().stream()
                                .anyMatch(i -> i.getLabel().toLowerCase().equals(attrNameLower))) {
                            continue;
                        }

                        CompletionItem item = new CompletionItem();
                        item.setLabel(attrName);
                        // Check if it looks like a constant
                        if (attrName.equals(attrName.toUpperCase()) && attrName.length() > 1) {
                            item.setKind(FlintConstants.COMPLETION_KIND_CONSTANT);
                        } else {
                            item.setKind(FlintConstants.COMPLETION_KIND_PROPERTY);
                        }
                        item.setDetail(
                                attr.getDescription() != null ? attr.getDescription() : "Property");
                        item.setInsertText(attrName);
                        item.setInsertTextFormat(1); // Plain text
                        item.setSortText(attrName.toLowerCase());
                        item.setFilterText(attrName);
                        result.addItem(item);
                    }
                }
            } else {
                // No exact tree node found - try partial matching
                // Navigate to the parent node and filter children by partial match
                int lastDot = normalizedPrefix.lastIndexOf('.');
                if (lastDot > 0) {
                    String parentPath = normalizedPrefix.substring(0, lastDot);
                    String partialName = normalizedPrefix.substring(lastDot + 1).toLowerCase();
                    PackageTreeNode parentNode = navigateTree(hintsTree, parentPath);

                    if (parentNode != null) {
                        // Filter children by partial match
                        if (parentNode.children() != null) {
                            for (Map.Entry<String, PackageTreeNode> entry :
                                    parentNode.children().entrySet()) {
                                if (entry.getKey().toLowerCase().startsWith(partialName)) {
                                    CompletionItem item = new CompletionItem();
                                    item.setLabel(entry.getKey());
                                    item.setKind(FlintConstants.COMPLETION_KIND_MODULE);
                                    item.setDetail("Module");
                                    item.setDocumentation(
                                            "Ignition scripting module: "
                                                    + parentPath
                                                    + "."
                                                    + entry.getKey());
                                    item.setInsertText(entry.getKey());
                                    item.setInsertTextFormat(1);
                                    item.setSortText(entry.getKey().toLowerCase());
                                    item.setFilterText(entry.getKey());
                                    result.addItem(item);
                                }
                            }
                        }

                        // Filter methods by partial match
                        if (parentNode.methods() != null) {
                            for (CompletionDescriptor.Method method : parentNode.methods()) {
                                String methodName = method.getName();
                                if (methodName != null
                                        && methodName.toLowerCase().startsWith(partialName)) {
                                    String fullPath = parentPath + "." + methodName;
                                    CompletionItem item =
                                            createCompletionItem(method, methodName, fullPath);
                                    result.addItem(item);
                                }
                            }
                        }
                    }
                } else {
                    // Single segment partial match at root level
                    if (hintsTree.children() != null) {
                        for (String key : hintsTree.children().keySet()) {
                            if (key.toLowerCase().startsWith(normalizedPrefixLower)) {
                                CompletionItem item = new CompletionItem();
                                item.setLabel(key);
                                item.setKind(FlintConstants.COMPLETION_KIND_MODULE);
                                item.setDetail("Module");
                                item.setDocumentation("Ignition scripting module: " + key);
                                item.setInsertText(key);
                                item.setInsertTextFormat(1);
                                item.setSortText(key.toLowerCase());
                                item.setFilterText(key);
                                result.addItem(item);
                            }
                        }
                    }
                }
            }

            // Add Perspective module to "system." completions (since it's not in hintsTree)
            if (normalizedPrefixLower.equals("system")) {
                // Check if "perspective" is not already in results
                boolean hasPerspective =
                        result.getItems().stream()
                                .anyMatch(i -> i.getLabel().toLowerCase().equals("perspective"));
                if (!hasPerspective) {
                    CompletionItem perspectiveItem = new CompletionItem();
                    perspectiveItem.setLabel("perspective");
                    perspectiveItem.setKind(FlintConstants.COMPLETION_KIND_MODULE);
                    perspectiveItem.setDetail("Module");
                    perspectiveItem.setDocumentation("Perspective module functions");
                    perspectiveItem.setInsertText("perspective");
                    perspectiveItem.setInsertTextFormat(1);
                    perspectiveItem.setSortText("perspective");
                    perspectiveItem.setFilterText("perspective");
                    result.addItem(perspectiveItem);
                }
            }

            // Handle "system.perspective" prefix - add Perspective functions
            if (normalizedPrefixLower.equals("system.perspective")) {
                for (String[] funcInfo : PERSPECTIVE_FUNCTIONS) {
                    String funcName = funcInfo[0];
                    String signature = funcInfo[1];
                    String description = funcInfo[2];

                    CompletionItem item = new CompletionItem();
                    item.setLabel(funcName);
                    item.setKind(FlintConstants.COMPLETION_KIND_FUNCTION);
                    item.setDetail(signature);
                    item.setDocumentation(description);
                    item.setInsertText(buildPerspectiveFunctionSnippet(funcName, signature));
                    item.setInsertTextFormat(2); // Snippet format
                    item.setSortText(funcName.toLowerCase());
                    item.setFilterText(funcName);
                    result.addItem(item);
                }
            }

            // Handle project script prefixes - supports nested modules like "Test12.Test123"
            Set<String> projectScripts = getProjectScriptNames();

            // Check if the prefix is a valid script path (could be nested like "Test12.Test123")
            if (isValidScriptPath(normalizedPrefix)) {
                logger.info(
                        "Prefix '{}' is a valid script path, getting completions",
                        normalizedPrefix);

                // 1. Add child modules (packages) if any exist
                Set<String> childModules = getChildModules(normalizedPrefix);
                for (String childName : childModules) {
                    CompletionItem item = new CompletionItem();
                    item.setLabel(childName);
                    item.setKind(FlintConstants.COMPLETION_KIND_MODULE);
                    item.setDetail("Script Package");
                    item.setDocumentation(
                            "Project script package: " + normalizedPrefix + "." + childName);
                    item.setInsertText(childName);
                    item.setInsertTextFormat(1); // Plain text
                    item.setSortText("a_" + childName.toLowerCase()); // Sort packages first
                    item.setFilterText(childName);
                    result.addItem(item);
                    logger.debug("Added child module: {}", childName);
                }

                // 2. Add functions from this module (if it has a code.py)
                List<ScriptFunction> functions = getProjectScriptFunctions(normalizedPrefix);
                logger.info(
                        "Getting functions for script path '{}': found {} functions",
                        normalizedPrefix,
                        functions.size());
                for (ScriptFunction func : functions) {
                    CompletionItem item =
                            createProjectScriptFunctionItem(normalizedPrefix, func, false);
                    result.addItem(item);
                }
            }
            // Also check top-level scripts for backward compatibility
            else {
                for (String scriptName : projectScripts) {
                    if (normalizedPrefix.equalsIgnoreCase(scriptName)) {
                        // User typed "Test" or "test" - show functions in that script
                        List<ScriptFunction> functions = getProjectScriptFunctions(scriptName);
                        logger.info(
                                "Getting functions for project script '{}': found {} functions",
                                scriptName,
                                functions.size());
                        for (ScriptFunction func : functions) {
                            CompletionItem item =
                                    createProjectScriptFunctionItem(scriptName, func, false);
                            result.addItem(item);
                        }

                        // Also check for child modules
                        Set<String> childModules = getChildModules(scriptName);
                        for (String childName : childModules) {
                            CompletionItem item = new CompletionItem();
                            item.setLabel(childName);
                            item.setKind(FlintConstants.COMPLETION_KIND_MODULE);
                            item.setDetail("Script Package");
                            item.setDocumentation(
                                    "Project script package: " + scriptName + "." + childName);
                            item.setInsertText(childName);
                            item.setInsertTextFormat(1);
                            item.setSortText("a_" + childName.toLowerCase());
                            item.setFilterText(childName);
                            result.addItem(item);
                        }
                        break;
                    }
                }
            }

            // Deep search: If prefix doesn't contain a dot and isn't a known module,
            // search all project script functions for partial matches
            if (!normalizedPrefix.isEmpty()
                    && !normalizedPrefix.contains(".")
                    && !projectScripts.contains(normalizedPrefix)
                    && !isValidScriptPath(normalizedPrefix)) {
                // Check if this might be a system module from the tree
                boolean isSystemPrefix = false;
                if (hintsTree.children() != null) {
                    for (String key : hintsTree.children().keySet()) {
                        if (key.equalsIgnoreCase(normalizedPrefix)) {
                            isSystemPrefix = true;
                            break;
                        }
                    }
                }

                if (!isSystemPrefix) {
                    // Search through all project scripts (including nested) for partial matches
                    Set<String> allPaths = getAllScriptPaths();
                    for (String scriptPath : allPaths) {
                        // Convert slash path to dot notation for display
                        String dotPath = scriptPath.replace("/", ".");
                        List<ScriptFunction> functions = getProjectScriptFunctions(dotPath);
                        for (ScriptFunction func : functions) {
                            String funcNameLower = func.name.toLowerCase();
                            // Match if function name starts with or contains the prefix
                            if (funcNameLower.startsWith(normalizedPrefixLower)
                                    || funcNameLower.contains(normalizedPrefixLower)) {
                                // Create completion with full qualified path
                                CompletionItem item =
                                        createProjectScriptFunctionItem(dotPath, func, true);
                                // Use the full path as label for deep search results
                                item.setLabel(dotPath + "." + func.name);
                                item.setInsertText(
                                        dotPath
                                                + "."
                                                + buildProjectScriptFunctionSnippet(
                                                        func.name, func.params));
                                // Prioritize prefix matches over substring matches
                                String sortPrefix =
                                        funcNameLower.startsWith(normalizedPrefixLower)
                                                ? "0_"
                                                : "1_";
                                item.setSortText(sortPrefix + func.name.toLowerCase());
                                item.setFilterText(normalizedPrefix);
                                result.addItem(item);
                            }
                        }
                    }
                    if (result.getItems().size() > 0) {
                        logger.info(
                                "Deep search for '{}' found {} matches across all project scripts",
                                normalizedPrefix,
                                result.getItems().size());
                    }
                }
            }

            // Log what completions we're returning
            if (result.getItems().size() > 0) {
                List<String> labels = new ArrayList<>();
                for (CompletionItem item : result.getItems()) {
                    labels.add(item.getLabel());
                }
                logger.info(
                        "Returning {} completions for prefix '{}': {}",
                        result.getItems().size(),
                        normalizedPrefix,
                        labels);
            } else {
                logger.debug(
                        "Found {} completion items for prefix: '{}'",
                        result.getItems().size(),
                        normalizedPrefix);
            }

        } catch (Exception e) {
            logger.error("Error getting completions for prefix: {}", prefix, e);
        }

        return result;
    }

    /**
     * Navigates the PackageTreeNode tree by splitting the path on dots and walking each segment.
     *
     * @param root The root tree node
     * @param path The dotted path to navigate (e.g., "system.tag")
     * @return The node at the given path, or null if not found
     */
    private PackageTreeNode navigateTree(PackageTreeNode root, String path) {
        if (root == null || path == null || path.isEmpty()) {
            return root;
        }

        String[] segments = path.split("\\.");
        PackageTreeNode current = root;

        for (String segment : segments) {
            if (current.children() == null) {
                return null;
            }

            // Try exact match first
            PackageTreeNode next = current.children().get(segment);

            // If no exact match, try case-insensitive match
            if (next == null) {
                for (Map.Entry<String, PackageTreeNode> entry : current.children().entrySet()) {
                    if (entry.getKey().equalsIgnoreCase(segment)) {
                        next = entry.getValue();
                        break;
                    }
                }
            }

            if (next == null) {
                return null;
            }

            current = next;
        }

        return current;
    }

    /** Gets the hints tree from ScriptManager, using cache if valid. */
    private PackageTreeNode getHintsTree() {
        long now = System.currentTimeMillis();

        // Check cache validity
        if (hintsTreeCache != null && (now - lastCacheTime) < CACHE_TTL_MS) {
            return hintsTreeCache;
        }

        try {
            ScriptManager scriptManager = context.getScriptManager();
            if (scriptManager == null) {
                logger.warn("ScriptManager not available");
                return hintsTreeCache;
            }

            // Get fresh hints tree from ScriptManager
            PackageTreeNode freshTree = scriptManager.getHintsTree();

            if (freshTree != null) {
                hintsTreeCache = freshTree;
                lastCacheTime = now;
                logger.info(
                        "Refreshed hints tree cache with {} top-level entries: {}",
                        freshTree.children() != null ? freshTree.children().size() : 0,
                        freshTree.children() != null ? freshTree.children().keySet() : "null");

                // Log some sample info to understand the structure
                if (freshTree.children() != null) {
                    for (Map.Entry<String, PackageTreeNode> entry :
                            freshTree.children().entrySet()) {
                        PackageTreeNode child = entry.getValue();
                        int childCount = child.children() != null ? child.children().size() : 0;
                        int methodCount = child.methods() != null ? child.methods().size() : 0;
                        logger.info(
                                "Tree key '{}' has {} children and {} methods",
                                entry.getKey(),
                                childCount,
                                methodCount);
                    }
                }
            }

        } catch (Exception e) {
            logger.error("Error getting hints tree from ScriptManager", e);
        }

        return hintsTreeCache;
    }

    /** Creates a CompletionItem from a CompletionDescriptor.Method. */
    private CompletionItem createCompletionItem(
            CompletionDescriptor.Method method, String label, String path) {
        CompletionItem item = new CompletionItem();
        item.setLabel(label);
        item.setPath(path);

        // Determine kind based on method characteristics
        int kind = determineCompletionKind(method, label, path);
        item.setKind(kind);

        // Get documentation if available
        String description = method.getDescription();
        if (description != null && !description.isEmpty()) {
            StringBuilder doc = new StringBuilder(description);

            // Add parameter descriptions if available
            List<CompletionDescriptor.Parameter> params = method.getParameters();
            if (params != null && !params.isEmpty()) {
                doc.append("\n\n**Parameters:**\n");
                for (CompletionDescriptor.Parameter param : params) {
                    doc.append("- `").append(param.getName()).append("`");
                    if (param.getOptional()) {
                        doc.append(" (optional");
                        String defaultVal = param.getDefault();
                        if (defaultVal != null && !defaultVal.isEmpty()) {
                            doc.append(", default: ").append(defaultVal);
                        }
                        doc.append(")");
                    }
                    String paramDesc = param.getDescription();
                    if (paramDesc != null && !paramDesc.isEmpty()) {
                        doc.append(": ").append(paramDesc);
                    }
                    doc.append("\n");
                }
            }
            item.setDocumentation(doc.toString());
        }

        // Build method signature as detail
        String signature = buildMethodSignature(method);
        if (signature != null && !signature.isEmpty()) {
            item.setDetail(signature);
        }

        // Build insert text with snippet placeholders for functions
        if (kind == FlintConstants.COMPLETION_KIND_FUNCTION
                || kind == FlintConstants.COMPLETION_KIND_METHOD) {
            String insertText = buildInsertText(method, label);
            item.setInsertText(insertText);
            item.setInsertTextFormat(2); // Snippet format
        } else {
            item.setInsertText(label);
            item.setInsertTextFormat(1); // Plain text
        }

        // Set sort text to maintain alphabetical order
        item.setSortText(label.toLowerCase());
        item.setFilterText(label);

        return item;
    }

    /** Builds a method signature string from a CompletionDescriptor.Method. */
    private String buildMethodSignature(CompletionDescriptor.Method method) {
        StringBuilder sig = new StringBuilder(method.getName());
        sig.append("(");
        List<CompletionDescriptor.Parameter> params = method.getParameters();
        if (params != null) {
            for (int i = 0; i < params.size(); i++) {
                if (i > 0) {
                    sig.append(", ");
                }
                sig.append(params.get(i).getName());
            }
        }
        sig.append(")");
        return sig.toString();
    }

    /** Determines the completion kind based on the method. */
    private int determineCompletionKind(
            CompletionDescriptor.Method method, String label, String path) {
        // Check if this is a module (label appears in the middle of path)
        if (path.contains(label + ".")) {
            return FlintConstants.COMPLETION_KIND_MODULE;
        }

        // Methods from the tree are functions
        List<CompletionDescriptor.Parameter> params = method.getParameters();
        if (params != null) {
            return FlintConstants.COMPLETION_KIND_FUNCTION;
        }

        // Check if it looks like a constant (ALL_CAPS)
        if (label.equals(label.toUpperCase()) && label.length() > 1) {
            return FlintConstants.COMPLETION_KIND_CONSTANT;
        }

        // Default to function since these come from method descriptors
        return FlintConstants.COMPLETION_KIND_FUNCTION;
    }

    /** Builds the insert text with snippet placeholders. */
    private String buildInsertText(CompletionDescriptor.Method method, String label) {
        List<CompletionDescriptor.Parameter> params = method.getParameters();
        if (params == null || params.isEmpty()) {
            return label + "()";
        }

        StringBuilder sb = new StringBuilder(label);
        sb.append("(");

        for (int i = 0; i < params.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            String paramName = params.get(i).getName();
            // Handle optional parameters like "[timeout]"
            paramName = paramName.replace("[", "").replace("]", "");
            // Use ${1:paramName} format for snippets
            sb.append("${").append(i + 1).append(":").append(paramName).append("}");
        }

        sb.append(")");
        return sb.toString();
    }

    /** Builds a snippet for Perspective functions from signature string. */
    private String buildPerspectiveFunctionSnippet(String funcName, String signature) {
        if (signature == null || !signature.contains("(")) {
            return funcName + "()";
        }

        int openParen = signature.indexOf('(');
        int closeParen = signature.indexOf(')');
        if (openParen < 0 || closeParen < 0 || closeParen <= openParen + 1) {
            return funcName + "()";
        }

        String paramsStr = signature.substring(openParen + 1, closeParen).trim();
        if (paramsStr.isEmpty()) {
            return funcName + "()";
        }

        String[] params = paramsStr.split(",");
        StringBuilder sb = new StringBuilder(funcName);
        sb.append("(");

        int snippetIndex = 1;
        for (int i = 0; i < params.length; i++) {
            String paramName = params[i].trim();
            // Skip optional parameters (those in brackets)
            if (paramName.startsWith("[")) {
                continue;
            }
            if (snippetIndex > 1) {
                sb.append(", ");
            }
            sb.append("${").append(snippetIndex).append(":").append(paramName).append("}");
            snippetIndex++;
        }

        sb.append(")");
        return sb.toString();
    }

    /**
     * Creates a CompletionItem for a project script function.
     *
     * @param scriptName The module name (e.g., "Test")
     * @param func The parsed function info
     * @param isDeepSearch If true, this is from a deep search (partial match)
     * @return The completion item
     */
    private CompletionItem createProjectScriptFunctionItem(
            String scriptName, ScriptFunction func, boolean isDeepSearch) {
        CompletionItem item = new CompletionItem();
        item.setLabel(func.name);
        item.setKind(FlintConstants.COMPLETION_KIND_FUNCTION);
        // Detail shows the full function signature
        item.setDetail(func.signature);

        // Build documentation with docstring if available
        StringBuilder doc = new StringBuilder();
        if (func.docstring != null && !func.docstring.isEmpty()) {
            doc.append(func.docstring);
        } else {
            doc.append("Project script function");
        }
        doc.append("\n\n**Module:** `").append(scriptName).append("`");

        // Add parameter info if available
        if (func.params != null && !func.params.isEmpty()) {
            doc.append("\n\n**Parameters:**\n");
            String[] paramList = func.params.split(",");
            for (String param : paramList) {
                String paramName = param.trim();
                if (!paramName.isEmpty()) {
                    // Handle default values
                    int equalsIdx = paramName.indexOf('=');
                    if (equalsIdx > 0) {
                        String name = paramName.substring(0, equalsIdx).trim();
                        String defaultVal = paramName.substring(equalsIdx + 1).trim();
                        doc.append("- `")
                                .append(name)
                                .append("` (default: `")
                                .append(defaultVal)
                                .append("`)\n");
                    } else {
                        doc.append("- `").append(paramName).append("`\n");
                    }
                }
            }
        }

        item.setDocumentation(doc.toString());
        item.setInsertText(buildProjectScriptFunctionSnippet(func.name, func.params));
        item.setInsertTextFormat(2); // Snippet format
        item.setSortText(func.name.toLowerCase());
        item.setFilterText(func.name);

        return item;
    }

    /** Builds a snippet for project script functions from params string. */
    private String buildProjectScriptFunctionSnippet(String funcName, String params) {
        if (params == null || params.trim().isEmpty()) {
            return funcName + "()";
        }

        String[] paramList = params.split(",");
        StringBuilder sb = new StringBuilder(funcName);
        sb.append("(");

        int snippetIndex = 1;
        for (int i = 0; i < paramList.length; i++) {
            String paramName = paramList[i].trim();
            if (paramName.isEmpty()) {
                continue;
            }
            // Handle default values like "param=value"
            int equalsIdx = paramName.indexOf('=');
            if (equalsIdx > 0) {
                paramName = paramName.substring(0, equalsIdx).trim();
            }
            if (snippetIndex > 1) {
                sb.append(", ");
            }
            sb.append("${").append(snippetIndex).append(":").append(paramName).append("}");
            snippetIndex++;
        }

        sb.append(")");
        return sb.toString();
    }

    /**
     * Extracts the docstring from Python code starting at a given position. Looks for triple-quoted
     * strings (''' or """) immediately after a function definition.
     *
     * @param code The full Python source code
     * @param startPos The position to start searching (should be right after the ":")
     * @return The docstring content, or null if none found
     */
    private String extractDocstring(String code, int startPos) {
        if (startPos >= code.length()) {
            return null;
        }

        // Get the substring after the function definition
        String remainder = code.substring(startPos);

        // Look for docstring - must start on the next line (after optional whitespace/newline)
        Matcher docMatcher = DOCSTRING_PATTERN.matcher(remainder);
        if (docMatcher.find()
                && docMatcher.start() < 50) { // Allow some whitespace before docstring
            // Check that only whitespace appears before the docstring
            String beforeDocstring = remainder.substring(0, docMatcher.start());
            if (beforeDocstring.trim().isEmpty()) {
                // Return the captured docstring content (group 1 for """, group 2 for ''')
                String docstring = docMatcher.group(1);
                if (docstring == null) {
                    docstring = docMatcher.group(2);
                }
                if (docstring != null) {
                    return docstring.trim();
                }
            }
        }

        return null;
    }

    /**
     * Gets the names of top-level project script modules. Scans the project's script-python
     * resources to find module names.
     */
    private Set<String> getProjectScriptNames() {
        long now = System.currentTimeMillis();

        // Check cache validity
        if (projectScriptNamesCache != null
                && !projectScriptNamesCache.isEmpty()
                && (now - lastScriptCacheTime) < CACHE_TTL_MS) {
            return projectScriptNamesCache;
        }

        Set<String> scriptNames = new HashSet<>();

        try {
            DesignableProject project = context.getProject();
            if (project == null) {
                logger.debug("Project not available for script discovery");
                return scriptNames;
            }

            // Get all script-python resources
            List<Resource> scriptResources = project.getResourcesOfType(SCRIPT_PYTHON_TYPE);
            logger.info("Found {} script-python resources", scriptResources.size());

            for (Resource resource : scriptResources) {
                try {
                    ResourcePath resourcePath = resource.getResourcePath();
                    if (resourcePath == null) {
                        continue;
                    }

                    // Get the path portion (CaseSensitiveStringPath)
                    // The path format should be like "Test" or "Shared/MyLib"
                    Object pathObj = resourcePath.getPath();
                    String pathStr = pathObj != null ? pathObj.toString() : null;

                    logger.info(
                            "Script resource: full={}, path={}", resourcePath.toString(), pathStr);

                    if (pathStr == null || pathStr.isEmpty()) {
                        continue;
                    }

                    // Remove leading slash if present
                    if (pathStr.startsWith("/")) {
                        pathStr = pathStr.substring(1);
                    }

                    // Get the first segment (top-level module name)
                    String moduleName = pathStr;
                    int slashIndex = pathStr.indexOf('/');
                    if (slashIndex > 0) {
                        moduleName = pathStr.substring(0, slashIndex);
                    }

                    if (!moduleName.isEmpty()) {
                        scriptNames.add(moduleName);
                    }
                } catch (Exception e) {
                    logger.debug("Error processing script resource: {}", e.getMessage());
                }
            }

            // Update cache
            projectScriptNamesCache = scriptNames;
            lastScriptCacheTime = now;
            logger.info(
                    "Discovered {} project script modules: {}", scriptNames.size(), scriptNames);

        } catch (Exception e) {
            logger.error("Error getting project script names", e);
        }

        return scriptNames;
    }

    /**
     * Gets all script paths in the project (including nested paths). Returns paths like "Test",
     * "Test12", "Test12/Test123".
     */
    private Set<String> getAllScriptPaths() {
        long now = System.currentTimeMillis();

        // Check cache validity
        if (allScriptPathsCache != null
                && !allScriptPathsCache.isEmpty()
                && (now - lastAllPathsCacheTime) < CACHE_TTL_MS) {
            return allScriptPathsCache;
        }

        Set<String> scriptPaths = new HashSet<>();

        try {
            DesignableProject project = context.getProject();
            if (project == null) {
                logger.debug("Project not available for script path discovery");
                return scriptPaths;
            }

            // Get all script-python resources
            List<Resource> scriptResources = project.getResourcesOfType(SCRIPT_PYTHON_TYPE);

            for (Resource resource : scriptResources) {
                try {
                    ResourcePath resourcePath = resource.getResourcePath();
                    if (resourcePath == null) {
                        continue;
                    }

                    Object pathObj = resourcePath.getPath();
                    String pathStr = pathObj != null ? pathObj.toString() : null;

                    if (pathStr == null || pathStr.isEmpty()) {
                        continue;
                    }

                    // Remove leading slash if present
                    if (pathStr.startsWith("/")) {
                        pathStr = pathStr.substring(1);
                    }

                    if (!pathStr.isEmpty()) {
                        scriptPaths.add(pathStr);
                    }
                } catch (Exception e) {
                    logger.debug("Error processing script resource: {}", e.getMessage());
                }
            }

            // Update cache
            allScriptPathsCache = scriptPaths;
            lastAllPathsCacheTime = now;
            logger.debug("Discovered {} script paths: {}", scriptPaths.size(), scriptPaths);

        } catch (Exception e) {
            logger.error("Error getting all script paths", e);
        }

        return scriptPaths;
    }

    /**
     * Gets child modules of a given parent path. For example, getChildModules("Test12") would
     * return ["Test123"] if Test12/Test123 exists.
     *
     * @param parentPath The parent module path using dots (e.g., "Test12" or "Test12.SubModule")
     * @return Set of child module names (just the name, not the full path)
     */
    private Set<String> getChildModules(String parentPath) {
        Set<String> children = new HashSet<>();

        // Convert dot notation to slash notation for path matching
        String parentSlashPath = parentPath.replace(".", "/");

        Set<String> allPaths = getAllScriptPaths();

        for (String scriptPath : allPaths) {
            // Check if this path is a direct child of the parent
            // e.g., parentSlashPath="Test12", scriptPath="Test12/Test123"
            if (scriptPath.startsWith(parentSlashPath + "/")) {
                // Get the remainder after the parent path
                String remainder = scriptPath.substring(parentSlashPath.length() + 1);

                // Get only the immediate child (first segment)
                int slashIndex = remainder.indexOf('/');
                String childName = slashIndex >= 0 ? remainder.substring(0, slashIndex) : remainder;

                if (!childName.isEmpty()) {
                    children.add(childName);
                }
            }
        }

        logger.debug("Child modules of '{}': {}", parentPath, children);
        return children;
    }

    /**
     * Checks if a given path (dot notation) represents a valid script module or package.
     *
     * @param dotPath The module path using dots (e.g., "Test12" or "Test12.Test123")
     * @return true if the path exists as a script or package
     */
    private boolean isValidScriptPath(String dotPath) {
        String slashPath = dotPath.replace(".", "/");
        Set<String> allPaths = getAllScriptPaths();

        // Check exact match
        if (allPaths.contains(slashPath)) {
            return true;
        }

        // Check if it's a package (has children)
        for (String path : allPaths) {
            if (path.startsWith(slashPath + "/")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Gets the functions defined in a project script module. Parses the script's Python code to
     * find function definitions.
     *
     * @param moduleName The name of the script module (e.g., "Test" or "Test12.Test123")
     * @return List of ScriptFunction objects
     */
    private List<ScriptFunction> getProjectScriptFunctions(String moduleName) {
        long now = System.currentTimeMillis();

        // Check cache validity
        if (projectScriptFunctionsCache.containsKey(moduleName)
                && (now - lastFunctionCacheTime) < CACHE_TTL_MS) {
            return projectScriptFunctionsCache.get(moduleName);
        }

        List<ScriptFunction> functions = new ArrayList<>();

        try {
            DesignableProject project = context.getProject();
            if (project == null) {
                logger.debug("Project not available for function discovery");
                return functions;
            }

            // Find the script resource for this module
            List<Resource> scriptResources = project.getResourcesOfType(SCRIPT_PYTHON_TYPE);

            for (Resource resource : scriptResources) {
                try {
                    ResourcePath resourcePath = resource.getResourcePath();
                    if (resourcePath == null) {
                        continue;
                    }

                    Object pathObj = resourcePath.getPath();
                    String pathStr = pathObj != null ? pathObj.toString() : null;
                    if (pathStr == null) {
                        continue;
                    }

                    // Remove leading slash
                    if (pathStr.startsWith("/")) {
                        pathStr = pathStr.substring(1);
                    }

                    // Convert moduleName from dot notation to slash notation for comparison
                    // e.g., "Test12.Test123" becomes "Test12/Test123"
                    String moduleSlashPath = moduleName.replace(".", "/");

                    // Check if this is the module we're looking for
                    // Match exact name (case-insensitive)
                    if (!pathStr.equalsIgnoreCase(moduleSlashPath)) {
                        continue;
                    }

                    // Found the module - get its code from code.py
                    // In Ignition 8.3, getData returns Optional<ImmutableBytes>
                    Optional<ImmutableBytes> dataOpt = resource.getData("code.py");
                    byte[] data = dataOpt.map(ImmutableBytes::getBytes).orElse(null);

                    if (data == null || data.length == 0) {
                        logger.debug("Script resource has no code.py data: {}", pathStr);
                        // Try the default getData() as fallback
                        Optional<ImmutableBytes> defaultDataOpt = resource.getData();
                        data = defaultDataOpt.map(ImmutableBytes::getBytes).orElse(null);
                        if (data == null || data.length == 0) {
                            logger.debug("Script resource has no data at all: {}", pathStr);
                            continue;
                        }
                    }

                    String code = new String(data, java.nio.charset.StandardCharsets.UTF_8);
                    logger.info(
                            "Parsing script '{}' ({} bytes): first 100 chars: {}",
                            pathStr,
                            data.length,
                            code.substring(0, Math.min(100, code.length())));

                    // Parse function definitions using regex
                    Matcher matcher = PYTHON_DEF_PATTERN.matcher(code);
                    while (matcher.find()) {
                        String funcName = matcher.group(1);
                        String params = matcher.group(2).trim();

                        // Extract docstring if present (immediately after the function definition)
                        String docstring = extractDocstring(code, matcher.end());

                        functions.add(new ScriptFunction(funcName, params, docstring));
                        logger.debug(
                                "Found function: {}({}) with docstring: {}",
                                funcName,
                                params,
                                docstring != null ? docstring.length() + " chars" : "none");
                    }

                    logger.info("Parsed {} functions from script '{}'", functions.size(), pathStr);
                    break; // Found and processed the module

                } catch (Exception e) {
                    logger.debug("Error processing script resource: {}", e.getMessage());
                }
            }

            // Update cache
            projectScriptFunctionsCache.put(moduleName, functions);
            lastFunctionCacheTime = now;

        } catch (Exception e) {
            logger.error("Error getting project script functions for module: {}", moduleName, e);
        }

        return functions;
    }

    /** Invalidates the hints cache. Call this when project changes are detected. */
    public void invalidateCache() {
        hintsTreeCache = null;
        lastCacheTime = 0;
        projectScriptNamesCache.clear();
        lastScriptCacheTime = 0;
        allScriptPathsCache.clear();
        lastAllPathsCacheTime = 0;
        projectScriptFunctionsCache.clear();
        lastFunctionCacheTime = 0;
        logger.debug("Hints cache invalidated");
    }
}

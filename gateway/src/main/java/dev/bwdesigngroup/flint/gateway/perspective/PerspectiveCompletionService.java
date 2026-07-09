package dev.bwdesigngroup.flint.gateway.perspective;

import com.inductiveautomation.ignition.common.gson.JsonElement;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.perspective.gateway.model.ComponentModel;
import com.inductiveautomation.perspective.gateway.property.PropertyTree;
import dev.bwdesigngroup.flint.common.FlintConstants;
import dev.bwdesigngroup.flint.common.protocol.methods.lsp.CompletionItem;
import dev.bwdesigngroup.flint.common.protocol.methods.perspective.PerspectiveCompletionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service that provides code completion for Perspective component properties. Extracts property
 * information from ComponentModel's PropertyTree to provide completions for self.props.*,
 * self.custom.*, self.meta.* etc.
 */
public class PerspectiveCompletionService {
    private static final Logger logger =
            LoggerFactory.getLogger("flint.Gateway.PerspectiveCompletion");

    /** Gets completion items for a component's properties based on the prefix. */
    public PerspectiveCompletionResult getComponentCompletions(
            ComponentModel component, String prefix) {
        PerspectiveCompletionResult result = new PerspectiveCompletionResult();

        if (component == null) {
            return result;
        }

        String normalizedPrefix = prefix != null ? prefix.trim() : "";

        try {
            if (normalizedPrefix.equals("self") || normalizedPrefix.isEmpty()) {
                addTopLevelCompletions(result, component);
            } else if (normalizedPrefix.equals("self.props")
                    || normalizedPrefix.startsWith("self.props.")) {
                addPropertyTreeCompletions(
                        result, component, "props", "self.props", normalizedPrefix);
            } else if (normalizedPrefix.equals("self.custom")
                    || normalizedPrefix.startsWith("self.custom.")) {
                addPropertyTreeCompletions(
                        result, component, "custom", "self.custom", normalizedPrefix);
            } else if (normalizedPrefix.equals("self.meta")
                    || normalizedPrefix.startsWith("self.meta.")) {
                addPropertyTreeCompletions(
                        result, component, "meta", "self.meta", normalizedPrefix);
            } else if (normalizedPrefix.equals("self.position")
                    || normalizedPrefix.startsWith("self.position.")) {
                addPropertyTreeCompletions(
                        result, component, "position", "self.position", normalizedPrefix);
            }
        } catch (Exception e) {
            logger.error(
                    "Error getting component completions for prefix '{}': {}",
                    normalizedPrefix,
                    e.getMessage());
        }

        return result;
    }

    /**
     * Adds top-level completions for self.* including property trees and standard
     * attributes/methods.
     */
    private void addTopLevelCompletions(
            PerspectiveCompletionResult result, ComponentModel component) {
        // Add property trees dynamically - only if they exist
        if (hasPropertyTree(component, "props")) {
            result.addItem(
                    createCompletionItem(
                            "props",
                            FlintConstants.COMPLETION_KIND_PROPERTY,
                            "PropertyTree",
                            "Component properties defined by the component type"));
        }
        if (hasPropertyTree(component, "custom")) {
            result.addItem(
                    createCompletionItem(
                            "custom",
                            FlintConstants.COMPLETION_KIND_PROPERTY,
                            "PropertyTree",
                            "Custom properties defined on this component"));
        }
        if (hasPropertyTree(component, "meta")) {
            result.addItem(
                    createCompletionItem(
                            "meta",
                            FlintConstants.COMPLETION_KIND_PROPERTY,
                            "PropertyTree",
                            "Meta properties (name, visible, etc.)"));
        }
        if (hasPropertyTree(component, "position")) {
            result.addItem(
                    createCompletionItem(
                            "position",
                            FlintConstants.COMPLETION_KIND_PROPERTY,
                            "PropertyTree",
                            "Position properties (when using coordinate container)"));
        }

        // Standard Perspective component attributes
        result.addItem(
                createCompletionItem(
                        "view",
                        FlintConstants.COMPLETION_KIND_PROPERTY,
                        "PerspectiveView",
                        "Reference to the containing view"));
        result.addItem(
                createCompletionItem(
                        "session",
                        FlintConstants.COMPLETION_KIND_PROPERTY,
                        "Session",
                        "Reference to the Perspective session"));
        result.addItem(
                createCompletionItem(
                        "page",
                        FlintConstants.COMPLETION_KIND_PROPERTY,
                        "Page",
                        "Reference to the containing page"));
        result.addItem(
                createCompletionItem(
                        "parent",
                        FlintConstants.COMPLETION_KIND_PROPERTY,
                        "Component",
                        "Reference to the parent component (None if root)"));

        // Standard Perspective component methods
        result.addItem(
                createMethodCompletionItem(
                        "getSibling", "(name)", "Component", "Get a sibling component by name"));
        result.addItem(
                createMethodCompletionItem(
                        "getChild", "(name)", "Component", "Get a child component by name"));
        result.addItem(
                createMethodCompletionItem(
                        "refreshBinding",
                        "(propertyPath)",
                        "None",
                        "Refresh the binding at the specified property path"));
    }

    private CompletionItem createMethodCompletionItem(
            String name, String signature, String returnType, String documentation) {
        CompletionItem item = new CompletionItem();
        item.setLabel(name);
        item.setKind(FlintConstants.COMPLETION_KIND_METHOD);
        item.setDetail(signature + " -> " + returnType);
        item.setDocumentation(documentation);
        item.setInsertText(name);
        item.setPath("self." + name);
        return item;
    }

    private boolean hasPropertyTree(ComponentModel component, String typeName) {
        try {
            PropertyTree tree = getPropertyTreeByName(component, typeName);
            return tree != null;
        } catch (Exception e) {
            return false;
        }
    }

    /** Adds completions from a PropertyTree at the specified path level. */
    private void addPropertyTreeCompletions(
            PerspectiveCompletionResult result,
            ComponentModel component,
            String propertyTypeName,
            String basePrefix,
            String fullPrefix) {

        try {
            PropertyTree propertyTree = getPropertyTreeByName(component, propertyTypeName);
            if (propertyTree == null) {
                return;
            }

            JsonObject propsJson = getPropertyTreeJson(propertyTree);
            if (propsJson == null) {
                return;
            }

            // Calculate the relative path we're looking for
            String relativePath = "";
            if (fullPrefix.length() > basePrefix.length()) {
                relativePath = fullPrefix.substring(basePrefix.length());
                if (relativePath.startsWith(".")) {
                    relativePath = relativePath.substring(1);
                }
            }

            // Navigate to the correct level in the JSON
            JsonObject currentLevel = propsJson;
            if (!relativePath.isEmpty()) {
                String[] parts = relativePath.split("\\.");
                for (String part : parts) {
                    if (currentLevel.has(part)) {
                        JsonElement child = currentLevel.get(part);
                        if (child.isJsonObject()) {
                            currentLevel = child.getAsJsonObject();
                        } else {
                            return; // Reached a leaf value
                        }
                    } else {
                        return; // Path doesn't exist
                    }
                }
            }

            // Add completions for all keys at this level
            for (String key : currentLevel.keySet()) {
                JsonElement value = currentLevel.get(key);
                String valueType = getJsonValueType(value);
                int kind =
                        value.isJsonObject()
                                ? FlintConstants.COMPLETION_KIND_PROPERTY
                                : FlintConstants.COMPLETION_KIND_FIELD;
                String path = fullPrefix.isEmpty() ? key : fullPrefix + "." + key;
                result.addItem(createCompletionItem(key, kind, valueType, "Property at " + path));
            }

        } catch (Exception e) {
            logger.debug("Error getting completions for {}: {}", propertyTypeName, e.getMessage());
        }
    }

    /** Gets the property keys from a PropertyTree by accessing its internal MapNode structure. */
    private JsonObject getPropertyTreeJson(PropertyTree propertyTree) {
        try {
            // Access the 'root' field which is a MapNode
            java.lang.reflect.Field rootField = propertyTree.getClass().getDeclaredField("root");
            rootField.setAccessible(true);
            Object rootNode = rootField.get(propertyTree);

            if (rootNode == null) {
                return null;
            }

            // Try to access the children map field directly
            for (java.lang.reflect.Field field : rootNode.getClass().getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(rootNode);
                    if (value instanceof java.util.Map) {
                        java.util.Map<?, ?> map = (java.util.Map<?, ?>) value;
                        if (!map.isEmpty()) {
                            JsonObject jsonResult = new JsonObject();
                            for (Object key : map.keySet()) {
                                if (key instanceof String) {
                                    Object childNode = map.get(key);
                                    boolean hasChildren = checkNodeHasChildren(childNode);
                                    if (hasChildren) {
                                        jsonResult.add((String) key, new JsonObject());
                                    } else {
                                        jsonResult.addProperty((String) key, "value");
                                    }
                                }
                            }
                            if (jsonResult.size() > 0) {
                                return jsonResult;
                            }
                        }
                    }
                } catch (Exception e) {
                    // Continue to next field
                }
            }

            return null;
        } catch (Exception e) {
            logger.debug("Error accessing property tree: {}", e.getMessage());
            return null;
        }
    }

    private boolean checkNodeHasChildren(Object node) {
        try {
            for (java.lang.reflect.Field field : node.getClass().getDeclaredFields()) {
                if (field.getType().getName().contains("Map")) {
                    field.setAccessible(true);
                    Object childMap = field.get(node);
                    if (childMap instanceof java.util.Map
                            && !((java.util.Map<?, ?>) childMap).isEmpty()) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return false;
    }

    /** Gets a PropertyTree from a ComponentModel by type name using reflection. */
    private PropertyTree getPropertyTreeByName(ComponentModel component, String typeName) {
        try {
            // Find the getPropertyTreeOf method
            java.lang.reflect.Method getPropertyTreeOfMethod = null;
            Class<?> parameterType = null;

            for (java.lang.reflect.Method method : component.getClass().getMethods()) {
                if ("getPropertyTreeOf".equals(method.getName())
                        && method.getParameterCount() == 1) {
                    getPropertyTreeOfMethod = method;
                    parameterType = method.getParameterTypes()[0];
                    break;
                }
            }

            if (getPropertyTreeOfMethod != null
                    && parameterType != null
                    && parameterType.isEnum()) {
                Object[] enumConstants = parameterType.getEnumConstants();
                if (enumConstants != null) {
                    for (Object constant : enumConstants) {
                        if (constant.toString().equalsIgnoreCase(typeName)) {
                            Object result = getPropertyTreeOfMethod.invoke(component, constant);
                            if (result instanceof PropertyTree) {
                                return (PropertyTree) result;
                            }
                        }
                    }
                }
            }

            return null;
        } catch (Exception e) {
            logger.debug("Error getting property tree for {}: {}", typeName, e.getMessage());
            return null;
        }
    }

    private String getJsonValueType(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "null";
        } else if (element.isJsonPrimitive()) {
            var primitive = element.getAsJsonPrimitive();
            if (primitive.isBoolean()) return "Boolean";
            if (primitive.isNumber()) return "Number";
            if (primitive.isString()) return "String";
        } else if (element.isJsonObject()) {
            return "Object";
        } else if (element.isJsonArray()) {
            return "Array";
        }
        return "unknown";
    }

    private CompletionItem createCompletionItem(
            String label, int kind, String detail, String documentation) {
        CompletionItem item = new CompletionItem();
        item.setLabel(label);
        item.setKind(kind);
        item.setDetail(detail);
        item.setDocumentation(documentation);
        item.setInsertText(label);
        item.setInsertTextFormat(1);
        item.setSortText(label.toLowerCase());
        item.setFilterText(label);
        return item;
    }
}

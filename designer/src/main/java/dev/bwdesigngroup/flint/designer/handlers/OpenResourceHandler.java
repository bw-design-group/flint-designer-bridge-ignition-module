package dev.bwdesigngroup.flint.designer.handlers;

import com.inductiveautomation.ignition.designer.model.DesignerContext;
import com.inductiveautomation.ignition.designer.navtree.model.AbstractNavTreeNode;
import com.inductiveautomation.ignition.designer.navtree.model.ProjectBrowserRoot;
import dev.bwdesigngroup.flint.common.platform.ResourceInfo;
import dev.bwdesigngroup.flint.common.protocol.ErrorCodes;
import dev.bwdesigngroup.flint.common.protocol.JsonRpcRequest;
import dev.bwdesigngroup.flint.common.protocol.JsonRpcResponse;
import dev.bwdesigngroup.flint.common.protocol.methods.OpenResourceParams;
import dev.bwdesigngroup.flint.designer.platform.PlatformResources;
import dev.bwdesigngroup.flint.designer.server.FlintWebSocketHandler;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for the designer.openResource method. Opens a resource in the Designer's editor by
 * finding it in the project and triggering its default open action.
 */
public class OpenResourceHandler implements MethodHandler {
    private static final Logger logger = LoggerFactory.getLogger("flint.Designer.OpenResource");

    private final FlintWebSocketHandler handler;
    private final PlatformResources platformResources;

    /** Maps Flint resource type IDs to [moduleId, typeId] pairs. */
    private static final Map<String, String[]> RESOURCE_TYPE_MAP = new HashMap<>();

    static {
        RESOURCE_TYPE_MAP.put("script-python", new String[] {"ignition", "script-python"});
        RESOURCE_TYPE_MAP.put("named-query", new String[] {"ignition", "named-query"});
        RESOURCE_TYPE_MAP.put(
                "perspective-view", new String[] {"com.inductiveautomation.perspective", "views"});
        RESOURCE_TYPE_MAP.put(
                "perspective-style",
                new String[] {"com.inductiveautomation.perspective", "style-classes"});
        RESOURCE_TYPE_MAP.put(
                "perspective-page-config",
                new String[] {"com.inductiveautomation.perspective", "page-config"});
        RESOURCE_TYPE_MAP.put(
                "perspective-session-props",
                new String[] {"com.inductiveautomation.perspective", "session-props"});
        RESOURCE_TYPE_MAP.put(
                "perspective-session-events",
                new String[] {"com.inductiveautomation.perspective", "session-events"});
        RESOURCE_TYPE_MAP.put(
                "perspective-general-props",
                new String[] {"com.inductiveautomation.perspective", "general-props"});
        RESOURCE_TYPE_MAP.put("vision-window", new String[] {"fpmi", "window"});
        RESOURCE_TYPE_MAP.put("vision-template", new String[] {"fpmi", "template"});
        RESOURCE_TYPE_MAP.put(
                "report", new String[] {"com.inductiveautomation.reporting", "report"});
    }

    public OpenResourceHandler(FlintWebSocketHandler handler) {
        this.handler = handler;
        this.platformResources = handler.getPlatformResources();
    }

    @Override
    public JsonRpcResponse handle(JsonRpcRequest request) {
        Object id = request.getId();

        OpenResourceParams params;
        try {
            params = handler.getGson().fromJson(request.getParams(), OpenResourceParams.class);
        } catch (Exception e) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS, "Invalid params: " + e.getMessage(), id);
        }

        if (params == null
                || params.getResourceType() == null
                || params.getResourceType().isEmpty()) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS, "Missing required parameter: resourceType", id);
        }

        if (params.getResourcePath() == null || params.getResourcePath().isEmpty()) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS, "Missing required parameter: resourcePath", id);
        }

        String flintResourceType = params.getResourceType();
        String resourcePath = params.getResourcePath();

        logger.info("Opening resource: type={}, path={}", flintResourceType, resourcePath);

        String[] moduleAndType = RESOURCE_TYPE_MAP.get(flintResourceType);
        if (moduleAndType == null) {
            logger.warn("Unknown resource type: {}", flintResourceType);
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS,
                    "Unsupported resource type: " + flintResourceType,
                    id);
        }

        try {
            boolean success =
                    openResourceInDesigner(moduleAndType[0], moduleAndType[1], resourcePath);

            if (success) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("resourceType", flintResourceType);
                result.put("resourcePath", resourcePath);
                return JsonRpcResponse.success(result, id);
            } else {
                return JsonRpcResponse.error(
                        ErrorCodes.INTERNAL_ERROR,
                        "Failed to open resource in Designer - resource not found or could not be opened",
                        id);
            }
        } catch (Exception e) {
            logger.error("Failed to open resource", e);
            return JsonRpcResponse.error(
                    ErrorCodes.INTERNAL_ERROR, "Failed to open resource: " + e.getMessage(), id);
        }
    }

    private boolean openResourceInDesigner(String moduleId, String typeId, String resourcePath) {
        try {
            DesignerContext context = handler.getContext();

            if (!platformResources.isProjectAvailable(context)) {
                logger.warn("No project available in Designer context");
                return false;
            }

            // Find the resource
            String normalizedPath =
                    resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
            List<ResourceInfo> resources =
                    platformResources.getResourcesOfType(context, moduleId, typeId);
            ResourceInfo targetResource = null;
            for (ResourceInfo r : resources) {
                if (r.getPath().equals(normalizedPath)
                        || r.getPath().equalsIgnoreCase(normalizedPath)) {
                    targetResource = r;
                    break;
                }
            }

            if (targetResource == null) {
                logger.warn(
                        "Resource not found: type={}/{}, path={}", moduleId, typeId, resourcePath);
                return false;
            }

            Object nativeResourcePath = targetResource.getNativeResourcePath();
            logger.debug("Found resource: {}", nativeResourcePath);

            final boolean[] result = {false};
            final Exception[] error = {null};
            final ResourceInfo finalTarget = targetResource;

            SwingUtilities.invokeAndWait(
                    () -> {
                        try {
                            boolean opened = tryOpenViaWorkspace(context, finalTarget);

                            if (!opened) {
                                logger.debug(
                                        "Workspace approach failed, trying project browser node");
                                opened = tryOpenViaProjectBrowser(context, finalTarget);
                            }

                            if (opened) {
                                logger.info("Successfully opened resource");
                            } else {
                                logger.warn("Could not open resource");
                            }
                            result[0] = opened;
                        } catch (Exception e) {
                            logger.error("Failed to open resource on EDT", e);
                            error[0] = e;
                        }
                    });

            if (error[0] != null) {
                throw error[0];
            }

            return result[0];
        } catch (Exception e) {
            logger.error("Error opening resource in Designer", e);
            return false;
        }
    }

    private boolean tryOpenViaWorkspace(DesignerContext context, ResourceInfo resource) {
        try {
            Object frame = context.getFrame();
            if (frame == null) {
                return false;
            }

            Object workspaceManager = null;
            try {
                java.lang.reflect.Method getWsm = frame.getClass().getMethod("getWorkspace");
                workspaceManager = getWsm.invoke(frame);
            } catch (NoSuchMethodException e) {
                return false;
            }

            if (workspaceManager == null) {
                return false;
            }

            int workspaceCount = 0;
            try {
                java.lang.reflect.Method getCount =
                        workspaceManager.getClass().getMethod("getWorkspaceCount");
                workspaceCount = (Integer) getCount.invoke(workspaceManager);
            } catch (Exception e) {
                return false;
            }

            Object nativeResourcePath = resource.getNativeResourcePath();
            Class<?> resourcePathClass = platformResources.getResourcePathClass();

            // Get the resource type info via reflection for workspace matching
            Object nativeResourceType = null;
            String typeIdStr = null;
            try {
                java.lang.reflect.Method getResourceType =
                        nativeResourcePath.getClass().getMethod("getResourceType");
                nativeResourceType = getResourceType.invoke(nativeResourcePath);
                java.lang.reflect.Method getTypeId =
                        nativeResourceType.getClass().getMethod("typeId");
                typeIdStr = (String) getTypeId.invoke(nativeResourceType);
            } catch (Exception e) {
                try {
                    java.lang.reflect.Method getTypeId =
                            nativeResourceType.getClass().getMethod("getTypeId");
                    typeIdStr = (String) getTypeId.invoke(nativeResourceType);
                } catch (Exception e2) {
                    typeIdStr = resource.getTypeId();
                }
            }

            for (int i = 0; i < workspaceCount; i++) {
                try {
                    java.lang.reflect.Method getWorkspace =
                            workspaceManager.getClass().getMethod("getWorkspace", int.class);
                    Object workspace = getWorkspace.invoke(workspaceManager, i);

                    if (workspace == null) {
                        continue;
                    }

                    // Check if workspace handles this resource type
                    boolean typeMatches = false;

                    try {
                        java.lang.reflect.Method getResType =
                                workspace.getClass().getMethod("getResourceType");
                        Object workspaceResourceType = getResType.invoke(workspace);
                        if (workspaceResourceType != null
                                && workspaceResourceType.equals(nativeResourceType)) {
                            typeMatches = true;
                        }
                    } catch (Exception e) {
                        // Try heuristics
                    }

                    if (!typeMatches && typeIdStr != null) {
                        String wsClassName = workspace.getClass().getSimpleName().toLowerCase();
                        String targetType = typeIdStr.toLowerCase();
                        if ((wsClassName.contains("view") && targetType.contains("view"))
                                || (wsClassName.contains("query") && targetType.contains("query"))
                                || (wsClassName.contains("script") && targetType.contains("script"))
                                || (wsClassName.contains("report")
                                        && targetType.contains("report"))) {
                            typeMatches = true;
                        }
                    }

                    if (typeMatches) {
                        logger.debug(
                                "Found matching workspace: {}",
                                workspace.getClass().getSimpleName());

                        // Switch to workspace
                        try {
                            String workspaceKey = null;
                            try {
                                java.lang.reflect.Method getKey =
                                        workspace.getClass().getMethod("getKey");
                                workspaceKey = (String) getKey.invoke(workspace);
                            } catch (NoSuchMethodException e) {
                                try {
                                    java.lang.reflect.Method getName =
                                            workspace.getClass().getMethod("getName");
                                    workspaceKey = (String) getName.invoke(workspace);
                                } catch (NoSuchMethodException e2) {
                                    // OK
                                }
                            }

                            if (workspaceKey != null) {
                                java.lang.reflect.Method setSelected =
                                        workspaceManager
                                                .getClass()
                                                .getMethod("setSelectedWorkspace", String.class);
                                setSelected.invoke(workspaceManager, workspaceKey);
                            }
                        } catch (Exception e) {
                            logger.debug("Error switching workspace: {}", e.getMessage());
                        }

                        // Open the resource
                        try {
                            java.lang.reflect.Method openMethod =
                                    workspace.getClass().getMethod("open", resourcePathClass);
                            openMethod.invoke(workspace, nativeResourcePath);
                            return true;
                        } catch (NoSuchMethodException e) {
                            logger.debug("Workspace doesn't have open(ResourcePath) method");
                        } catch (Exception e) {
                            logger.debug("Error calling open(ResourcePath): {}", e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    logger.debug("Error checking workspace {}: {}", i, e.getMessage());
                }
            }

            return false;
        } catch (Exception e) {
            logger.debug("Failed to open via WorkspaceManager: {}", e.getMessage());
            return false;
        }
    }

    private boolean tryOpenViaProjectBrowser(DesignerContext context, ResourceInfo resource) {
        try {
            ProjectBrowserRoot browserRoot = context.getProjectBrowserRoot();
            if (browserRoot == null) {
                return false;
            }

            Object nativeResourcePath = resource.getNativeResourcePath();
            AbstractNavTreeNode node = findNodeForResource(browserRoot, nativeResourcePath);
            if (node == null) {
                return false;
            }

            boolean opened = tryOnEdit(node);
            if (!opened) {
                node.onDoubleClick();
                opened = true;
            }

            return opened;
        } catch (Exception e) {
            logger.debug("Failed to open via project browser: {}", e.getMessage());
            return false;
        }
    }

    private boolean tryOnEdit(AbstractNavTreeNode node) {
        try {
            java.lang.reflect.Method onEdit = node.getClass().getMethod("onEdit");
            onEdit.invoke(node);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        } catch (Exception e) {
            logger.debug("Failed to call onEdit(): {}", e.getMessage());
            return false;
        }
    }

    private AbstractNavTreeNode findNodeForResource(
            ProjectBrowserRoot browserRoot, Object targetResourcePath) {
        for (int i = 0; i < browserRoot.getChildCount(); i++) {
            Object child = browserRoot.getChildAt(i);
            if (child instanceof AbstractNavTreeNode) {
                AbstractNavTreeNode node =
                        findNodeRecursive((AbstractNavTreeNode) child, targetResourcePath);
                if (node != null) {
                    return node;
                }
            }
        }
        return null;
    }

    private AbstractNavTreeNode findNodeRecursive(
            AbstractNavTreeNode node, Object targetResourcePath) {
        try {
            java.lang.reflect.Method getResourcePath = null;
            try {
                getResourcePath = node.getClass().getMethod("getResourcePath");
            } catch (NoSuchMethodException e) {
                // Continue searching children
            }

            if (getResourcePath != null) {
                Object nodePath = getResourcePath.invoke(node);
                if (nodePath != null && nodePath.equals(targetResourcePath)) {
                    return node;
                }
            }
        } catch (Exception e) {
            logger.debug("Error checking node resource path: {}", e.getMessage());
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            Object child = node.getChildAt(i);
            if (child instanceof AbstractNavTreeNode) {
                AbstractNavTreeNode found =
                        findNodeRecursive((AbstractNavTreeNode) child, targetResourcePath);
                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }
}

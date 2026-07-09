package dev.bwdesigngroup.flint.designer.handlers;

import com.inductiveautomation.ignition.designer.model.DesignerContext;
import dev.bwdesigngroup.flint.common.protocol.JsonRpcRequest;
import dev.bwdesigngroup.flint.common.protocol.JsonRpcResponse;
import dev.bwdesigngroup.flint.designer.server.FlintWebSocketHandler;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for the designer.getOpenTabs method. Returns a list of currently open resource tabs in
 * the Designer workspace.
 *
 * <p>Iterates the WorkspaceManager's tabbed workspaces and inspects each tab component for a
 * getResourcePath() method to discover open resources.
 */
public class GetOpenTabsHandler implements MethodHandler {
    private static final Logger logger = LoggerFactory.getLogger("flint.Designer.GetOpenTabs");

    private final FlintWebSocketHandler handler;

    public GetOpenTabsHandler(FlintWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public JsonRpcResponse handle(JsonRpcRequest request) {
        Object id = request.getId();

        try {
            List<Map<String, Object>> tabs = new ArrayList<>();
            final Exception[] error = {null};

            // Must run on EDT to access Swing components
            SwingUtilities.invokeAndWait(
                    () -> {
                        try {
                            collectOpenTabs(tabs);
                        } catch (Exception e) {
                            error[0] = e;
                        }
                    });

            if (error[0] != null) {
                throw error[0];
            }

            Map<String, Object> result = new HashMap<>();
            result.put("tabs", tabs);
            result.put("count", tabs.size());
            return JsonRpcResponse.success(result, id);

        } catch (Exception e) {
            logger.error("Error getting open tabs: {}", e.getMessage(), e);
            Map<String, Object> result = new HashMap<>();
            result.put("tabs", new ArrayList<>());
            result.put("count", 0);
            result.put("error", e.getMessage());
            return JsonRpcResponse.success(result, id);
        }
    }

    /**
     * Collects open tabs from the Designer's WorkspaceManager. Uses reflection to access workspace
     * internals.
     */
    private void collectOpenTabs(List<Map<String, Object>> tabs) {
        DesignerContext context = handler.getContext();
        Object frame = context.getFrame();
        if (frame == null) {
            logger.debug("Designer frame is null");
            return;
        }

        // Get WorkspaceManager
        Object workspaceManager = invokeMethod(frame, "getWorkspace");
        if (workspaceManager == null) {
            logger.debug("WorkspaceManager is null");
            return;
        }

        // Get workspace count
        Integer workspaceCount = (Integer) invokeMethod(workspaceManager, "getWorkspaceCount");
        if (workspaceCount == null) {
            return;
        }

        for (int i = 0; i < workspaceCount; i++) {
            try {
                Method getWorkspace =
                        workspaceManager.getClass().getMethod("getWorkspace", int.class);
                Object workspace = getWorkspace.invoke(workspaceManager, i);
                if (workspace == null) continue;

                collectTabsFromWorkspace(workspace, tabs);
            } catch (Exception e) {
                logger.debug("Could not access workspace {}: {}", i, e.getMessage());
            }
        }
    }

    /**
     * Collects open tabs from a single tabbed workspace. Iterates tab components and checks each
     * for a getResourcePath() method.
     */
    private void collectTabsFromWorkspace(Object workspace, List<Map<String, Object>> tabs) {
        // Get workspace type name for context
        String workspaceName = workspace.getClass().getSimpleName();

        // Check if this workspace has tabs (getTabCount / getComponentAt)
        Integer tabCount = (Integer) invokeMethod(workspace, "getTabCount");
        if (tabCount == null || tabCount == 0) {
            return;
        }

        Method getComponentAt;
        Method getTitleAt;
        try {
            getComponentAt = workspace.getClass().getMethod("getComponentAt", int.class);
            getTitleAt = workspace.getClass().getMethod("getTitleAt", int.class);
        } catch (NoSuchMethodException e) {
            logger.debug("Workspace {} does not have tab methods", workspaceName);
            return;
        }

        for (int i = 0; i < tabCount; i++) {
            try {
                Object component = getComponentAt.invoke(workspace, i);
                if (component == null) continue;

                // Check if the tab component has getResourcePath()
                Method getResourcePath = findMethod(component.getClass(), "getResourcePath");
                if (getResourcePath == null) continue;

                Object resourcePathObj = getResourcePath.invoke(component);
                if (resourcePathObj == null
                        || !resourcePathObj.getClass().getSimpleName().contains("ResourcePath"))
                    continue;

                Map<String, Object> tab = new HashMap<>();
                try {
                    Object path =
                            resourcePathObj.getClass().getMethod("getPath").invoke(resourcePathObj);
                    tab.put("resourcePath", path != null ? path.toString() : "");

                    Object resourceType =
                            resourcePathObj
                                    .getClass()
                                    .getMethod("getResourceType")
                                    .invoke(resourcePathObj);
                    if (resourceType != null) {
                        String typeId = null;
                        try {
                            typeId =
                                    (String)
                                            resourceType
                                                    .getClass()
                                                    .getMethod("typeId")
                                                    .invoke(resourceType);
                        } catch (NoSuchMethodException e) {
                            typeId =
                                    (String)
                                            resourceType
                                                    .getClass()
                                                    .getMethod("getTypeId")
                                                    .invoke(resourceType);
                        }
                        tab.put("resourceType", typeId != null ? typeId : "");

                        String moduleId = null;
                        try {
                            moduleId =
                                    (String)
                                            resourceType
                                                    .getClass()
                                                    .getMethod("moduleId")
                                                    .invoke(resourceType);
                        } catch (NoSuchMethodException e) {
                            moduleId =
                                    (String)
                                            resourceType
                                                    .getClass()
                                                    .getMethod("getModuleId")
                                                    .invoke(resourceType);
                        }
                        tab.put("moduleId", moduleId != null ? moduleId : "");
                    }
                } catch (Exception e) {
                    logger.debug(
                            "Could not extract resource path details via reflection: {}",
                            e.getMessage());
                    continue;
                }
                tab.put("workspace", workspaceName);

                // Include tab title
                try {
                    String title = (String) getTitleAt.invoke(workspace, i);
                    if (title != null && !title.isEmpty()) {
                        tab.put("title", title);
                    }
                } catch (Exception ignored) {
                }

                tabs.add(tab);
            } catch (Exception e) {
                logger.debug(
                        "Could not inspect tab {} in workspace {}: {}",
                        i,
                        workspaceName,
                        e.getMessage());
            }
        }
    }

    private Object invokeMethod(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (Exception e) {
            // Try declared method
            try {
                Method method = target.getClass().getDeclaredMethod(methodName);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private Method findMethod(Class<?> clazz, String name) {
        try {
            Method method = clazz.getMethod(name);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            // Try declared methods up the hierarchy
            Class<?> current = clazz;
            while (current != null) {
                try {
                    Method method = current.getDeclaredMethod(name);
                    method.setAccessible(true);
                    return method;
                } catch (NoSuchMethodException ignored) {
                }
                current = current.getSuperclass();
            }
        }
        return null;
    }
}

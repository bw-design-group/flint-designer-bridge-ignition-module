package dev.bwdesigngroup.flint.designer.handlers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.inductiveautomation.ignition.designer.model.DesignerContext;
import dev.bwdesigngroup.flint.common.protocol.JsonRpcRequest;
import dev.bwdesigngroup.flint.common.protocol.JsonRpcResponse;
import dev.bwdesigngroup.flint.designer.server.FlintWebSocketHandler;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for the designer.togglePreviewMode method. Finds the "Preview Mode" JCheckBoxMenuItem in
 * the Project menu and clicks it.
 */
public class PreviewModeHandler implements MethodHandler {
    private static final Logger logger = LoggerFactory.getLogger("flint.Designer.PreviewMode");

    private final FlintWebSocketHandler handler;

    public PreviewModeHandler(FlintWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public JsonRpcResponse handle(JsonRpcRequest request) {
        Object id = request.getId();
        JsonObject params = getParams(request);
        final Boolean enable;
        if (params.has("enable") && params.get("enable").isJsonPrimitive()) {
            enable = params.get("enable").getAsBoolean();
        } else {
            enable = null;
        }

        try {
            Map<String, Object> result = new HashMap<>();
            final Exception[] error = {null};

            SwingUtilities.invokeAndWait(
                    () -> {
                        try {
                            togglePreviewMode(enable, result);
                        } catch (Exception e) {
                            error[0] = e;
                        }
                    });

            if (error[0] != null) {
                throw error[0];
            }

            return JsonRpcResponse.success(result, id);

        } catch (Exception e) {
            logger.error("Error toggling preview mode: {}", e.getMessage(), e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("error", e.getMessage());
            return JsonRpcResponse.success(errorResult, id);
        }
    }

    private void togglePreviewMode(Boolean enable, Map<String, Object> result) throws Exception {
        DesignerContext context = handler.getContext();
        Object frame = context.getFrame();
        if (frame == null) {
            throw new Exception("Designer frame is null");
        }

        // Find the current view path from the active Perspective editor
        String viewPath = findActiveViewPath(frame);

        // Find the "Preview Mode" checkbox menu item in the menu bar
        JCheckBoxMenuItem previewItem = findPreviewMenuItem(frame);
        if (previewItem == null) {
            throw new Exception(
                    "Preview Mode menu item not found. Ensure a Perspective view is open.");
        }

        boolean currentState = previewItem.isSelected();
        if (enable != null && enable == currentState) {
            result.put("previewMode", currentState);
            result.put("viewPath", viewPath != null ? viewPath : "unknown");
            result.put("toggled", false);
            return;
        }

        previewItem.doClick();

        result.put("previewMode", !currentState);
        result.put("viewPath", viewPath != null ? viewPath : "unknown");
        result.put("toggled", true);
    }

    /**
     * Finds the "Preview Mode" JCheckBoxMenuItem by scanning the menu bar. The item is in the
     * "Project" menu.
     */
    private JCheckBoxMenuItem findPreviewMenuItem(Object frame) {
        // Get the JMenuBar from the frame
        JMenuBar menuBar = null;
        if (frame instanceof JFrame) {
            menuBar = ((JFrame) frame).getJMenuBar();
        } else {
            Object menuBarObj = invokeMethod(frame, "getJMenuBar");
            if (menuBarObj instanceof JMenuBar) {
                menuBar = (JMenuBar) menuBarObj;
            }
        }

        if (menuBar == null) {
            logger.debug("No menu bar found on frame");
            return null;
        }

        // Scan all menus for a "Preview Mode" checkbox item
        for (int i = 0; i < menuBar.getMenuCount(); i++) {
            JMenu menu = menuBar.getMenu(i);
            if (menu == null) continue;

            for (int j = 0; j < menu.getItemCount(); j++) {
                JMenuItem item = menu.getItem(j);
                if (item instanceof JCheckBoxMenuItem) {
                    String text = item.getText();
                    if (text != null && text.toLowerCase().contains("preview")) {
                        return (JCheckBoxMenuItem) item;
                    }
                }
            }
        }

        return null;
    }

    /** Finds the view path of the currently active Perspective editor tab. */
    private String findActiveViewPath(Object frame) {
        try {
            Object workspaceManager = invokeMethod(frame, "getWorkspace");
            if (workspaceManager == null) return null;

            Integer workspaceCount = (Integer) invokeMethod(workspaceManager, "getWorkspaceCount");
            if (workspaceCount == null) return null;

            for (int i = 0; i < workspaceCount; i++) {
                Method getWorkspace =
                        workspaceManager.getClass().getMethod("getWorkspace", int.class);
                Object workspace = getWorkspace.invoke(workspaceManager, i);
                if (workspace == null) continue;

                Integer selectedIndex = (Integer) invokeMethod(workspace, "getSelectedIndex");
                if (selectedIndex == null || selectedIndex < 0) continue;

                Integer tabCount = (Integer) invokeMethod(workspace, "getTabCount");
                if (tabCount == null || selectedIndex >= tabCount) continue;

                Method getComponentAt = workspace.getClass().getMethod("getComponentAt", int.class);
                Object component = getComponentAt.invoke(workspace, selectedIndex);
                if (component == null) continue;

                Method getResourcePath = findMethod(component.getClass(), "getResourcePath");
                if (getResourcePath != null) {
                    Object resourcePathObj = getResourcePath.invoke(component);
                    if (resourcePathObj != null
                            && resourcePathObj
                                    .getClass()
                                    .getSimpleName()
                                    .contains("ResourcePath")) {
                        try {
                            Object resourceType =
                                    resourcePathObj
                                            .getClass()
                                            .getMethod("getResourceType")
                                            .invoke(resourcePathObj);
                            String moduleId = null;
                            if (resourceType != null) {
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
                            }
                            if (moduleId != null && moduleId.contains("perspective")) {
                                Object path =
                                        resourcePathObj
                                                .getClass()
                                                .getMethod("getPath")
                                                .invoke(resourcePathObj);
                                return path != null ? path.toString() : null;
                            }
                        } catch (Exception e) {
                            logger.debug(
                                    "Could not extract resource path details via reflection: {}",
                                    e.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Could not find active view path: {}", e.getMessage());
        }
        return null;
    }

    private Object invokeMethod(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (Exception e) {
            try {
                Method method = target.getClass().getDeclaredMethod(methodName);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private JsonObject getParams(JsonRpcRequest request) {
        JsonElement paramsElement = request.getParams();
        if (paramsElement != null && paramsElement.isJsonObject()) {
            return paramsElement.getAsJsonObject();
        }
        return new JsonObject();
    }

    private Method findMethod(Class<?> clazz, String name) {
        try {
            Method method = clazz.getMethod(name);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
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

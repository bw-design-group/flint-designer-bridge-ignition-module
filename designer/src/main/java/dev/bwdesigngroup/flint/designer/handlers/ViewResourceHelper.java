package dev.bwdesigngroup.flint.designer.handlers;

import com.google.gson.JsonObject;
import com.inductiveautomation.ignition.designer.model.DesignerContext;
import dev.bwdesigngroup.flint.common.platform.ResourceInfo;
import dev.bwdesigngroup.flint.common.protocol.methods.view.ViewTreeResult.ComponentTreeNode;
import dev.bwdesigngroup.flint.common.view.ViewJsonUtil;
import dev.bwdesigngroup.flint.designer.platform.PlatformResources;
import java.awt.Frame;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared utility for view resource read/write/refresh operations. Uses PlatformResources
 * abstraction for version-independent resource access.
 */
public class ViewResourceHelper {
    private static final Logger logger =
            LoggerFactory.getLogger("flint.Designer.ViewResourceHelper");

    private static final String PERSPECTIVE_MODULE_ID = "com.inductiveautomation.perspective";
    private static final String PERSPECTIVE_VIEW_TYPE_ID = "views";
    private static final String VIEW_JSON_KEY = "view.json";

    private final DesignerContext context;
    private final PlatformResources platformResources;

    public ViewResourceHelper(DesignerContext context, PlatformResources platformResources) {
        this.context = context;
        this.platformResources = platformResources;
    }

    /** Finds a Perspective view resource by path. */
    public ResourceInfo findViewResource(String viewPath) {
        if (!platformResources.isProjectAvailable(context)) {
            logger.warn("No project available in Designer context");
            return null;
        }

        String normalizedPath = viewPath.startsWith("/") ? viewPath.substring(1) : viewPath;

        List<ResourceInfo> resources =
                platformResources.getResourcesOfType(
                        context, PERSPECTIVE_MODULE_ID, PERSPECTIVE_VIEW_TYPE_ID);

        for (ResourceInfo resource : resources) {
            if (resource.getPath().equals(normalizedPath)
                    || resource.getPath().equalsIgnoreCase(normalizedPath)) {
                return resource;
            }
        }

        return null;
    }

    /** Reads the view.json content from a resource. */
    public String readViewJson(ResourceInfo resource) {
        try {
            byte[] data = platformResources.readResourceData(resource, VIEW_JSON_KEY);

            if (data == null || data.length == 0) {
                data = platformResources.readDefaultData(resource);
            }

            if (data == null || data.length == 0) {
                logger.warn("View resource has no view.json data");
                return null;
            }

            return new String(data, StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.error("Failed to read view.json", e);
            return null;
        }
    }

    /** Writes JSON content back to the view resource's view.json. */
    public boolean writeViewJson(ResourceInfo resource, String json) {
        try {
            byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
            return platformResources.writeResourceData(context, resource, VIEW_JSON_KEY, jsonBytes);
        } catch (Exception e) {
            logger.error("Failed to write view.json", e);
            return false;
        }
    }

    /** Checks if a view is currently open in a Designer editor tab. */
    public boolean isViewOpenInEditor(String viewPath) {
        try {
            String normalizedPath = viewPath.startsWith("/") ? viewPath.substring(1) : viewPath;

            final boolean[] result = {false};
            SwingUtilities.invokeAndWait(
                    () -> {
                        try {
                            result[0] = checkViewOpenInEditor(normalizedPath);
                        } catch (Exception e) {
                            logger.error("Error checking editor state on EDT", e);
                        }
                    });

            return result[0];
        } catch (Exception e) {
            logger.error("Failed to check editor state", e);
            return false;
        }
    }

    /**
     * Replaces a view's content using an atomic delete+create pattern. Returns null on success, or
     * an error message string on failure.
     */
    public String replaceViewContent(String viewPath, String newJson) {
        String normalizedPath = viewPath.startsWith("/") ? viewPath.substring(1) : viewPath;

        ResourceInfo existing = findViewResource(normalizedPath);
        if (existing == null) {
            return "View not found: " + normalizedPath;
        }

        String backup = readViewJson(existing);

        String deleteError = deleteView(normalizedPath);
        if (deleteError != null) {
            return "Failed to delete view for replace: " + deleteError;
        }

        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        String createError = createView(normalizedPath, newJson);
        if (createError != null) {
            logger.error(
                    "Failed to create view during replace, attempting restore: {}", createError);
            if (backup != null) {
                String restoreError = createView(normalizedPath, backup);
                if (restoreError != null) {
                    logger.error("Failed to restore backup after failed replace: {}", restoreError);
                    return "Replace failed and backup restore failed: "
                            + createError
                            + " / "
                            + restoreError;
                }
            }
            return "Failed to create view during replace: " + createError;
        }

        logger.info("Replaced view content via delete+create: {}", normalizedPath);
        return null;
    }

    /** Triggers the Designer's project save action via the File > Save menu item. */
    public boolean triggerSave() {
        try {
            Frame designerFrame = null;
            for (Frame frame : Frame.getFrames()) {
                if (frame.getClass().getName().contains("IgnitionDesigner")) {
                    designerFrame = frame;
                    break;
                }
            }

            if (designerFrame == null) {
                logger.warn("Could not find IgnitionDesigner frame");
                return false;
            }

            JMenuBar menuBar = null;
            if (designerFrame instanceof javax.swing.JFrame) {
                menuBar = ((javax.swing.JFrame) designerFrame).getJMenuBar();
            }

            if (menuBar == null) {
                logger.warn("No menu bar found on Designer frame");
                return false;
            }

            JMenu fileMenu = null;
            for (int i = 0; i < menuBar.getMenuCount(); i++) {
                JMenu menu = menuBar.getMenu(i);
                if (menu != null && "File".equals(menu.getText())) {
                    fileMenu = menu;
                    break;
                }
            }

            if (fileMenu == null) {
                logger.warn("Could not find File menu");
                return false;
            }

            JMenuItem saveItem = null;
            for (int i = 0; i < fileMenu.getItemCount(); i++) {
                JMenuItem item = fileMenu.getItem(i);
                if (item != null) {
                    String text = item.getText();
                    if ("Save All".equals(text) || "Save".equals(text)) {
                        saveItem = item;
                        break;
                    }
                }
            }

            if (saveItem == null) {
                logger.warn("Could not find Save/Save All menu item");
                return false;
            }

            final JMenuItem finalSaveItem = saveItem;
            SwingUtilities.invokeLater(
                    () -> {
                        try {
                            finalSaveItem.doClick();
                            logger.info("Triggered project save via File > Save menu");
                        } catch (Exception e) {
                            logger.error("Error clicking Save menu item", e);
                        }
                    });

            return true;
        } catch (Exception e) {
            logger.error("Failed to trigger save", e);
            return false;
        }
    }

    /** Navigates to a component within the view JSON by path. */
    public JsonObject navigateToComponent(JsonObject viewJson, String componentPath) {
        return ViewJsonUtil.navigateToComponent(viewJson, componentPath);
    }

    /** Replaces a component at the given path within the view JSON. */
    public JsonObject replaceComponent(
            JsonObject viewJson, String componentPath, JsonObject newComponent) {
        return ViewJsonUtil.replaceComponent(viewJson, componentPath, newComponent);
    }

    /** Builds a component tree summary from view JSON. */
    public ComponentTreeNode buildComponentTree(JsonObject viewJson) {
        return ViewJsonUtil.buildComponentTree(viewJson);
    }

    /** Counts total components in the tree. */
    public int countComponents(JsonObject viewJson) {
        return ViewJsonUtil.countComponents(viewJson);
    }

    /**
     * Creates a new Perspective view at the given path with the provided JSON config. Returns null
     * on success, or an error message string on failure.
     */
    public String createView(String viewPath, String json) {
        try {
            String normalizedPath = viewPath.startsWith("/") ? viewPath.substring(1) : viewPath;

            if (findViewResource(normalizedPath) != null) {
                return "VIEW_ALREADY_EXISTS";
            }

            if (!platformResources.isProjectAvailable(context)) {
                return "No project available in Designer context";
            }

            // Get an existing view resource to use as a template
            List<ResourceInfo> existingViews =
                    platformResources.getResourcesOfType(
                            context, PERSPECTIVE_MODULE_ID, PERSPECTIVE_VIEW_TYPE_ID);
            if (existingViews.isEmpty()) {
                return "No existing view resources to use as template. Create a view manually in Designer first.";
            }

            ResourceInfo template = existingViews.get(0);
            byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);

            String error =
                    platformResources.createResource(
                            context,
                            template,
                            PERSPECTIVE_MODULE_ID,
                            PERSPECTIVE_VIEW_TYPE_ID,
                            normalizedPath,
                            VIEW_JSON_KEY,
                            jsonBytes);

            if (error == null) {
                logger.info("Created view: {}", normalizedPath);
            }
            return error;
        } catch (Exception e) {
            logger.error("Failed to create view: {}", viewPath, e);
            return "Failed to create view: " + e.getMessage();
        }
    }

    /**
     * Deletes a Perspective view at the given path. Returns null on success, or an error message
     * string on failure.
     */
    public String deleteView(String viewPath) {
        try {
            String normalizedPath = viewPath.startsWith("/") ? viewPath.substring(1) : viewPath;

            ResourceInfo resource = findViewResource(normalizedPath);
            if (resource == null) {
                return "VIEW_NOT_FOUND";
            }

            String error = platformResources.deleteResource(context, resource);
            if (error == null) {
                logger.info("Deleted view: {}", normalizedPath);
            }
            return error;
        } catch (Exception e) {
            logger.error("Failed to delete view: {}", viewPath, e);
            return "Failed to delete view: " + e.getMessage();
        }
    }

    // --- Private helpers ---

    private boolean checkViewOpenInEditor(String normalizedPath) {
        try {
            Object frame = context.getFrame();
            if (frame == null) {
                return false;
            }

            Object workspaceManager;
            try {
                Method getWsm = frame.getClass().getMethod("getWorkspace");
                workspaceManager = getWsm.invoke(frame);
            } catch (NoSuchMethodException e) {
                return false;
            }

            if (workspaceManager == null) {
                return false;
            }

            Integer workspaceCount;
            try {
                Method getCount = workspaceManager.getClass().getMethod("getWorkspaceCount");
                workspaceCount = (Integer) getCount.invoke(workspaceManager);
            } catch (Exception e) {
                return false;
            }

            if (workspaceCount == null) {
                return false;
            }

            Class<?> resourcePathClass = platformResources.getResourcePathClass();

            for (int i = 0; i < workspaceCount; i++) {
                try {
                    Method getWorkspace =
                            workspaceManager.getClass().getMethod("getWorkspace", int.class);
                    Object workspace = getWorkspace.invoke(workspaceManager, i);
                    if (workspace == null) {
                        continue;
                    }

                    Integer tabCount = null;
                    try {
                        Method getTabCount = workspace.getClass().getMethod("getTabCount");
                        tabCount = (Integer) getTabCount.invoke(workspace);
                    } catch (Exception e) {
                        continue;
                    }

                    if (tabCount == null || tabCount == 0) {
                        continue;
                    }

                    Method getComponentAt;
                    try {
                        getComponentAt =
                                workspace.getClass().getMethod("getComponentAt", int.class);
                    } catch (NoSuchMethodException e) {
                        continue;
                    }

                    for (int j = 0; j < tabCount; j++) {
                        try {
                            Object component = getComponentAt.invoke(workspace, j);
                            if (component == null) {
                                continue;
                            }

                            Method getResourcePath =
                                    findMethodOnClass(component.getClass(), "getResourcePath");
                            if (getResourcePath == null) {
                                continue;
                            }

                            Object resourcePathObj = getResourcePath.invoke(component);
                            if (!resourcePathClass.isInstance(resourcePathObj)) {
                                continue;
                            }

                            // Get path string via reflection
                            Method getPath = resourcePathObj.getClass().getMethod("getPath");
                            Object pathObj = getPath.invoke(resourcePathObj);
                            String pathStr = pathObj != null ? pathObj.toString() : null;
                            if (pathStr == null) {
                                continue;
                            }

                            if (pathStr.startsWith("/")) {
                                pathStr = pathStr.substring(1);
                            }

                            if (pathStr.equals(normalizedPath)
                                    || pathStr.equalsIgnoreCase(normalizedPath)) {
                                return true;
                            }
                        } catch (Exception e) {
                            logger.debug(
                                    "Could not inspect tab {} in workspace: {}", j, e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    logger.debug("Error checking workspace {}: {}", i, e.getMessage());
                }
            }

            return false;
        } catch (Exception e) {
            logger.error("Error checking if view is open in editor", e);
            return false;
        }
    }

    private Method findMethodOnClass(Class<?> clazz, String name) {
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

package dev.bwdesigngroup.flint.gateway.resources;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.bwdesigngroup.flint.common.platform.ResourceInfo;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Version-neutral orchestration over a {@link GatewayResourceStore}: project resolution, the Flint
 * resource-type map, resource listing, view read/write helpers, and the view catalog. Mirrors the
 * Designer's {@code ListResourcesHandler}/{@code ViewResourceHelper} without any Swing dependency.
 */
public class GatewayResourceService {

    private static final Logger logger = LoggerFactory.getLogger("flint.Gateway.Resources");

    public static final String PERSPECTIVE_MODULE_ID = "com.inductiveautomation.perspective";
    public static final String PERSPECTIVE_VIEW_TYPE_ID = "views";
    public static final String VIEW_JSON_KEY = "view.json";

    /** Maps Flint resource type IDs to [moduleId, typeId] pairs. */
    private static final Map<String, String[]> RESOURCE_TYPE_MAP = new LinkedHashMap<>();

    static {
        RESOURCE_TYPE_MAP.put("script-python", new String[] {"ignition", "script-python"});
        RESOURCE_TYPE_MAP.put("named-query", new String[] {"ignition", "named-query"});
        RESOURCE_TYPE_MAP.put("perspective-view", new String[] {PERSPECTIVE_MODULE_ID, "views"});
        RESOURCE_TYPE_MAP.put(
                "perspective-style", new String[] {PERSPECTIVE_MODULE_ID, "style-classes"});
        RESOURCE_TYPE_MAP.put(
                "perspective-page-config", new String[] {PERSPECTIVE_MODULE_ID, "page-config"});
        RESOURCE_TYPE_MAP.put(
                "perspective-session-props", new String[] {PERSPECTIVE_MODULE_ID, "session-props"});
        RESOURCE_TYPE_MAP.put(
                "perspective-session-events",
                new String[] {PERSPECTIVE_MODULE_ID, "session-events"});
        RESOURCE_TYPE_MAP.put(
                "perspective-general-props", new String[] {PERSPECTIVE_MODULE_ID, "general-props"});
        RESOURCE_TYPE_MAP.put("vision-window", new String[] {"fpmi", "window"});
        RESOURCE_TYPE_MAP.put("vision-template", new String[] {"fpmi", "template"});
        RESOURCE_TYPE_MAP.put(
                "report", new String[] {"com.inductiveautomation.reporting", "report"});
    }

    private final GatewayResourceStore store;

    public GatewayResourceService(GatewayResourceStore store) {
        this.store = store;
    }

    public GatewayResourceStore getStore() {
        return store;
    }

    /**
     * Resolves the effective project name. Uses the requested name if given; otherwise the single
     * project if only one exists; otherwise the configured default; otherwise null.
     */
    public String resolveProject(String requested) {
        if (requested != null && !requested.isEmpty()) {
            return requested;
        }
        List<String> names = store.listProjectNames();
        if (names.size() == 1) {
            return names.get(0);
        }
        String configured = System.getProperty("flint.gateway.project");
        if (configured == null || configured.isEmpty()) {
            configured = System.getenv("FLINT_GATEWAY_PROJECT");
        }
        if (configured != null && !configured.isEmpty()) {
            return configured;
        }
        return null;
    }

    public List<String> listProjectNames() {
        return store.listProjectNames();
    }

    /** Lists resources in a project, optionally filtered by Flint resource type id. */
    public Map<String, Object> listResources(String project, String filterType) {
        List<Map<String, Object>> resources = new ArrayList<>();

        for (Map.Entry<String, String[]> entry : RESOURCE_TYPE_MAP.entrySet()) {
            String flintTypeId = entry.getKey();
            if (filterType != null && !filterType.equals(flintTypeId)) {
                continue;
            }
            String[] moduleAndType = entry.getValue();
            try {
                for (ResourceInfo res :
                        store.getResourcesOfType(project, moduleAndType[0], moduleAndType[1])) {
                    Map<String, Object> info = new HashMap<>();
                    info.put("resourceType", flintTypeId);
                    info.put("resourcePath", res.getPath());
                    info.put("moduleId", res.getModuleId());
                    info.put("typeId", res.getTypeId());
                    resources.add(info);
                }
            } catch (Exception e) {
                logger.debug("Could not list {}: {}", flintTypeId, e.getMessage());
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("projectName", store.getProjectTitle(project));
        result.put("resources", resources);
        result.put("count", resources.size());
        return result;
    }

    // --- View helpers ---

    public ResourceInfo findViewResource(String project, String viewPath) {
        String normalized = viewPath.startsWith("/") ? viewPath.substring(1) : viewPath;
        for (ResourceInfo resource :
                store.getResourcesOfType(
                        project, PERSPECTIVE_MODULE_ID, PERSPECTIVE_VIEW_TYPE_ID)) {
            if (resource.getPath().equalsIgnoreCase(normalized)) {
                return resource;
            }
        }
        return null;
    }

    public String readViewJson(ResourceInfo resource) {
        byte[] data = store.readResourceData(resource, VIEW_JSON_KEY);
        if (data == null || data.length == 0) {
            data = store.readDefaultData(resource);
        }
        if (data == null || data.length == 0) {
            return null;
        }
        return new String(data, StandardCharsets.UTF_8);
    }

    public String writeViewJson(String project, ResourceInfo resource, String json) {
        return store.writeResourceData(
                project, resource, VIEW_JSON_KEY, json.getBytes(StandardCharsets.UTF_8));
    }

    /** Creates a view, cloning an existing view resource for attributes. */
    public String createView(String project, String viewPath, String json) {
        String normalized = viewPath.startsWith("/") ? viewPath.substring(1) : viewPath;
        if (findViewResource(project, normalized) != null) {
            return "VIEW_ALREADY_EXISTS";
        }
        List<ResourceInfo> existingViews =
                store.getResourcesOfType(project, PERSPECTIVE_MODULE_ID, PERSPECTIVE_VIEW_TYPE_ID);
        if (existingViews.isEmpty()) {
            return "No existing view resources to use as a template. Create one view in Designer "
                    + "first, or ensure the project has at least one Perspective view.";
        }
        return store.createResource(
                project,
                existingViews.get(0),
                PERSPECTIVE_MODULE_ID,
                PERSPECTIVE_VIEW_TYPE_ID,
                normalized,
                VIEW_JSON_KEY,
                json.getBytes(StandardCharsets.UTF_8));
    }

    public String deleteView(String project, String viewPath) {
        String normalized = viewPath.startsWith("/") ? viewPath.substring(1) : viewPath;
        ResourceInfo resource = findViewResource(project, normalized);
        if (resource == null) {
            return "VIEW_NOT_FOUND";
        }
        return store.deleteResource(project, resource);
    }

    /** Builds a lightweight catalog of all views in a project (path + params + root type). */
    public Map<String, Object> getViewCatalog(String project) {
        List<Map<String, Object>> views = new ArrayList<>();
        for (ResourceInfo resource :
                store.getResourcesOfType(
                        project, PERSPECTIVE_MODULE_ID, PERSPECTIVE_VIEW_TYPE_ID)) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("viewPath", resource.getPath());
            try {
                String json = readViewJson(resource);
                if (json != null) {
                    JsonObject viewJson = JsonParser.parseString(json).getAsJsonObject();
                    if (viewJson.has("params") && viewJson.get("params").isJsonObject()) {
                        entry.put(
                                "params",
                                new ArrayList<>(viewJson.getAsJsonObject("params").keySet()));
                    }
                    if (viewJson.has("root") && viewJson.get("root").isJsonObject()) {
                        JsonObject root = viewJson.getAsJsonObject("root");
                        if (root.has("type")) {
                            entry.put("rootType", root.get("type").getAsString());
                        }
                    }
                }
            } catch (Exception e) {
                logger.debug("Could not parse view {}: {}", resource.getPath(), e.getMessage());
            }
            views.add(entry);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("views", views);
        result.put("count", views.size());
        return result;
    }
}

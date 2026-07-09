package dev.bwdesigngroup.flint.designer.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.inductiveautomation.ignition.designer.model.DesignerContext;
import dev.bwdesigngroup.flint.common.FlintConstants;
import dev.bwdesigngroup.flint.common.platform.ResourceInfo;
import dev.bwdesigngroup.flint.common.protocol.ErrorCodes;
import dev.bwdesigngroup.flint.common.protocol.JsonRpcRequest;
import dev.bwdesigngroup.flint.common.protocol.JsonRpcResponse;
import dev.bwdesigngroup.flint.designer.platform.PlatformResources;
import dev.bwdesigngroup.flint.designer.server.FlintWebSocketHandler;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for project.getViewCatalog JSON-RPC method. Enumerates all Perspective views, extracts
 * metadata, and builds an embedded view graph.
 */
public class ViewCatalogHandler implements MethodHandler {
    private static final Logger logger = LoggerFactory.getLogger("flint.Designer.ViewCatalog");

    private static final String PERSPECTIVE_MODULE_ID = "com.inductiveautomation.perspective";
    private static final String PERSPECTIVE_VIEW_TYPE_ID = "views";
    private static final String VIEW_JSON_KEY = "view.json";

    private final FlintWebSocketHandler handler;
    private final DesignerContext context;
    private final PlatformResources platformResources;

    public ViewCatalogHandler(FlintWebSocketHandler handler) {
        this.handler = handler;
        this.context = handler.getContext();
        this.platformResources = handler.getPlatformResources();
    }

    @Override
    public JsonRpcResponse handle(JsonRpcRequest request) {
        String method = request.getMethod();
        Object id = request.getId();

        if (FlintConstants.METHOD_VIEW_CATALOG.equals(method)) {
            return handleGetViewCatalog(request);
        }

        return JsonRpcResponse.error(ErrorCodes.METHOD_NOT_FOUND, "Unknown method: " + method, id);
    }

    private JsonRpcResponse handleGetViewCatalog(JsonRpcRequest request) {
        Object id = request.getId();
        JsonObject params = getParams(request);
        String folderFilter = getStringParam(params, "folder");

        if (!platformResources.isProjectAvailable(context)) {
            return JsonRpcResponse.error(
                    ErrorCodes.INTERNAL_ERROR, "No project available in Designer context", id);
        }

        List<ResourceInfo> viewResources =
                platformResources.getResourcesOfType(
                        context, PERSPECTIVE_MODULE_ID, PERSPECTIVE_VIEW_TYPE_ID);
        List<Map<String, Object>> views = new ArrayList<>();

        Map<String, List<String>> forwardGraph = new HashMap<>();

        for (ResourceInfo resource : viewResources) {
            String viewPath = resource.getPath();

            // Apply folder filter if provided
            if (folderFilter != null && !viewPath.startsWith(folderFilter)) {
                continue;
            }

            // Read and parse view.json
            String json = readViewJson(resource);
            if (json == null) continue;

            JsonObject viewJson;
            try {
                viewJson = JsonParser.parseString(json).getAsJsonObject();
            } catch (Exception e) {
                logger.debug("Failed to parse view.json for {}: {}", viewPath, e.getMessage());
                continue;
            }

            Map<String, Object> viewInfo = new HashMap<>();
            viewInfo.put("path", viewPath);

            Map<String, String> customProperties = extractCustomProperties(viewJson);
            viewInfo.put("customProperties", customProperties);

            Map<String, String> viewParams = extractParams(viewJson);
            viewInfo.put("params", viewParams);

            JsonObject root = viewJson.has("root") ? viewJson.getAsJsonObject("root") : null;
            if (root != null) {
                viewInfo.put(
                        "rootType", root.has("type") ? root.get("type").getAsString() : "unknown");
                viewInfo.put("componentCount", countComponents(root));
            } else {
                viewInfo.put("rootType", "unknown");
                viewInfo.put("componentCount", 0);
            }

            List<String> embeddedViews = new ArrayList<>();
            if (root != null) {
                findEmbeddedViews(root, embeddedViews);
            }
            viewInfo.put("embeddedViews", embeddedViews);
            forwardGraph.put(viewPath, embeddedViews);

            views.add(viewInfo);
        }

        // Compute reverse references
        Map<String, Set<String>> reverseGraph = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : forwardGraph.entrySet()) {
            String parentView = entry.getKey();
            for (String embeddedView : entry.getValue()) {
                reverseGraph.computeIfAbsent(embeddedView, k -> new HashSet<>()).add(parentView);
            }
        }

        for (Map<String, Object> viewInfo : views) {
            String path = (String) viewInfo.get("path");
            Set<String> embeddedBy = reverseGraph.getOrDefault(path, new HashSet<>());
            viewInfo.put("embeddedBy", new ArrayList<>(embeddedBy));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("views", views);
        result.put("graph", forwardGraph);
        result.put("count", views.size());
        return JsonRpcResponse.success(result, id);
    }

    private String readViewJson(ResourceInfo resource) {
        try {
            byte[] data = platformResources.readResourceData(resource, VIEW_JSON_KEY);

            if (data == null || data.length == 0) {
                data = platformResources.readDefaultData(resource);
            }

            if (data == null || data.length == 0) {
                return null;
            }

            return new String(data, StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.debug("Failed to read view.json: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, String> extractCustomProperties(JsonObject viewJson) {
        Map<String, String> properties = new HashMap<>();
        if (!viewJson.has("custom") || !viewJson.get("custom").isJsonObject()) {
            return properties;
        }
        JsonObject custom = viewJson.getAsJsonObject("custom");
        for (String key : custom.keySet()) {
            properties.put(key, inferType(custom.get(key)));
        }
        return properties;
    }

    private Map<String, String> extractParams(JsonObject viewJson) {
        Map<String, String> params = new HashMap<>();
        if (!viewJson.has("params") || !viewJson.get("params").isJsonObject()) {
            return params;
        }
        JsonObject paramsObj = viewJson.getAsJsonObject("params");
        for (String key : paramsObj.keySet()) {
            params.put(key, inferType(paramsObj.get(key)));
        }
        return params;
    }

    private String inferType(JsonElement value) {
        if (value == null || value.isJsonNull()) return "null";
        if (value.isJsonPrimitive()) {
            if (value.getAsJsonPrimitive().isBoolean()) return "boolean";
            if (value.getAsJsonPrimitive().isNumber()) return "number";
            return "string";
        }
        if (value.isJsonArray()) return "array";
        if (value.isJsonObject()) return "object";
        return "unknown";
    }

    private void findEmbeddedViews(JsonObject component, List<String> embeddedViews) {
        if (component.has("type")) {
            String type = component.get("type").getAsString();
            if ("ia.display.view".equals(type)) {
                if (component.has("props") && component.get("props").isJsonObject()) {
                    JsonObject props = component.getAsJsonObject("props");
                    if (props.has("path") && props.get("path").isJsonPrimitive()) {
                        String path = props.get("path").getAsString();
                        if (!path.isEmpty()) {
                            embeddedViews.add(path);
                        }
                    }
                }
            }
        }
        if (component.has("children") && component.get("children").isJsonArray()) {
            JsonArray children = component.getAsJsonArray("children");
            for (int i = 0; i < children.size(); i++) {
                if (children.get(i).isJsonObject()) {
                    findEmbeddedViews(children.get(i).getAsJsonObject(), embeddedViews);
                }
            }
        }
    }

    private int countComponents(JsonObject component) {
        int count = 1;
        if (component.has("children") && component.get("children").isJsonArray()) {
            JsonArray children = component.getAsJsonArray("children");
            for (int i = 0; i < children.size(); i++) {
                if (children.get(i).isJsonObject()) {
                    count += countComponents(children.get(i).getAsJsonObject());
                }
            }
        }
        return count;
    }

    private JsonObject getParams(JsonRpcRequest request) {
        JsonElement paramsElement = request.getParams();
        if (paramsElement != null && paramsElement.isJsonObject()) {
            return paramsElement.getAsJsonObject();
        }
        return new JsonObject();
    }

    private String getStringParam(JsonObject params, String key) {
        if (params.has(key) && params.get(key).isJsonPrimitive()) {
            return params.get(key).getAsString();
        }
        return null;
    }
}

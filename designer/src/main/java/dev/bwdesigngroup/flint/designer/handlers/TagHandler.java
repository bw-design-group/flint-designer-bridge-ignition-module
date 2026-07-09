package dev.bwdesigngroup.flint.designer.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.bwdesigngroup.flint.common.FlintConstants;
import dev.bwdesigngroup.flint.common.protocol.ErrorCodes;
import dev.bwdesigngroup.flint.common.protocol.JsonRpcRequest;
import dev.bwdesigngroup.flint.common.protocol.JsonRpcResponse;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.TagBrowseResult;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.TagCreateResult;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.TagDeleteResult;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.TagEditResult;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.TagGetConfigResult;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.TagProvidersResult;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.TagReadResult;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.TagWriteResult;
import dev.bwdesigngroup.flint.designer.platform.PlatformFactory;
import dev.bwdesigngroup.flint.designer.rpc.GatewayRpcClient;
import dev.bwdesigngroup.flint.designer.server.FlintWebSocketHandler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Handler for all tags.* JSON-RPC methods. Routes tag operations to the Gateway via RPC. */
public class TagHandler implements MethodHandler {
    private static final Logger logger = LoggerFactory.getLogger("flint.Designer.Tags");

    private final FlintWebSocketHandler handler;
    private final GatewayRpcClient gatewayRpcClient;

    public TagHandler(FlintWebSocketHandler handler) {
        this.handler = handler;
        this.gatewayRpcClient = PlatformFactory.createGatewayRpcClient();
        this.gatewayRpcClient.initialize();
    }

    /** Constructor for testing with an injected RPC client. */
    TagHandler(FlintWebSocketHandler handler, GatewayRpcClient gatewayRpcClient) {
        this.handler = handler;
        this.gatewayRpcClient = gatewayRpcClient;
    }

    @Override
    public JsonRpcResponse handle(JsonRpcRequest request) {
        String method = request.getMethod();
        Object id = request.getId();

        try {
            switch (method) {
                case FlintConstants.METHOD_TAGS_BROWSE:
                    return handleBrowse(request);
                case FlintConstants.METHOD_TAGS_READ:
                    return handleRead(request);
                case FlintConstants.METHOD_TAGS_WRITE:
                    return handleWrite(request);
                case FlintConstants.METHOD_TAGS_GET_CONFIG:
                    return handleGetConfig(request);
                case FlintConstants.METHOD_TAGS_CREATE:
                    return handleCreate(request);
                case FlintConstants.METHOD_TAGS_EDIT:
                    return handleEdit(request);
                case FlintConstants.METHOD_TAGS_DELETE:
                    return handleDelete(request);
                case FlintConstants.METHOD_TAGS_GET_PROVIDERS:
                    return handleGetProviders(request);
                default:
                    return JsonRpcResponse.error(
                            ErrorCodes.METHOD_NOT_FOUND, "Unknown tag method: " + method, id);
            }
        } catch (Exception e) {
            logger.error("Tag operation failed: {} - {}", method, e.getMessage(), e);
            return JsonRpcResponse.error(
                    ErrorCodes.GATEWAY_RPC_ERROR, "Tag operation failed: " + e.getMessage(), id);
        }
    }

    private JsonRpcResponse handleBrowse(JsonRpcRequest request) {
        Object id = request.getId();
        JsonObject params = getParams(request);

        String provider = getStringParam(params, "provider");
        String parentPath = getStringParam(params, "parentPath");
        String typeFilter = getStringParam(params, "typeFilter");
        String nameFilter = getStringParam(params, "nameFilter");

        TagBrowseResult result =
                gatewayRpcClient
                        .getRpc()
                        .tagBrowse(
                                provider != null ? provider : "default",
                                parentPath != null ? parentPath : "",
                                typeFilter,
                                nameFilter);

        return JsonRpcResponse.success(result, id);
    }

    private JsonRpcResponse handleRead(JsonRpcRequest request) {
        Object id = request.getId();
        JsonObject params = getParams(request);

        List<String> tagPaths = getStringListParam(params, "tagPaths");
        if (tagPaths == null || tagPaths.isEmpty()) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS, "Missing required parameter: tagPaths", id);
        }

        TagReadResult result = gatewayRpcClient.getRpc().tagRead(tagPaths);
        return JsonRpcResponse.success(result, id);
    }

    private JsonRpcResponse handleWrite(JsonRpcRequest request) {
        Object id = request.getId();
        JsonObject params = getParams(request);

        // Writes come as an array of {path, value} objects
        if (!params.has("writes") || !params.get("writes").isJsonArray()) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS,
                    "Missing required parameter: writes (array of {path, value})",
                    id);
        }

        JsonArray writes = params.getAsJsonArray("writes");
        List<String> paths = new ArrayList<>();
        List<String> values = new ArrayList<>();
        List<String> dataTypes = new ArrayList<>();

        for (JsonElement elem : writes) {
            JsonObject write = elem.getAsJsonObject();
            paths.add(write.get("path").getAsString());
            values.add(write.has("value") ? write.get("value").getAsString() : "");
            dataTypes.add(write.has("dataType") ? write.get("dataType").getAsString() : "");
        }

        TagWriteResult result = gatewayRpcClient.getRpc().tagWrite(paths, values, dataTypes);
        return JsonRpcResponse.success(result, id);
    }

    private JsonRpcResponse handleGetConfig(JsonRpcRequest request) {
        Object id = request.getId();
        JsonObject params = getParams(request);

        String tagPath = getStringParam(params, "tagPath");
        if (tagPath == null) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS, "Missing required parameter: tagPath", id);
        }

        TagGetConfigResult result = gatewayRpcClient.getRpc().tagGetConfig(tagPath);

        // Parse the config JSON string back to an object for the response
        Map<String, Object> response = new HashMap<>();
        response.put("path", result.getPath());
        try {
            JsonElement configElement = com.google.gson.JsonParser.parseString(result.getConfig());
            response.put("config", configElement);
        } catch (Exception e) {
            response.put("config", result.getConfig());
        }
        return JsonRpcResponse.success(response, id);
    }

    private JsonRpcResponse handleCreate(JsonRpcRequest request) {
        Object id = request.getId();
        JsonObject params = getParams(request);

        String parentPath = getStringParam(params, "parentPath");
        if (parentPath == null) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS, "Missing required parameter: parentPath", id);
        }

        if (!params.has("tags") || !params.get("tags").isJsonArray()) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS,
                    "Missing required parameter: tags (array of tag configs)",
                    id);
        }

        String tagsJson = handler.getGson().toJson(params.getAsJsonArray("tags"));
        TagCreateResult result = gatewayRpcClient.getRpc().tagCreate(parentPath, tagsJson);
        return JsonRpcResponse.success(result, id);
    }

    private JsonRpcResponse handleEdit(JsonRpcRequest request) {
        Object id = request.getId();
        JsonObject params = getParams(request);

        String tagPath = getStringParam(params, "tagPath");
        if (tagPath == null) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS, "Missing required parameter: tagPath", id);
        }

        if (!params.has("config") || !params.get("config").isJsonObject()) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS,
                    "Missing required parameter: config (JSON object)",
                    id);
        }

        String configJson = handler.getGson().toJson(params.getAsJsonObject("config"));
        TagEditResult result = gatewayRpcClient.getRpc().tagEdit(tagPath, configJson);
        return JsonRpcResponse.success(result, id);
    }

    private JsonRpcResponse handleDelete(JsonRpcRequest request) {
        Object id = request.getId();
        JsonObject params = getParams(request);

        List<String> tagPaths = getStringListParam(params, "tagPaths");
        if (tagPaths == null || tagPaths.isEmpty()) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS, "Missing required parameter: tagPaths", id);
        }

        TagDeleteResult result = gatewayRpcClient.getRpc().tagDelete(tagPaths);
        return JsonRpcResponse.success(result, id);
    }

    private JsonRpcResponse handleGetProviders(JsonRpcRequest request) {
        Object id = request.getId();

        TagProvidersResult result = gatewayRpcClient.getRpc().tagGetProviders();
        return JsonRpcResponse.success(result, id);
    }

    // --- Param helpers ---

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

    private List<String> getStringListParam(JsonObject params, String key) {
        if (!params.has(key) || !params.get(key).isJsonArray()) {
            return null;
        }
        JsonArray array = params.getAsJsonArray(key);
        List<String> result = new ArrayList<>();
        for (JsonElement elem : array) {
            result.add(elem.getAsString());
        }
        return result;
    }
}

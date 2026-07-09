package dev.bwdesigngroup.flint.designer.handlers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.bwdesigngroup.flint.common.FlintConstants;
import dev.bwdesigngroup.flint.common.protocol.ErrorCodes;
import dev.bwdesigngroup.flint.common.protocol.JsonRpcRequest;
import dev.bwdesigngroup.flint.common.protocol.JsonRpcResponse;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.TagCreateResult;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.UdtDefinitionResult;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.UdtListResult;
import dev.bwdesigngroup.flint.designer.platform.PlatformFactory;
import dev.bwdesigngroup.flint.designer.rpc.GatewayRpcClient;
import dev.bwdesigngroup.flint.designer.server.FlintWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Handler for all udt.* JSON-RPC methods. Routes UDT operations to the Gateway via RPC. */
public class UdtHandler implements MethodHandler {
    private static final Logger logger = LoggerFactory.getLogger("flint.Designer.Udt");

    private final FlintWebSocketHandler handler;
    private final GatewayRpcClient gatewayRpcClient;

    public UdtHandler(FlintWebSocketHandler handler) {
        this.handler = handler;
        this.gatewayRpcClient = PlatformFactory.createGatewayRpcClient();
        this.gatewayRpcClient.initialize();
    }

    /** Constructor for testing with an injected RPC client. */
    UdtHandler(FlintWebSocketHandler handler, GatewayRpcClient gatewayRpcClient) {
        this.handler = handler;
        this.gatewayRpcClient = gatewayRpcClient;
    }

    @Override
    public JsonRpcResponse handle(JsonRpcRequest request) {
        String method = request.getMethod();
        Object id = request.getId();

        try {
            switch (method) {
                case FlintConstants.METHOD_UDT_GET_DEFINITIONS:
                    return handleGetDefinitions(request);
                case FlintConstants.METHOD_UDT_GET_DEFINITION:
                    return handleGetDefinition(request);
                case FlintConstants.METHOD_UDT_CREATE_DEFINITION:
                    return handleCreateDefinition(request);
                case FlintConstants.METHOD_UDT_CREATE_INSTANCE:
                    return handleCreateInstance(request);
                default:
                    return JsonRpcResponse.error(
                            ErrorCodes.METHOD_NOT_FOUND, "Unknown UDT method: " + method, id);
            }
        } catch (Exception e) {
            logger.error("UDT operation failed: {} - {}", method, e.getMessage(), e);
            return JsonRpcResponse.error(
                    ErrorCodes.GATEWAY_RPC_ERROR, "UDT operation failed: " + e.getMessage(), id);
        }
    }

    private JsonRpcResponse handleGetDefinitions(JsonRpcRequest request) {
        Object id = request.getId();
        JsonObject params = getParams(request);

        String provider = getStringParam(params, "provider");

        UdtListResult result =
                gatewayRpcClient
                        .getRpc()
                        .udtGetDefinitions(provider != null ? provider : "default");

        return JsonRpcResponse.success(result, id);
    }

    private JsonRpcResponse handleGetDefinition(JsonRpcRequest request) {
        Object id = request.getId();
        JsonObject params = getParams(request);

        String provider = getStringParam(params, "provider");
        String typePath = getStringParam(params, "typePath");

        if (typePath == null) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS, "Missing required parameter: typePath", id);
        }

        UdtDefinitionResult result =
                gatewayRpcClient
                        .getRpc()
                        .udtGetDefinition(provider != null ? provider : "default", typePath);

        return JsonRpcResponse.success(result, id);
    }

    private JsonRpcResponse handleCreateDefinition(JsonRpcRequest request) {
        Object id = request.getId();
        JsonObject params = getParams(request);

        String provider = getStringParam(params, "provider");
        String name = getStringParam(params, "name");
        String parentTypePath = getStringParam(params, "parentTypePath");

        if (name == null) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS, "Missing required parameter: name", id);
        }

        String membersJson = null;
        if (params.has("members") && params.get("members").isJsonArray()) {
            membersJson = handler.getGson().toJson(params.getAsJsonArray("members"));
        }

        TagCreateResult result =
                gatewayRpcClient
                        .getRpc()
                        .udtCreateDefinition(
                                provider != null ? provider : "default",
                                name,
                                parentTypePath != null ? parentTypePath : "",
                                membersJson);

        return JsonRpcResponse.success(result, id);
    }

    private JsonRpcResponse handleCreateInstance(JsonRpcRequest request) {
        Object id = request.getId();
        JsonObject params = getParams(request);

        String parentPath = getStringParam(params, "parentPath");
        String name = getStringParam(params, "name");
        String typeId = getStringParam(params, "typeId");

        if (parentPath == null) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS, "Missing required parameter: parentPath", id);
        }
        if (name == null) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS, "Missing required parameter: name", id);
        }
        if (typeId == null) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS, "Missing required parameter: typeId", id);
        }

        String overridesJson = null;
        if (params.has("overrides") && params.get("overrides").isJsonObject()) {
            overridesJson = handler.getGson().toJson(params.getAsJsonObject("overrides"));
        }

        TagCreateResult result =
                gatewayRpcClient
                        .getRpc()
                        .udtCreateInstance(parentPath, name, typeId, overridesJson);

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
}

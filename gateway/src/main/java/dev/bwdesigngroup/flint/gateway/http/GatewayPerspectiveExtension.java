package dev.bwdesigngroup.flint.gateway.http;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.bwdesigngroup.flint.common.FlintConstants;
import dev.bwdesigngroup.flint.common.protocol.ErrorCodes;
import dev.bwdesigngroup.flint.common.protocol.JsonRpcRequest;
import dev.bwdesigngroup.flint.common.protocol.JsonRpcResponse;
import dev.bwdesigngroup.flint.gateway.perspective.GatewayPerspectiveRegistry;
import java.util.Map;

/**
 * Dispatch extension adding Perspective component-registry and icon-library methods headless:
 * {@code component.list}, {@code component.getSchema}, {@code icon.list}, {@code icon.search}.
 * Backed by {@link GatewayPerspectiveRegistry} ({@code PerspectiveContext} component registry +
 * icon manager). Returns a clear error when Perspective isn't available.
 */
public class GatewayPerspectiveExtension implements GatewayRpcDispatcher.DispatchExtension {

    private final GatewayPerspectiveRegistry registry;

    public GatewayPerspectiveExtension(GatewayPerspectiveRegistry registry) {
        this.registry = registry;
    }

    @Override
    public JsonRpcResponse tryDispatch(JsonRpcRequest request) {
        String method = request.getMethod();
        Object id = request.getId();

        switch (method) {
            case FlintConstants.METHOD_COMPONENT_LIST:
            case FlintConstants.METHOD_COMPONENT_GET_SCHEMA:
            case FlintConstants.METHOD_ICON_LIST:
            case FlintConstants.METHOD_ICON_SEARCH:
                break;
            default:
                return null; // not handled by this extension
        }

        if (!registry.isAvailable()) {
            return JsonRpcResponse.error(
                    ErrorCodes.GATEWAY_NOT_AVAILABLE,
                    "Perspective is not installed or not running on this gateway",
                    id);
        }

        JsonObject params = params(request);
        try {
            switch (method) {
                case FlintConstants.METHOD_COMPONENT_LIST:
                    return JsonRpcResponse.success(registry.listComponents(), id);
                case FlintConstants.METHOD_COMPONENT_GET_SCHEMA:
                    {
                        String componentId = getString(params, "componentId");
                        if (componentId == null) {
                            componentId = getString(params, "component");
                        }
                        if (componentId == null) {
                            return JsonRpcResponse.error(
                                    ErrorCodes.INVALID_PARAMS,
                                    "Missing required parameter: componentId",
                                    id);
                        }
                        Map<String, Object> schema = registry.getComponentSchema(componentId);
                        if (schema == null) {
                            return JsonRpcResponse.error(
                                    ErrorCodes.INVALID_PARAMS,
                                    "Component not found: " + componentId,
                                    id);
                        }
                        return JsonRpcResponse.success(schema, id);
                    }
                case FlintConstants.METHOD_ICON_LIST:
                    return JsonRpcResponse.success(
                            registry.listIcons(getString(params, "library")), id);
                case FlintConstants.METHOD_ICON_SEARCH:
                    {
                        String query = getString(params, "query");
                        if (query == null) {
                            query = getString(params, "term");
                        }
                        if (query == null) {
                            return JsonRpcResponse.error(
                                    ErrorCodes.INVALID_PARAMS,
                                    "Missing required parameter: query",
                                    id);
                        }
                        return JsonRpcResponse.success(registry.searchIcons(query), id);
                    }
                default:
                    return null;
            }
        } catch (Exception e) {
            return JsonRpcResponse.error(
                    ErrorCodes.INTERNAL_ERROR, "Perspective registry error: " + e.getMessage(), id);
        }
    }

    private JsonObject params(JsonRpcRequest request) {
        JsonElement paramsElement = request.getParams();
        if (paramsElement != null && paramsElement.isJsonObject()) {
            return paramsElement.getAsJsonObject();
        }
        return new JsonObject();
    }

    private String getString(JsonObject params, String key) {
        if (params.has(key) && params.get(key).isJsonPrimitive()) {
            return params.get(key).getAsString();
        }
        return null;
    }
}

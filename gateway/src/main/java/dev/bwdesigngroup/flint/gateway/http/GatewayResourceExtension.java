package dev.bwdesigngroup.flint.gateway.http;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.bwdesigngroup.flint.common.FlintConstants;
import dev.bwdesigngroup.flint.common.protocol.ErrorCodes;
import dev.bwdesigngroup.flint.common.protocol.JsonRpcRequest;
import dev.bwdesigngroup.flint.common.protocol.JsonRpcResponse;
import dev.bwdesigngroup.flint.gateway.resources.GatewayResourceService;
import dev.bwdesigngroup.flint.gateway.view.GatewayViewService;

/**
 * Dispatch extension that adds project resource + view methods to the {@link GatewayRpcDispatcher}:
 * {@code project.listResources}, {@code project.getViewCatalog}, and all {@code view.*} methods.
 * All are project-scoped; the project is taken from a {@code project} param, or resolved to the
 * single project / configured default.
 */
public class GatewayResourceExtension implements GatewayRpcDispatcher.DispatchExtension {

    private final GatewayResourceService resources;
    private final GatewayViewService views;

    public GatewayResourceExtension(GatewayResourceService resources, GatewayViewService views) {
        this.resources = resources;
        this.views = views;
    }

    @Override
    public JsonRpcResponse tryDispatch(JsonRpcRequest request) {
        String method = request.getMethod();
        Object id = request.getId();
        JsonObject params = params(request);

        switch (method) {
            case FlintConstants.METHOD_LIST_RESOURCES:
                {
                    String project = resolveProject(params, id);
                    if (project == null) {
                        return noProject(id);
                    }
                    String filterType =
                            params.has("resourceType") && !params.get("resourceType").isJsonNull()
                                    ? params.get("resourceType").getAsString()
                                    : null;
                    return JsonRpcResponse.success(
                            resources.listResources(project, filterType), id);
                }
            case FlintConstants.METHOD_VIEW_CATALOG:
                {
                    String project = resolveProject(params, id);
                    if (project == null) {
                        return noProject(id);
                    }
                    return JsonRpcResponse.success(resources.getViewCatalog(project), id);
                }
            case FlintConstants.METHOD_VIEW_GET_CONFIG:
            case FlintConstants.METHOD_VIEW_SET_CONFIG:
            case FlintConstants.METHOD_VIEW_GET_COMPONENT:
            case FlintConstants.METHOD_VIEW_SET_COMPONENT:
            case FlintConstants.METHOD_VIEW_VALIDATE:
            case FlintConstants.METHOD_VIEW_GET_TREE:
            case FlintConstants.METHOD_VIEW_SAVE:
            case FlintConstants.METHOD_VIEW_CREATE:
            case FlintConstants.METHOD_VIEW_DELETE:
                return dispatchView(method, params, id);
            default:
                return null; // not handled by this extension
        }
    }

    private JsonRpcResponse dispatchView(String method, JsonObject params, Object id) {
        String project = resolveProject(params, id);
        if (project == null) {
            return noProject(id);
        }
        switch (method) {
            case FlintConstants.METHOD_VIEW_GET_CONFIG:
                return views.getConfig(project, params, id);
            case FlintConstants.METHOD_VIEW_SET_CONFIG:
                return views.setConfig(project, params, id);
            case FlintConstants.METHOD_VIEW_GET_COMPONENT:
                return views.getComponent(project, params, id);
            case FlintConstants.METHOD_VIEW_SET_COMPONENT:
                return views.setComponent(project, params, id);
            case FlintConstants.METHOD_VIEW_VALIDATE:
                return views.validate(project, params, id);
            case FlintConstants.METHOD_VIEW_GET_TREE:
                return views.getTree(project, params, id);
            case FlintConstants.METHOD_VIEW_SAVE:
                return views.save(project, params, id);
            case FlintConstants.METHOD_VIEW_CREATE:
                return views.create(project, params, id);
            case FlintConstants.METHOD_VIEW_DELETE:
                return views.delete(project, params, id);
            default:
                return null;
        }
    }

    private String resolveProject(JsonObject params, Object id) {
        String requested = null;
        if (params.has("project") && params.get("project").isJsonPrimitive()) {
            requested = params.get("project").getAsString();
        }
        return resources.resolveProject(requested);
    }

    private JsonRpcResponse noProject(Object id) {
        return JsonRpcResponse.error(
                ErrorCodes.INVALID_PARAMS,
                "No project specified and none could be resolved. Pass a 'project' param. "
                        + "Available projects: "
                        + resources.listProjectNames(),
                id);
    }

    private JsonObject params(JsonRpcRequest request) {
        JsonElement paramsElement = request.getParams();
        if (paramsElement != null && paramsElement.isJsonObject()) {
            return paramsElement.getAsJsonObject();
        }
        return new JsonObject();
    }
}

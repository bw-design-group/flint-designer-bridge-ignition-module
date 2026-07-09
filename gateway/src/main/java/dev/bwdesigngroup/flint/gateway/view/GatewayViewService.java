package dev.bwdesigngroup.flint.gateway.view;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.bwdesigngroup.flint.common.platform.ResourceInfo;
import dev.bwdesigngroup.flint.common.protocol.ErrorCodes;
import dev.bwdesigngroup.flint.common.protocol.JsonRpcResponse;
import dev.bwdesigngroup.flint.common.protocol.methods.view.ViewTreeResult;
import dev.bwdesigngroup.flint.common.protocol.methods.view.ViewValidationResult;
import dev.bwdesigngroup.flint.common.view.ViewJsonUtil;
import dev.bwdesigngroup.flint.gateway.resources.GatewayResourceService;
import java.util.HashMap;
import java.util.Map;

/**
 * Headless gateway implementation of the {@code view.*} JSON-RPC methods. Mirrors the Designer's
 * {@code ViewConfigHandler} minus the Swing/editor behaviors: writes commit immediately (no "editor
 * open" delete+create dance) and {@code view.save} is a no-op success.
 */
public class GatewayViewService {

    private static final String DEFAULT_VIEW_JSON =
            "{\"root\":{\"type\":\"ia.container.flex\",\"version\":0,"
                    + "\"props\":{\"direction\":\"column\"},\"meta\":{\"name\":\"root\"},"
                    + "\"children\":[]}}";

    private final GatewayResourceService resources;
    private final GatewayViewValidator validator;
    private final Gson gson;

    public GatewayViewService(GatewayResourceService resources, Gson gson) {
        this.resources = resources;
        this.validator = new GatewayViewValidator();
        this.gson = gson;
    }

    public JsonRpcResponse getConfig(String project, JsonObject params, Object id) {
        String viewPath = str(params, "viewPath");
        if (viewPath == null) {
            return missing(id, "viewPath");
        }
        ResourceInfo resource = resources.findViewResource(project, viewPath);
        if (resource == null) {
            return notFound(id, viewPath);
        }
        String json = resources.readViewJson(resource);
        if (json == null) {
            return JsonRpcResponse.error(
                    ErrorCodes.VIEW_NOT_FOUND, "Could not read view.json for: " + viewPath, id);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("viewPath", viewPath);
        result.put("config", JsonParser.parseString(json).getAsJsonObject());
        return JsonRpcResponse.success(result, id);
    }

    public JsonRpcResponse setConfig(String project, JsonObject params, Object id) {
        String viewPath = str(params, "viewPath");
        if (viewPath == null) {
            return missing(id, "viewPath");
        }
        if (!params.has("config") || !params.get("config").isJsonObject()) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS,
                    "Missing or invalid required parameter: config (must be a JSON object)",
                    id);
        }
        JsonObject config = params.getAsJsonObject("config");

        ViewValidationResult validation = validator.validate(config);
        if (!validation.isValid()) {
            return validationError(id, validation, "View validation failed");
        }

        ResourceInfo resource = resources.findViewResource(project, viewPath);
        if (resource == null) {
            return notFound(id, viewPath);
        }
        String writeError = resources.writeViewJson(project, resource, gson.toJson(config));
        if (writeError != null) {
            return JsonRpcResponse.error(
                    ErrorCodes.VIEW_WRITE_ERROR, "Failed to write view: " + writeError, id);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("viewPath", viewPath);
        result.put("success", true);
        if (!validation.getWarnings().isEmpty()) {
            result.put("warnings", validation.getWarnings());
        }
        return JsonRpcResponse.success(result, id);
    }

    public JsonRpcResponse getComponent(String project, JsonObject params, Object id) {
        String viewPath = str(params, "viewPath");
        String componentPath = str(params, "componentPath");
        if (viewPath == null || componentPath == null) {
            return missing(id, "viewPath, componentPath");
        }
        JsonObject viewJson = readViewJsonObj(project, viewPath, id);
        if (viewJson == null) {
            return notFound(id, viewPath);
        }
        JsonObject component = ViewJsonUtil.navigateToComponent(viewJson, componentPath);
        if (component == null) {
            return JsonRpcResponse.error(
                    ErrorCodes.COMPONENT_NOT_FOUND,
                    "Component not found at path: " + componentPath,
                    id);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("viewPath", viewPath);
        result.put("componentPath", componentPath);
        result.put("component", component);
        return JsonRpcResponse.success(result, id);
    }

    public JsonRpcResponse setComponent(String project, JsonObject params, Object id) {
        String viewPath = str(params, "viewPath");
        String componentPath = str(params, "componentPath");
        if (viewPath == null || componentPath == null) {
            return missing(id, "viewPath, componentPath");
        }
        if (!params.has("component") || !params.get("component").isJsonObject()) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS,
                    "Missing or invalid required parameter: component (must be a JSON object)",
                    id);
        }
        JsonObject newComponent = params.getAsJsonObject("component");

        ResourceInfo resource = resources.findViewResource(project, viewPath);
        if (resource == null) {
            return notFound(id, viewPath);
        }
        String json = resources.readViewJson(resource);
        if (json == null) {
            return JsonRpcResponse.error(
                    ErrorCodes.VIEW_NOT_FOUND, "Could not read view.json for: " + viewPath, id);
        }
        JsonObject viewJson = JsonParser.parseString(json).getAsJsonObject();

        JsonObject tempView = new JsonObject();
        tempView.add("root", newComponent);
        ViewValidationResult validation = validator.validate(tempView);
        if (!validation.isValid()) {
            return validationError(id, validation, "Component validation failed");
        }

        JsonObject modified = ViewJsonUtil.replaceComponent(viewJson, componentPath, newComponent);
        if (modified == null) {
            return JsonRpcResponse.error(
                    ErrorCodes.COMPONENT_NOT_FOUND,
                    "Component not found at path: " + componentPath,
                    id);
        }
        String writeError = resources.writeViewJson(project, resource, gson.toJson(modified));
        if (writeError != null) {
            return JsonRpcResponse.error(
                    ErrorCodes.VIEW_WRITE_ERROR, "Failed to write view: " + writeError, id);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("viewPath", viewPath);
        result.put("componentPath", componentPath);
        result.put("success", true);
        return JsonRpcResponse.success(result, id);
    }

    public JsonRpcResponse validate(String project, JsonObject params, Object id) {
        JsonObject config = null;
        if (params.has("config") && params.get("config").isJsonObject()) {
            config = params.getAsJsonObject("config");
        } else if (params.has("viewPath")) {
            JsonObject viewJson = readViewJsonObj(project, str(params, "viewPath"), id);
            if (viewJson == null) {
                return notFound(id, str(params, "viewPath"));
            }
            config = viewJson;
        } else {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS,
                    "Either 'viewPath' or 'config' parameter is required",
                    id);
        }

        ViewValidationResult validation = validator.validate(config);
        Map<String, Object> result = new HashMap<>();
        result.put("valid", validation.isValid());
        result.put("errors", validation.getErrors());
        result.put("warnings", validation.getWarnings());
        result.put("registeredComponentTypes", validator.getRegisteredComponentTypes());
        return JsonRpcResponse.success(result, id);
    }

    public JsonRpcResponse getTree(String project, JsonObject params, Object id) {
        String viewPath = str(params, "viewPath");
        if (viewPath == null) {
            return missing(id, "viewPath");
        }
        JsonObject viewJson = readViewJsonObj(project, viewPath, id);
        if (viewJson == null) {
            return notFound(id, viewPath);
        }
        ViewTreeResult.ComponentTreeNode tree = ViewJsonUtil.buildComponentTree(viewJson);
        int total = ViewJsonUtil.countComponents(viewJson);
        return JsonRpcResponse.success(new ViewTreeResult(viewPath, tree, total), id);
    }

    public JsonRpcResponse save(String project, JsonObject params, Object id) {
        // Gateway writes commit immediately (push + scan); nothing to save.
        Map<String, Object> result = new HashMap<>();
        result.put("saved", true);
        result.put("message", "Gateway writes are committed immediately; no explicit save needed.");
        return JsonRpcResponse.success(result, id);
    }

    public JsonRpcResponse create(String project, JsonObject params, Object id) {
        String viewPath = str(params, "viewPath");
        if (viewPath == null) {
            return missing(id, "viewPath");
        }
        String json;
        if (params.has("config") && params.get("config").isJsonObject()) {
            ViewValidationResult validation = validator.validate(params.getAsJsonObject("config"));
            if (!validation.isValid()) {
                return validationError(id, validation, "View validation failed");
            }
            json = gson.toJson(params.getAsJsonObject("config"));
        } else {
            json = DEFAULT_VIEW_JSON;
        }

        String error = resources.createView(project, viewPath, json);
        if (error != null) {
            if ("VIEW_ALREADY_EXISTS".equals(error)) {
                return JsonRpcResponse.error(
                        ErrorCodes.VIEW_ALREADY_EXISTS, "View already exists: " + viewPath, id);
            }
            return JsonRpcResponse.error(ErrorCodes.VIEW_WRITE_ERROR, error, id);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("viewPath", viewPath);
        result.put("created", true);
        return JsonRpcResponse.success(result, id);
    }

    public JsonRpcResponse delete(String project, JsonObject params, Object id) {
        String viewPath = str(params, "viewPath");
        if (viewPath == null) {
            return missing(id, "viewPath");
        }
        String error = resources.deleteView(project, viewPath);
        if (error != null) {
            if ("VIEW_NOT_FOUND".equals(error)) {
                return notFound(id, viewPath);
            }
            return JsonRpcResponse.error(ErrorCodes.VIEW_WRITE_ERROR, error, id);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("viewPath", viewPath);
        result.put("deleted", true);
        return JsonRpcResponse.success(result, id);
    }

    // --- helpers ---

    private JsonObject readViewJsonObj(String project, String viewPath, Object id) {
        if (viewPath == null) {
            return null;
        }
        ResourceInfo resource = resources.findViewResource(project, viewPath);
        if (resource == null) {
            return null;
        }
        String json = resources.readViewJson(resource);
        if (json == null) {
            return null;
        }
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private String str(JsonObject params, String key) {
        if (params.has(key) && params.get(key).isJsonPrimitive()) {
            return params.get(key).getAsString();
        }
        return null;
    }

    private JsonRpcResponse missing(Object id, String names) {
        return JsonRpcResponse.error(
                ErrorCodes.INVALID_PARAMS, "Missing required parameter(s): " + names, id);
    }

    private JsonRpcResponse notFound(Object id, String viewPath) {
        return JsonRpcResponse.error(ErrorCodes.VIEW_NOT_FOUND, "View not found: " + viewPath, id);
    }

    private JsonRpcResponse validationError(
            Object id, ViewValidationResult validation, String message) {
        Map<String, Object> errorData = new HashMap<>();
        errorData.put("valid", false);
        errorData.put("errors", validation.getErrors());
        errorData.put("warnings", validation.getWarnings());
        return JsonRpcResponse.error(ErrorCodes.VIEW_VALIDATION_ERROR, message, errorData, id);
    }
}

package dev.bwdesigngroup.flint.designer.handlers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.inductiveautomation.ignition.designer.model.DesignerContext;
import dev.bwdesigngroup.flint.common.FlintConstants;
import dev.bwdesigngroup.flint.common.platform.ResourceInfo;
import dev.bwdesigngroup.flint.common.protocol.ErrorCodes;
import dev.bwdesigngroup.flint.common.protocol.JsonRpcRequest;
import dev.bwdesigngroup.flint.common.protocol.JsonRpcResponse;
import dev.bwdesigngroup.flint.common.protocol.methods.view.ViewTreeResult;
import dev.bwdesigngroup.flint.common.protocol.methods.view.ViewTreeResult.ComponentTreeNode;
import dev.bwdesigngroup.flint.common.protocol.methods.view.ViewValidationResult;
import dev.bwdesigngroup.flint.designer.server.FlintWebSocketHandler;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for all view.* JSON-RPC methods. Routes to the appropriate operation based on the method
 * name.
 */
public class ViewConfigHandler implements MethodHandler {
    private static final Logger logger = LoggerFactory.getLogger("flint.Designer.ViewConfig");

    private final FlintWebSocketHandler handler;
    private final ViewResourceHelper resourceHelper;
    private final ViewValidator validator;

    public ViewConfigHandler(FlintWebSocketHandler handler) {
        this.handler = handler;
        DesignerContext context = handler.getContext();
        this.resourceHelper = new ViewResourceHelper(context, handler.getPlatformResources());
        this.validator = new ViewValidator(context);
    }

    @Override
    public JsonRpcResponse handle(JsonRpcRequest request) {
        String method = request.getMethod();
        Object id = request.getId();

        switch (method) {
            case FlintConstants.METHOD_VIEW_GET_CONFIG:
                return handleGetConfig(request);
            case FlintConstants.METHOD_VIEW_SET_CONFIG:
                return handleSetConfig(request);
            case FlintConstants.METHOD_VIEW_GET_COMPONENT:
                return handleGetComponent(request);
            case FlintConstants.METHOD_VIEW_SET_COMPONENT:
                return handleSetComponent(request);
            case FlintConstants.METHOD_VIEW_VALIDATE:
                return handleValidate(request);
            case FlintConstants.METHOD_VIEW_GET_TREE:
                return handleGetTree(request);
            case FlintConstants.METHOD_VIEW_SAVE:
                return handleSave(request);
            case FlintConstants.METHOD_VIEW_CREATE:
                return handleCreate(request);
            case FlintConstants.METHOD_VIEW_DELETE:
                return handleDelete(request);
            default:
                return JsonRpcResponse.error(
                        ErrorCodes.METHOD_NOT_FOUND, "Unknown view method: " + method, id);
        }
    }

    /** view.getConfig - Read full view JSON config. */
    private JsonRpcResponse handleGetConfig(JsonRpcRequest request) {
        Object id = request.getId();
        JsonObject params = getParams(request);

        String viewPath = getStringParam(params, "viewPath");
        if (viewPath == null) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS, "Missing required parameter: viewPath", id);
        }

        ResourceInfo resource = resourceHelper.findViewResource(viewPath);
        if (resource == null) {
            return JsonRpcResponse.error(
                    ErrorCodes.VIEW_NOT_FOUND, "View not found: " + viewPath, id);
        }

        String json = resourceHelper.readViewJson(resource);
        if (json == null) {
            return JsonRpcResponse.error(
                    ErrorCodes.VIEW_NOT_FOUND, "Could not read view.json for: " + viewPath, id);
        }

        JsonObject viewJson = JsonParser.parseString(json).getAsJsonObject();

        Map<String, Object> result = new HashMap<>();
        result.put("viewPath", viewPath);
        result.put("config", viewJson);
        return JsonRpcResponse.success(result, id);
    }

    /** view.setConfig - Write complete view config (validates first). */
    private JsonRpcResponse handleSetConfig(JsonRpcRequest request) {
        Object id = request.getId();
        JsonObject params = getParams(request);

        String viewPath = getStringParam(params, "viewPath");
        if (viewPath == null) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS, "Missing required parameter: viewPath", id);
        }

        if (!params.has("config") || !params.get("config").isJsonObject()) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS,
                    "Missing or invalid required parameter: config (must be a JSON object)",
                    id);
        }

        JsonObject config = params.getAsJsonObject("config");
        boolean refreshEditor = getBooleanParam(params, "refreshEditor", true);

        // Validate first
        ViewValidationResult validation = validator.validate(config);
        if (!validation.isValid()) {
            Map<String, Object> errorData = new HashMap<>();
            errorData.put("valid", false);
            errorData.put("errors", validation.getErrors());
            errorData.put("warnings", validation.getWarnings());
            return JsonRpcResponse.error(
                    ErrorCodes.VIEW_VALIDATION_ERROR, "View validation failed", errorData, id);
        }

        String jsonString = handler.getGson().toJson(config);
        boolean isOpen = resourceHelper.isViewOpenInEditor(viewPath);

        if (isOpen) {
            // Editor is open — use delete+create to force the editor to reload
            String replaceError = resourceHelper.replaceViewContent(viewPath, jsonString);
            if (replaceError != null) {
                return JsonRpcResponse.error(
                        ErrorCodes.VIEW_WRITE_ERROR, "Failed to replace view: " + replaceError, id);
            }
        } else {
            // Editor not open — direct write is sufficient
            ResourceInfo resource = resourceHelper.findViewResource(viewPath);
            if (resource == null) {
                return JsonRpcResponse.error(
                        ErrorCodes.VIEW_NOT_FOUND, "View not found: " + viewPath, id);
            }

            boolean written = resourceHelper.writeViewJson(resource, jsonString);
            if (!written) {
                return JsonRpcResponse.error(
                        ErrorCodes.VIEW_WRITE_ERROR,
                        "Failed to write view.json for: " + viewPath,
                        id);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("viewPath", viewPath);
        result.put("success", true);
        result.put("refreshed", isOpen);
        if (!validation.getWarnings().isEmpty()) {
            result.put("warnings", validation.getWarnings());
        }
        return JsonRpcResponse.success(result, id);
    }

    /** view.getComponent - Read a single component by path. */
    private JsonRpcResponse handleGetComponent(JsonRpcRequest request) {
        Object id = request.getId();
        JsonObject params = getParams(request);

        String viewPath = getStringParam(params, "viewPath");
        if (viewPath == null) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS, "Missing required parameter: viewPath", id);
        }

        String componentPath = getStringParam(params, "componentPath");
        if (componentPath == null) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS, "Missing required parameter: componentPath", id);
        }

        ResourceInfo resource = resourceHelper.findViewResource(viewPath);
        if (resource == null) {
            return JsonRpcResponse.error(
                    ErrorCodes.VIEW_NOT_FOUND, "View not found: " + viewPath, id);
        }

        String json = resourceHelper.readViewJson(resource);
        if (json == null) {
            return JsonRpcResponse.error(
                    ErrorCodes.VIEW_NOT_FOUND, "Could not read view.json for: " + viewPath, id);
        }

        JsonObject viewJson = JsonParser.parseString(json).getAsJsonObject();
        JsonObject component = resourceHelper.navigateToComponent(viewJson, componentPath);

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

    /** view.setComponent - Replace a single component (validates subtree first). */
    private JsonRpcResponse handleSetComponent(JsonRpcRequest request) {
        Object id = request.getId();
        JsonObject params = getParams(request);

        String viewPath = getStringParam(params, "viewPath");
        if (viewPath == null) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS, "Missing required parameter: viewPath", id);
        }

        String componentPath = getStringParam(params, "componentPath");
        if (componentPath == null) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS, "Missing required parameter: componentPath", id);
        }

        if (!params.has("component") || !params.get("component").isJsonObject()) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS,
                    "Missing or invalid required parameter: component (must be a JSON object)",
                    id);
        }

        JsonObject newComponent = params.getAsJsonObject("component");
        boolean refreshEditor = getBooleanParam(params, "refreshEditor", true);

        ResourceInfo resource = resourceHelper.findViewResource(viewPath);
        if (resource == null) {
            return JsonRpcResponse.error(
                    ErrorCodes.VIEW_NOT_FOUND, "View not found: " + viewPath, id);
        }

        String json = resourceHelper.readViewJson(resource);
        if (json == null) {
            return JsonRpcResponse.error(
                    ErrorCodes.VIEW_NOT_FOUND, "Could not read view.json for: " + viewPath, id);
        }

        JsonObject viewJson = JsonParser.parseString(json).getAsJsonObject();

        // Validate the new component subtree by wrapping it as a view root
        JsonObject tempView = new JsonObject();
        tempView.add("root", newComponent);
        ViewValidationResult validation = validator.validate(tempView);
        if (!validation.isValid()) {
            Map<String, Object> errorData = new HashMap<>();
            errorData.put("valid", false);
            errorData.put("errors", validation.getErrors());
            errorData.put("warnings", validation.getWarnings());
            return JsonRpcResponse.error(
                    ErrorCodes.VIEW_VALIDATION_ERROR, "Component validation failed", errorData, id);
        }

        // Replace the component
        JsonObject modified =
                resourceHelper.replaceComponent(viewJson, componentPath, newComponent);
        if (modified == null) {
            return JsonRpcResponse.error(
                    ErrorCodes.COMPONENT_NOT_FOUND,
                    "Component not found at path: " + componentPath,
                    id);
        }

        String modifiedJson = handler.getGson().toJson(modified);
        boolean isOpen = resourceHelper.isViewOpenInEditor(viewPath);

        if (isOpen) {
            // Editor is open — use delete+create to force the editor to reload
            String replaceError = resourceHelper.replaceViewContent(viewPath, modifiedJson);
            if (replaceError != null) {
                return JsonRpcResponse.error(
                        ErrorCodes.VIEW_WRITE_ERROR, "Failed to replace view: " + replaceError, id);
            }
        } else {
            // Editor not open — direct write is sufficient
            boolean written = resourceHelper.writeViewJson(resource, modifiedJson);
            if (!written) {
                return JsonRpcResponse.error(
                        ErrorCodes.VIEW_WRITE_ERROR,
                        "Failed to write view.json for: " + viewPath,
                        id);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("viewPath", viewPath);
        result.put("componentPath", componentPath);
        result.put("success", true);
        result.put("refreshed", isOpen);
        if (!validation.getWarnings().isEmpty()) {
            result.put("warnings", validation.getWarnings());
        }
        return JsonRpcResponse.success(result, id);
    }

    /**
     * view.validate - Validate view JSON without applying. Accepts either {viewPath} to validate an
     * existing view or {config} to validate arbitrary JSON.
     */
    private JsonRpcResponse handleValidate(JsonRpcRequest request) {
        Object id = request.getId();
        JsonObject params = getParams(request);

        JsonObject config = null;

        // If config is provided directly, use it
        if (params.has("config") && params.get("config").isJsonObject()) {
            config = params.getAsJsonObject("config");
        }
        // Otherwise, read from viewPath
        else if (params.has("viewPath")) {
            String viewPath = getStringParam(params, "viewPath");
            ResourceInfo resource = resourceHelper.findViewResource(viewPath);
            if (resource == null) {
                return JsonRpcResponse.error(
                        ErrorCodes.VIEW_NOT_FOUND, "View not found: " + viewPath, id);
            }
            String json = resourceHelper.readViewJson(resource);
            if (json == null) {
                return JsonRpcResponse.error(
                        ErrorCodes.VIEW_NOT_FOUND, "Could not read view.json for: " + viewPath, id);
            }
            config = JsonParser.parseString(json).getAsJsonObject();
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

    /** view.getTree - Get lightweight component tree summary. */
    private JsonRpcResponse handleGetTree(JsonRpcRequest request) {
        Object id = request.getId();
        JsonObject params = getParams(request);

        String viewPath = getStringParam(params, "viewPath");
        if (viewPath == null) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS, "Missing required parameter: viewPath", id);
        }

        ResourceInfo resource = resourceHelper.findViewResource(viewPath);
        if (resource == null) {
            return JsonRpcResponse.error(
                    ErrorCodes.VIEW_NOT_FOUND, "View not found: " + viewPath, id);
        }

        String json = resourceHelper.readViewJson(resource);
        if (json == null) {
            return JsonRpcResponse.error(
                    ErrorCodes.VIEW_NOT_FOUND, "Could not read view.json for: " + viewPath, id);
        }

        JsonObject viewJson = JsonParser.parseString(json).getAsJsonObject();
        ComponentTreeNode tree = resourceHelper.buildComponentTree(viewJson);
        int totalComponents = resourceHelper.countComponents(viewJson);

        ViewTreeResult treeResult = new ViewTreeResult(viewPath, tree, totalComponents);
        return JsonRpcResponse.success(treeResult, id);
    }

    /** view.save - Save/commit changes in Designer. */
    private JsonRpcResponse handleSave(JsonRpcRequest request) {
        Object id = request.getId();

        boolean saved = resourceHelper.triggerSave();

        Map<String, Object> result = new HashMap<>();
        result.put("saved", saved);
        if (!saved) {
            result.put(
                    "message", "Could not trigger save. Try saving manually in Designer (Ctrl+S).");
        }
        return JsonRpcResponse.success(result, id);
    }

    /** view.create - Create a new Perspective view. */
    private JsonRpcResponse handleCreate(JsonRpcRequest request) {
        Object id = request.getId();
        JsonObject params = getParams(request);

        String viewPath = getStringParam(params, "viewPath");
        if (viewPath == null) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS, "Missing required parameter: viewPath", id);
        }

        // Default config: empty flex container
        String json;
        if (params.has("config") && params.get("config").isJsonObject()) {
            JsonObject config = params.getAsJsonObject("config");
            json = handler.getGson().toJson(config);
        } else {
            json =
                    "{\"root\":{\"type\":\"ia.container.flex\",\"version\":0,\"props\":{\"direction\":\"column\"},\"meta\":{\"name\":\"root\"},\"children\":[]}}";
        }

        String error = resourceHelper.createView(viewPath, json);
        if (error != null) {
            if ("VIEW_ALREADY_EXISTS".equals(error)) {
                return JsonRpcResponse.error(
                        ErrorCodes.VIEW_ALREADY_EXISTS, "View already exists: " + viewPath, id);
            }
            return JsonRpcResponse.error(ErrorCodes.INTERNAL_ERROR, error, id);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("viewPath", viewPath);
        result.put("created", true);
        return JsonRpcResponse.success(result, id);
    }

    /** view.delete - Delete a Perspective view. */
    private JsonRpcResponse handleDelete(JsonRpcRequest request) {
        Object id = request.getId();
        JsonObject params = getParams(request);

        String viewPath = getStringParam(params, "viewPath");
        if (viewPath == null) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS, "Missing required parameter: viewPath", id);
        }

        String error = resourceHelper.deleteView(viewPath);
        if (error != null) {
            if ("VIEW_NOT_FOUND".equals(error)) {
                return JsonRpcResponse.error(
                        ErrorCodes.VIEW_NOT_FOUND, "View not found: " + viewPath, id);
            }
            return JsonRpcResponse.error(ErrorCodes.INTERNAL_ERROR, error, id);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("viewPath", viewPath);
        result.put("deleted", true);
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

    private boolean getBooleanParam(JsonObject params, String key, boolean defaultValue) {
        if (params.has(key) && params.get(key).isJsonPrimitive()) {
            return params.get(key).getAsBoolean();
        }
        return defaultValue;
    }
}

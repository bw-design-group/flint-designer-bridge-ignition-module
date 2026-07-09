package dev.bwdesigngroup.flint.gateway.http;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import dev.bwdesigngroup.flint.common.FlintConstants;
import dev.bwdesigngroup.flint.common.protocol.ErrorCodes;
import dev.bwdesigngroup.flint.common.protocol.JsonRpcRequest;
import dev.bwdesigngroup.flint.common.protocol.JsonRpcResponse;
import dev.bwdesigngroup.flint.common.protocol.methods.ExecuteScriptParams;
import dev.bwdesigngroup.flint.common.protocol.methods.ExecuteScriptResult;
import dev.bwdesigngroup.flint.common.protocol.methods.HealthResult;
import dev.bwdesigngroup.flint.common.protocol.methods.debug.DebugSetBreakpointsParams;
import dev.bwdesigngroup.flint.common.rpc.FlintGatewayRpc;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Version-neutral JSON-RPC 2.0 dispatcher for the Gateway HTTP transport. Decodes requests exactly
 * as the Designer WebSocket handlers do (byte-for-byte wire compatibility) and routes them to the
 * in-process {@link FlintGatewayRpc} implementation and, in later phases, the gateway resource
 * services. Contains no servlet types so it is shared across the 8.1/8.3 builds.
 */
public class GatewayRpcDispatcher {

    private static final Logger logger = LoggerFactory.getLogger("flint.Gateway.Dispatcher");
    private static final int DEFAULT_TIMEOUT_MS = 30000;

    private final GatewayContext context;
    private final FlintGatewayRpc rpc;
    private final Gson gson;

    public GatewayRpcDispatcher(GatewayContext context, FlintGatewayRpc rpc, Gson gson) {
        this.context = context;
        this.rpc = rpc;
        this.gson = gson;
    }

    /**
     * Dispatches a raw HTTP request body (single JSON-RPC object or a batch array) and returns the
     * serialized response. Auth is enforced by the transport before this is called, so a parse
     * error or method error is reported as an HTTP 200 with a JSON-RPC error envelope, per
     * convention.
     */
    public HttpRpcResult dispatchHttp(String body) {
        JsonElement root;
        try {
            root = JsonParser.parseString(body == null ? "" : body);
        } catch (Exception e) {
            return new HttpRpcResult(
                    200,
                    gson.toJson(
                            JsonRpcResponse.error(
                                    ErrorCodes.PARSE_ERROR,
                                    "Parse error: " + e.getMessage(),
                                    null)));
        }

        // Batch request.
        if (root.isJsonArray()) {
            JsonArray batch = root.getAsJsonArray();
            JsonArray responses = new JsonArray();
            for (JsonElement element : batch) {
                JsonRpcResponse response = dispatchElement(element);
                if (response != null) {
                    responses.add(gson.toJsonTree(response));
                }
            }
            // An all-notification batch yields no responses; return 200 with empty body.
            return new HttpRpcResult(200, responses.size() == 0 ? "" : gson.toJson(responses));
        }

        // Single request.
        JsonRpcResponse response = dispatchElement(root);
        return new HttpRpcResult(200, response == null ? "" : gson.toJson(response));
    }

    private JsonRpcResponse dispatchElement(JsonElement element) {
        JsonRpcRequest request;
        try {
            request = gson.fromJson(element, JsonRpcRequest.class);
        } catch (Exception e) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_REQUEST, "Invalid request: " + e.getMessage(), null);
        }
        if (request == null || !request.isValid()) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_REQUEST,
                    "Invalid JSON-RPC request",
                    request != null ? request.getId() : null);
        }
        JsonRpcResponse response = dispatch(request);
        // Notifications (no id) expect no response.
        if (request.isNotification()) {
            return null;
        }
        return response;
    }

    /** Routes a single validated request to the appropriate gateway operation. */
    public JsonRpcResponse dispatch(JsonRpcRequest request) {
        String method = request.getMethod();
        Object id = request.getId();
        try {
            switch (method) {
                case FlintConstants.METHOD_AUTHENTICATE:
                    // Auth is per-request via headers on the HTTP transport; echo success.
                    return JsonRpcResponse.success(capabilityInfo(), id);
                case FlintConstants.METHOD_PING:
                    return JsonRpcResponse.success(capabilityInfo(), id);
                case FlintConstants.METHOD_EXECUTE_SCRIPT:
                    return handleExecuteScript(request);
                case FlintConstants.METHOD_RESET_SESSION:
                    return handleResetSession(request);
                case FlintConstants.METHOD_PROJECT_SCAN:
                    return handleProjectScan(request);

                    // Tags
                case FlintConstants.METHOD_TAGS_BROWSE:
                case FlintConstants.METHOD_TAGS_READ:
                case FlintConstants.METHOD_TAGS_WRITE:
                case FlintConstants.METHOD_TAGS_GET_CONFIG:
                case FlintConstants.METHOD_TAGS_CREATE:
                case FlintConstants.METHOD_TAGS_EDIT:
                case FlintConstants.METHOD_TAGS_DELETE:
                case FlintConstants.METHOD_TAGS_GET_PROVIDERS:
                    return handleTags(request);

                    // UDTs
                case FlintConstants.METHOD_UDT_GET_DEFINITIONS:
                case FlintConstants.METHOD_UDT_GET_DEFINITION:
                case FlintConstants.METHOD_UDT_CREATE_DEFINITION:
                case FlintConstants.METHOD_UDT_CREATE_INSTANCE:
                    return handleUdt(request);

                    // Perspective
                case FlintConstants.METHOD_PERSPECTIVE_IS_AVAILABLE:
                case FlintConstants.METHOD_PERSPECTIVE_LIST_SESSIONS:
                case FlintConstants.METHOD_PERSPECTIVE_GET_SESSION_PAGES:
                case FlintConstants.METHOD_PERSPECTIVE_GET_PAGE_VIEWS:
                case FlintConstants.METHOD_PERSPECTIVE_GET_VIEW_COMPONENTS:
                case FlintConstants.METHOD_PERSPECTIVE_EXECUTE_SCRIPT:
                case FlintConstants.METHOD_PERSPECTIVE_GET_COMPONENT_COMPLETIONS:
                case FlintConstants.METHOD_PERSPECTIVE_PROFILE_VIEW:
                case FlintConstants.METHOD_PERSPECTIVE_START_RECORDING:
                case FlintConstants.METHOD_PERSPECTIVE_STOP_RECORDING:
                case FlintConstants.METHOD_PERSPECTIVE_POLL_RECORDING:
                    return handlePerspective(request);

                    // Debug (poll-based over HTTP)
                case FlintConstants.METHOD_DEBUG_START_SESSION:
                case FlintConstants.METHOD_DEBUG_STOP_SESSION:
                case FlintConstants.METHOD_DEBUG_SET_BREAKPOINTS:
                case FlintConstants.METHOD_DEBUG_RUN:
                case FlintConstants.METHOD_DEBUG_CONTINUE:
                case FlintConstants.METHOD_DEBUG_STEP_OVER:
                case FlintConstants.METHOD_DEBUG_STEP_INTO:
                case FlintConstants.METHOD_DEBUG_STEP_OUT:
                case FlintConstants.METHOD_DEBUG_PAUSE:
                case FlintConstants.METHOD_DEBUG_POLL_EVENTS:
                case FlintConstants.METHOD_DEBUG_GET_STACK_TRACE:
                case FlintConstants.METHOD_DEBUG_GET_SCOPES:
                case FlintConstants.METHOD_DEBUG_GET_VARIABLES:
                case FlintConstants.METHOD_DEBUG_EVALUATE:
                    return handleDebug(request);

                    // Designer-UI-only methods have no headless equivalent.
                case FlintConstants.METHOD_OPEN_RESOURCE:
                case FlintConstants.METHOD_DESIGNER_GET_OPEN_TABS:
                case FlintConstants.METHOD_DESIGNER_TOGGLE_PREVIEW:
                case FlintConstants.METHOD_SHOW_MESSAGE:
                case FlintConstants.METHOD_BROWSER_GET_CDP_INFO:
                    return JsonRpcResponse.error(
                            ErrorCodes.GATEWAY_SCOPE_NOT_SUPPORTED,
                            "Method '"
                                    + method
                                    + "' requires a running Designer and is not "
                                    + "available on the headless gateway transport",
                            id);

                default:
                    // lsp.* (document sync, diagnostics, symbols, hover, definition, completion,
                    // references) and component/icon are served by dispatch extensions.
                    return handleExtended(request);
            }
        } catch (Exception e) {
            logger.error("Error dispatching method {}: {}", method, e.getMessage(), e);
            return JsonRpcResponse.error(
                    ErrorCodes.INTERNAL_ERROR, "Internal error: " + e.getMessage(), id);
        }
    }

    /**
     * Pluggable handlers for methods not backed directly by the RPC facade (resources/views,
     * Perspective registry, etc.). Each is tried in registration order.
     */
    private final List<DispatchExtension> extensions = new ArrayList<>();

    /** Optional extension for resource/view/registry methods. */
    public interface DispatchExtension {
        /** Returns null if the method is not handled by this extension. */
        JsonRpcResponse tryDispatch(JsonRpcRequest request);
    }

    public void addExtension(DispatchExtension extension) {
        this.extensions.add(extension);
    }

    private JsonRpcResponse handleExtended(JsonRpcRequest request) {
        for (DispatchExtension extension : extensions) {
            JsonRpcResponse response = extension.tryDispatch(request);
            if (response != null) {
                return response;
            }
        }
        return JsonRpcResponse.error(
                ErrorCodes.METHOD_NOT_FOUND,
                "Method not found: " + request.getMethod(),
                request.getId());
    }

    // ==================== Core / script ====================

    private JsonRpcResponse handleExecuteScript(JsonRpcRequest request) {
        Object id = request.getId();
        ExecuteScriptParams params;
        try {
            params = gson.fromJson(request.getParams(), ExecuteScriptParams.class);
        } catch (Exception e) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS, "Invalid params: " + e.getMessage(), id);
        }
        if (params == null || params.getCode() == null || params.getCode().isEmpty()) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS, "Missing required parameter: code", id);
        }

        int timeoutMs = params.getTimeoutMs() != null ? params.getTimeoutMs() : DEFAULT_TIMEOUT_MS;
        String sessionId = params.getSessionId();
        boolean resetSession = params.getResetSession() != null && params.getResetSession();

        // Perspective scope runs in a live session; everything else runs in gateway scope
        // (headless has no Designer scope).
        if (params.isPerspectiveScope()) {
            String perspectiveSessionId = params.getPerspectiveSessionId();
            if (perspectiveSessionId == null || perspectiveSessionId.isEmpty()) {
                return JsonRpcResponse.error(
                        ErrorCodes.INVALID_PARAMS,
                        "Perspective scope requires perspectiveSessionId parameter",
                        id);
            }
            ExecuteScriptResult result =
                    rpc.perspectiveExecuteScript(
                            params.getCode(),
                            timeoutMs,
                            perspectiveSessionId,
                            params.getPerspectivePageId(),
                            params.getPerspectiveViewInstanceId(),
                            params.getPerspectiveComponentPath(),
                            sessionId,
                            resetSession);
            return JsonRpcResponse.success(result, id);
        }

        ExecuteScriptResult result =
                rpc.executeScript(params.getCode(), timeoutMs, sessionId, resetSession);
        return JsonRpcResponse.success(result, id);
    }

    private JsonRpcResponse handleResetSession(JsonRpcRequest request) {
        Object id = request.getId();
        JsonObject params = params(request);
        String sessionId = getString(params, "sessionId");
        if (sessionId == null) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS, "Missing required parameter: sessionId", id);
        }
        rpc.resetSession(sessionId);
        return JsonRpcResponse.success(success(true), id);
    }

    private JsonRpcResponse handleProjectScan(JsonRpcRequest request) {
        boolean ok = rpc.requestProjectScan();
        return JsonRpcResponse.success(success(ok), request.getId());
    }

    // ==================== Tags ====================

    private JsonRpcResponse handleTags(JsonRpcRequest request) {
        String method = request.getMethod();
        Object id = request.getId();
        JsonObject params = params(request);

        switch (method) {
            case FlintConstants.METHOD_TAGS_BROWSE:
                {
                    String provider = getString(params, "provider");
                    String parentPath = getString(params, "parentPath");
                    return JsonRpcResponse.success(
                            rpc.tagBrowse(
                                    provider != null ? provider : "default",
                                    parentPath != null ? parentPath : "",
                                    getString(params, "typeFilter"),
                                    getString(params, "nameFilter")),
                            id);
                }
            case FlintConstants.METHOD_TAGS_READ:
                {
                    List<String> tagPaths = getStringList(params, "tagPaths");
                    if (tagPaths == null || tagPaths.isEmpty()) {
                        return JsonRpcResponse.error(
                                ErrorCodes.INVALID_PARAMS,
                                "Missing required parameter: tagPaths",
                                id);
                    }
                    return JsonRpcResponse.success(rpc.tagRead(tagPaths), id);
                }
            case FlintConstants.METHOD_TAGS_WRITE:
                {
                    if (!params.has("writes") || !params.get("writes").isJsonArray()) {
                        return JsonRpcResponse.error(
                                ErrorCodes.INVALID_PARAMS,
                                "Missing required parameter: writes (array of {path, value})",
                                id);
                    }
                    List<String> paths = new ArrayList<>();
                    List<String> values = new ArrayList<>();
                    List<String> dataTypes = new ArrayList<>();
                    for (JsonElement elem : params.getAsJsonArray("writes")) {
                        JsonObject write = elem.getAsJsonObject();
                        paths.add(write.get("path").getAsString());
                        values.add(write.has("value") ? write.get("value").getAsString() : "");
                        dataTypes.add(
                                write.has("dataType") ? write.get("dataType").getAsString() : "");
                    }
                    return JsonRpcResponse.success(rpc.tagWrite(paths, values, dataTypes), id);
                }
            case FlintConstants.METHOD_TAGS_GET_CONFIG:
                {
                    String tagPath = getString(params, "tagPath");
                    if (tagPath == null) {
                        return JsonRpcResponse.error(
                                ErrorCodes.INVALID_PARAMS,
                                "Missing required parameter: tagPath",
                                id);
                    }
                    dev.bwdesigngroup.flint.common.protocol.methods.tags.TagGetConfigResult result =
                            rpc.tagGetConfig(tagPath);
                    Map<String, Object> response = new HashMap<>();
                    response.put("path", result.getPath());
                    try {
                        response.put("config", JsonParser.parseString(result.getConfig()));
                    } catch (Exception e) {
                        response.put("config", result.getConfig());
                    }
                    return JsonRpcResponse.success(response, id);
                }
            case FlintConstants.METHOD_TAGS_CREATE:
                {
                    String parentPath = getString(params, "parentPath");
                    if (parentPath == null) {
                        return JsonRpcResponse.error(
                                ErrorCodes.INVALID_PARAMS,
                                "Missing required parameter: parentPath",
                                id);
                    }
                    if (!params.has("tags") || !params.get("tags").isJsonArray()) {
                        return JsonRpcResponse.error(
                                ErrorCodes.INVALID_PARAMS,
                                "Missing required parameter: tags (array of tag configs)",
                                id);
                    }
                    String tagsJson = gson.toJson(params.getAsJsonArray("tags"));
                    return JsonRpcResponse.success(rpc.tagCreate(parentPath, tagsJson), id);
                }
            case FlintConstants.METHOD_TAGS_EDIT:
                {
                    String tagPath = getString(params, "tagPath");
                    if (tagPath == null) {
                        return JsonRpcResponse.error(
                                ErrorCodes.INVALID_PARAMS,
                                "Missing required parameter: tagPath",
                                id);
                    }
                    if (!params.has("config") || !params.get("config").isJsonObject()) {
                        return JsonRpcResponse.error(
                                ErrorCodes.INVALID_PARAMS,
                                "Missing required parameter: config (JSON object)",
                                id);
                    }
                    String configJson = gson.toJson(params.getAsJsonObject("config"));
                    return JsonRpcResponse.success(rpc.tagEdit(tagPath, configJson), id);
                }
            case FlintConstants.METHOD_TAGS_DELETE:
                {
                    List<String> tagPaths = getStringList(params, "tagPaths");
                    if (tagPaths == null || tagPaths.isEmpty()) {
                        return JsonRpcResponse.error(
                                ErrorCodes.INVALID_PARAMS,
                                "Missing required parameter: tagPaths",
                                id);
                    }
                    return JsonRpcResponse.success(rpc.tagDelete(tagPaths), id);
                }
            case FlintConstants.METHOD_TAGS_GET_PROVIDERS:
                return JsonRpcResponse.success(rpc.tagGetProviders(), id);
            default:
                return JsonRpcResponse.error(
                        ErrorCodes.METHOD_NOT_FOUND, "Unknown tag method: " + method, id);
        }
    }

    // ==================== UDTs ====================

    private JsonRpcResponse handleUdt(JsonRpcRequest request) {
        String method = request.getMethod();
        Object id = request.getId();
        JsonObject params = params(request);

        switch (method) {
            case FlintConstants.METHOD_UDT_GET_DEFINITIONS:
                {
                    String provider = getString(params, "provider");
                    return JsonRpcResponse.success(
                            rpc.udtGetDefinitions(provider != null ? provider : "default"), id);
                }
            case FlintConstants.METHOD_UDT_GET_DEFINITION:
                {
                    String provider = getString(params, "provider");
                    String typePath = getString(params, "typePath");
                    if (typePath == null) {
                        return JsonRpcResponse.error(
                                ErrorCodes.INVALID_PARAMS,
                                "Missing required parameter: typePath",
                                id);
                    }
                    return JsonRpcResponse.success(
                            rpc.udtGetDefinition(provider != null ? provider : "default", typePath),
                            id);
                }
            case FlintConstants.METHOD_UDT_CREATE_DEFINITION:
                {
                    String provider = getString(params, "provider");
                    String name = getString(params, "name");
                    String parentTypePath = getString(params, "parentTypePath");
                    if (name == null) {
                        return JsonRpcResponse.error(
                                ErrorCodes.INVALID_PARAMS, "Missing required parameter: name", id);
                    }
                    String membersJson = null;
                    if (params.has("members") && params.get("members").isJsonArray()) {
                        membersJson = gson.toJson(params.getAsJsonArray("members"));
                    }
                    return JsonRpcResponse.success(
                            rpc.udtCreateDefinition(
                                    provider != null ? provider : "default",
                                    name,
                                    parentTypePath != null ? parentTypePath : "",
                                    membersJson),
                            id);
                }
            case FlintConstants.METHOD_UDT_CREATE_INSTANCE:
                {
                    String parentPath = getString(params, "parentPath");
                    String name = getString(params, "name");
                    String typeId = getString(params, "typeId");
                    if (parentPath == null || name == null || typeId == null) {
                        return JsonRpcResponse.error(
                                ErrorCodes.INVALID_PARAMS,
                                "Missing required parameters: parentPath, name, typeId",
                                id);
                    }
                    String overridesJson = null;
                    if (params.has("overrides") && params.get("overrides").isJsonObject()) {
                        overridesJson = gson.toJson(params.getAsJsonObject("overrides"));
                    }
                    return JsonRpcResponse.success(
                            rpc.udtCreateInstance(parentPath, name, typeId, overridesJson), id);
                }
            default:
                return JsonRpcResponse.error(
                        ErrorCodes.METHOD_NOT_FOUND, "Unknown UDT method: " + method, id);
        }
    }

    // ==================== Perspective ====================

    private JsonRpcResponse handlePerspective(JsonRpcRequest request) {
        String method = request.getMethod();
        Object id = request.getId();
        JsonObject params = params(request);

        switch (method) {
            case FlintConstants.METHOD_PERSPECTIVE_IS_AVAILABLE:
                {
                    Map<String, Object> result = new HashMap<>();
                    result.put("available", rpc.isPerspectiveAvailable());
                    return JsonRpcResponse.success(result, id);
                }
            case FlintConstants.METHOD_PERSPECTIVE_LIST_SESSIONS:
                return JsonRpcResponse.success(rpc.perspectiveListSessions(), id);
            case FlintConstants.METHOD_PERSPECTIVE_GET_SESSION_PAGES:
                {
                    String sessionId = getString(params, "sessionId");
                    if (sessionId == null) {
                        return missing(id, "sessionId");
                    }
                    return JsonRpcResponse.success(rpc.perspectiveGetSessionPages(sessionId), id);
                }
            case FlintConstants.METHOD_PERSPECTIVE_GET_PAGE_VIEWS:
                {
                    String sessionId = getString(params, "sessionId");
                    String pageId = getString(params, "pageId");
                    if (sessionId == null || pageId == null) {
                        return missing(id, "sessionId, pageId");
                    }
                    return JsonRpcResponse.success(
                            rpc.perspectiveGetPageViews(sessionId, pageId), id);
                }
            case FlintConstants.METHOD_PERSPECTIVE_GET_VIEW_COMPONENTS:
                {
                    String sessionId = getString(params, "sessionId");
                    String pageId = getString(params, "pageId");
                    String viewInstanceId = getString(params, "viewInstanceId");
                    if (sessionId == null || pageId == null || viewInstanceId == null) {
                        return missing(id, "sessionId, pageId, viewInstanceId");
                    }
                    return JsonRpcResponse.success(
                            rpc.perspectiveGetViewComponents(sessionId, pageId, viewInstanceId),
                            id);
                }
            case FlintConstants.METHOD_PERSPECTIVE_EXECUTE_SCRIPT:
                {
                    String code = getString(params, "code");
                    String sessionId = getString(params, "sessionId");
                    if (code == null || sessionId == null) {
                        return missing(id, "code, sessionId");
                    }
                    return JsonRpcResponse.success(
                            rpc.perspectiveExecuteScript(
                                    code,
                                    getInt(params, "timeoutMs", DEFAULT_TIMEOUT_MS),
                                    sessionId,
                                    getString(params, "pageId"),
                                    getString(params, "viewInstanceId"),
                                    getString(params, "componentPath"),
                                    getString(params, "scriptSessionId"),
                                    getBool(params, "resetSession", false)),
                            id);
                }
            case FlintConstants.METHOD_PERSPECTIVE_GET_COMPONENT_COMPLETIONS:
                {
                    String sessionId = getString(params, "sessionId");
                    String pageId = getString(params, "pageId");
                    String viewInstanceId = getString(params, "viewInstanceId");
                    String componentPath = getString(params, "componentPath");
                    if (sessionId == null
                            || pageId == null
                            || viewInstanceId == null
                            || componentPath == null) {
                        return missing(id, "sessionId, pageId, viewInstanceId, componentPath");
                    }
                    String prefix = getString(params, "prefix");
                    return JsonRpcResponse.success(
                            rpc.perspectiveGetComponentCompletions(
                                    sessionId,
                                    pageId,
                                    viewInstanceId,
                                    componentPath,
                                    prefix != null ? prefix : ""),
                            id);
                }
            case FlintConstants.METHOD_PERSPECTIVE_PROFILE_VIEW:
                {
                    String sessionId = getString(params, "sessionId");
                    String pageId = getString(params, "pageId");
                    String viewInstanceId = getString(params, "viewInstanceId");
                    if (sessionId == null || pageId == null || viewInstanceId == null) {
                        return missing(id, "sessionId, pageId, viewInstanceId");
                    }
                    return JsonRpcResponse.success(
                            rpc.perspectiveProfileView(sessionId, pageId, viewInstanceId), id);
                }
            case FlintConstants.METHOD_PERSPECTIVE_START_RECORDING:
                {
                    String sessionId = getString(params, "sessionId");
                    String pageId = getString(params, "pageId");
                    if (sessionId == null || pageId == null) {
                        return missing(id, "sessionId, pageId");
                    }
                    return JsonRpcResponse.success(
                            rpc.perspectiveStartRecording(
                                    sessionId,
                                    pageId,
                                    getString(params, "viewInstanceId"),
                                    getInt(params, "pollIntervalMs", 50),
                                    getInt(params, "maxDurationMs", 120000),
                                    getBool(params, "autoStopOnAllResolved", false),
                                    getInt(params, "autoStopDelayMs", 2000)),
                            id);
                }
            case FlintConstants.METHOD_PERSPECTIVE_STOP_RECORDING:
                {
                    String recordingId = getString(params, "recordingId");
                    if (recordingId == null) {
                        return missing(id, "recordingId");
                    }
                    return JsonRpcResponse.success(rpc.perspectiveStopRecording(recordingId), id);
                }
            case FlintConstants.METHOD_PERSPECTIVE_POLL_RECORDING:
                {
                    String recordingId = getString(params, "recordingId");
                    if (recordingId == null) {
                        return missing(id, "recordingId");
                    }
                    return JsonRpcResponse.success(
                            rpc.perspectivePollRecordingEvents(recordingId), id);
                }
            default:
                return JsonRpcResponse.error(
                        ErrorCodes.METHOD_NOT_FOUND, "Unknown Perspective method: " + method, id);
        }
    }

    // ==================== Debug (poll-based) ====================

    private JsonRpcResponse handleDebug(JsonRpcRequest request) {
        String method = request.getMethod();
        Object id = request.getId();
        JsonObject params = params(request);

        switch (method) {
            case FlintConstants.METHOD_DEBUG_START_SESSION:
                {
                    String code = getString(params, "code");
                    if (code == null || code.isEmpty()) {
                        return missing(id, "code");
                    }
                    // Headless: force remote scope. "designer" collapses to "gateway".
                    String scope = getString(params, "scope");
                    if (scope == null || "designer".equalsIgnoreCase(scope)) {
                        scope = "gateway";
                    }
                    return JsonRpcResponse.success(
                            rpc.debugStartSession(
                                    code,
                                    getString(params, "filePath"),
                                    getString(params, "modulePath"),
                                    scope,
                                    getString(params, "perspectiveSessionId"),
                                    getString(params, "perspectivePageId"),
                                    getString(params, "perspectiveViewInstanceId"),
                                    getString(params, "perspectiveComponentPath")),
                            id);
                }
            case FlintConstants.METHOD_DEBUG_STOP_SESSION:
                {
                    String sessionId = getString(params, "sessionId");
                    if (sessionId == null) {
                        return missing(id, "sessionId");
                    }
                    rpc.debugStopSession(sessionId);
                    return JsonRpcResponse.success(success(true), id);
                }
            case FlintConstants.METHOD_DEBUG_SET_BREAKPOINTS:
                {
                    DebugSetBreakpointsParams p;
                    try {
                        p = gson.fromJson(request.getParams(), DebugSetBreakpointsParams.class);
                    } catch (Exception e) {
                        return JsonRpcResponse.error(
                                ErrorCodes.INVALID_PARAMS, "Invalid params: " + e.getMessage(), id);
                    }
                    if (p == null || p.getSessionId() == null) {
                        return missing(id, "sessionId");
                    }
                    List<DebugSetBreakpointsParams.BreakpointInfo> bps = p.getBreakpoints();
                    if (bps == null) {
                        bps = new ArrayList<>();
                    }
                    List<Integer> ids =
                            rpc.debugSetBreakpoints(p.getSessionId(), p.getFilePath(), bps);
                    Map<String, Object> result = new HashMap<>();
                    result.put("breakpointIds", ids);
                    return JsonRpcResponse.success(result, id);
                }
            case FlintConstants.METHOD_DEBUG_RUN:
                {
                    String sessionId = getString(params, "sessionId");
                    if (sessionId == null) {
                        return missing(id, "sessionId");
                    }
                    rpc.debugRun(sessionId);
                    return JsonRpcResponse.success(success(true), id);
                }
            case FlintConstants.METHOD_DEBUG_CONTINUE:
                return debugCommand(params, id, "continue");
            case FlintConstants.METHOD_DEBUG_STEP_OVER:
                return debugCommand(params, id, "stepOver");
            case FlintConstants.METHOD_DEBUG_STEP_INTO:
                return debugCommand(params, id, "stepInto");
            case FlintConstants.METHOD_DEBUG_STEP_OUT:
                return debugCommand(params, id, "stepOut");
            case FlintConstants.METHOD_DEBUG_PAUSE:
                return debugCommand(params, id, "pause");
            case FlintConstants.METHOD_DEBUG_POLL_EVENTS:
                {
                    String sessionId = getString(params, "sessionId");
                    if (sessionId == null) {
                        return missing(id, "sessionId");
                    }
                    long maxWaitMs = (long) getInt(params, "maxWaitMs", 1000);
                    return JsonRpcResponse.success(rpc.debugPollEvents(sessionId, maxWaitMs), id);
                }
            case FlintConstants.METHOD_DEBUG_GET_STACK_TRACE:
                {
                    String sessionId = getString(params, "sessionId");
                    if (sessionId == null) {
                        return missing(id, "sessionId");
                    }
                    return JsonRpcResponse.success(rpc.debugGetStackTrace(sessionId), id);
                }
            case FlintConstants.METHOD_DEBUG_GET_SCOPES:
                {
                    String sessionId = getString(params, "sessionId");
                    if (sessionId == null) {
                        return missing(id, "sessionId");
                    }
                    return JsonRpcResponse.success(
                            rpc.debugGetScopes(sessionId, getInt(params, "frameId", 0)), id);
                }
            case FlintConstants.METHOD_DEBUG_GET_VARIABLES:
                {
                    String sessionId = getString(params, "sessionId");
                    if (sessionId == null) {
                        return missing(id, "sessionId");
                    }
                    return JsonRpcResponse.success(
                            rpc.debugGetVariables(
                                    sessionId, getInt(params, "variablesReference", 0)),
                            id);
                }
            case FlintConstants.METHOD_DEBUG_EVALUATE:
                {
                    String sessionId = getString(params, "sessionId");
                    String expression = getString(params, "expression");
                    if (sessionId == null || expression == null) {
                        return missing(id, "sessionId, expression");
                    }
                    Integer frameId = params.has("frameId") ? getInt(params, "frameId", 0) : null;
                    return JsonRpcResponse.success(
                            rpc.debugEvaluate(sessionId, expression, frameId), id);
                }
            default:
                return JsonRpcResponse.error(
                        ErrorCodes.METHOD_NOT_FOUND, "Unknown debug method: " + method, id);
        }
    }

    private JsonRpcResponse debugCommand(JsonObject params, Object id, String command) {
        String sessionId = getString(params, "sessionId");
        if (sessionId == null) {
            return missing(id, "sessionId");
        }
        rpc.debugSendCommand(sessionId, command);
        return JsonRpcResponse.success(success(true), id);
    }

    // ==================== Health / capabilities ====================

    /** Builds the health document served (unauthenticated) at {@code GET /health}. */
    public HealthResult buildHealth() {
        HealthResult health = new HealthResult();
        health.setModule(FlintConstants.MODULE_NAME);
        health.setModuleVersion(moduleVersion());
        health.setIgnitionVersion(System.getProperty("ignition.version", "unknown"));
        health.setScope("gateway");
        health.setAuthSchemes(Arrays.asList("ignition-api-token", "flint-bearer"));
        health.setCapabilities(capabilities());
        health.setUnsupported(
                Arrays.asList(
                        FlintConstants.METHOD_OPEN_RESOURCE,
                        FlintConstants.METHOD_DESIGNER_GET_OPEN_TABS,
                        FlintConstants.METHOD_DESIGNER_TOGGLE_PREVIEW,
                        FlintConstants.METHOD_SHOW_MESSAGE,
                        FlintConstants.METHOD_BROWSER_GET_CDP_INFO));
        health.setProjects(listProjectNames());
        health.setRpcPath(
                "/data/" + FlintConstants.GATEWAY_ROUTE_ALIAS + FlintConstants.GATEWAY_ROUTE_RPC);
        return health;
    }

    private Map<String, Object> capabilityInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("pong", true);
        info.put("scope", "gateway");
        info.put("module", FlintConstants.MODULE_NAME);
        info.put("moduleVersion", moduleVersion());
        info.put("capabilities", capabilities());
        return info;
    }

    private List<String> capabilities() {
        return new ArrayList<>(
                Arrays.asList(
                        "executeScript",
                        "tags",
                        "udt",
                        "perspective",
                        "debug",
                        "project.scan",
                        "project.listResources",
                        "view",
                        "project.getViewCatalog",
                        "component",
                        "icon",
                        "lsp"));
    }

    private String moduleVersion() {
        try {
            String v = getClass().getPackage().getImplementationVersion();
            return v != null ? v : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }

    /** Lists project names in a version-neutral way (8.1 getProjectNames vs 8.3 getNames). */
    @SuppressWarnings("unchecked")
    private List<String> listProjectNames() {
        try {
            Object pm = context.getProjectManager();
            for (String candidate : new String[] {"getProjectNames", "getNames"}) {
                try {
                    Object result = pm.getClass().getMethod(candidate).invoke(pm);
                    if (result instanceof List) {
                        return (List<String>) result;
                    }
                } catch (NoSuchMethodException ignored) {
                    // try next
                }
            }
        } catch (Exception e) {
            logger.debug("Could not list project names: {}", e.getMessage());
        }
        return new ArrayList<>();
    }

    // ==================== Param helpers ====================

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

    private int getInt(JsonObject params, String key, int defaultValue) {
        if (params.has(key) && params.get(key).isJsonPrimitive()) {
            try {
                return params.get(key).getAsInt();
            } catch (Exception ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private boolean getBool(JsonObject params, String key, boolean defaultValue) {
        if (params.has(key) && params.get(key).isJsonPrimitive()) {
            try {
                return params.get(key).getAsBoolean();
            } catch (Exception ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private List<String> getStringList(JsonObject params, String key) {
        if (!params.has(key) || !params.get(key).isJsonArray()) {
            return null;
        }
        List<String> result = new ArrayList<>();
        for (JsonElement elem : params.getAsJsonArray(key)) {
            result.add(elem.getAsString());
        }
        return result;
    }

    private Map<String, Object> success(boolean value) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", value);
        return result;
    }

    private JsonRpcResponse missing(Object id, String paramNames) {
        return JsonRpcResponse.error(
                ErrorCodes.INVALID_PARAMS, "Missing required parameter(s): " + paramNames, id);
    }
}

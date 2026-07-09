package dev.bwdesigngroup.flint.designer.handlers;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.bwdesigngroup.flint.common.FlintConstants;
import dev.bwdesigngroup.flint.common.protocol.ErrorCodes;
import dev.bwdesigngroup.flint.common.protocol.JsonRpcRequest;
import dev.bwdesigngroup.flint.common.protocol.JsonRpcResponse;
import dev.bwdesigngroup.flint.common.protocol.methods.ExecuteScriptResult;
import dev.bwdesigngroup.flint.common.protocol.methods.perspective.PerspectiveCompletionResult;
import dev.bwdesigngroup.flint.common.protocol.methods.perspective.PerspectiveListComponentsResult;
import dev.bwdesigngroup.flint.common.protocol.methods.perspective.PerspectiveListPagesResult;
import dev.bwdesigngroup.flint.common.protocol.methods.perspective.PerspectiveListSessionsResult;
import dev.bwdesigngroup.flint.common.protocol.methods.perspective.PerspectiveListViewsResult;
import dev.bwdesigngroup.flint.common.protocol.methods.perspective.ViewProfileResult;
import dev.bwdesigngroup.flint.common.protocol.methods.perspective.recording.RecordingEventBatch;
import dev.bwdesigngroup.flint.common.protocol.methods.perspective.recording.StartRecordingResult;
import dev.bwdesigngroup.flint.common.protocol.methods.perspective.recording.StopRecordingResult;
import dev.bwdesigngroup.flint.designer.platform.PlatformFactory;
import dev.bwdesigngroup.flint.designer.rpc.GatewayRpcClient;
import dev.bwdesigngroup.flint.designer.server.FlintWebSocketHandler;
import dev.bwdesigngroup.flint.designer.server.FlintWebSocketServer;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Handler for Perspective-related WebSocket methods. Forwards requests to the Gateway via RPC. */
public class PerspectiveHandler implements MethodHandler {
    private static final Logger logger = LoggerFactory.getLogger("flint.Designer.Perspective");

    private final FlintWebSocketHandler handler;
    private final GatewayRpcClient gatewayRpcClient;
    private final Gson gson;
    private final FlintWebSocketServer webSocketServer;
    private RecordingPollingBridge recordingPollingBridge;

    public PerspectiveHandler(FlintWebSocketHandler handler) {
        this(handler, null);
    }

    public PerspectiveHandler(FlintWebSocketHandler handler, FlintWebSocketServer webSocketServer) {
        this.handler = handler;
        this.gatewayRpcClient = PlatformFactory.createGatewayRpcClient();
        this.gatewayRpcClient.initialize();
        this.gson = handler.getGson();
        this.webSocketServer = webSocketServer;
    }

    @Override
    public JsonRpcResponse handle(JsonRpcRequest request) {
        String method = request.getMethod();
        Object id = request.getId();

        logger.debug("Handling Perspective method: {}", method);

        try {
            switch (method) {
                case FlintConstants.METHOD_PERSPECTIVE_IS_AVAILABLE:
                    return handleIsAvailable(id);

                case FlintConstants.METHOD_PERSPECTIVE_LIST_SESSIONS:
                    return handleListSessions(id);

                case FlintConstants.METHOD_PERSPECTIVE_GET_SESSION_PAGES:
                    return handleGetSessionPages(request, id);

                case FlintConstants.METHOD_PERSPECTIVE_GET_PAGE_VIEWS:
                    return handleGetPageViews(request, id);

                case FlintConstants.METHOD_PERSPECTIVE_GET_VIEW_COMPONENTS:
                    return handleGetViewComponents(request, id);

                case FlintConstants.METHOD_PERSPECTIVE_EXECUTE_SCRIPT:
                    return handleExecuteScript(request, id);

                case FlintConstants.METHOD_PERSPECTIVE_GET_COMPONENT_COMPLETIONS:
                    return handleGetComponentCompletions(request, id);

                case FlintConstants.METHOD_PERSPECTIVE_PROFILE_VIEW:
                    return handleProfileView(request, id);

                case FlintConstants.METHOD_PERSPECTIVE_START_RECORDING:
                    return handleStartRecording(request, id);

                case FlintConstants.METHOD_PERSPECTIVE_STOP_RECORDING:
                    return handleStopRecording(request, id);

                case FlintConstants.METHOD_PERSPECTIVE_POLL_RECORDING:
                    return handlePollRecording(request, id);

                default:
                    return JsonRpcResponse.error(
                            ErrorCodes.METHOD_NOT_FOUND,
                            "Unknown Perspective method: " + method,
                            id);
            }
        } catch (Exception e) {
            logger.error("Error handling Perspective method {}: {}", method, e.getMessage(), e);
            return JsonRpcResponse.error(
                    ErrorCodes.INTERNAL_ERROR, "Internal error: " + e.getMessage(), id);
        }
    }

    private JsonRpcResponse handleIsAvailable(Object id) {
        boolean available = gatewayRpcClient.getRpc().isPerspectiveAvailable();
        Map<String, Object> result = new HashMap<>();
        result.put("available", available);
        return JsonRpcResponse.success(result, id);
    }

    private JsonRpcResponse handleListSessions(Object id) {
        PerspectiveListSessionsResult result = gatewayRpcClient.getRpc().perspectiveListSessions();
        return JsonRpcResponse.success(result, id);
    }

    private JsonRpcResponse handleGetSessionPages(JsonRpcRequest request, Object id) {
        JsonObject params = getParams(request);
        String sessionId = params.has("sessionId") ? params.get("sessionId").getAsString() : null;

        if (sessionId == null) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS, "Missing required parameter: sessionId", id);
        }

        PerspectiveListPagesResult result =
                gatewayRpcClient.getRpc().perspectiveGetSessionPages(sessionId);
        return JsonRpcResponse.success(result, id);
    }

    private JsonRpcResponse handleGetPageViews(JsonRpcRequest request, Object id) {
        JsonObject params = getParams(request);
        String sessionId = params.has("sessionId") ? params.get("sessionId").getAsString() : null;
        String pageId = params.has("pageId") ? params.get("pageId").getAsString() : null;

        if (sessionId == null || pageId == null) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS,
                    "Missing required parameters: sessionId, pageId",
                    id);
        }

        PerspectiveListViewsResult result =
                gatewayRpcClient.getRpc().perspectiveGetPageViews(sessionId, pageId);
        return JsonRpcResponse.success(result, id);
    }

    private JsonRpcResponse handleGetViewComponents(JsonRpcRequest request, Object id) {
        JsonObject params = getParams(request);
        String sessionId = params.has("sessionId") ? params.get("sessionId").getAsString() : null;
        String pageId = params.has("pageId") ? params.get("pageId").getAsString() : null;
        String viewInstanceId =
                params.has("viewInstanceId") ? params.get("viewInstanceId").getAsString() : null;

        if (sessionId == null || pageId == null || viewInstanceId == null) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS,
                    "Missing required parameters: sessionId, pageId, viewInstanceId",
                    id);
        }

        PerspectiveListComponentsResult result =
                gatewayRpcClient
                        .getRpc()
                        .perspectiveGetViewComponents(sessionId, pageId, viewInstanceId);
        return JsonRpcResponse.success(result, id);
    }

    private JsonRpcResponse handleExecuteScript(JsonRpcRequest request, Object id) {
        JsonObject params = getParams(request);

        String code = params.has("code") ? params.get("code").getAsString() : null;
        int timeoutMs = params.has("timeoutMs") ? params.get("timeoutMs").getAsInt() : 30000;
        String sessionId = params.has("sessionId") ? params.get("sessionId").getAsString() : null;
        String pageId = params.has("pageId") ? params.get("pageId").getAsString() : null;
        String viewInstanceId =
                params.has("viewInstanceId") ? params.get("viewInstanceId").getAsString() : null;
        String componentPath =
                params.has("componentPath") ? params.get("componentPath").getAsString() : null;
        String scriptSessionId =
                params.has("scriptSessionId") ? params.get("scriptSessionId").getAsString() : null;
        boolean resetSession =
                params.has("resetSession") && params.get("resetSession").getAsBoolean();

        if (code == null || sessionId == null) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS, "Missing required parameters: code, sessionId", id);
        }

        ExecuteScriptResult result =
                gatewayRpcClient
                        .getRpc()
                        .perspectiveExecuteScript(
                                code,
                                timeoutMs,
                                sessionId,
                                pageId,
                                viewInstanceId,
                                componentPath,
                                scriptSessionId,
                                resetSession);
        return JsonRpcResponse.success(result, id);
    }

    private JsonRpcResponse handleGetComponentCompletions(JsonRpcRequest request, Object id) {
        JsonObject params = getParams(request);

        String sessionId = params.has("sessionId") ? params.get("sessionId").getAsString() : null;
        String pageId = params.has("pageId") ? params.get("pageId").getAsString() : null;
        String viewInstanceId =
                params.has("viewInstanceId") ? params.get("viewInstanceId").getAsString() : null;
        String componentPath =
                params.has("componentPath") ? params.get("componentPath").getAsString() : null;
        String prefix = params.has("prefix") ? params.get("prefix").getAsString() : "";

        if (sessionId == null
                || pageId == null
                || viewInstanceId == null
                || componentPath == null) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS,
                    "Missing required parameters: sessionId, pageId, viewInstanceId, componentPath",
                    id);
        }

        PerspectiveCompletionResult result =
                gatewayRpcClient
                        .getRpc()
                        .perspectiveGetComponentCompletions(
                                sessionId, pageId, viewInstanceId, componentPath, prefix);
        return JsonRpcResponse.success(result, id);
    }

    private JsonRpcResponse handleProfileView(JsonRpcRequest request, Object id) {
        JsonObject params = getParams(request);
        String sessionId = params.has("sessionId") ? params.get("sessionId").getAsString() : null;
        String pageId = params.has("pageId") ? params.get("pageId").getAsString() : null;
        String viewInstanceId =
                params.has("viewInstanceId") ? params.get("viewInstanceId").getAsString() : null;

        if (sessionId == null || pageId == null || viewInstanceId == null) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS,
                    "Missing required parameters: sessionId, pageId, viewInstanceId",
                    id);
        }

        ViewProfileResult result =
                gatewayRpcClient.getRpc().perspectiveProfileView(sessionId, pageId, viewInstanceId);
        return JsonRpcResponse.success(result, id);
    }

    private JsonRpcResponse handleStartRecording(JsonRpcRequest request, Object id) {
        JsonObject params = getParams(request);
        String sessionId = params.has("sessionId") ? params.get("sessionId").getAsString() : null;
        String pageId = params.has("pageId") ? params.get("pageId").getAsString() : null;
        String viewInstanceId =
                params.has("viewInstanceId") ? params.get("viewInstanceId").getAsString() : null;
        int pollIntervalMs =
                params.has("pollIntervalMs") ? params.get("pollIntervalMs").getAsInt() : 50;
        int maxDurationMs =
                params.has("maxDurationMs") ? params.get("maxDurationMs").getAsInt() : 120000;
        boolean autoStopOnAllResolved =
                params.has("autoStopOnAllResolved")
                        && params.get("autoStopOnAllResolved").getAsBoolean();
        int autoStopDelayMs =
                params.has("autoStopDelayMs") ? params.get("autoStopDelayMs").getAsInt() : 2000;

        if (sessionId == null || pageId == null) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS,
                    "Missing required parameters: sessionId, pageId",
                    id);
        }

        StartRecordingResult result =
                gatewayRpcClient
                        .getRpc()
                        .perspectiveStartRecording(
                                sessionId,
                                pageId,
                                viewInstanceId,
                                pollIntervalMs,
                                maxDurationMs,
                                autoStopOnAllResolved,
                                autoStopDelayMs);

        // If recording started successfully, start the polling bridge
        if (result.isSuccess() && webSocketServer != null) {
            startRecordingPollingBridge(result.getRecordingId());
        }

        return JsonRpcResponse.success(result, id);
    }

    private JsonRpcResponse handleStopRecording(JsonRpcRequest request, Object id) {
        JsonObject params = getParams(request);
        String recordingId =
                params.has("recordingId") ? params.get("recordingId").getAsString() : null;

        if (recordingId == null) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS, "Missing required parameter: recordingId", id);
        }

        // Stop the polling bridge first
        stopRecordingPollingBridge();

        StopRecordingResult result =
                gatewayRpcClient.getRpc().perspectiveStopRecording(recordingId);
        return JsonRpcResponse.success(result, id);
    }

    private JsonRpcResponse handlePollRecording(JsonRpcRequest request, Object id) {
        JsonObject params = getParams(request);
        String recordingId =
                params.has("recordingId") ? params.get("recordingId").getAsString() : null;

        if (recordingId == null) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS, "Missing required parameter: recordingId", id);
        }

        RecordingEventBatch result =
                gatewayRpcClient.getRpc().perspectivePollRecordingEvents(recordingId);
        return JsonRpcResponse.success(result, id);
    }

    private void startRecordingPollingBridge(String recordingId) {
        stopRecordingPollingBridge();
        recordingPollingBridge =
                new RecordingPollingBridge(gatewayRpcClient, webSocketServer, recordingId);
        recordingPollingBridge.start();
    }

    private void stopRecordingPollingBridge() {
        if (recordingPollingBridge != null) {
            recordingPollingBridge.stop();
            recordingPollingBridge = null;
        }
    }

    private JsonObject getParams(JsonRpcRequest request) {
        Object params = request.getParams();
        if (params instanceof JsonObject) {
            return (JsonObject) params;
        }
        return gson.toJsonTree(params).getAsJsonObject();
    }
}

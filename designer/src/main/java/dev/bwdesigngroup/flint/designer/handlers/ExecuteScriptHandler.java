package dev.bwdesigngroup.flint.designer.handlers;

import dev.bwdesigngroup.flint.common.protocol.ErrorCodes;
import dev.bwdesigngroup.flint.common.protocol.JsonRpcRequest;
import dev.bwdesigngroup.flint.common.protocol.JsonRpcResponse;
import dev.bwdesigngroup.flint.common.protocol.methods.ExecuteScriptParams;
import dev.bwdesigngroup.flint.common.protocol.methods.ExecuteScriptResult;
import dev.bwdesigngroup.flint.designer.platform.PlatformFactory;
import dev.bwdesigngroup.flint.designer.rpc.GatewayRpcClient;
import dev.bwdesigngroup.flint.designer.script.ScriptExecutor;
import dev.bwdesigngroup.flint.designer.server.FlintWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for the executeScript method. Executes Python code in the Designer's or Gateway's script
 * context based on scope.
 */
public class ExecuteScriptHandler implements MethodHandler {
    private static final Logger logger = LoggerFactory.getLogger("flint.Designer.Execute");
    private static final int DEFAULT_TIMEOUT_MS = 30000; // 30 seconds default

    private final FlintWebSocketHandler handler;
    private final ScriptExecutor scriptExecutor;
    private final GatewayRpcClient gatewayRpcClient;

    public ExecuteScriptHandler(FlintWebSocketHandler handler) {
        this.handler = handler;
        this.scriptExecutor = new ScriptExecutor(handler.getContext());
        this.gatewayRpcClient = PlatformFactory.createGatewayRpcClient();
        this.gatewayRpcClient.initialize();
    }

    @Override
    public JsonRpcResponse handle(JsonRpcRequest request) {
        Object id = request.getId();

        // Parse params
        ExecuteScriptParams params;
        try {
            params = handler.getGson().fromJson(request.getParams(), ExecuteScriptParams.class);
        } catch (Exception e) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS, "Invalid params: " + e.getMessage(), id);
        }

        if (params == null || params.getCode() == null || params.getCode().isEmpty()) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS, "Missing required parameter: code", id);
        }

        // Get timeout (use default if not specified)
        int timeoutMs = params.getTimeoutMs() != null ? params.getTimeoutMs() : DEFAULT_TIMEOUT_MS;

        // Get session parameters
        String sessionId = params.getSessionId();
        boolean resetSession = params.getResetSession() != null && params.getResetSession();

        // Check scope - route to appropriate executor
        if (params.isPerspectiveScope()) {
            return executeInPerspectiveScope(params, timeoutMs, sessionId, resetSession, id);
        } else if (params.isGatewayScope()) {
            return executeInGatewayScope(params.getCode(), timeoutMs, sessionId, resetSession, id);
        }

        // Execute in Designer scope (default)
        return executeInDesignerScope(params.getCode(), timeoutMs, sessionId, resetSession, id);
    }

    /** Executes script in Designer scope (local to this Designer instance). */
    private JsonRpcResponse executeInDesignerScope(
            String code, int timeoutMs, String sessionId, boolean resetSession, Object id) {
        logger.info(
                "Executing script in Designer scope ({} chars, timeout={}ms, session={})",
                code.length(),
                timeoutMs,
                sessionId);

        try {
            ExecuteScriptResult result =
                    scriptExecutor.execute(code, timeoutMs, sessionId, resetSession);
            return JsonRpcResponse.success(result, id);
        } catch (Exception e) {
            logger.error("Designer script execution failed", e);
            return JsonRpcResponse.error(
                    ErrorCodes.SCRIPT_EXECUTION_ERROR,
                    "Script execution failed: " + e.getMessage(),
                    id);
        }
    }

    /** Executes script in Gateway scope via RPC. */
    private JsonRpcResponse executeInGatewayScope(
            String code, int timeoutMs, String sessionId, boolean resetSession, Object id) {
        logger.info(
                "Executing script in Gateway scope ({} chars, timeout={}ms, session={})",
                code.length(),
                timeoutMs,
                sessionId);

        try {
            ExecuteScriptResult result =
                    gatewayRpcClient
                            .getRpc()
                            .executeScript(code, timeoutMs, sessionId, resetSession);
            return JsonRpcResponse.success(result, id);
        } catch (Exception e) {
            logger.error("Gateway script execution failed", e);
            return JsonRpcResponse.error(
                    ErrorCodes.GATEWAY_RPC_ERROR,
                    "Gateway script execution failed: " + e.getMessage(),
                    id);
        }
    }

    /** Executes script in Perspective session context via RPC. */
    private JsonRpcResponse executeInPerspectiveScope(
            ExecuteScriptParams params,
            int timeoutMs,
            String sessionId,
            boolean resetSession,
            Object id) {
        String perspectiveSessionId = params.getPerspectiveSessionId();
        if (perspectiveSessionId == null || perspectiveSessionId.isEmpty()) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS,
                    "Perspective scope requires perspectiveSessionId parameter",
                    id);
        }

        logger.info(
                "Executing script in Perspective scope (session={}, page={}, view={}, component={})",
                perspectiveSessionId,
                params.getPerspectivePageId(),
                params.getPerspectiveViewInstanceId(),
                params.getPerspectiveComponentPath());

        try {
            ExecuteScriptResult result =
                    gatewayRpcClient
                            .getRpc()
                            .perspectiveExecuteScript(
                                    params.getCode(),
                                    timeoutMs,
                                    perspectiveSessionId,
                                    params.getPerspectivePageId(),
                                    params.getPerspectiveViewInstanceId(),
                                    params.getPerspectiveComponentPath(),
                                    sessionId, // scriptSessionId for variable persistence
                                    resetSession);
            return JsonRpcResponse.success(result, id);
        } catch (Exception e) {
            logger.error("Perspective script execution failed", e);
            return JsonRpcResponse.error(
                    ErrorCodes.GATEWAY_RPC_ERROR,
                    "Perspective script execution failed: " + e.getMessage(),
                    id);
        }
    }
}

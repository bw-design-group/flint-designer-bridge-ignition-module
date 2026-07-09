package dev.bwdesigngroup.flint.designer.handlers;

import dev.bwdesigngroup.flint.common.protocol.JsonRpcRequest;
import dev.bwdesigngroup.flint.common.protocol.JsonRpcResponse;
import dev.bwdesigngroup.flint.common.protocol.methods.PingResult;
import dev.bwdesigngroup.flint.designer.server.FlintWebSocketHandler;

/** Handler for the ping method. Returns status information about the Designer instance. */
public class PingHandler implements MethodHandler {

    private final FlintWebSocketHandler handler;

    public PingHandler(FlintWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public JsonRpcResponse handle(JsonRpcRequest request) {
        String projectName = handler.getContext().getProjectName();
        PingResult result = PingResult.ok(projectName, handler.isAuthenticated());
        return JsonRpcResponse.success(result, request.getId());
    }
}

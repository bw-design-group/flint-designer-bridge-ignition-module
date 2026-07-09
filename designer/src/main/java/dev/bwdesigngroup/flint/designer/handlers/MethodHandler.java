package dev.bwdesigngroup.flint.designer.handlers;

import dev.bwdesigngroup.flint.common.protocol.JsonRpcRequest;
import dev.bwdesigngroup.flint.common.protocol.JsonRpcResponse;

/** Interface for JSON-RPC method handlers. */
public interface MethodHandler {
    /**
     * Handles a JSON-RPC request.
     *
     * @param request The JSON-RPC request
     * @return The JSON-RPC response, or null for notifications
     */
    JsonRpcResponse handle(JsonRpcRequest request);
}

package dev.bwdesigngroup.flint.designer.handlers;

import dev.bwdesigngroup.flint.common.FlintConstants;
import dev.bwdesigngroup.flint.common.protocol.JsonRpcRequest;
import dev.bwdesigngroup.flint.common.protocol.JsonRpcResponse;
import dev.bwdesigngroup.flint.common.protocol.methods.lsp.CompletionParams;
import dev.bwdesigngroup.flint.common.protocol.methods.lsp.CompletionResult;
import dev.bwdesigngroup.flint.designer.lsp.CompletionService;
import dev.bwdesigngroup.flint.designer.server.FlintWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for LSP (Language Server Protocol) related methods. Provides code completion, hover,
 * signature help, and other IDE features.
 */
public class LspHandler implements MethodHandler {
    private static final Logger logger = LoggerFactory.getLogger("flint.Designer.LspHandler");

    private final FlintWebSocketHandler handler;
    private final CompletionService completionService;

    public LspHandler(FlintWebSocketHandler handler) {
        this.handler = handler;
        this.completionService = new CompletionService(handler.getContext());
    }

    @Override
    public JsonRpcResponse handle(JsonRpcRequest request) {
        String method = request.getMethod();

        switch (method) {
            case FlintConstants.METHOD_LSP_COMPLETION:
                return handleCompletion(request);
            case FlintConstants.METHOD_LSP_HOVER:
                return handleHover(request);
            case FlintConstants.METHOD_LSP_SIGNATURE_HELP:
                return handleSignatureHelp(request);
            case FlintConstants.METHOD_LSP_INVALIDATE_CACHE:
                return handleInvalidateCache(request);
            default:
                logger.warn("Unknown LSP method: {}", method);
                return JsonRpcResponse.error(
                        -32601, "Method not found: " + method, request.getId());
        }
    }

    /** Handles the lsp.completion method. Returns completion items for the given prefix. */
    private JsonRpcResponse handleCompletion(JsonRpcRequest request) {
        try {
            CompletionParams params =
                    handler.getGson()
                            .fromJson(
                                    handler.getGson().toJson(request.getParams()),
                                    CompletionParams.class);

            if (params == null) {
                params = new CompletionParams();
            }

            String prefix = params.getPrefix();
            logger.debug("Handling completion request for prefix: {}", prefix);

            CompletionResult result = completionService.getCompletions(prefix);

            logger.debug(
                    "Returning {} completion items for prefix: {}",
                    result.getItems().size(),
                    prefix);

            return JsonRpcResponse.success(result, request.getId());

        } catch (Exception e) {
            logger.error("Error handling completion request", e);
            return JsonRpcResponse.error(
                    -32603, "Internal error: " + e.getMessage(), request.getId());
        }
    }

    /** Handles the lsp.hover method. Returns documentation for a symbol at a given position. */
    private JsonRpcResponse handleHover(JsonRpcRequest request) {
        // TODO: Implement hover support
        return JsonRpcResponse.error(-32601, "Method not implemented: lsp.hover", request.getId());
    }

    /** Handles the lsp.signatureHelp method. Returns parameter information for a function call. */
    private JsonRpcResponse handleSignatureHelp(JsonRpcRequest request) {
        // TODO: Implement signature help support
        return JsonRpcResponse.error(
                -32601, "Method not implemented: lsp.signatureHelp", request.getId());
    }

    /**
     * Handles the lsp.invalidateCache method. Clears the completion cache to force fresh data on
     * next request.
     */
    private JsonRpcResponse handleInvalidateCache(JsonRpcRequest request) {
        try {
            logger.debug("Invalidating LSP completion cache");
            completionService.invalidateCache();
            return JsonRpcResponse.success(true, request.getId());
        } catch (Exception e) {
            logger.error("Error invalidating cache", e);
            return JsonRpcResponse.error(
                    -32603, "Internal error: " + e.getMessage(), request.getId());
        }
    }
}

package dev.bwdesigngroup.flint.designer.handlers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.inductiveautomation.ignition.designer.model.DesignerContext;
import dev.bwdesigngroup.flint.common.FlintConstants;
import dev.bwdesigngroup.flint.common.protocol.JsonRpcRequest;
import dev.bwdesigngroup.flint.common.protocol.JsonRpcResponse;
import dev.bwdesigngroup.flint.designer.server.FlintWebSocketHandler;
import org.java_websocket.WebSocket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("LspHandler")
@ExtendWith(MockitoExtension.class)
class LspHandlerTest {

    private static final String TEST_SECRET = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4";
    private static final String TEST_PROJECT = "TestProject";

    @Mock private WebSocket conn;

    @Mock private DesignerContext context;

    private FlintWebSocketHandler wsHandler;
    private LspHandler lspHandler;
    private Gson gson;

    @BeforeEach
    void setUp() {
        lenient().when(context.getProjectName()).thenReturn(TEST_PROJECT);
        wsHandler = new FlintWebSocketHandler(conn, context, TEST_SECRET);
        wsHandler.setAuthenticated(true);
        lspHandler = new LspHandler(wsHandler);
        gson = new GsonBuilder().create();
    }

    @Nested
    @DisplayName("method routing")
    class MethodRouting {

        @Test
        @DisplayName("returns METHOD_NOT_FOUND for unknown LSP methods")
        void returnsMethodNotFoundForUnknown() {
            JsonRpcRequest request = new JsonRpcRequest("lsp.unknownMethod", null, 1);

            JsonRpcResponse response = lspHandler.handle(request);

            assertFalse(response.isSuccess());
            String json = gson.toJson(response);
            assertTrue(json.contains("Method not found"));
        }

        @Test
        @DisplayName("routes lsp.hover to not-implemented response")
        void routesHoverToNotImplemented() {
            JsonRpcRequest request = new JsonRpcRequest(FlintConstants.METHOD_LSP_HOVER, null, 2);

            JsonRpcResponse response = lspHandler.handle(request);

            assertFalse(response.isSuccess());
            String json = gson.toJson(response);
            assertTrue(json.contains("not implemented") || json.contains("Method not"));
        }

        @Test
        @DisplayName("routes lsp.signatureHelp to not-implemented response")
        void routesSignatureHelpToNotImplemented() {
            JsonRpcRequest request =
                    new JsonRpcRequest(FlintConstants.METHOD_LSP_SIGNATURE_HELP, null, 3);

            JsonRpcResponse response = lspHandler.handle(request);

            assertFalse(response.isSuccess());
        }

        @Test
        @DisplayName("routes lsp.completion without error on valid method")
        void routesCompletionValidMethod() {
            JsonRpcRequest request =
                    new JsonRpcRequest(FlintConstants.METHOD_LSP_COMPLETION, null, 4);

            // Completion handler may succeed or fail depending on mocked context,
            // but it should not return METHOD_NOT_FOUND
            JsonRpcResponse response = lspHandler.handle(request);

            String json = gson.toJson(response);
            assertFalse(json.contains("Method not found"));
        }

        @Test
        @DisplayName("routes lsp.invalidateCache without error on valid method")
        void routesInvalidateCacheValidMethod() {
            JsonRpcRequest request =
                    new JsonRpcRequest(FlintConstants.METHOD_LSP_INVALIDATE_CACHE, null, 5);

            JsonRpcResponse response = lspHandler.handle(request);

            // Cache invalidation should succeed even with mocked context
            String json = gson.toJson(response);
            assertFalse(json.contains("Method not found"));
        }
    }

    @Nested
    @DisplayName("completion handling")
    class CompletionHandling {

        @Test
        @DisplayName("accepts null params for completion")
        void acceptsNullParams() {
            JsonRpcRequest request =
                    new JsonRpcRequest(FlintConstants.METHOD_LSP_COMPLETION, null, 10);

            JsonRpcResponse response = lspHandler.handle(request);

            // Should not throw - null params gets defaulted
            String json = gson.toJson(response);
            assertFalse(json.contains("Method not found"));
        }

        @Test
        @DisplayName("accepts params with prefix for completion")
        void acceptsParamsWithPrefix() {
            JsonObject params = new JsonObject();
            params.addProperty("prefix", "system.");
            JsonRpcRequest request =
                    new JsonRpcRequest(FlintConstants.METHOD_LSP_COMPLETION, params, 11);

            JsonRpcResponse response = lspHandler.handle(request);

            String json = gson.toJson(response);
            assertFalse(json.contains("Method not found"));
        }

        @Test
        @DisplayName("accepts empty prefix for completion")
        void acceptsEmptyPrefix() {
            JsonObject params = new JsonObject();
            params.addProperty("prefix", "");
            JsonRpcRequest request =
                    new JsonRpcRequest(FlintConstants.METHOD_LSP_COMPLETION, params, 12);

            JsonRpcResponse response = lspHandler.handle(request);

            assertFalse(gson.toJson(response).contains("Method not found"));
        }
    }

    @Nested
    @DisplayName("cache invalidation")
    class CacheInvalidation {

        @Test
        @DisplayName("returns success on cache invalidation")
        void returnsSuccessOnInvalidateCache() {
            JsonRpcRequest request =
                    new JsonRpcRequest(FlintConstants.METHOD_LSP_INVALIDATE_CACHE, null, 20);

            JsonRpcResponse response = lspHandler.handle(request);

            assertTrue(response.isSuccess());
        }

        @Test
        @DisplayName("response id matches request id for cache invalidation")
        void responseIdMatchesRequestId() {
            JsonRpcRequest request =
                    new JsonRpcRequest(FlintConstants.METHOD_LSP_INVALIDATE_CACHE, null, 99);

            JsonRpcResponse response = lspHandler.handle(request);

            assertEquals(99, ((Number) response.getId()).intValue());
        }
    }

    @Nested
    @DisplayName("response metadata")
    class ResponseMetadata {

        @Test
        @DisplayName("response id matches request id")
        void responseIdMatchesRequestId() {
            JsonRpcRequest request = new JsonRpcRequest("lsp.unknownMethod", null, 42);

            JsonRpcResponse response = lspHandler.handle(request);

            assertEquals(42, ((Number) response.getId()).intValue());
        }

        @Test
        @DisplayName("error response includes jsonrpc 2.0")
        void errorResponseIncludesJsonrpc() {
            JsonRpcRequest request = new JsonRpcRequest("lsp.unknownMethod", null, 5);

            JsonRpcResponse response = lspHandler.handle(request);

            assertEquals("2.0", response.getJsonrpc());
        }
    }
}

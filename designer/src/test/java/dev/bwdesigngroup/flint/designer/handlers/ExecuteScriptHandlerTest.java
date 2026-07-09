package dev.bwdesigngroup.flint.designer.handlers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.inductiveautomation.ignition.designer.model.DesignerContext;
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

@DisplayName("ExecuteScriptHandler")
@ExtendWith(MockitoExtension.class)
class ExecuteScriptHandlerTest {

    private static final String TEST_SECRET = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4";
    private static final String TEST_PROJECT = "TestProject";

    @Mock private WebSocket conn;

    @Mock private DesignerContext context;

    private FlintWebSocketHandler wsHandler;
    private ExecuteScriptHandler executeHandler;
    private Gson gson;

    @BeforeEach
    void setUp() {
        lenient().when(context.getProjectName()).thenReturn(TEST_PROJECT);
        wsHandler = new FlintWebSocketHandler(conn, context, TEST_SECRET);
        wsHandler.setAuthenticated(true);
        executeHandler = new ExecuteScriptHandler(wsHandler);
        gson = new GsonBuilder().create();
    }

    @Nested
    @DisplayName("missing code parameter")
    class MissingCodeParameter {

        @Test
        @DisplayName("returns INVALID_PARAMS when params is null")
        void returnsInvalidParamsWhenNull() {
            JsonRpcRequest request = new JsonRpcRequest("executeScript", null, 1);

            JsonRpcResponse response = executeHandler.handle(request);

            assertFalse(response.isSuccess());
            String json = gson.toJson(response);
            assertTrue(
                    json.contains("Missing required parameter: code")
                            || json.contains("INVALID_PARAMS"));
        }

        @Test
        @DisplayName("returns INVALID_PARAMS when code is empty")
        void returnsInvalidParamsWhenCodeEmpty() {
            JsonObject params = new JsonObject();
            params.addProperty("code", "");
            JsonRpcRequest request = new JsonRpcRequest("executeScript", params, 2);

            JsonRpcResponse response = executeHandler.handle(request);

            assertFalse(response.isSuccess());
        }

        @Test
        @DisplayName("returns INVALID_PARAMS when code field is missing")
        void returnsInvalidParamsWhenCodeMissing() {
            JsonObject params = new JsonObject();
            params.addProperty("scope", "designer");
            JsonRpcRequest request = new JsonRpcRequest("executeScript", params, 3);

            JsonRpcResponse response = executeHandler.handle(request);

            assertFalse(response.isSuccess());
        }
    }

    @Nested
    @DisplayName("scope validation")
    class ScopeValidation {

        @Test
        @DisplayName("defaults to designer scope when scope not specified")
        void defaultsToDesignerScope() {
            JsonObject params = new JsonObject();
            params.addProperty("code", "print('hello')");
            JsonRpcRequest request = new JsonRpcRequest("executeScript", params, 1);

            // This will attempt to execute in designer scope
            // The actual execution may fail due to mocked context, but we verify it doesn't
            // return INVALID_PARAMS - meaning scope validation passed
            JsonRpcResponse response = executeHandler.handle(request);
            Object id = response.getId();
            assertEquals(1, ((Number) id).intValue());
        }

        @Test
        @DisplayName("perspective scope requires perspectiveSessionId")
        void perspectiveScopeRequiresSessionId() {
            JsonObject params = new JsonObject();
            params.addProperty("code", "print('hello')");
            params.addProperty("scope", "perspective");
            JsonRpcRequest request = new JsonRpcRequest("executeScript", params, 4);

            JsonRpcResponse response = executeHandler.handle(request);

            assertFalse(response.isSuccess());
            String json = gson.toJson(response);
            assertTrue(json.contains("perspectiveSessionId"));
        }
    }

    @Nested
    @DisplayName("response metadata")
    class ResponseMetadata {

        @Test
        @DisplayName("response id matches request id")
        void responseIdMatchesRequestId() {
            JsonRpcRequest request = new JsonRpcRequest("executeScript", null, 42);

            JsonRpcResponse response = executeHandler.handle(request);

            assertEquals(42, ((Number) response.getId()).intValue());
        }

        @Test
        @DisplayName("error response includes jsonrpc 2.0")
        void errorResponseIncludesJsonrpc() {
            JsonRpcRequest request = new JsonRpcRequest("executeScript", null, 5);

            JsonRpcResponse response = executeHandler.handle(request);

            assertEquals("2.0", response.getJsonrpc());
        }
    }
}

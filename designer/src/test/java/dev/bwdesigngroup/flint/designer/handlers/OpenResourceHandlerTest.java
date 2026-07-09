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

@DisplayName("OpenResourceHandler")
@ExtendWith(MockitoExtension.class)
class OpenResourceHandlerTest {

    private static final String TEST_SECRET = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4";
    private static final String TEST_PROJECT = "TestProject";

    @Mock private WebSocket conn;

    @Mock private DesignerContext context;

    private FlintWebSocketHandler wsHandler;
    private OpenResourceHandler openHandler;
    private Gson gson;

    @BeforeEach
    void setUp() {
        lenient().when(context.getProjectName()).thenReturn(TEST_PROJECT);
        wsHandler = new FlintWebSocketHandler(conn, context, TEST_SECRET);
        wsHandler.setAuthenticated(true);
        openHandler = new OpenResourceHandler(wsHandler);
        gson = new GsonBuilder().create();
    }

    @Nested
    @DisplayName("missing parameters")
    class MissingParameters {

        @Test
        @DisplayName("returns INVALID_PARAMS when params is null")
        void returnsErrorWhenParamsNull() {
            JsonRpcRequest request = new JsonRpcRequest("designer.openResource", null, 1);

            JsonRpcResponse response = openHandler.handle(request);

            assertFalse(response.isSuccess());
            String json = gson.toJson(response);
            assertTrue(json.contains("Missing required parameter: resourceType"));
        }

        @Test
        @DisplayName("returns INVALID_PARAMS when resourceType is missing")
        void returnsErrorWhenResourceTypeMissing() {
            JsonObject params = new JsonObject();
            params.addProperty("resourcePath", "MyFolder/MyScript");
            JsonRpcRequest request = new JsonRpcRequest("designer.openResource", params, 2);

            JsonRpcResponse response = openHandler.handle(request);

            assertFalse(response.isSuccess());
            String json = gson.toJson(response);
            assertTrue(json.contains("Missing required parameter: resourceType"));
        }

        @Test
        @DisplayName("returns INVALID_PARAMS when resourcePath is missing")
        void returnsErrorWhenResourcePathMissing() {
            JsonObject params = new JsonObject();
            params.addProperty("resourceType", "script-python");
            JsonRpcRequest request = new JsonRpcRequest("designer.openResource", params, 3);

            JsonRpcResponse response = openHandler.handle(request);

            assertFalse(response.isSuccess());
            String json = gson.toJson(response);
            assertTrue(json.contains("Missing required parameter: resourcePath"));
        }

        @Test
        @DisplayName("returns INVALID_PARAMS when resourceType is empty")
        void returnsErrorWhenResourceTypeEmpty() {
            JsonObject params = new JsonObject();
            params.addProperty("resourceType", "");
            params.addProperty("resourcePath", "MyFolder/MyScript");
            JsonRpcRequest request = new JsonRpcRequest("designer.openResource", params, 4);

            JsonRpcResponse response = openHandler.handle(request);

            assertFalse(response.isSuccess());
        }

        @Test
        @DisplayName("returns INVALID_PARAMS when resourcePath is empty")
        void returnsErrorWhenResourcePathEmpty() {
            JsonObject params = new JsonObject();
            params.addProperty("resourceType", "script-python");
            params.addProperty("resourcePath", "");
            JsonRpcRequest request = new JsonRpcRequest("designer.openResource", params, 5);

            JsonRpcResponse response = openHandler.handle(request);

            assertFalse(response.isSuccess());
        }
    }

    @Nested
    @DisplayName("unsupported resource types")
    class UnsupportedResourceTypes {

        @Test
        @DisplayName("returns error for unknown resource type")
        void returnsErrorForUnknownType() {
            JsonObject params = new JsonObject();
            params.addProperty("resourceType", "unknown-type");
            params.addProperty("resourcePath", "MyFolder/MyScript");
            JsonRpcRequest request = new JsonRpcRequest("designer.openResource", params, 6);

            JsonRpcResponse response = openHandler.handle(request);

            assertFalse(response.isSuccess());
            String json = gson.toJson(response);
            assertTrue(json.contains("Unsupported resource type"));
        }

        @Test
        @DisplayName("error message includes the unsupported type name")
        void errorIncludesTypeName() {
            JsonObject params = new JsonObject();
            params.addProperty("resourceType", "nonexistent");
            params.addProperty("resourcePath", "path");
            JsonRpcRequest request = new JsonRpcRequest("designer.openResource", params, 7);

            JsonRpcResponse response = openHandler.handle(request);

            assertFalse(response.isSuccess());
            String json = gson.toJson(response);
            assertTrue(json.contains("nonexistent"));
        }
    }

    @Nested
    @DisplayName("valid resource types")
    class ValidResourceTypes {

        @Test
        @DisplayName("accepts script-python resource type")
        void acceptsScriptPython() {
            // Will fail at open step (no real Designer) but should not fail at validation
            JsonObject params = new JsonObject();
            params.addProperty("resourceType", "script-python");
            params.addProperty("resourcePath", "MyFolder/MyScript");
            JsonRpcRequest request = new JsonRpcRequest("designer.openResource", params, 10);

            JsonRpcResponse response = openHandler.handle(request);

            // Should NOT be an INVALID_PARAMS error - may be INTERNAL_ERROR due to mock context
            String json = gson.toJson(response);
            assertFalse(json.contains("Unsupported resource type"));
            assertFalse(json.contains("Missing required parameter"));
        }

        @Test
        @DisplayName("accepts perspective-view resource type")
        void acceptsPerspectiveView() {
            JsonObject params = new JsonObject();
            params.addProperty("resourceType", "perspective-view");
            params.addProperty("resourcePath", "Views/MainView");
            JsonRpcRequest request = new JsonRpcRequest("designer.openResource", params, 11);

            JsonRpcResponse response = openHandler.handle(request);

            String json = gson.toJson(response);
            assertFalse(json.contains("Unsupported resource type"));
        }

        @Test
        @DisplayName("accepts named-query resource type")
        void acceptsNamedQuery() {
            JsonObject params = new JsonObject();
            params.addProperty("resourceType", "named-query");
            params.addProperty("resourcePath", "Queries/GetUsers");
            JsonRpcRequest request = new JsonRpcRequest("designer.openResource", params, 12);

            JsonRpcResponse response = openHandler.handle(request);

            String json = gson.toJson(response);
            assertFalse(json.contains("Unsupported resource type"));
        }
    }

    @Nested
    @DisplayName("response metadata")
    class ResponseMetadata {

        @Test
        @DisplayName("response id matches request id")
        void responseIdMatchesRequestId() {
            JsonRpcRequest request = new JsonRpcRequest("designer.openResource", null, 42);

            JsonRpcResponse response = openHandler.handle(request);

            assertEquals(42, ((Number) response.getId()).intValue());
        }

        @Test
        @DisplayName("response includes jsonrpc 2.0")
        void responseIncludesJsonrpc() {
            JsonRpcRequest request = new JsonRpcRequest("designer.openResource", null, 8);

            JsonRpcResponse response = openHandler.handle(request);

            assertEquals("2.0", response.getJsonrpc());
        }
    }
}

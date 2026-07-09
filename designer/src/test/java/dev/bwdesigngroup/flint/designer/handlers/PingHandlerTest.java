package dev.bwdesigngroup.flint.designer.handlers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.inductiveautomation.ignition.designer.model.DesignerContext;
import dev.bwdesigngroup.flint.common.protocol.JsonRpcRequest;
import dev.bwdesigngroup.flint.common.protocol.JsonRpcResponse;
import dev.bwdesigngroup.flint.common.protocol.methods.PingResult;
import dev.bwdesigngroup.flint.designer.server.FlintWebSocketHandler;
import org.java_websocket.WebSocket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("PingHandler")
@ExtendWith(MockitoExtension.class)
class PingHandlerTest {

    private static final String TEST_SECRET = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4";
    private static final String TEST_PROJECT = "TestProject";

    @Mock private WebSocket conn;

    @Mock private DesignerContext context;

    private FlintWebSocketHandler wsHandler;
    private PingHandler pingHandler;
    private Gson gson;

    @BeforeEach
    void setUp() {
        when(context.getProjectName()).thenReturn(TEST_PROJECT);
        wsHandler = new FlintWebSocketHandler(conn, context, TEST_SECRET);
        pingHandler = new PingHandler(wsHandler);
        gson = new GsonBuilder().create();
    }

    private JsonRpcRequest createPingRequest(Object id) {
        return new JsonRpcRequest("ping", null, id);
    }

    @Nested
    @DisplayName("when not authenticated")
    class WhenNotAuthenticated {

        @Test
        @DisplayName("returns success response with authenticated=false")
        void returnsSuccessWithAuthenticatedFalse() {
            JsonRpcRequest request = createPingRequest(1);

            JsonRpcResponse response = pingHandler.handle(request);

            assertTrue(response.isSuccess());
            assertNotNull(response.getResult());

            // Deserialize the result to check fields
            String json = gson.toJson(response.getResult());
            PingResult result = gson.fromJson(json, PingResult.class);
            assertFalse(result.isAuthenticated());
        }

        @Test
        @DisplayName("returns project name from context")
        void returnsProjectName() {
            JsonRpcRequest request = createPingRequest(1);

            JsonRpcResponse response = pingHandler.handle(request);

            String json = gson.toJson(response.getResult());
            PingResult result = gson.fromJson(json, PingResult.class);
            assertEquals(TEST_PROJECT, result.getProjectName());
        }

        @Test
        @DisplayName("returns status 'ok'")
        void returnsStatusOk() {
            JsonRpcRequest request = createPingRequest(1);

            JsonRpcResponse response = pingHandler.handle(request);

            String json = gson.toJson(response.getResult());
            PingResult result = gson.fromJson(json, PingResult.class);
            assertEquals("ok", result.getStatus());
        }
    }

    @Nested
    @DisplayName("when authenticated")
    class WhenAuthenticated {

        @BeforeEach
        void authenticate() {
            wsHandler.setAuthenticated(true);
        }

        @Test
        @DisplayName("returns success response with authenticated=true")
        void returnsSuccessWithAuthenticatedTrue() {
            JsonRpcRequest request = createPingRequest(1);

            JsonRpcResponse response = pingHandler.handle(request);

            assertTrue(response.isSuccess());
            String json = gson.toJson(response.getResult());
            PingResult result = gson.fromJson(json, PingResult.class);
            assertTrue(result.isAuthenticated());
        }

        @Test
        @DisplayName("returns project name from context when authenticated")
        void returnsProjectNameWhenAuthenticated() {
            JsonRpcRequest request = createPingRequest(1);

            JsonRpcResponse response = pingHandler.handle(request);

            String json = gson.toJson(response.getResult());
            PingResult result = gson.fromJson(json, PingResult.class);
            assertEquals(TEST_PROJECT, result.getProjectName());
        }
    }

    @Nested
    @DisplayName("response metadata")
    class ResponseMetadata {

        @Test
        @DisplayName("response id matches request id")
        void responseIdMatchesRequestId() {
            JsonRpcRequest request = createPingRequest(55);

            JsonRpcResponse response = pingHandler.handle(request);

            assertEquals(55, ((Number) response.getId()).intValue());
        }

        @Test
        @DisplayName("includes a non-zero timestamp")
        void includesTimestamp() {
            JsonRpcRequest request = createPingRequest(1);

            JsonRpcResponse response = pingHandler.handle(request);

            String json = gson.toJson(response.getResult());
            PingResult result = gson.fromJson(json, PingResult.class);
            assertTrue(result.getTimestamp() > 0);
        }
    }
}

package dev.bwdesigngroup.flint.designer.handlers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.inductiveautomation.ignition.designer.model.DesignerContext;
import dev.bwdesigngroup.flint.common.protocol.ErrorCodes;
import dev.bwdesigngroup.flint.common.protocol.JsonRpcRequest;
import dev.bwdesigngroup.flint.common.protocol.JsonRpcResponse;
import dev.bwdesigngroup.flint.common.protocol.methods.AuthenticateParams;
import dev.bwdesigngroup.flint.designer.server.FlintWebSocketHandler;
import org.java_websocket.WebSocket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("AuthenticateHandler")
@ExtendWith(MockitoExtension.class)
class AuthenticateHandlerTest {

    private static final String TEST_SECRET = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4";
    private static final String TEST_PROJECT = "TestProject";

    @Mock private WebSocket conn;

    @Mock private DesignerContext context;

    private FlintWebSocketHandler wsHandler;
    private AuthenticateHandler authenticateHandler;
    private Gson gson;

    @BeforeEach
    void setUp() {
        lenient().when(context.getProjectName()).thenReturn(TEST_PROJECT);
        wsHandler = new FlintWebSocketHandler(conn, context, TEST_SECRET);
        authenticateHandler = new AuthenticateHandler(wsHandler);
        gson = new GsonBuilder().create();
    }

    private JsonRpcRequest createAuthRequest(Object params, Object id) {
        JsonElement paramsJson = params != null ? gson.toJsonTree(params) : null;
        return new JsonRpcRequest("authenticate", paramsJson, id);
    }

    @Nested
    @DisplayName("successful authentication")
    class SuccessfulAuthentication {

        @Test
        @DisplayName("returns success response when secret is correct")
        void returnsSuccessWithCorrectSecret() {
            AuthenticateParams params = new AuthenticateParams(TEST_SECRET, "Flint", "1.0.0");
            JsonRpcRequest request = createAuthRequest(params, 1);

            JsonRpcResponse response = authenticateHandler.handle(request);

            assertTrue(response.isSuccess());
            assertNotNull(response.getResult());
        }

        @Test
        @DisplayName("sets handler authenticated to true on success")
        void setsAuthenticatedTrue() {
            assertFalse(wsHandler.isAuthenticated());

            AuthenticateParams params = new AuthenticateParams(TEST_SECRET, "Flint", "1.0.0");
            JsonRpcRequest request = createAuthRequest(params, 1);

            authenticateHandler.handle(request);

            assertTrue(wsHandler.isAuthenticated());
        }

        @Test
        @DisplayName("sets client name and version on success")
        void setsClientNameAndVersion() {
            AuthenticateParams params = new AuthenticateParams(TEST_SECRET, "FlintVSCode", "2.5.0");
            JsonRpcRequest request = createAuthRequest(params, 1);

            authenticateHandler.handle(request);

            assertEquals("FlintVSCode", wsHandler.getClientName());
            assertEquals("2.5.0", wsHandler.getClientVersion());
        }

        @Test
        @DisplayName("response contains project name from context")
        void responseContainsProjectName() {
            AuthenticateParams params = new AuthenticateParams(TEST_SECRET, "Flint", "1.0.0");
            JsonRpcRequest request = createAuthRequest(params, 1);

            JsonRpcResponse response = authenticateHandler.handle(request);

            // Serialize and deserialize to inspect the result
            String json = gson.toJson(response.getResult());
            assertTrue(json.contains(TEST_PROJECT));
        }

        @Test
        @DisplayName("response id matches request id")
        void responseIdMatchesRequestId() {
            AuthenticateParams params = new AuthenticateParams(TEST_SECRET, "Flint", "1.0.0");
            JsonRpcRequest request = createAuthRequest(params, 42);

            JsonRpcResponse response = authenticateHandler.handle(request);

            assertEquals(42, ((Number) response.getId()).intValue());
        }
    }

    @Nested
    @DisplayName("failed authentication")
    class FailedAuthentication {

        @Test
        @DisplayName("returns AUTHENTICATION_FAILED error when secret is wrong")
        void returnsErrorWithWrongSecret() {
            AuthenticateParams params =
                    new AuthenticateParams("wrong-secret-value-0000000000000", "Flint", "1.0.0");
            JsonRpcRequest request = createAuthRequest(params, 1);

            JsonRpcResponse response = authenticateHandler.handle(request);

            assertFalse(response.isSuccess());
            assertNotNull(response.getError());
            assertEquals(ErrorCodes.AUTHENTICATION_FAILED, response.getError().getCode());
        }

        @Test
        @DisplayName("does not set handler authenticated on wrong secret")
        void doesNotSetAuthenticatedOnWrongSecret() {
            AuthenticateParams params =
                    new AuthenticateParams("wrong-secret-value-0000000000000", "Flint", "1.0.0");
            JsonRpcRequest request = createAuthRequest(params, 1);

            authenticateHandler.handle(request);

            assertFalse(wsHandler.isAuthenticated());
        }
    }

    @Nested
    @DisplayName("missing or invalid params")
    class MissingParams {

        @Test
        @DisplayName("returns INVALID_PARAMS error when params are null")
        void returnsErrorWithNullParams() {
            JsonRpcRequest request = createAuthRequest(null, 1);

            JsonRpcResponse response = authenticateHandler.handle(request);

            assertFalse(response.isSuccess());
            assertNotNull(response.getError());
            assertEquals(ErrorCodes.INVALID_PARAMS, response.getError().getCode());
        }

        @Test
        @DisplayName("returns INVALID_PARAMS error when secret field is missing")
        void returnsErrorWithMissingSecretField() {
            // Create params with null secret
            AuthenticateParams params = new AuthenticateParams(null, "Flint", "1.0.0");
            JsonRpcRequest request = createAuthRequest(params, 1);

            JsonRpcResponse response = authenticateHandler.handle(request);

            assertFalse(response.isSuccess());
            assertNotNull(response.getError());
            assertEquals(ErrorCodes.INVALID_PARAMS, response.getError().getCode());
        }

        @Test
        @DisplayName("does not set handler authenticated when params are null")
        void doesNotSetAuthenticatedOnNullParams() {
            JsonRpcRequest request = createAuthRequest(null, 1);

            authenticateHandler.handle(request);

            assertFalse(wsHandler.isAuthenticated());
        }

        @Test
        @DisplayName("error response id matches request id for null params")
        void errorResponseIdMatchesRequestId() {
            JsonRpcRequest request = createAuthRequest(null, 99);

            JsonRpcResponse response = authenticateHandler.handle(request);

            assertEquals(99, ((Number) response.getId()).intValue());
        }
    }
}

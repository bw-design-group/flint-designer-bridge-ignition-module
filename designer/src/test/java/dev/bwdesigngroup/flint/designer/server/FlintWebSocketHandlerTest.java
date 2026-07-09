package dev.bwdesigngroup.flint.designer.server;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.inductiveautomation.ignition.designer.model.DesignerContext;
import dev.bwdesigngroup.flint.common.FlintConstants;
import dev.bwdesigngroup.flint.common.protocol.ErrorCodes;
import dev.bwdesigngroup.flint.common.protocol.methods.AuthenticateParams;
import org.java_websocket.WebSocket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("FlintWebSocketHandler")
@ExtendWith(MockitoExtension.class)
class FlintWebSocketHandlerTest {

    private static final String TEST_SECRET = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4";
    private static final String TEST_PROJECT = "TestProject";

    @Mock private WebSocket conn;

    @Mock private DesignerContext context;

    private FlintWebSocketHandler handler;
    private Gson gson;

    @BeforeEach
    void setUp() {
        lenient().when(context.getProjectName()).thenReturn(TEST_PROJECT);
        handler = new FlintWebSocketHandler(conn, context, TEST_SECRET);
        gson = new GsonBuilder().create();
    }

    /** Captures the JSON string sent via conn.send() and parses it as a JsonObject. */
    private JsonObject captureResponse() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(conn, atLeastOnce()).send(captor.capture());
        String lastResponse = captor.getValue();
        return gson.fromJson(lastResponse, JsonObject.class);
    }

    /** Captures all JSON strings sent via conn.send() and returns the last one as JsonObject. */
    private JsonObject captureLastResponse() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(conn, atLeastOnce()).send(captor.capture());
        String lastResponse = captor.getAllValues().get(captor.getAllValues().size() - 1);
        return gson.fromJson(lastResponse, JsonObject.class);
    }

    private String buildJsonRpcMessage(String method, Object params, Object id) {
        JsonObject msg = new JsonObject();
        msg.addProperty("jsonrpc", "2.0");
        msg.addProperty("method", method);
        if (params != null) {
            msg.add("params", gson.toJsonTree(params));
        }
        if (id != null) {
            if (id instanceof Number) {
                msg.addProperty("id", (Number) id);
            } else {
                msg.addProperty("id", id.toString());
            }
        }
        return gson.toJson(msg);
    }

    @Nested
    @DisplayName("handler registration")
    class HandlerRegistration {

        @Test
        @DisplayName("registers all expected method handlers")
        void registersExpectedMethods() {
            // The handler should accept the authenticate method without being authenticated
            String authMessage =
                    buildJsonRpcMessage(
                            FlintConstants.METHOD_AUTHENTICATE,
                            new AuthenticateParams(TEST_SECRET, "Test", "1.0"),
                            1);
            handler.handleMessage(authMessage);

            // If the handler is registered, we should get a success response (not METHOD_NOT_FOUND)
            JsonObject response = captureResponse();
            assertFalse(
                    response.has("error")
                            && response.getAsJsonObject("error").get("code").getAsInt()
                                    == ErrorCodes.METHOD_NOT_FOUND,
                    "authenticate method should be registered");
        }
    }

    @Nested
    @DisplayName("parse error handling")
    class ParseErrorHandling {

        @Test
        @DisplayName("sends PARSE_ERROR for invalid JSON")
        void sendsParseErrorForInvalidJson() {
            handler.handleMessage("{ not valid json !!!");

            JsonObject response = captureResponse();
            assertTrue(response.has("error"));
            assertEquals(
                    ErrorCodes.PARSE_ERROR,
                    response.getAsJsonObject("error").get("code").getAsInt());
        }

        @Test
        @DisplayName("sends PARSE_ERROR with jsonrpc version 2.0")
        void parseErrorIncludesJsonrpcVersion() {
            handler.handleMessage("not json at all");

            JsonObject response = captureResponse();
            assertEquals("2.0", response.get("jsonrpc").getAsString());
        }

        @Test
        @DisplayName("sends PARSE_ERROR with null id for invalid JSON")
        void parseErrorHasNullId() {
            handler.handleMessage("{{{broken");

            JsonObject response = captureResponse();
            // The id may be absent from the JSON or present as a JSON null
            assertTrue(
                    !response.has("id")
                            || response.get("id") == null
                            || response.get("id").isJsonNull(),
                    "Parse error response should have null or absent id");
        }
    }

    @Nested
    @DisplayName("invalid request handling")
    class InvalidRequestHandling {

        @Test
        @DisplayName("sends INVALID_REQUEST when method is missing")
        void sendsInvalidRequestWhenMethodMissing() {
            // Valid JSON, valid jsonrpc version, but no method
            String message = "{\"jsonrpc\":\"2.0\",\"id\":1}";
            handler.handleMessage(message);

            JsonObject response = captureResponse();
            assertTrue(response.has("error"));
            assertEquals(
                    ErrorCodes.INVALID_REQUEST,
                    response.getAsJsonObject("error").get("code").getAsInt());
        }

        @Test
        @DisplayName("sends INVALID_REQUEST when jsonrpc version is wrong")
        void sendsInvalidRequestWhenVersionWrong() {
            String message = "{\"jsonrpc\":\"1.0\",\"method\":\"ping\",\"id\":1}";
            handler.handleMessage(message);

            JsonObject response = captureResponse();
            assertTrue(response.has("error"));
            assertEquals(
                    ErrorCodes.INVALID_REQUEST,
                    response.getAsJsonObject("error").get("code").getAsInt());
        }

        @Test
        @DisplayName("sends INVALID_REQUEST when jsonrpc field is missing")
        void sendsInvalidRequestWhenJsonrpcMissing() {
            String message = "{\"method\":\"ping\",\"id\":1}";
            handler.handleMessage(message);

            JsonObject response = captureResponse();
            assertTrue(response.has("error"));
            assertEquals(
                    ErrorCodes.INVALID_REQUEST,
                    response.getAsJsonObject("error").get("code").getAsInt());
        }
    }

    @Nested
    @DisplayName("authentication enforcement")
    class AuthenticationEnforcement {

        @Test
        @DisplayName("sends NOT_AUTHENTICATED for non-auth method when not authenticated")
        void sendsNotAuthenticatedForNonAuthMethod() {
            String message = buildJsonRpcMessage("ping", null, 1);
            handler.handleMessage(message);

            JsonObject response = captureResponse();
            assertTrue(response.has("error"));
            assertEquals(
                    ErrorCodes.NOT_AUTHENTICATED,
                    response.getAsJsonObject("error").get("code").getAsInt());
        }

        @Test
        @DisplayName("allows authenticate method without being authenticated")
        void allowsAuthenticateMethodWithoutAuth() {
            AuthenticateParams params = new AuthenticateParams(TEST_SECRET, "Test", "1.0");
            String message = buildJsonRpcMessage(FlintConstants.METHOD_AUTHENTICATE, params, 1);

            handler.handleMessage(message);

            JsonObject response = captureResponse();
            // Should NOT be a NOT_AUTHENTICATED error
            if (response.has("error")) {
                assertNotEquals(
                        ErrorCodes.NOT_AUTHENTICATED,
                        response.getAsJsonObject("error").get("code").getAsInt(),
                        "authenticate method should be allowed without prior authentication");
            }
        }

        @Test
        @DisplayName("sends NOT_AUTHENTICATED for executeScript when not authenticated")
        void sendsNotAuthenticatedForExecuteScript() {
            String message = buildJsonRpcMessage("executeScript", null, 1);
            handler.handleMessage(message);

            JsonObject response = captureResponse();
            assertTrue(response.has("error"));
            assertEquals(
                    ErrorCodes.NOT_AUTHENTICATED,
                    response.getAsJsonObject("error").get("code").getAsInt());
        }
    }

    @Nested
    @DisplayName("method not found handling")
    class MethodNotFoundHandling {

        @BeforeEach
        void authenticate() {
            handler.setAuthenticated(true);
        }

        @Test
        @DisplayName("sends METHOD_NOT_FOUND for unknown method")
        void sendsMethodNotFoundForUnknownMethod() {
            String message = buildJsonRpcMessage("nonexistent.method", null, 1);
            handler.handleMessage(message);

            JsonObject response = captureResponse();
            assertTrue(response.has("error"));
            assertEquals(
                    ErrorCodes.METHOD_NOT_FOUND,
                    response.getAsJsonObject("error").get("code").getAsInt());
        }

        @Test
        @DisplayName("METHOD_NOT_FOUND response includes method name in message")
        void methodNotFoundIncludesMethodName() {
            String message = buildJsonRpcMessage("foo.bar.baz", null, 1);
            handler.handleMessage(message);

            JsonObject response = captureResponse();
            String errorMessage = response.getAsJsonObject("error").get("message").getAsString();
            assertTrue(errorMessage.contains("foo.bar.baz"));
        }

        @Test
        @DisplayName("METHOD_NOT_FOUND response includes the request id")
        void methodNotFoundIncludesId() {
            String message = buildJsonRpcMessage("unknown", null, 77);
            handler.handleMessage(message);

            JsonObject response = captureResponse();
            assertEquals(77, response.get("id").getAsInt());
        }
    }

    @Nested
    @DisplayName("method dispatch after authentication")
    class MethodDispatchAfterAuth {

        @BeforeEach
        void authenticate() {
            handler.setAuthenticated(true);
        }

        @Test
        @DisplayName("dispatches ping method successfully when authenticated")
        void dispatchesPingSuccessfully() {
            String message = buildJsonRpcMessage("ping", null, 1);
            handler.handleMessage(message);

            JsonObject response = captureResponse();
            // Ping should return a success response with result
            assertTrue(response.has("result"), "ping should return a result, not an error");
            assertFalse(response.has("error") && !response.get("error").isJsonNull());
        }

        @Test
        @DisplayName("ping response contains project name")
        void pingResponseContainsProjectName() {
            String message = buildJsonRpcMessage("ping", null, 1);
            handler.handleMessage(message);

            JsonObject response = captureResponse();
            JsonObject result = response.getAsJsonObject("result");
            assertEquals(TEST_PROJECT, result.get("projectName").getAsString());
        }
    }

    @Nested
    @DisplayName("full authentication flow")
    class FullAuthenticationFlow {

        @Test
        @DisplayName("authenticate then ping succeeds end-to-end")
        void authenticateThenPingSucceeds() {
            // Step 1: Authenticate
            AuthenticateParams params = new AuthenticateParams(TEST_SECRET, "TestClient", "1.0.0");
            String authMessage = buildJsonRpcMessage(FlintConstants.METHOD_AUTHENTICATE, params, 1);
            handler.handleMessage(authMessage);

            // Step 2: Ping (should succeed now)
            String pingMessage = buildJsonRpcMessage("ping", null, 2);
            handler.handleMessage(pingMessage);

            // Verify the last response is the ping success
            JsonObject pingResponse = captureLastResponse();
            assertTrue(pingResponse.has("result"), "ping should succeed after authentication");
            assertEquals(2, pingResponse.get("id").getAsInt());
        }

        @Test
        @DisplayName("handler is authenticated after successful auth")
        void handlerIsAuthenticatedAfterSuccess() {
            assertFalse(handler.isAuthenticated());

            AuthenticateParams params = new AuthenticateParams(TEST_SECRET, "TestClient", "1.0.0");
            String authMessage = buildJsonRpcMessage(FlintConstants.METHOD_AUTHENTICATE, params, 1);
            handler.handleMessage(authMessage);

            assertTrue(handler.isAuthenticated());
        }
    }

    @Nested
    @DisplayName("notification handling")
    class NotificationHandling {

        @BeforeEach
        void authenticate() {
            handler.setAuthenticated(true);
        }

        @Test
        @DisplayName("does not send response for notification (no id)")
        void doesNotSendResponseForNotification() {
            // A notification has no id field
            String message = buildJsonRpcMessage("ping", null, null);
            // Remove the id from the JSON since buildJsonRpcMessage skips null id
            handler.handleMessage(message);

            // For a notification, no response should be sent
            verify(conn, never()).send(anyString());
        }
    }

    @Nested
    @DisplayName("getter/setter methods")
    class GetterSetterMethods {

        @Test
        @DisplayName("getSecret returns the secret passed to constructor")
        void getSecretReturnsConstructorSecret() {
            assertEquals(TEST_SECRET, handler.getSecret());
        }

        @Test
        @DisplayName("getContext returns the context passed to constructor")
        void getContextReturnsConstructorContext() {
            assertSame(context, handler.getContext());
        }

        @Test
        @DisplayName("getConnection returns the WebSocket passed to constructor")
        void getConnectionReturnsConstructorConn() {
            assertSame(conn, handler.getConnection());
        }

        @Test
        @DisplayName("getGson returns a non-null Gson instance")
        void getGsonReturnsNonNull() {
            assertNotNull(handler.getGson());
        }

        @Test
        @DisplayName("isAuthenticated defaults to false")
        void isAuthenticatedDefaultsFalse() {
            assertFalse(handler.isAuthenticated());
        }

        @Test
        @DisplayName("setAuthenticated updates authenticated state")
        void setAuthenticatedUpdatesState() {
            handler.setAuthenticated(true);
            assertTrue(handler.isAuthenticated());
            handler.setAuthenticated(false);
            assertFalse(handler.isAuthenticated());
        }

        @Test
        @DisplayName("client name and version default to null")
        void clientNameVersionDefaultNull() {
            assertNull(handler.getClientName());
            assertNull(handler.getClientVersion());
        }

        @Test
        @DisplayName("setClientName and setClientVersion update values")
        void setClientNameAndVersionUpdatesValues() {
            handler.setClientName("TestClient");
            handler.setClientVersion("3.0.0");
            assertEquals("TestClient", handler.getClientName());
            assertEquals("3.0.0", handler.getClientVersion());
        }
    }
}

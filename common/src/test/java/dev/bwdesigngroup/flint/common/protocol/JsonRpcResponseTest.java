package dev.bwdesigngroup.flint.common.protocol;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("JsonRpcResponse")
class JsonRpcResponseTest {

    @Nested
    @DisplayName("success() factory method")
    class SuccessFactory {

        @Test
        @DisplayName("creates a response with jsonrpc set to '2.0'")
        void setsJsonrpcVersion() {
            JsonRpcResponse response = JsonRpcResponse.success("ok", 1);
            assertEquals("2.0", response.getJsonrpc());
        }

        @Test
        @DisplayName("creates a response with the given result")
        void setsResult() {
            JsonRpcResponse response = JsonRpcResponse.success("hello", 1);
            assertEquals("hello", response.getResult());
        }

        @Test
        @DisplayName("creates a response with the given id")
        void setsId() {
            JsonRpcResponse response = JsonRpcResponse.success("ok", 42);
            assertEquals(42, response.getId());
        }

        @Test
        @DisplayName("creates a response with null error")
        void errorIsNull() {
            JsonRpcResponse response = JsonRpcResponse.success("ok", 1);
            assertNull(response.getError());
        }

        @Test
        @DisplayName("allows null result")
        void allowsNullResult() {
            JsonRpcResponse response = JsonRpcResponse.success(null, 1);
            assertNull(response.getResult());
        }

        @Test
        @DisplayName("allows complex result objects")
        void allowsComplexResult() {
            Map<String, Object> result = Map.of("key", "value", "count", 5);
            JsonRpcResponse response = JsonRpcResponse.success(result, 1);
            assertEquals(result, response.getResult());
        }

        @Test
        @DisplayName("allows string id")
        void allowsStringId() {
            JsonRpcResponse response = JsonRpcResponse.success("ok", "request-abc");
            assertEquals("request-abc", response.getId());
        }

        @Test
        @DisplayName("allows null id")
        void allowsNullId() {
            JsonRpcResponse response = JsonRpcResponse.success("ok", null);
            assertNull(response.getId());
        }
    }

    @Nested
    @DisplayName("error(JsonRpcError, Object) factory method")
    class ErrorWithErrorObjectFactory {

        @Test
        @DisplayName("creates a response with jsonrpc set to '2.0'")
        void setsJsonrpcVersion() {
            JsonRpcError err = new JsonRpcError(-32600, "Invalid Request");
            JsonRpcResponse response = JsonRpcResponse.error(err, 1);
            assertEquals("2.0", response.getJsonrpc());
        }

        @Test
        @DisplayName("creates a response with the given error")
        void setsError() {
            JsonRpcError err = new JsonRpcError(-32600, "Invalid Request");
            JsonRpcResponse response = JsonRpcResponse.error(err, 1);
            assertNotNull(response.getError());
            assertEquals(-32600, response.getError().getCode());
            assertEquals("Invalid Request", response.getError().getMessage());
        }

        @Test
        @DisplayName("creates a response with the given id")
        void setsId() {
            JsonRpcError err = new JsonRpcError(-32600, "Invalid Request");
            JsonRpcResponse response = JsonRpcResponse.error(err, 99);
            assertEquals(99, response.getId());
        }

        @Test
        @DisplayName("creates a response with null result")
        void resultIsNull() {
            JsonRpcError err = new JsonRpcError(-32600, "Invalid Request");
            JsonRpcResponse response = JsonRpcResponse.error(err, 1);
            assertNull(response.getResult());
        }
    }

    @Nested
    @DisplayName("error(int, String, Object) factory method")
    class ErrorWithCodeAndMessageFactory {

        @Test
        @DisplayName("creates a response with the correct error code")
        void setsErrorCode() {
            JsonRpcResponse response = JsonRpcResponse.error(-32601, "Method not found", 1);
            assertNotNull(response.getError());
            assertEquals(-32601, response.getError().getCode());
        }

        @Test
        @DisplayName("creates a response with the correct error message")
        void setsErrorMessage() {
            JsonRpcResponse response = JsonRpcResponse.error(-32601, "Method not found", 1);
            assertEquals("Method not found", response.getError().getMessage());
        }

        @Test
        @DisplayName("creates a response with the correct id")
        void setsId() {
            JsonRpcResponse response = JsonRpcResponse.error(-32601, "Method not found", 7);
            assertEquals(7, response.getId());
        }

        @Test
        @DisplayName("creates a response with null result")
        void resultIsNull() {
            JsonRpcResponse response = JsonRpcResponse.error(-32601, "Method not found", 1);
            assertNull(response.getResult());
        }

        @Test
        @DisplayName("error data is null when not provided")
        void errorDataIsNull() {
            JsonRpcResponse response = JsonRpcResponse.error(-32601, "Method not found", 1);
            assertNull(response.getError().getData());
        }
    }

    @Nested
    @DisplayName("error(int, String, Object, Object) factory method with data")
    class ErrorWithDataFactory {

        @Test
        @DisplayName("creates a response with error data")
        void setsErrorData() {
            JsonRpcResponse response =
                    JsonRpcResponse.error(-32010, "Script error", "Traceback: ...", 1);
            assertNotNull(response.getError());
            assertEquals("Traceback: ...", response.getError().getData());
        }

        @Test
        @DisplayName("creates a response with the correct error code and message")
        void setsCodeAndMessage() {
            JsonRpcResponse response = JsonRpcResponse.error(-32010, "Script error", "details", 1);
            assertEquals(-32010, response.getError().getCode());
            assertEquals("Script error", response.getError().getMessage());
        }

        @Test
        @DisplayName("creates a response with the correct id")
        void setsId() {
            JsonRpcResponse response = JsonRpcResponse.error(-32010, "Script error", "details", 5);
            assertEquals(5, response.getId());
        }

        @Test
        @DisplayName("allows null data")
        void allowsNullData() {
            JsonRpcResponse response = JsonRpcResponse.error(-32010, "Script error", null, 1);
            assertNull(response.getError().getData());
        }

        @Test
        @DisplayName("allows complex data objects")
        void allowsComplexData() {
            Map<String, String> errorData =
                    Map.of("traceback", "line 1\nline 2", "scope", "gateway");
            JsonRpcResponse response = JsonRpcResponse.error(-32010, "Script error", errorData, 1);
            assertEquals(errorData, response.getError().getData());
        }
    }

    @Nested
    @DisplayName("isSuccess()")
    class IsSuccess {

        @Test
        @DisplayName("returns true for a success response")
        void trueForSuccessResponse() {
            JsonRpcResponse response = JsonRpcResponse.success("result", 1);
            assertTrue(response.isSuccess());
        }

        @Test
        @DisplayName("returns true for a success response with null result")
        void trueForSuccessWithNullResult() {
            JsonRpcResponse response = JsonRpcResponse.success(null, 1);
            assertTrue(response.isSuccess());
        }

        @Test
        @DisplayName("returns false for an error response created with JsonRpcError")
        void falseForErrorWithObject() {
            JsonRpcError err = new JsonRpcError(-32600, "Invalid Request");
            JsonRpcResponse response = JsonRpcResponse.error(err, 1);
            assertFalse(response.isSuccess());
        }

        @Test
        @DisplayName("returns false for an error response created with code and message")
        void falseForErrorWithCodeAndMessage() {
            JsonRpcResponse response = JsonRpcResponse.error(-32601, "Method not found", 1);
            assertFalse(response.isSuccess());
        }

        @Test
        @DisplayName("returns false for an error response created with data")
        void falseForErrorWithData() {
            JsonRpcResponse response = JsonRpcResponse.error(-32010, "Script error", "trace", 1);
            assertFalse(response.isSuccess());
        }
    }

    @Nested
    @DisplayName("getJsonrpc()")
    class GetJsonrpc {

        @Test
        @DisplayName("always returns '2.0' for success responses")
        void alwaysReturns2dot0ForSuccess() {
            JsonRpcResponse response = JsonRpcResponse.success("ok", 1);
            assertEquals("2.0", response.getJsonrpc());
        }

        @Test
        @DisplayName("always returns '2.0' for error responses")
        void alwaysReturns2dot0ForError() {
            JsonRpcResponse response = JsonRpcResponse.error(-32700, "Parse error", 1);
            assertEquals("2.0", response.getJsonrpc());
        }
    }

    @Nested
    @DisplayName("Error codes integration")
    class ErrorCodesIntegration {

        @Test
        @DisplayName("can create parse error response")
        void parseError() {
            JsonRpcResponse response =
                    JsonRpcResponse.error(ErrorCodes.PARSE_ERROR, "Parse error", null);
            assertEquals(ErrorCodes.PARSE_ERROR, response.getError().getCode());
        }

        @Test
        @DisplayName("can create method not found response")
        void methodNotFound() {
            JsonRpcResponse response =
                    JsonRpcResponse.error(ErrorCodes.METHOD_NOT_FOUND, "Method not found", 1);
            assertEquals(ErrorCodes.METHOD_NOT_FOUND, response.getError().getCode());
        }

        @Test
        @DisplayName("can create authentication failed response")
        void authenticationFailed() {
            JsonRpcResponse response =
                    JsonRpcResponse.error(ErrorCodes.AUTHENTICATION_FAILED, "Bad credentials", 1);
            assertEquals(ErrorCodes.AUTHENTICATION_FAILED, response.getError().getCode());
        }

        @Test
        @DisplayName("can create script execution error response with traceback data")
        void scriptExecutionError() {
            JsonRpcResponse response =
                    JsonRpcResponse.error(
                            ErrorCodes.SCRIPT_EXECUTION_ERROR,
                            "Script failed",
                            "TypeError at line 5",
                            1);
            assertEquals(ErrorCodes.SCRIPT_EXECUTION_ERROR, response.getError().getCode());
            assertEquals("TypeError at line 5", response.getError().getData());
        }
    }
}

package dev.bwdesigngroup.flint.common.protocol;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Constructor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ErrorCodes")
class ErrorCodesTest {

    @Nested
    @DisplayName("Standard JSON-RPC 2.0 error codes")
    class StandardErrorCodes {

        @Test
        @DisplayName("PARSE_ERROR is -32700")
        void parseError() {
            assertEquals(-32700, ErrorCodes.PARSE_ERROR);
        }

        @Test
        @DisplayName("INVALID_REQUEST is -32600")
        void invalidRequest() {
            assertEquals(-32600, ErrorCodes.INVALID_REQUEST);
        }

        @Test
        @DisplayName("METHOD_NOT_FOUND is -32601")
        void methodNotFound() {
            assertEquals(-32601, ErrorCodes.METHOD_NOT_FOUND);
        }

        @Test
        @DisplayName("INVALID_PARAMS is -32602")
        void invalidParams() {
            assertEquals(-32602, ErrorCodes.INVALID_PARAMS);
        }

        @Test
        @DisplayName("INTERNAL_ERROR is -32603")
        void internalError() {
            assertEquals(-32603, ErrorCodes.INTERNAL_ERROR);
        }
    }

    @Nested
    @DisplayName("Standard error codes conform to JSON-RPC 2.0 spec ranges")
    class StandardErrorCodeRanges {

        @Test
        @DisplayName("PARSE_ERROR is in the JSON-RPC reserved range (-32700 to -32600)")
        void parseErrorInRange() {
            assertTrue(
                    ErrorCodes.PARSE_ERROR >= -32700 && ErrorCodes.PARSE_ERROR <= -32600,
                    "PARSE_ERROR should be in range [-32700, -32600]");
        }

        @Test
        @DisplayName("INVALID_REQUEST is in the JSON-RPC reserved range (-32700 to -32600)")
        void invalidRequestInRange() {
            assertTrue(
                    ErrorCodes.INVALID_REQUEST >= -32700 && ErrorCodes.INVALID_REQUEST <= -32600,
                    "INVALID_REQUEST should be in range [-32700, -32600]");
        }

        @Test
        @DisplayName("METHOD_NOT_FOUND is in the JSON-RPC reserved range (-32700 to -32600)")
        void methodNotFoundInRange() {
            assertTrue(
                    ErrorCodes.METHOD_NOT_FOUND >= -32700 && ErrorCodes.METHOD_NOT_FOUND <= -32600,
                    "METHOD_NOT_FOUND should be in range [-32700, -32600]");
        }

        @Test
        @DisplayName("INVALID_PARAMS is in the JSON-RPC reserved range (-32700 to -32600)")
        void invalidParamsInRange() {
            assertTrue(
                    ErrorCodes.INVALID_PARAMS >= -32700 && ErrorCodes.INVALID_PARAMS <= -32600,
                    "INVALID_PARAMS should be in range [-32700, -32600]");
        }

        @Test
        @DisplayName("INTERNAL_ERROR is in the JSON-RPC reserved range (-32700 to -32600)")
        void internalErrorInRange() {
            assertTrue(
                    ErrorCodes.INTERNAL_ERROR >= -32700 && ErrorCodes.INTERNAL_ERROR <= -32600,
                    "INTERNAL_ERROR should be in range [-32700, -32600]");
        }
    }

    @Nested
    @DisplayName("Authentication error codes")
    class AuthenticationErrorCodes {

        @Test
        @DisplayName("NOT_AUTHENTICATED is -32000")
        void notAuthenticated() {
            assertEquals(-32000, ErrorCodes.NOT_AUTHENTICATED);
        }

        @Test
        @DisplayName("AUTHENTICATION_FAILED is -32001")
        void authenticationFailed() {
            assertEquals(-32001, ErrorCodes.AUTHENTICATION_FAILED);
        }

        @Test
        @DisplayName("AUTHENTICATION_TIMEOUT is -32002")
        void authenticationTimeout() {
            assertEquals(-32002, ErrorCodes.AUTHENTICATION_TIMEOUT);
        }
    }

    @Nested
    @DisplayName("Script execution error codes")
    class ScriptErrorCodes {

        @Test
        @DisplayName("SCRIPT_EXECUTION_ERROR is -32010")
        void scriptExecutionError() {
            assertEquals(-32010, ErrorCodes.SCRIPT_EXECUTION_ERROR);
        }

        @Test
        @DisplayName("SCRIPT_TIMEOUT is -32011")
        void scriptTimeout() {
            assertEquals(-32011, ErrorCodes.SCRIPT_TIMEOUT);
        }
    }

    @Nested
    @DisplayName("Gateway scope error codes")
    class GatewayErrorCodes {

        @Test
        @DisplayName("GATEWAY_RPC_ERROR is -32020")
        void gatewayRpcError() {
            assertEquals(-32020, ErrorCodes.GATEWAY_RPC_ERROR);
        }

        @Test
        @DisplayName("GATEWAY_NOT_AVAILABLE is -32021")
        void gatewayNotAvailable() {
            assertEquals(-32021, ErrorCodes.GATEWAY_NOT_AVAILABLE);
        }

        @Test
        @DisplayName("GATEWAY_SCOPE_NOT_SUPPORTED is -32022")
        void gatewayScopeNotSupported() {
            assertEquals(-32022, ErrorCodes.GATEWAY_SCOPE_NOT_SUPPORTED);
        }
    }

    @Nested
    @DisplayName("Custom error codes are in the server error range")
    class CustomErrorCodeRanges {

        @Test
        @DisplayName("NOT_AUTHENTICATED is in the server error range (-32000 to -32099)")
        void notAuthenticatedInRange() {
            assertCustomCodeInRange(ErrorCodes.NOT_AUTHENTICATED);
        }

        @Test
        @DisplayName("AUTHENTICATION_FAILED is in the server error range (-32000 to -32099)")
        void authenticationFailedInRange() {
            assertCustomCodeInRange(ErrorCodes.AUTHENTICATION_FAILED);
        }

        @Test
        @DisplayName("AUTHENTICATION_TIMEOUT is in the server error range (-32000 to -32099)")
        void authenticationTimeoutInRange() {
            assertCustomCodeInRange(ErrorCodes.AUTHENTICATION_TIMEOUT);
        }

        @Test
        @DisplayName("SCRIPT_EXECUTION_ERROR is in the server error range (-32000 to -32099)")
        void scriptExecutionErrorInRange() {
            assertCustomCodeInRange(ErrorCodes.SCRIPT_EXECUTION_ERROR);
        }

        @Test
        @DisplayName("SCRIPT_TIMEOUT is in the server error range (-32000 to -32099)")
        void scriptTimeoutInRange() {
            assertCustomCodeInRange(ErrorCodes.SCRIPT_TIMEOUT);
        }

        @Test
        @DisplayName("GATEWAY_RPC_ERROR is in the server error range (-32000 to -32099)")
        void gatewayRpcErrorInRange() {
            assertCustomCodeInRange(ErrorCodes.GATEWAY_RPC_ERROR);
        }

        @Test
        @DisplayName("GATEWAY_NOT_AVAILABLE is in the server error range (-32000 to -32099)")
        void gatewayNotAvailableInRange() {
            assertCustomCodeInRange(ErrorCodes.GATEWAY_NOT_AVAILABLE);
        }

        @Test
        @DisplayName("GATEWAY_SCOPE_NOT_SUPPORTED is in the server error range (-32000 to -32099)")
        void gatewayScopeNotSupportedInRange() {
            assertCustomCodeInRange(ErrorCodes.GATEWAY_SCOPE_NOT_SUPPORTED);
        }

        private void assertCustomCodeInRange(int code) {
            assertTrue(
                    code >= -32099 && code <= -32000,
                    "Custom error code " + code + " should be in range [-32099, -32000]");
        }
    }

    @Nested
    @DisplayName("All error codes are unique")
    class ErrorCodesUniqueness {

        @Test
        @DisplayName("no two standard error codes share the same value")
        void standardCodesAreUnique() {
            int[] standardCodes = {
                ErrorCodes.PARSE_ERROR,
                ErrorCodes.INVALID_REQUEST,
                ErrorCodes.METHOD_NOT_FOUND,
                ErrorCodes.INVALID_PARAMS,
                ErrorCodes.INTERNAL_ERROR
            };
            assertAllUnique(standardCodes);
        }

        @Test
        @DisplayName("no two custom error codes share the same value")
        void customCodesAreUnique() {
            int[] customCodes = {
                ErrorCodes.NOT_AUTHENTICATED,
                ErrorCodes.AUTHENTICATION_FAILED,
                ErrorCodes.AUTHENTICATION_TIMEOUT,
                ErrorCodes.SCRIPT_EXECUTION_ERROR,
                ErrorCodes.SCRIPT_TIMEOUT,
                ErrorCodes.GATEWAY_RPC_ERROR,
                ErrorCodes.GATEWAY_NOT_AVAILABLE,
                ErrorCodes.GATEWAY_SCOPE_NOT_SUPPORTED
            };
            assertAllUnique(customCodes);
        }

        @Test
        @DisplayName("no overlap between standard and custom error codes")
        void noOverlapBetweenStandardAndCustom() {
            int[] allCodes = {
                ErrorCodes.PARSE_ERROR,
                ErrorCodes.INVALID_REQUEST,
                ErrorCodes.METHOD_NOT_FOUND,
                ErrorCodes.INVALID_PARAMS,
                ErrorCodes.INTERNAL_ERROR,
                ErrorCodes.NOT_AUTHENTICATED,
                ErrorCodes.AUTHENTICATION_FAILED,
                ErrorCodes.AUTHENTICATION_TIMEOUT,
                ErrorCodes.SCRIPT_EXECUTION_ERROR,
                ErrorCodes.SCRIPT_TIMEOUT,
                ErrorCodes.GATEWAY_RPC_ERROR,
                ErrorCodes.GATEWAY_NOT_AVAILABLE,
                ErrorCodes.GATEWAY_SCOPE_NOT_SUPPORTED
            };
            assertAllUnique(allCodes);
        }

        private void assertAllUnique(int[] codes) {
            for (int i = 0; i < codes.length; i++) {
                for (int j = i + 1; j < codes.length; j++) {
                    assertNotEquals(
                            codes[i],
                            codes[j],
                            "Error codes at index "
                                    + i
                                    + " and "
                                    + j
                                    + " should be different but both are "
                                    + codes[i]);
                }
            }
        }
    }

    @Nested
    @DisplayName("All error codes are negative")
    class ErrorCodesAreNegative {

        @Test
        @DisplayName("all standard codes are negative")
        void standardCodesAreNegative() {
            assertTrue(ErrorCodes.PARSE_ERROR < 0);
            assertTrue(ErrorCodes.INVALID_REQUEST < 0);
            assertTrue(ErrorCodes.METHOD_NOT_FOUND < 0);
            assertTrue(ErrorCodes.INVALID_PARAMS < 0);
            assertTrue(ErrorCodes.INTERNAL_ERROR < 0);
        }

        @Test
        @DisplayName("all custom codes are negative")
        void customCodesAreNegative() {
            assertTrue(ErrorCodes.NOT_AUTHENTICATED < 0);
            assertTrue(ErrorCodes.AUTHENTICATION_FAILED < 0);
            assertTrue(ErrorCodes.AUTHENTICATION_TIMEOUT < 0);
            assertTrue(ErrorCodes.SCRIPT_EXECUTION_ERROR < 0);
            assertTrue(ErrorCodes.SCRIPT_TIMEOUT < 0);
            assertTrue(ErrorCodes.GATEWAY_RPC_ERROR < 0);
            assertTrue(ErrorCodes.GATEWAY_NOT_AVAILABLE < 0);
            assertTrue(ErrorCodes.GATEWAY_SCOPE_NOT_SUPPORTED < 0);
        }
    }

    @Nested
    @DisplayName("Utility class contract")
    class UtilityClassContract {

        @Test
        @DisplayName("private constructor prevents instantiation via reflection")
        void privateConstructorPreventsInstantiation() throws Exception {
            Constructor<ErrorCodes> constructor = ErrorCodes.class.getDeclaredConstructor();
            assertFalse(constructor.canAccess(null), "Constructor should be private");
            constructor.setAccessible(true);
            assertNotNull(constructor.newInstance());
        }
    }
}

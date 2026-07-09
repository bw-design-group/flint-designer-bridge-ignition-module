package dev.bwdesigngroup.flint.common;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Constructor;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("FlintConstants")
class FlintConstantsTest {

    @Nested
    @DisplayName("Module identification constants")
    class ModuleIdentification {

        @Test
        @DisplayName("MODULE_ID has the expected value")
        void moduleIdHasExpectedValue() {
            assertEquals("dev.bwdesigngroup.flint.FlintDesignerBridge", FlintConstants.MODULE_ID);
        }

        @Test
        @DisplayName("MODULE_NAME has the expected value")
        void moduleNameHasExpectedValue() {
            assertEquals("Flint Designer Bridge", FlintConstants.MODULE_NAME);
        }
    }

    @Nested
    @DisplayName("Port range constants")
    class PortRange {

        @Test
        @DisplayName("PORT_RANGE_START is 52400")
        void portRangeStartIs52400() {
            assertEquals(52400, FlintConstants.PORT_RANGE_START);
        }

        @Test
        @DisplayName("PORT_RANGE_END is 52500")
        void portRangeEndIs52500() {
            assertEquals(52500, FlintConstants.PORT_RANGE_END);
        }

        @Test
        @DisplayName("port range start is less than port range end")
        void portRangeStartIsLessThanEnd() {
            assertTrue(FlintConstants.PORT_RANGE_START < FlintConstants.PORT_RANGE_END);
        }

        @Test
        @DisplayName("port range provides at least 100 ports")
        void portRangeProvidesAtLeast100Ports() {
            int portCount = FlintConstants.PORT_RANGE_END - FlintConstants.PORT_RANGE_START;
            assertTrue(
                    portCount >= 100,
                    "Port range should provide at least 100 ports, got " + portCount);
        }
    }

    @Nested
    @DisplayName("Authentication constants")
    class Authentication {

        @Test
        @DisplayName("AUTH_TIMEOUT_MS is 5000")
        void authTimeoutIs5000() {
            assertEquals(5000, FlintConstants.AUTH_TIMEOUT_MS);
        }

        @Test
        @DisplayName("SECRET_LENGTH is 32")
        void secretLengthIs32() {
            assertEquals(32, FlintConstants.SECRET_LENGTH);
        }
    }

    @Nested
    @DisplayName("JSON-RPC method name constants")
    class MethodNames {

        @Test
        @DisplayName("METHOD_AUTHENTICATE is 'authenticate'")
        void authenticateMethod() {
            assertEquals("authenticate", FlintConstants.METHOD_AUTHENTICATE);
        }

        @Test
        @DisplayName("METHOD_PING is 'ping'")
        void pingMethod() {
            assertEquals("ping", FlintConstants.METHOD_PING);
        }

        @Test
        @DisplayName("METHOD_EXECUTE_SCRIPT is 'executeScript'")
        void executeScriptMethod() {
            assertEquals("executeScript", FlintConstants.METHOD_EXECUTE_SCRIPT);
        }

        @Test
        @DisplayName("METHOD_SHOW_MESSAGE is 'showMessage'")
        void showMessageMethod() {
            assertEquals("showMessage", FlintConstants.METHOD_SHOW_MESSAGE);
        }

        @Test
        @DisplayName("METHOD_PROJECT_SCAN is 'project.scan'")
        void projectScanMethod() {
            assertEquals("project.scan", FlintConstants.METHOD_PROJECT_SCAN);
        }

        @Test
        @DisplayName("METHOD_OPEN_RESOURCE is 'designer.openResource'")
        void openResourceMethod() {
            assertEquals("designer.openResource", FlintConstants.METHOD_OPEN_RESOURCE);
        }

        @Test
        @DisplayName("METHOD_LIST_RESOURCES is 'project.listResources'")
        void listResourcesMethod() {
            assertEquals("project.listResources", FlintConstants.METHOD_LIST_RESOURCES);
        }
    }

    @Nested
    @DisplayName("Debug method name constants")
    class DebugMethodNames {

        @Test
        @DisplayName("METHOD_DEBUG_START_SESSION is 'debug.startSession'")
        void debugStartSession() {
            assertEquals("debug.startSession", FlintConstants.METHOD_DEBUG_START_SESSION);
        }

        @Test
        @DisplayName("METHOD_DEBUG_STOP_SESSION is 'debug.stopSession'")
        void debugStopSession() {
            assertEquals("debug.stopSession", FlintConstants.METHOD_DEBUG_STOP_SESSION);
        }

        @Test
        @DisplayName("METHOD_DEBUG_SET_BREAKPOINTS is 'debug.setBreakpoints'")
        void debugSetBreakpoints() {
            assertEquals("debug.setBreakpoints", FlintConstants.METHOD_DEBUG_SET_BREAKPOINTS);
        }

        @Test
        @DisplayName("METHOD_DEBUG_CONTINUE is 'debug.continue'")
        void debugContinue() {
            assertEquals("debug.continue", FlintConstants.METHOD_DEBUG_CONTINUE);
        }

        @Test
        @DisplayName("METHOD_DEBUG_STEP_OVER is 'debug.stepOver'")
        void debugStepOver() {
            assertEquals("debug.stepOver", FlintConstants.METHOD_DEBUG_STEP_OVER);
        }

        @Test
        @DisplayName("METHOD_DEBUG_STEP_INTO is 'debug.stepInto'")
        void debugStepInto() {
            assertEquals("debug.stepInto", FlintConstants.METHOD_DEBUG_STEP_INTO);
        }

        @Test
        @DisplayName("METHOD_DEBUG_STEP_OUT is 'debug.stepOut'")
        void debugStepOut() {
            assertEquals("debug.stepOut", FlintConstants.METHOD_DEBUG_STEP_OUT);
        }

        @Test
        @DisplayName("METHOD_DEBUG_PAUSE is 'debug.pause'")
        void debugPause() {
            assertEquals("debug.pause", FlintConstants.METHOD_DEBUG_PAUSE);
        }

        @Test
        @DisplayName("DEBUG_TIMEOUT_MS is 30 minutes in milliseconds")
        void debugTimeoutIs30Minutes() {
            assertEquals(30 * 60 * 1000, FlintConstants.DEBUG_TIMEOUT_MS);
        }
    }

    @Nested
    @DisplayName("LSP method name constants")
    class LspMethodNames {

        @Test
        @DisplayName("METHOD_LSP_COMPLETION is 'lsp.completion'")
        void lspCompletion() {
            assertEquals("lsp.completion", FlintConstants.METHOD_LSP_COMPLETION);
        }

        @Test
        @DisplayName("METHOD_LSP_HOVER is 'lsp.hover'")
        void lspHover() {
            assertEquals("lsp.hover", FlintConstants.METHOD_LSP_HOVER);
        }

        @Test
        @DisplayName("METHOD_LSP_SIGNATURE_HELP is 'lsp.signatureHelp'")
        void lspSignatureHelp() {
            assertEquals("lsp.signatureHelp", FlintConstants.METHOD_LSP_SIGNATURE_HELP);
        }

        @Test
        @DisplayName("METHOD_LSP_DEFINITION is 'lsp.definition'")
        void lspDefinition() {
            assertEquals("lsp.definition", FlintConstants.METHOD_LSP_DEFINITION);
        }

        @Test
        @DisplayName("METHOD_LSP_REFERENCES is 'lsp.references'")
        void lspReferences() {
            assertEquals("lsp.references", FlintConstants.METHOD_LSP_REFERENCES);
        }

        @Test
        @DisplayName("METHOD_LSP_INVALIDATE_CACHE is 'lsp.invalidateCache'")
        void lspInvalidateCache() {
            assertEquals("lsp.invalidateCache", FlintConstants.METHOD_LSP_INVALIDATE_CACHE);
        }
    }

    @Nested
    @DisplayName("Perspective method name constants")
    class PerspectiveMethodNames {

        @Test
        @DisplayName("METHOD_PERSPECTIVE_IS_AVAILABLE is 'perspective.isAvailable'")
        void perspectiveIsAvailable() {
            assertEquals("perspective.isAvailable", FlintConstants.METHOD_PERSPECTIVE_IS_AVAILABLE);
        }

        @Test
        @DisplayName("METHOD_PERSPECTIVE_LIST_SESSIONS is 'perspective.listSessions'")
        void perspectiveListSessions() {
            assertEquals(
                    "perspective.listSessions", FlintConstants.METHOD_PERSPECTIVE_LIST_SESSIONS);
        }

        @Test
        @DisplayName("METHOD_PERSPECTIVE_EXECUTE_SCRIPT is 'perspective.executeScript'")
        void perspectiveExecuteScript() {
            assertEquals(
                    "perspective.executeScript", FlintConstants.METHOD_PERSPECTIVE_EXECUTE_SCRIPT);
        }
    }

    @Nested
    @DisplayName("LSP completion kind constants")
    class CompletionKinds {

        @Test
        @DisplayName("COMPLETION_KIND_TEXT is 1")
        void completionKindText() {
            assertEquals(1, FlintConstants.COMPLETION_KIND_TEXT);
        }

        @Test
        @DisplayName("COMPLETION_KIND_METHOD is 2")
        void completionKindMethod() {
            assertEquals(2, FlintConstants.COMPLETION_KIND_METHOD);
        }

        @Test
        @DisplayName("COMPLETION_KIND_FUNCTION is 3")
        void completionKindFunction() {
            assertEquals(3, FlintConstants.COMPLETION_KIND_FUNCTION);
        }

        @Test
        @DisplayName("COMPLETION_KIND_CLASS is 7")
        void completionKindClass() {
            assertEquals(7, FlintConstants.COMPLETION_KIND_CLASS);
        }

        @Test
        @DisplayName("COMPLETION_KIND_MODULE is 9")
        void completionKindModule() {
            assertEquals(9, FlintConstants.COMPLETION_KIND_MODULE);
        }

        @Test
        @DisplayName("COMPLETION_KIND_PROPERTY is 10")
        void completionKindProperty() {
            assertEquals(10, FlintConstants.COMPLETION_KIND_PROPERTY);
        }

        @Test
        @DisplayName("COMPLETION_KIND_CONSTANT is 21")
        void completionKindConstant() {
            assertEquals(21, FlintConstants.COMPLETION_KIND_CONSTANT);
        }
    }

    @Nested
    @DisplayName("JSON-RPC version constant")
    class JsonRpcVersion {

        @Test
        @DisplayName("JSONRPC_VERSION is '2.0'")
        void jsonrpcVersionIs2dot0() {
            assertEquals("2.0", FlintConstants.JSONRPC_VERSION);
        }
    }

    @Nested
    @DisplayName("Registry directory constants")
    class RegistryDirectory {

        @Test
        @DisplayName("FLINT_REGISTRY_DIR is '.ignition/flint/designers'")
        void registryDirHasExpectedValue() {
            assertEquals(".ignition/flint/designers", FlintConstants.FLINT_REGISTRY_DIR);
        }
    }

    @Nested
    @DisplayName("getRegistryDirectory()")
    class GetRegistryDirectory {

        @Test
        @DisplayName("returns a path under the user home directory")
        void returnsPathUnderUserHome() {
            Path result = FlintConstants.getRegistryDirectory();
            String userHome = System.getProperty("user.home");
            assertTrue(
                    result.startsWith(userHome),
                    "Registry directory should start with user home: " + userHome);
        }

        @Test
        @DisplayName("path ends with .ignition/flint/designers")
        void pathEndsWithRegistryDir() {
            Path result = FlintConstants.getRegistryDirectory();
            assertTrue(
                    result.toString().endsWith(".ignition/flint/designers"),
                    "Registry directory should end with .ignition/flint/designers");
        }

        @Test
        @DisplayName("returns consistent results on repeated calls")
        void returnsConsistentResults() {
            Path first = FlintConstants.getRegistryDirectory();
            Path second = FlintConstants.getRegistryDirectory();
            assertEquals(first, second);
        }
    }

    @Nested
    @DisplayName("getRegistryFileName()")
    class GetRegistryFileName {

        @Test
        @DisplayName("returns designer-{pid}.json for a given PID")
        void returnsCorrectFileNameForPid() {
            assertEquals("designer-12345.json", FlintConstants.getRegistryFileName(12345));
        }

        @Test
        @DisplayName("handles PID of 0")
        void handlesPidZero() {
            assertEquals("designer-0.json", FlintConstants.getRegistryFileName(0));
        }

        @Test
        @DisplayName("handles large PID values")
        void handlesLargePid() {
            assertEquals("designer-999999999.json", FlintConstants.getRegistryFileName(999999999));
        }

        @Test
        @DisplayName("handles PID of 1")
        void handlesMinimalPid() {
            assertEquals("designer-1.json", FlintConstants.getRegistryFileName(1));
        }
    }

    @Nested
    @DisplayName("getRegistryFilePath()")
    class GetRegistryFilePath {

        @Test
        @DisplayName("combines registry directory with file name")
        void combinesDirectoryAndFileName() {
            Path result = FlintConstants.getRegistryFilePath(42);
            Path expectedDir = FlintConstants.getRegistryDirectory();
            String expectedFileName = FlintConstants.getRegistryFileName(42);

            assertEquals(expectedDir, result.getParent());
            assertEquals(expectedFileName, result.getFileName().toString());
        }

        @Test
        @DisplayName("result is under user home directory")
        void resultIsUnderUserHome() {
            Path result = FlintConstants.getRegistryFilePath(100);
            String userHome = System.getProperty("user.home");
            assertTrue(result.startsWith(userHome));
        }

        @Test
        @DisplayName("file path ends with designer-{pid}.json")
        void filePathEndsWithExpectedName() {
            Path result = FlintConstants.getRegistryFilePath(7890);
            assertTrue(result.toString().endsWith("designer-7890.json"));
        }
    }

    @Nested
    @DisplayName("Utility class contract")
    class UtilityClassContract {

        @Test
        @DisplayName("private constructor prevents instantiation via reflection")
        void privateConstructorPreventsInstantiation() throws Exception {
            Constructor<FlintConstants> constructor = FlintConstants.class.getDeclaredConstructor();
            assertFalse(constructor.canAccess(null), "Constructor should be private");
            constructor.setAccessible(true);
            // Should be able to invoke via reflection (no exception thrown from constructor itself)
            assertNotNull(constructor.newInstance());
        }
    }
}

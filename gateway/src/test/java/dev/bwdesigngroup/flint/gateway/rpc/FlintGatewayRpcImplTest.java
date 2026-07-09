package dev.bwdesigngroup.flint.gateway.rpc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import dev.bwdesigngroup.flint.common.protocol.methods.ExecuteScriptResult;
import dev.bwdesigngroup.flint.common.protocol.methods.perspective.PerspectiveListSessionsResult;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.TagBrowseResult;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.TagCreateResult;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.TagDeleteResult;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.TagEditResult;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.TagGetConfigResult;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.TagProvidersResult;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.TagReadResult;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.TagWriteResult;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.UdtDefinitionResult;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.UdtListResult;
import dev.bwdesigngroup.flint.gateway.debug.GatewayDebugExecutor;
import dev.bwdesigngroup.flint.gateway.perspective.PerspectiveScriptExecutor;
import dev.bwdesigngroup.flint.gateway.perspective.PerspectiveSessionInspector;
import dev.bwdesigngroup.flint.gateway.script.GatewayScriptExecutor;
import dev.bwdesigngroup.flint.gateway.tags.GatewayTagExecutor;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("FlintGatewayRpcImpl")
@ExtendWith(MockitoExtension.class)
class FlintGatewayRpcImplTest {

    @Mock private GatewayContext context;

    @Mock private GatewayScriptExecutor scriptExecutor;

    @Mock private PerspectiveSessionInspector perspectiveInspector;

    @Mock private PerspectiveScriptExecutor perspectiveScriptExecutor;

    @Mock private GatewayDebugExecutor debugExecutor;

    @Mock private GatewayTagExecutor tagExecutor;

    private FlintGatewayRpcImpl rpc;

    @BeforeEach
    void setUp() {
        rpc =
                new FlintGatewayRpcImpl(
                        context,
                        scriptExecutor,
                        perspectiveInspector,
                        perspectiveScriptExecutor,
                        debugExecutor,
                        tagExecutor);
    }

    @Nested
    @DisplayName("isAvailable")
    class IsAvailable {

        @Test
        @DisplayName("always returns true")
        void alwaysReturnsTrue() {
            assertTrue(rpc.isAvailable());
        }
    }

    @Nested
    @DisplayName("executeScript")
    class ExecuteScript {

        @Test
        @DisplayName("delegates to script executor")
        void delegatesToScriptExecutor() {
            ExecuteScriptResult expected = ExecuteScriptResult.success("output", "", 100);
            when(scriptExecutor.execute("print('hi')", 5000, null, false)).thenReturn(expected);

            ExecuteScriptResult result = rpc.executeScript("print('hi')", 5000, null, false);

            assertEquals(expected, result);
            verify(scriptExecutor).execute("print('hi')", 5000, null, false);
        }

        @Test
        @DisplayName("returns failure on script executor exception")
        void returnsFailureOnException() {
            when(scriptExecutor.execute(anyString(), anyInt(), any(), anyBoolean()))
                    .thenThrow(new RuntimeException("Script engine error"));

            ExecuteScriptResult result = rpc.executeScript("bad code", 5000, null, false);

            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("resetSession")
    class ResetSession {

        @Test
        @DisplayName("delegates to script executor")
        void delegatesToScriptExecutor() {
            rpc.resetSession("session-123");

            verify(scriptExecutor).resetSession("session-123");
        }
    }

    @Nested
    @DisplayName("requestProjectScan")
    class RequestProjectScan {

        @Test
        @DisplayName("returns false when project manager throws")
        void returnsFalseOnException() {
            when(context.getProjectManager()).thenThrow(new RuntimeException("No project manager"));

            boolean result = rpc.requestProjectScan();

            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("perspective methods")
    class PerspectiveMethods {

        @Test
        @DisplayName("isPerspectiveAvailable delegates to inspector")
        void isPerspectiveAvailable() {
            when(perspectiveInspector.isPerspectiveAvailable()).thenReturn(true);

            assertTrue(rpc.isPerspectiveAvailable());
            verify(perspectiveInspector).isPerspectiveAvailable();
        }

        @Test
        @DisplayName("perspectiveListSessions delegates to inspector")
        void listSessions() {
            PerspectiveListSessionsResult expected = new PerspectiveListSessionsResult();
            when(perspectiveInspector.listSessions()).thenReturn(expected);

            PerspectiveListSessionsResult result = rpc.perspectiveListSessions();

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("perspectiveListSessions returns empty result on exception")
        void listSessionsReturnsEmptyOnException() {
            when(perspectiveInspector.listSessions())
                    .thenThrow(new RuntimeException("Not available"));

            PerspectiveListSessionsResult result = rpc.perspectiveListSessions();

            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("debug methods")
    class DebugMethods {

        @Test
        @DisplayName("debugStopSession delegates to executor")
        void debugStopSession() {
            rpc.debugStopSession("session-1");

            verify(debugExecutor).stopSession("session-1");
        }

        @Test
        @DisplayName("debugRun delegates to executor")
        void debugRun() {
            rpc.debugRun("session-1");

            verify(debugExecutor).runSession("session-1");
        }

        @Test
        @DisplayName("debugSendCommand parses continue command")
        void debugSendCommandContinue() {
            rpc.debugSendCommand("session-1", "continue");

            verify(debugExecutor).sendCommand(eq("session-1"), any());
        }

        @Test
        @DisplayName("debugSendCommand parses stepOver command")
        void debugSendCommandStepOver() {
            rpc.debugSendCommand("session-1", "stepover");

            verify(debugExecutor).sendCommand(eq("session-1"), any());
        }

        @Test
        @DisplayName("debugSendCommand handles unknown command gracefully")
        void debugSendCommandUnknown() {
            assertDoesNotThrow(() -> rpc.debugSendCommand("session-1", "invalid_command"));
        }
    }

    @Nested
    @DisplayName("tag methods")
    class TagMethods {

        @Test
        @DisplayName("tagBrowse delegates to tag executor")
        void tagBrowse() {
            TagBrowseResult expected = new TagBrowseResult();
            when(tagExecutor.browse("default", "", null, null)).thenReturn(expected);

            TagBrowseResult result = rpc.tagBrowse("default", "", null, null);

            assertEquals(expected, result);
            verify(tagExecutor).browse("default", "", null, null);
        }

        @Test
        @DisplayName("tagBrowse returns empty result on exception")
        void tagBrowseReturnsEmptyOnException() {
            when(tagExecutor.browse(anyString(), anyString(), any(), any()))
                    .thenThrow(new RuntimeException("Browse error"));

            TagBrowseResult result = rpc.tagBrowse("default", "", null, null);

            assertNotNull(result);
            assertTrue(result.getResults().isEmpty());
        }

        @Test
        @DisplayName("tagRead delegates to tag executor")
        void tagRead() {
            TagReadResult expected = new TagReadResult();
            List<String> paths = Collections.singletonList("[default]Tag1");
            when(tagExecutor.read(paths)).thenReturn(expected);

            TagReadResult result = rpc.tagRead(paths);

            assertEquals(expected, result);
            verify(tagExecutor).read(paths);
        }

        @Test
        @DisplayName("tagRead returns empty result on exception")
        void tagReadReturnsEmptyOnException() {
            when(tagExecutor.read(anyList())).thenThrow(new RuntimeException("Read error"));

            TagReadResult result = rpc.tagRead(Collections.singletonList("[default]Tag1"));

            assertNotNull(result);
            assertTrue(result.getResults().isEmpty());
        }

        @Test
        @DisplayName("tagWrite delegates to tag executor")
        void tagWrite() {
            TagWriteResult expected = new TagWriteResult();
            List<String> paths = Collections.singletonList("[default]Tag1");
            List<String> values = Collections.singletonList("42");
            List<String> dataTypes = Collections.singletonList("int4");
            when(tagExecutor.write(paths, values, dataTypes)).thenReturn(expected);

            TagWriteResult result = rpc.tagWrite(paths, values, dataTypes);

            assertEquals(expected, result);
            verify(tagExecutor).write(paths, values, dataTypes);
        }

        @Test
        @DisplayName("tagWrite returns empty result on exception")
        void tagWriteReturnsEmptyOnException() {
            when(tagExecutor.write(anyList(), anyList(), anyList()))
                    .thenThrow(new RuntimeException("Write error"));

            TagWriteResult result =
                    rpc.tagWrite(
                            Collections.singletonList("[default]Tag1"),
                            Collections.singletonList("42"),
                            Collections.emptyList());

            assertNotNull(result);
            assertTrue(result.getResults().isEmpty());
        }

        @Test
        @DisplayName("tagGetConfig delegates to tag executor")
        void tagGetConfig() {
            TagGetConfigResult expected = new TagGetConfigResult("[default]Tag1", "{\"value\":42}");
            when(tagExecutor.getConfig("[default]Tag1")).thenReturn(expected);

            TagGetConfigResult result = rpc.tagGetConfig("[default]Tag1");

            assertEquals(expected, result);
            verify(tagExecutor).getConfig("[default]Tag1");
        }

        @Test
        @DisplayName("tagGetConfig returns empty config on exception")
        void tagGetConfigReturnsEmptyOnException() {
            when(tagExecutor.getConfig(anyString()))
                    .thenThrow(new RuntimeException("Config error"));

            TagGetConfigResult result = rpc.tagGetConfig("[default]Tag1");

            assertNotNull(result);
            assertEquals("{}", result.getConfig());
        }

        @Test
        @DisplayName("tagCreate delegates to tag executor")
        void tagCreate() {
            TagCreateResult expected = new TagCreateResult();
            when(tagExecutor.create("[default]Folder", "[{\"name\":\"Tag1\"}]"))
                    .thenReturn(expected);

            TagCreateResult result = rpc.tagCreate("[default]Folder", "[{\"name\":\"Tag1\"}]");

            assertEquals(expected, result);
            verify(tagExecutor).create("[default]Folder", "[{\"name\":\"Tag1\"}]");
        }

        @Test
        @DisplayName("tagCreate returns empty result on exception")
        void tagCreateReturnsEmptyOnException() {
            when(tagExecutor.create(anyString(), anyString()))
                    .thenThrow(new RuntimeException("Create error"));

            TagCreateResult result = rpc.tagCreate("[default]Folder", "[{\"name\":\"Tag1\"}]");

            assertNotNull(result);
            assertTrue(result.getResults().isEmpty());
        }

        @Test
        @DisplayName("tagEdit delegates to tag executor")
        void tagEdit() {
            TagEditResult expected = TagEditResult.success();
            when(tagExecutor.edit("[default]Tag1", "{\"tooltip\":\"new\"}")).thenReturn(expected);

            TagEditResult result = rpc.tagEdit("[default]Tag1", "{\"tooltip\":\"new\"}");

            assertEquals(expected, result);
            verify(tagExecutor).edit("[default]Tag1", "{\"tooltip\":\"new\"}");
        }

        @Test
        @DisplayName("tagEdit returns failure on exception")
        void tagEditReturnsFailureOnException() {
            when(tagExecutor.edit(anyString(), anyString()))
                    .thenThrow(new RuntimeException("Edit error"));

            TagEditResult result = rpc.tagEdit("[default]Tag1", "{\"tooltip\":\"new\"}");

            assertFalse(result.isSuccess());
            assertNotNull(result.getError());
        }

        @Test
        @DisplayName("tagDelete delegates to tag executor")
        void tagDelete() {
            TagDeleteResult expected = new TagDeleteResult();
            List<String> paths = Collections.singletonList("[default]Tag1");
            when(tagExecutor.delete(paths)).thenReturn(expected);

            TagDeleteResult result = rpc.tagDelete(paths);

            assertEquals(expected, result);
            verify(tagExecutor).delete(paths);
        }

        @Test
        @DisplayName("tagDelete returns empty result on exception")
        void tagDeleteReturnsEmptyOnException() {
            when(tagExecutor.delete(anyList())).thenThrow(new RuntimeException("Delete error"));

            TagDeleteResult result = rpc.tagDelete(Collections.singletonList("[default]Tag1"));

            assertNotNull(result);
            assertTrue(result.getResults().isEmpty());
        }

        @Test
        @DisplayName("tagGetProviders delegates to tag executor")
        void tagGetProviders() {
            TagProvidersResult expected = new TagProvidersResult();
            when(tagExecutor.getProviders()).thenReturn(expected);

            TagProvidersResult result = rpc.tagGetProviders();

            assertEquals(expected, result);
            verify(tagExecutor).getProviders();
        }

        @Test
        @DisplayName("tagGetProviders returns empty result on exception")
        void tagGetProvidersReturnsEmptyOnException() {
            when(tagExecutor.getProviders()).thenThrow(new RuntimeException("Provider error"));

            TagProvidersResult result = rpc.tagGetProviders();

            assertNotNull(result);
            assertTrue(result.getProviders().isEmpty());
        }
    }

    @Nested
    @DisplayName("UDT methods")
    class UdtMethods {

        @Test
        @DisplayName("udtGetDefinitions delegates to tag executor")
        void udtGetDefinitions() {
            UdtListResult expected = new UdtListResult();
            when(tagExecutor.getDefinitions("default")).thenReturn(expected);

            UdtListResult result = rpc.udtGetDefinitions("default");

            assertEquals(expected, result);
            verify(tagExecutor).getDefinitions("default");
        }

        @Test
        @DisplayName("udtGetDefinitions returns empty result on exception")
        void udtGetDefinitionsReturnsEmptyOnException() {
            when(tagExecutor.getDefinitions(anyString())).thenThrow(new RuntimeException("Error"));

            UdtListResult result = rpc.udtGetDefinitions("default");

            assertNotNull(result);
            assertTrue(result.getDefinitions().isEmpty());
        }

        @Test
        @DisplayName("udtGetDefinition delegates to tag executor")
        void udtGetDefinition() {
            UdtDefinitionResult expected = new UdtDefinitionResult("Motor", "Motor", null);
            when(tagExecutor.getDefinition("default", "Motor")).thenReturn(expected);

            UdtDefinitionResult result = rpc.udtGetDefinition("default", "Motor");

            assertEquals(expected, result);
            verify(tagExecutor).getDefinition("default", "Motor");
        }

        @Test
        @DisplayName("udtGetDefinition returns empty result on exception")
        void udtGetDefinitionReturnsEmptyOnException() {
            when(tagExecutor.getDefinition(anyString(), anyString()))
                    .thenThrow(new RuntimeException("Error"));

            UdtDefinitionResult result = rpc.udtGetDefinition("default", "Motor");

            assertNotNull(result);
            assertTrue(result.getMembers().isEmpty());
        }

        @Test
        @DisplayName("udtCreateDefinition delegates to tag executor")
        void udtCreateDefinition() {
            TagCreateResult expected = new TagCreateResult();
            when(tagExecutor.createDefinition("default", "Motor", "", null)).thenReturn(expected);

            TagCreateResult result = rpc.udtCreateDefinition("default", "Motor", "", null);

            assertEquals(expected, result);
            verify(tagExecutor).createDefinition("default", "Motor", "", null);
        }

        @Test
        @DisplayName("udtCreateDefinition returns empty result on exception")
        void udtCreateDefinitionReturnsEmptyOnException() {
            when(tagExecutor.createDefinition(anyString(), anyString(), anyString(), any()))
                    .thenThrow(new RuntimeException("Error"));

            TagCreateResult result = rpc.udtCreateDefinition("default", "Motor", "", null);

            assertNotNull(result);
            assertTrue(result.getResults().isEmpty());
        }

        @Test
        @DisplayName("udtCreateInstance delegates to tag executor")
        void udtCreateInstance() {
            TagCreateResult expected = new TagCreateResult();
            when(tagExecutor.createInstance("[default]Line1", "Motor1", "Motor", null))
                    .thenReturn(expected);

            TagCreateResult result =
                    rpc.udtCreateInstance("[default]Line1", "Motor1", "Motor", null);

            assertEquals(expected, result);
            verify(tagExecutor).createInstance("[default]Line1", "Motor1", "Motor", null);
        }

        @Test
        @DisplayName("udtCreateInstance returns empty result on exception")
        void udtCreateInstanceReturnsEmptyOnException() {
            when(tagExecutor.createInstance(anyString(), anyString(), anyString(), any()))
                    .thenThrow(new RuntimeException("Error"));

            TagCreateResult result =
                    rpc.udtCreateInstance("[default]Line1", "Motor1", "Motor", null);

            assertNotNull(result);
            assertTrue(result.getResults().isEmpty());
        }
    }
}

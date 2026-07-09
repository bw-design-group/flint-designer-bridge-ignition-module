package dev.bwdesigngroup.flint.designer.handlers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.bwdesigngroup.flint.common.FlintConstants;
import dev.bwdesigngroup.flint.common.protocol.JsonRpcRequest;
import dev.bwdesigngroup.flint.common.protocol.JsonRpcResponse;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.TagBrowseResult;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.TagCreateResult;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.TagDeleteResult;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.TagEditResult;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.TagGetConfigResult;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.TagProvidersResult;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.TagReadResult;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.TagWriteResult;
import dev.bwdesigngroup.flint.common.rpc.FlintGatewayRpc;
import dev.bwdesigngroup.flint.designer.rpc.GatewayRpcClient;
import dev.bwdesigngroup.flint.designer.server.FlintWebSocketHandler;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("TagHandler")
@ExtendWith(MockitoExtension.class)
class TagHandlerTest {

    @Mock private FlintWebSocketHandler wsHandler;

    @Mock private GatewayRpcClient gatewayRpcClient;

    @Mock private FlintGatewayRpc gatewayRpc;

    private TagHandler tagHandler;
    private Gson gson;

    @BeforeEach
    void setUp() {
        lenient().when(gatewayRpcClient.getRpc()).thenReturn(gatewayRpc);
        lenient().when(wsHandler.getGson()).thenReturn(new GsonBuilder().create());
        tagHandler = new TagHandler(wsHandler, gatewayRpcClient);
        gson = new GsonBuilder().create();
    }

    // ==================== Method Routing ====================

    @Nested
    @DisplayName("method routing")
    class MethodRouting {

        @Test
        @DisplayName("routes tags.browse correctly")
        void routesBrowse() {
            when(gatewayRpc.tagBrowse(anyString(), anyString(), any(), any()))
                    .thenReturn(new TagBrowseResult());

            JsonRpcRequest request =
                    new JsonRpcRequest(FlintConstants.METHOD_TAGS_BROWSE, new JsonObject(), 1);
            JsonRpcResponse response = tagHandler.handle(request);

            assertTrue(response.isSuccess());
            verify(gatewayRpc).tagBrowse(anyString(), anyString(), any(), any());
        }

        @Test
        @DisplayName("routes tags.read correctly")
        void routesRead() {
            when(gatewayRpc.tagRead(anyList())).thenReturn(new TagReadResult());

            JsonObject params = new JsonObject();
            JsonArray paths = new JsonArray();
            paths.add("[default]Tag1");
            params.add("tagPaths", paths);

            JsonRpcRequest request = new JsonRpcRequest(FlintConstants.METHOD_TAGS_READ, params, 2);
            JsonRpcResponse response = tagHandler.handle(request);

            assertTrue(response.isSuccess());
        }

        @Test
        @DisplayName("routes tags.write correctly")
        void routesWrite() {
            when(gatewayRpc.tagWrite(anyList(), anyList(), anyList()))
                    .thenReturn(new TagWriteResult());

            JsonObject params = new JsonObject();
            JsonArray writes = new JsonArray();
            JsonObject w = new JsonObject();
            w.addProperty("path", "[default]Tag1");
            w.addProperty("value", "42");
            writes.add(w);
            params.add("writes", writes);

            JsonRpcRequest request =
                    new JsonRpcRequest(FlintConstants.METHOD_TAGS_WRITE, params, 3);
            JsonRpcResponse response = tagHandler.handle(request);

            assertTrue(response.isSuccess());
        }

        @Test
        @DisplayName("routes tags.getConfig correctly")
        void routesGetConfig() {
            when(gatewayRpc.tagGetConfig(anyString()))
                    .thenReturn(new TagGetConfigResult("[default]Tag1", "{}"));

            JsonObject params = new JsonObject();
            params.addProperty("tagPath", "[default]Tag1");

            JsonRpcRequest request =
                    new JsonRpcRequest(FlintConstants.METHOD_TAGS_GET_CONFIG, params, 4);
            JsonRpcResponse response = tagHandler.handle(request);

            assertTrue(response.isSuccess());
        }

        @Test
        @DisplayName("routes tags.create correctly")
        void routesCreate() {
            when(gatewayRpc.tagCreate(anyString(), anyString())).thenReturn(new TagCreateResult());

            JsonObject params = new JsonObject();
            params.addProperty("parentPath", "[default]Folder");
            JsonArray tags = new JsonArray();
            JsonObject tag = new JsonObject();
            tag.addProperty("name", "NewTag");
            tags.add(tag);
            params.add("tags", tags);

            JsonRpcRequest request =
                    new JsonRpcRequest(FlintConstants.METHOD_TAGS_CREATE, params, 5);
            JsonRpcResponse response = tagHandler.handle(request);

            assertTrue(response.isSuccess());
        }

        @Test
        @DisplayName("routes tags.edit correctly")
        void routesEdit() {
            when(gatewayRpc.tagEdit(anyString(), anyString())).thenReturn(TagEditResult.success());

            JsonObject params = new JsonObject();
            params.addProperty("tagPath", "[default]Tag1");
            JsonObject config = new JsonObject();
            config.addProperty("tooltip", "updated");
            params.add("config", config);

            JsonRpcRequest request = new JsonRpcRequest(FlintConstants.METHOD_TAGS_EDIT, params, 6);
            JsonRpcResponse response = tagHandler.handle(request);

            assertTrue(response.isSuccess());
        }

        @Test
        @DisplayName("routes tags.delete correctly")
        void routesDelete() {
            when(gatewayRpc.tagDelete(anyList())).thenReturn(new TagDeleteResult());

            JsonObject params = new JsonObject();
            JsonArray paths = new JsonArray();
            paths.add("[default]Tag1");
            params.add("tagPaths", paths);

            JsonRpcRequest request =
                    new JsonRpcRequest(FlintConstants.METHOD_TAGS_DELETE, params, 7);
            JsonRpcResponse response = tagHandler.handle(request);

            assertTrue(response.isSuccess());
        }

        @Test
        @DisplayName("routes tags.getProviders correctly")
        void routesGetProviders() {
            when(gatewayRpc.tagGetProviders()).thenReturn(new TagProvidersResult());

            JsonRpcRequest request =
                    new JsonRpcRequest(
                            FlintConstants.METHOD_TAGS_GET_PROVIDERS, new JsonObject(), 8);
            JsonRpcResponse response = tagHandler.handle(request);

            assertTrue(response.isSuccess());
        }

        @Test
        @DisplayName("returns error for unknown tag method")
        void returnsErrorForUnknownMethod() {
            JsonRpcRequest request = new JsonRpcRequest("tags.unknown", new JsonObject(), 99);
            JsonRpcResponse response = tagHandler.handle(request);

            assertFalse(response.isSuccess());
            String json = gson.toJson(response);
            assertTrue(json.contains("Unknown tag method"));
        }
    }

    // ==================== handleBrowse ====================

    @Nested
    @DisplayName("handleBrowse")
    class HandleBrowse {

        @Test
        @DisplayName("defaults provider to 'default' when not specified")
        void defaultsProvider() {
            when(gatewayRpc.tagBrowse(eq("default"), eq(""), isNull(), isNull()))
                    .thenReturn(new TagBrowseResult());

            JsonRpcRequest request =
                    new JsonRpcRequest(FlintConstants.METHOD_TAGS_BROWSE, new JsonObject(), 1);
            tagHandler.handle(request);

            verify(gatewayRpc).tagBrowse("default", "", null, null);
        }

        @Test
        @DisplayName("passes all params to RPC")
        void passesAllParams() {
            when(gatewayRpc.tagBrowse(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(new TagBrowseResult());

            JsonObject params = new JsonObject();
            params.addProperty("provider", "custom");
            params.addProperty("parentPath", "Folder/SubFolder");
            params.addProperty("typeFilter", "AtomicTag");
            params.addProperty("nameFilter", "Temp*");

            JsonRpcRequest request =
                    new JsonRpcRequest(FlintConstants.METHOD_TAGS_BROWSE, params, 1);
            tagHandler.handle(request);

            verify(gatewayRpc).tagBrowse("custom", "Folder/SubFolder", "AtomicTag", "Temp*");
        }

        @Test
        @DisplayName("handles null params gracefully")
        void handlesNullParams() {
            when(gatewayRpc.tagBrowse(anyString(), anyString(), any(), any()))
                    .thenReturn(new TagBrowseResult());

            JsonRpcRequest request = new JsonRpcRequest(FlintConstants.METHOD_TAGS_BROWSE, null, 1);
            JsonRpcResponse response = tagHandler.handle(request);

            assertTrue(response.isSuccess());
        }
    }

    // ==================== handleRead ====================

    @Nested
    @DisplayName("handleRead")
    class HandleRead {

        @Test
        @DisplayName("returns error when tagPaths is missing")
        void returnsErrorWhenMissing() {
            JsonRpcRequest request =
                    new JsonRpcRequest(FlintConstants.METHOD_TAGS_READ, new JsonObject(), 1);
            JsonRpcResponse response = tagHandler.handle(request);

            assertFalse(response.isSuccess());
            String json = gson.toJson(response);
            assertTrue(json.contains("tagPaths"));
        }

        @Test
        @DisplayName("returns error when tagPaths is empty")
        void returnsErrorWhenEmpty() {
            JsonObject params = new JsonObject();
            params.add("tagPaths", new JsonArray());

            JsonRpcRequest request = new JsonRpcRequest(FlintConstants.METHOD_TAGS_READ, params, 1);
            JsonRpcResponse response = tagHandler.handle(request);

            assertFalse(response.isSuccess());
        }

        @Test
        @DisplayName("passes tagPaths to RPC")
        void passesTagPaths() {
            when(gatewayRpc.tagRead(anyList())).thenReturn(new TagReadResult());

            JsonObject params = new JsonObject();
            JsonArray paths = new JsonArray();
            paths.add("[default]Tag1");
            paths.add("[default]Tag2");
            params.add("tagPaths", paths);

            JsonRpcRequest request = new JsonRpcRequest(FlintConstants.METHOD_TAGS_READ, params, 1);
            tagHandler.handle(request);

            verify(gatewayRpc).tagRead(Arrays.asList("[default]Tag1", "[default]Tag2"));
        }
    }

    // ==================== handleWrite ====================

    @Nested
    @DisplayName("handleWrite")
    class HandleWrite {

        @Test
        @DisplayName("returns error when writes is missing")
        void returnsErrorWhenMissing() {
            JsonRpcRequest request =
                    new JsonRpcRequest(FlintConstants.METHOD_TAGS_WRITE, new JsonObject(), 1);
            JsonRpcResponse response = tagHandler.handle(request);

            assertFalse(response.isSuccess());
            String json = gson.toJson(response);
            assertTrue(json.contains("writes"));
        }

        @Test
        @DisplayName("extracts paths, values, and dataTypes from writes array")
        void extractsWriteComponents() {
            when(gatewayRpc.tagWrite(anyList(), anyList(), anyList()))
                    .thenReturn(new TagWriteResult());

            JsonObject params = new JsonObject();
            JsonArray writes = new JsonArray();

            JsonObject w1 = new JsonObject();
            w1.addProperty("path", "[default]Tag1");
            w1.addProperty("value", "42");
            w1.addProperty("dataType", "int4");
            writes.add(w1);

            JsonObject w2 = new JsonObject();
            w2.addProperty("path", "[default]Tag2");
            w2.addProperty("value", "true");
            writes.add(w2);

            params.add("writes", writes);

            JsonRpcRequest request =
                    new JsonRpcRequest(FlintConstants.METHOD_TAGS_WRITE, params, 1);
            tagHandler.handle(request);

            verify(gatewayRpc)
                    .tagWrite(
                            Arrays.asList("[default]Tag1", "[default]Tag2"),
                            Arrays.asList("42", "true"),
                            Arrays.asList("int4", ""));
        }

        @Test
        @DisplayName("handles missing value and dataType in write entry")
        void handlesMissingFields() {
            when(gatewayRpc.tagWrite(anyList(), anyList(), anyList()))
                    .thenReturn(new TagWriteResult());

            JsonObject params = new JsonObject();
            JsonArray writes = new JsonArray();
            JsonObject w = new JsonObject();
            w.addProperty("path", "[default]Tag1");
            writes.add(w);
            params.add("writes", writes);

            JsonRpcRequest request =
                    new JsonRpcRequest(FlintConstants.METHOD_TAGS_WRITE, params, 1);
            tagHandler.handle(request);

            verify(gatewayRpc)
                    .tagWrite(
                            Collections.singletonList("[default]Tag1"),
                            Collections.singletonList(""),
                            Collections.singletonList(""));
        }
    }

    // ==================== handleGetConfig ====================

    @Nested
    @DisplayName("handleGetConfig")
    class HandleGetConfig {

        @Test
        @DisplayName("returns error when tagPath is missing")
        void returnsErrorWhenMissing() {
            JsonRpcRequest request =
                    new JsonRpcRequest(FlintConstants.METHOD_TAGS_GET_CONFIG, new JsonObject(), 1);
            JsonRpcResponse response = tagHandler.handle(request);

            assertFalse(response.isSuccess());
            String json = gson.toJson(response);
            assertTrue(json.contains("tagPath"));
        }

        @Test
        @DisplayName("passes tagPath to RPC and parses config JSON")
        void passesTagPath() {
            String configJson = "{\"dataType\":\"Int4\",\"value\":42}";
            when(gatewayRpc.tagGetConfig("[default]Tag1"))
                    .thenReturn(new TagGetConfigResult("[default]Tag1", configJson));

            JsonObject params = new JsonObject();
            params.addProperty("tagPath", "[default]Tag1");

            JsonRpcRequest request =
                    new JsonRpcRequest(FlintConstants.METHOD_TAGS_GET_CONFIG, params, 1);
            JsonRpcResponse response = tagHandler.handle(request);

            assertTrue(response.isSuccess());
            verify(gatewayRpc).tagGetConfig("[default]Tag1");
        }
    }

    // ==================== handleCreate ====================

    @Nested
    @DisplayName("handleCreate")
    class HandleCreate {

        @Test
        @DisplayName("returns error when parentPath is missing")
        void returnsErrorWhenParentMissing() {
            JsonObject params = new JsonObject();
            params.add("tags", new JsonArray());

            JsonRpcRequest request =
                    new JsonRpcRequest(FlintConstants.METHOD_TAGS_CREATE, params, 1);
            JsonRpcResponse response = tagHandler.handle(request);

            assertFalse(response.isSuccess());
            String json = gson.toJson(response);
            assertTrue(json.contains("parentPath"));
        }

        @Test
        @DisplayName("returns error when tags array is missing")
        void returnsErrorWhenTagsMissing() {
            JsonObject params = new JsonObject();
            params.addProperty("parentPath", "[default]Folder");

            JsonRpcRequest request =
                    new JsonRpcRequest(FlintConstants.METHOD_TAGS_CREATE, params, 1);
            JsonRpcResponse response = tagHandler.handle(request);

            assertFalse(response.isSuccess());
            String json = gson.toJson(response);
            assertTrue(json.contains("tags"));
        }

        @Test
        @DisplayName("serializes tags array and passes to RPC")
        void serializesAndPasses() {
            when(gatewayRpc.tagCreate(anyString(), anyString())).thenReturn(new TagCreateResult());

            JsonObject params = new JsonObject();
            params.addProperty("parentPath", "[default]Folder");
            JsonArray tags = new JsonArray();
            JsonObject tag = new JsonObject();
            tag.addProperty("name", "NewTag");
            tag.addProperty("tagType", "AtomicTag");
            tags.add(tag);
            params.add("tags", tags);

            JsonRpcRequest request =
                    new JsonRpcRequest(FlintConstants.METHOD_TAGS_CREATE, params, 1);
            tagHandler.handle(request);

            verify(gatewayRpc).tagCreate(eq("[default]Folder"), anyString());
        }
    }

    // ==================== handleEdit ====================

    @Nested
    @DisplayName("handleEdit")
    class HandleEdit {

        @Test
        @DisplayName("returns error when tagPath is missing")
        void returnsErrorWhenTagPathMissing() {
            JsonObject params = new JsonObject();
            JsonObject config = new JsonObject();
            config.addProperty("tooltip", "updated");
            params.add("config", config);

            JsonRpcRequest request = new JsonRpcRequest(FlintConstants.METHOD_TAGS_EDIT, params, 1);
            JsonRpcResponse response = tagHandler.handle(request);

            assertFalse(response.isSuccess());
            String json = gson.toJson(response);
            assertTrue(json.contains("tagPath"));
        }

        @Test
        @DisplayName("returns error when config is missing")
        void returnsErrorWhenConfigMissing() {
            JsonObject params = new JsonObject();
            params.addProperty("tagPath", "[default]Tag1");

            JsonRpcRequest request = new JsonRpcRequest(FlintConstants.METHOD_TAGS_EDIT, params, 1);
            JsonRpcResponse response = tagHandler.handle(request);

            assertFalse(response.isSuccess());
            String json = gson.toJson(response);
            assertTrue(json.contains("config"));
        }

        @Test
        @DisplayName("returns error when config is not an object")
        void returnsErrorWhenConfigNotObject() {
            JsonObject params = new JsonObject();
            params.addProperty("tagPath", "[default]Tag1");
            params.addProperty("config", "not an object");

            JsonRpcRequest request = new JsonRpcRequest(FlintConstants.METHOD_TAGS_EDIT, params, 1);
            JsonRpcResponse response = tagHandler.handle(request);

            assertFalse(response.isSuccess());
        }

        @Test
        @DisplayName("serializes config and passes to RPC")
        void serializesAndPasses() {
            when(gatewayRpc.tagEdit(anyString(), anyString())).thenReturn(TagEditResult.success());

            JsonObject params = new JsonObject();
            params.addProperty("tagPath", "[default]Tag1");
            JsonObject config = new JsonObject();
            config.addProperty("tooltip", "new tooltip");
            params.add("config", config);

            JsonRpcRequest request = new JsonRpcRequest(FlintConstants.METHOD_TAGS_EDIT, params, 1);
            tagHandler.handle(request);

            verify(gatewayRpc).tagEdit(eq("[default]Tag1"), anyString());
        }
    }

    // ==================== handleDelete ====================

    @Nested
    @DisplayName("handleDelete")
    class HandleDelete {

        @Test
        @DisplayName("returns error when tagPaths is missing")
        void returnsErrorWhenMissing() {
            JsonRpcRequest request =
                    new JsonRpcRequest(FlintConstants.METHOD_TAGS_DELETE, new JsonObject(), 1);
            JsonRpcResponse response = tagHandler.handle(request);

            assertFalse(response.isSuccess());
            String json = gson.toJson(response);
            assertTrue(json.contains("tagPaths"));
        }

        @Test
        @DisplayName("passes tagPaths to RPC")
        void passesTagPaths() {
            when(gatewayRpc.tagDelete(anyList())).thenReturn(new TagDeleteResult());

            JsonObject params = new JsonObject();
            JsonArray paths = new JsonArray();
            paths.add("[default]Tag1");
            paths.add("[default]Tag2");
            params.add("tagPaths", paths);

            JsonRpcRequest request =
                    new JsonRpcRequest(FlintConstants.METHOD_TAGS_DELETE, params, 1);
            tagHandler.handle(request);

            verify(gatewayRpc).tagDelete(Arrays.asList("[default]Tag1", "[default]Tag2"));
        }
    }

    // ==================== handleGetProviders ====================

    @Nested
    @DisplayName("handleGetProviders")
    class HandleGetProviders {

        @Test
        @DisplayName("calls RPC with no params")
        void callsRpcWithNoParams() {
            when(gatewayRpc.tagGetProviders()).thenReturn(new TagProvidersResult());

            JsonRpcRequest request =
                    new JsonRpcRequest(FlintConstants.METHOD_TAGS_GET_PROVIDERS, null, 1);
            JsonRpcResponse response = tagHandler.handle(request);

            assertTrue(response.isSuccess());
            verify(gatewayRpc).tagGetProviders();
        }
    }

    // ==================== Error handling ====================

    @Nested
    @DisplayName("error handling")
    class ErrorHandling {

        @Test
        @DisplayName("catches RPC exceptions and returns error response")
        void catchesRpcExceptions() {
            when(gatewayRpc.tagGetProviders()).thenThrow(new RuntimeException("RPC failed"));

            JsonRpcRequest request =
                    new JsonRpcRequest(FlintConstants.METHOD_TAGS_GET_PROVIDERS, null, 1);
            JsonRpcResponse response = tagHandler.handle(request);

            assertFalse(response.isSuccess());
            String json = gson.toJson(response);
            assertTrue(json.contains("Tag operation failed"));
        }

        @Test
        @DisplayName("response ID matches request ID")
        void responseIdMatches() {
            when(gatewayRpc.tagGetProviders()).thenReturn(new TagProvidersResult());

            JsonRpcRequest request =
                    new JsonRpcRequest(FlintConstants.METHOD_TAGS_GET_PROVIDERS, null, 42);
            JsonRpcResponse response = tagHandler.handle(request);

            assertEquals(42, ((Number) response.getId()).intValue());
        }
    }
}

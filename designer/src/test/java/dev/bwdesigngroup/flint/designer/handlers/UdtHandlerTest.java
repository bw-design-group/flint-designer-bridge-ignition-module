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
import dev.bwdesigngroup.flint.common.protocol.methods.tags.TagCreateResult;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.UdtDefinitionResult;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.UdtListResult;
import dev.bwdesigngroup.flint.common.rpc.FlintGatewayRpc;
import dev.bwdesigngroup.flint.designer.rpc.GatewayRpcClient;
import dev.bwdesigngroup.flint.designer.server.FlintWebSocketHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("UdtHandler")
@ExtendWith(MockitoExtension.class)
class UdtHandlerTest {

    @Mock private FlintWebSocketHandler wsHandler;

    @Mock private GatewayRpcClient gatewayRpcClient;

    @Mock private FlintGatewayRpc gatewayRpc;

    private UdtHandler udtHandler;
    private Gson gson;

    @BeforeEach
    void setUp() {
        lenient().when(gatewayRpcClient.getRpc()).thenReturn(gatewayRpc);
        lenient().when(wsHandler.getGson()).thenReturn(new GsonBuilder().create());
        udtHandler = new UdtHandler(wsHandler, gatewayRpcClient);
        gson = new GsonBuilder().create();
    }

    // ==================== Method Routing ====================

    @Nested
    @DisplayName("method routing")
    class MethodRouting {

        @Test
        @DisplayName("routes udt.getDefinitions correctly")
        void routesGetDefinitions() {
            when(gatewayRpc.udtGetDefinitions(anyString())).thenReturn(new UdtListResult());

            JsonRpcRequest request =
                    new JsonRpcRequest(
                            FlintConstants.METHOD_UDT_GET_DEFINITIONS, new JsonObject(), 1);
            JsonRpcResponse response = udtHandler.handle(request);

            assertTrue(response.isSuccess());
            verify(gatewayRpc).udtGetDefinitions(anyString());
        }

        @Test
        @DisplayName("routes udt.getDefinition correctly")
        void routesGetDefinition() {
            when(gatewayRpc.udtGetDefinition(anyString(), anyString()))
                    .thenReturn(new UdtDefinitionResult());

            JsonObject params = new JsonObject();
            params.addProperty("typePath", "Motor");

            JsonRpcRequest request =
                    new JsonRpcRequest(FlintConstants.METHOD_UDT_GET_DEFINITION, params, 2);
            JsonRpcResponse response = udtHandler.handle(request);

            assertTrue(response.isSuccess());
        }

        @Test
        @DisplayName("routes udt.createDefinition correctly")
        void routesCreateDefinition() {
            when(gatewayRpc.udtCreateDefinition(anyString(), anyString(), anyString(), any()))
                    .thenReturn(new TagCreateResult());

            JsonObject params = new JsonObject();
            params.addProperty("name", "Motor");

            JsonRpcRequest request =
                    new JsonRpcRequest(FlintConstants.METHOD_UDT_CREATE_DEFINITION, params, 3);
            JsonRpcResponse response = udtHandler.handle(request);

            assertTrue(response.isSuccess());
        }

        @Test
        @DisplayName("routes udt.createInstance correctly")
        void routesCreateInstance() {
            when(gatewayRpc.udtCreateInstance(anyString(), anyString(), anyString(), any()))
                    .thenReturn(new TagCreateResult());

            JsonObject params = new JsonObject();
            params.addProperty("parentPath", "[default]Line1");
            params.addProperty("name", "Motor1");
            params.addProperty("typeId", "Motor");

            JsonRpcRequest request =
                    new JsonRpcRequest(FlintConstants.METHOD_UDT_CREATE_INSTANCE, params, 4);
            JsonRpcResponse response = udtHandler.handle(request);

            assertTrue(response.isSuccess());
        }

        @Test
        @DisplayName("returns error for unknown UDT method")
        void returnsErrorForUnknownMethod() {
            JsonRpcRequest request = new JsonRpcRequest("udt.unknown", new JsonObject(), 99);
            JsonRpcResponse response = udtHandler.handle(request);

            assertFalse(response.isSuccess());
            String json = gson.toJson(response);
            assertTrue(json.contains("Unknown UDT method"));
        }
    }

    // ==================== handleGetDefinitions ====================

    @Nested
    @DisplayName("handleGetDefinitions")
    class HandleGetDefinitions {

        @Test
        @DisplayName("defaults provider to 'default' when not specified")
        void defaultsProvider() {
            when(gatewayRpc.udtGetDefinitions(eq("default"))).thenReturn(new UdtListResult());

            JsonRpcRequest request =
                    new JsonRpcRequest(
                            FlintConstants.METHOD_UDT_GET_DEFINITIONS, new JsonObject(), 1);
            udtHandler.handle(request);

            verify(gatewayRpc).udtGetDefinitions("default");
        }

        @Test
        @DisplayName("passes custom provider to RPC")
        void passesCustomProvider() {
            when(gatewayRpc.udtGetDefinitions(anyString())).thenReturn(new UdtListResult());

            JsonObject params = new JsonObject();
            params.addProperty("provider", "custom");

            JsonRpcRequest request =
                    new JsonRpcRequest(FlintConstants.METHOD_UDT_GET_DEFINITIONS, params, 1);
            udtHandler.handle(request);

            verify(gatewayRpc).udtGetDefinitions("custom");
        }
    }

    // ==================== handleGetDefinition ====================

    @Nested
    @DisplayName("handleGetDefinition")
    class HandleGetDefinition {

        @Test
        @DisplayName("returns error when typePath is missing")
        void returnsErrorWhenMissing() {
            JsonRpcRequest request =
                    new JsonRpcRequest(
                            FlintConstants.METHOD_UDT_GET_DEFINITION, new JsonObject(), 1);
            JsonRpcResponse response = udtHandler.handle(request);

            assertFalse(response.isSuccess());
            String json = gson.toJson(response);
            assertTrue(json.contains("typePath"));
        }

        @Test
        @DisplayName("passes typePath and provider to RPC")
        void passesParams() {
            when(gatewayRpc.udtGetDefinition(anyString(), anyString()))
                    .thenReturn(new UdtDefinitionResult());

            JsonObject params = new JsonObject();
            params.addProperty("provider", "custom");
            params.addProperty("typePath", "Equipment/Motor");

            JsonRpcRequest request =
                    new JsonRpcRequest(FlintConstants.METHOD_UDT_GET_DEFINITION, params, 1);
            udtHandler.handle(request);

            verify(gatewayRpc).udtGetDefinition("custom", "Equipment/Motor");
        }
    }

    // ==================== handleCreateDefinition ====================

    @Nested
    @DisplayName("handleCreateDefinition")
    class HandleCreateDefinition {

        @Test
        @DisplayName("returns error when name is missing")
        void returnsErrorWhenNameMissing() {
            JsonRpcRequest request =
                    new JsonRpcRequest(
                            FlintConstants.METHOD_UDT_CREATE_DEFINITION, new JsonObject(), 1);
            JsonRpcResponse response = udtHandler.handle(request);

            assertFalse(response.isSuccess());
            String json = gson.toJson(response);
            assertTrue(json.contains("name"));
        }

        @Test
        @DisplayName("passes name and members to RPC")
        void passesNameAndMembers() {
            when(gatewayRpc.udtCreateDefinition(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(new TagCreateResult());

            JsonObject params = new JsonObject();
            params.addProperty("name", "Motor");
            JsonArray members = new JsonArray();
            JsonObject member = new JsonObject();
            member.addProperty("name", "Speed");
            member.addProperty("tagType", "AtomicTag");
            member.addProperty("dataType", "Float4");
            members.add(member);
            params.add("members", members);

            JsonRpcRequest request =
                    new JsonRpcRequest(FlintConstants.METHOD_UDT_CREATE_DEFINITION, params, 1);
            udtHandler.handle(request);

            verify(gatewayRpc).udtCreateDefinition(eq("default"), eq("Motor"), eq(""), anyString());
        }

        @Test
        @DisplayName("passes null membersJson when members not provided")
        void handlesNoMembers() {
            when(gatewayRpc.udtCreateDefinition(anyString(), anyString(), anyString(), isNull()))
                    .thenReturn(new TagCreateResult());

            JsonObject params = new JsonObject();
            params.addProperty("name", "EmptyType");

            JsonRpcRequest request =
                    new JsonRpcRequest(FlintConstants.METHOD_UDT_CREATE_DEFINITION, params, 1);
            udtHandler.handle(request);

            verify(gatewayRpc).udtCreateDefinition("default", "EmptyType", "", null);
        }

        @Test
        @DisplayName("passes parentTypePath for nested UDTs")
        void passesParentTypePath() {
            when(gatewayRpc.udtCreateDefinition(anyString(), anyString(), anyString(), any()))
                    .thenReturn(new TagCreateResult());

            JsonObject params = new JsonObject();
            params.addProperty("name", "Motor");
            params.addProperty("parentTypePath", "Equipment");

            JsonRpcRequest request =
                    new JsonRpcRequest(FlintConstants.METHOD_UDT_CREATE_DEFINITION, params, 1);
            udtHandler.handle(request);

            verify(gatewayRpc).udtCreateDefinition("default", "Motor", "Equipment", null);
        }
    }

    // ==================== handleCreateInstance ====================

    @Nested
    @DisplayName("handleCreateInstance")
    class HandleCreateInstance {

        @Test
        @DisplayName("returns error when parentPath is missing")
        void returnsErrorWhenParentPathMissing() {
            JsonObject params = new JsonObject();
            params.addProperty("name", "Motor1");
            params.addProperty("typeId", "Motor");

            JsonRpcRequest request =
                    new JsonRpcRequest(FlintConstants.METHOD_UDT_CREATE_INSTANCE, params, 1);
            JsonRpcResponse response = udtHandler.handle(request);

            assertFalse(response.isSuccess());
            String json = gson.toJson(response);
            assertTrue(json.contains("parentPath"));
        }

        @Test
        @DisplayName("returns error when name is missing")
        void returnsErrorWhenNameMissing() {
            JsonObject params = new JsonObject();
            params.addProperty("parentPath", "[default]Line1");
            params.addProperty("typeId", "Motor");

            JsonRpcRequest request =
                    new JsonRpcRequest(FlintConstants.METHOD_UDT_CREATE_INSTANCE, params, 1);
            JsonRpcResponse response = udtHandler.handle(request);

            assertFalse(response.isSuccess());
            String json = gson.toJson(response);
            assertTrue(json.contains("name"));
        }

        @Test
        @DisplayName("returns error when typeId is missing")
        void returnsErrorWhenTypeIdMissing() {
            JsonObject params = new JsonObject();
            params.addProperty("parentPath", "[default]Line1");
            params.addProperty("name", "Motor1");

            JsonRpcRequest request =
                    new JsonRpcRequest(FlintConstants.METHOD_UDT_CREATE_INSTANCE, params, 1);
            JsonRpcResponse response = udtHandler.handle(request);

            assertFalse(response.isSuccess());
            String json = gson.toJson(response);
            assertTrue(json.contains("typeId"));
        }

        @Test
        @DisplayName("passes all params to RPC")
        void passesAllParams() {
            when(gatewayRpc.udtCreateInstance(anyString(), anyString(), anyString(), any()))
                    .thenReturn(new TagCreateResult());

            JsonObject params = new JsonObject();
            params.addProperty("parentPath", "[default]Line1");
            params.addProperty("name", "Motor1");
            params.addProperty("typeId", "Motor");

            JsonRpcRequest request =
                    new JsonRpcRequest(FlintConstants.METHOD_UDT_CREATE_INSTANCE, params, 1);
            udtHandler.handle(request);

            verify(gatewayRpc).udtCreateInstance("[default]Line1", "Motor1", "Motor", null);
        }

        @Test
        @DisplayName("passes overrides as JSON when provided")
        void passesOverrides() {
            when(gatewayRpc.udtCreateInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(new TagCreateResult());

            JsonObject params = new JsonObject();
            params.addProperty("parentPath", "[default]Line1");
            params.addProperty("name", "Motor1");
            params.addProperty("typeId", "Motor");
            JsonObject overrides = new JsonObject();
            overrides.addProperty("tooltip", "Motor 1");
            params.add("overrides", overrides);

            JsonRpcRequest request =
                    new JsonRpcRequest(FlintConstants.METHOD_UDT_CREATE_INSTANCE, params, 1);
            udtHandler.handle(request);

            verify(gatewayRpc)
                    .udtCreateInstance(
                            eq("[default]Line1"), eq("Motor1"), eq("Motor"), anyString());
        }
    }

    // ==================== Error handling ====================

    @Nested
    @DisplayName("error handling")
    class ErrorHandling {

        @Test
        @DisplayName("catches RPC exceptions and returns error response")
        void catchesRpcExceptions() {
            when(gatewayRpc.udtGetDefinitions(anyString()))
                    .thenThrow(new RuntimeException("RPC failed"));

            JsonRpcRequest request =
                    new JsonRpcRequest(FlintConstants.METHOD_UDT_GET_DEFINITIONS, null, 1);
            JsonRpcResponse response = udtHandler.handle(request);

            assertFalse(response.isSuccess());
            String json = gson.toJson(response);
            assertTrue(json.contains("UDT operation failed"));
        }

        @Test
        @DisplayName("response ID matches request ID")
        void responseIdMatches() {
            when(gatewayRpc.udtGetDefinitions(anyString())).thenReturn(new UdtListResult());

            JsonRpcRequest request =
                    new JsonRpcRequest(FlintConstants.METHOD_UDT_GET_DEFINITIONS, null, 42);
            JsonRpcResponse response = udtHandler.handle(request);

            assertEquals(42, ((Number) response.getId()).intValue());
        }
    }
}

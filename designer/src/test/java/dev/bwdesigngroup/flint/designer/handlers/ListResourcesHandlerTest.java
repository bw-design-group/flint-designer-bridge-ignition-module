package dev.bwdesigngroup.flint.designer.handlers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.inductiveautomation.ignition.designer.model.DesignerContext;
import dev.bwdesigngroup.flint.common.protocol.JsonRpcRequest;
import dev.bwdesigngroup.flint.common.protocol.JsonRpcResponse;
import dev.bwdesigngroup.flint.designer.platform.PlatformResources;
import dev.bwdesigngroup.flint.designer.server.FlintWebSocketHandler;
import java.util.ArrayList;
import org.java_websocket.WebSocket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("ListResourcesHandler")
@ExtendWith(MockitoExtension.class)
class ListResourcesHandlerTest {

    private static final String TEST_SECRET = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4";
    private static final String TEST_PROJECT = "TestProject";

    @Mock private WebSocket conn;

    @Mock private DesignerContext context;

    @Mock private PlatformResources platformResources;

    private FlintWebSocketHandler wsHandler;
    private ListResourcesHandler listHandler;
    private Gson gson;

    @BeforeEach
    void setUp() {
        lenient().when(context.getProjectName()).thenReturn(TEST_PROJECT);
        wsHandler = new FlintWebSocketHandler(conn, context, TEST_SECRET);
        wsHandler.setAuthenticated(true);
        wsHandler.setPlatformResources(platformResources);
        listHandler = new ListResourcesHandler(wsHandler);
        gson = new GsonBuilder().create();
    }

    @Nested
    @DisplayName("when project is null")
    class WhenProjectNull {

        @Test
        @DisplayName("returns INTERNAL_ERROR when no project available")
        void returnsErrorWhenNoProject() {
            when(platformResources.isProjectAvailable(context)).thenReturn(false);
            JsonRpcRequest request = new JsonRpcRequest("project.listResources", null, 1);

            JsonRpcResponse response = listHandler.handle(request);

            assertFalse(response.isSuccess());
            String json = gson.toJson(response);
            assertTrue(json.contains("No project available"));
        }
    }

    @Nested
    @DisplayName("with valid project")
    class WithValidProject {

        @BeforeEach
        void setUpProject() {
            when(platformResources.isProjectAvailable(context)).thenReturn(true);
            when(platformResources.getProjectTitle(context)).thenReturn(TEST_PROJECT);
            lenient()
                    .when(platformResources.getResourcesOfType(eq(context), any(), any()))
                    .thenReturn(new ArrayList<>());
        }

        @Test
        @DisplayName("returns success with empty resources when project has no resources")
        void returnsEmptyResources() {
            JsonRpcRequest request = new JsonRpcRequest("project.listResources", null, 1);

            JsonRpcResponse response = listHandler.handle(request);

            assertTrue(response.isSuccess());
            String json = gson.toJson(response.getResult());
            assertTrue(json.contains("\"count\":0") || json.contains("\"count\": 0"));
        }

        @Test
        @DisplayName("includes project name in result")
        void includesProjectName() {
            JsonRpcRequest request = new JsonRpcRequest("project.listResources", null, 2);

            JsonRpcResponse response = listHandler.handle(request);

            assertTrue(response.isSuccess());
            String json = gson.toJson(response.getResult());
            assertTrue(json.contains(TEST_PROJECT));
        }

        @Test
        @DisplayName("response id matches request id")
        void responseIdMatchesRequestId() {
            JsonRpcRequest request = new JsonRpcRequest("project.listResources", null, 99);

            JsonRpcResponse response = listHandler.handle(request);

            assertEquals(99, ((Number) response.getId()).intValue());
        }
    }

    @Nested
    @DisplayName("filter by resource type")
    class FilterByResourceType {

        @BeforeEach
        void setUpProject() {
            when(platformResources.isProjectAvailable(context)).thenReturn(true);
            when(platformResources.getProjectTitle(context)).thenReturn(TEST_PROJECT);
            lenient()
                    .when(platformResources.getResourcesOfType(eq(context), any(), any()))
                    .thenReturn(new ArrayList<>());
        }

        @Test
        @DisplayName("accepts resourceType filter parameter")
        void acceptsResourceTypeFilter() {
            JsonObject params = new JsonObject();
            params.addProperty("resourceType", "script-python");
            JsonRpcRequest request = new JsonRpcRequest("project.listResources", params, 3);

            JsonRpcResponse response = listHandler.handle(request);

            assertTrue(response.isSuccess());
        }

        @Test
        @DisplayName("handles null params gracefully")
        void handlesNullParams() {
            JsonRpcRequest request = new JsonRpcRequest("project.listResources", null, 4);

            JsonRpcResponse response = listHandler.handle(request);

            assertTrue(response.isSuccess());
        }
    }
}

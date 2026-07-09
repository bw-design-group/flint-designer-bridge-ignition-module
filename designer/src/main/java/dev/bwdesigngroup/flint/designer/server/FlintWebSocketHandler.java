package dev.bwdesigngroup.flint.designer.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.inductiveautomation.ignition.designer.model.DesignerContext;
import dev.bwdesigngroup.flint.common.FlintConstants;
import dev.bwdesigngroup.flint.common.protocol.ErrorCodes;
import dev.bwdesigngroup.flint.common.protocol.JsonRpcRequest;
import dev.bwdesigngroup.flint.common.protocol.JsonRpcResponse;
import dev.bwdesigngroup.flint.designer.handlers.AuthenticateHandler;
import dev.bwdesigngroup.flint.designer.handlers.BrowserCdpInfoHandler;
import dev.bwdesigngroup.flint.designer.handlers.ComponentSchemaHandler;
import dev.bwdesigngroup.flint.designer.handlers.DebugScriptHandler;
import dev.bwdesigngroup.flint.designer.handlers.ExecuteScriptHandler;
import dev.bwdesigngroup.flint.designer.handlers.GetOpenTabsHandler;
import dev.bwdesigngroup.flint.designer.handlers.IconRegistryHandler;
import dev.bwdesigngroup.flint.designer.handlers.ListResourcesHandler;
import dev.bwdesigngroup.flint.designer.handlers.LspHandler;
import dev.bwdesigngroup.flint.designer.handlers.MethodHandler;
import dev.bwdesigngroup.flint.designer.handlers.OpenResourceHandler;
import dev.bwdesigngroup.flint.designer.handlers.PerspectiveHandler;
import dev.bwdesigngroup.flint.designer.handlers.PingHandler;
import dev.bwdesigngroup.flint.designer.handlers.PreviewModeHandler;
import dev.bwdesigngroup.flint.designer.handlers.ProjectScanHandler;
import dev.bwdesigngroup.flint.designer.handlers.ShowMessageHandler;
import dev.bwdesigngroup.flint.designer.handlers.TagHandler;
import dev.bwdesigngroup.flint.designer.handlers.UdtHandler;
import dev.bwdesigngroup.flint.designer.handlers.ViewCatalogHandler;
import dev.bwdesigngroup.flint.designer.handlers.ViewConfigHandler;
import dev.bwdesigngroup.flint.designer.platform.PlatformFactory;
import dev.bwdesigngroup.flint.designer.platform.PlatformResources;
import java.util.HashMap;
import java.util.Map;
import org.java_websocket.WebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Handles JSON-RPC messages for a single WebSocket connection. */
public class FlintWebSocketHandler {
    private static final Logger logger = LoggerFactory.getLogger("flint.Designer.Handler");

    private final WebSocket conn;
    private final DesignerContext context;
    private final String secret;
    private final Gson gson;
    private final Map<String, MethodHandler> handlers;
    private final FlintWebSocketServer webSocketServer;
    private PlatformResources platformResources;

    private boolean authenticated = false;
    private String clientName;
    private String clientVersion;

    public FlintWebSocketHandler(WebSocket conn, DesignerContext context, String secret) {
        this(conn, context, secret, null);
    }

    public FlintWebSocketHandler(
            WebSocket conn,
            DesignerContext context,
            String secret,
            FlintWebSocketServer webSocketServer) {
        this.conn = conn;
        this.context = context;
        this.secret = secret;
        this.webSocketServer = webSocketServer;
        this.gson = new GsonBuilder().create();
        this.handlers = new HashMap<>();
        this.platformResources = PlatformFactory.createPlatformResources();

        // Register method handlers
        handlers.put(FlintConstants.METHOD_AUTHENTICATE, new AuthenticateHandler(this));
        handlers.put(FlintConstants.METHOD_PING, new PingHandler(this));
        handlers.put(FlintConstants.METHOD_EXECUTE_SCRIPT, new ExecuteScriptHandler(this));
        handlers.put(FlintConstants.METHOD_SHOW_MESSAGE, new ShowMessageHandler(this));
        handlers.put(FlintConstants.METHOD_PROJECT_SCAN, new ProjectScanHandler(this));

        // Register navigation handlers
        handlers.put(FlintConstants.METHOD_OPEN_RESOURCE, new OpenResourceHandler(this));

        // Register project handlers
        handlers.put(FlintConstants.METHOD_LIST_RESOURCES, new ListResourcesHandler(this));

        // Register debug handlers
        DebugScriptHandler debugHandler = new DebugScriptHandler(this);
        handlers.put(FlintConstants.METHOD_DEBUG_START_SESSION, debugHandler);
        handlers.put(FlintConstants.METHOD_DEBUG_STOP_SESSION, debugHandler);
        handlers.put(FlintConstants.METHOD_DEBUG_SET_BREAKPOINTS, debugHandler);
        handlers.put(FlintConstants.METHOD_DEBUG_RUN, debugHandler);
        handlers.put(FlintConstants.METHOD_DEBUG_CONTINUE, debugHandler);
        handlers.put(FlintConstants.METHOD_DEBUG_STEP_OVER, debugHandler);
        handlers.put(FlintConstants.METHOD_DEBUG_STEP_INTO, debugHandler);
        handlers.put(FlintConstants.METHOD_DEBUG_STEP_OUT, debugHandler);
        handlers.put(FlintConstants.METHOD_DEBUG_PAUSE, debugHandler);
        handlers.put(FlintConstants.METHOD_DEBUG_GET_STACK_TRACE, debugHandler);
        handlers.put(FlintConstants.METHOD_DEBUG_GET_SCOPES, debugHandler);
        handlers.put(FlintConstants.METHOD_DEBUG_GET_VARIABLES, debugHandler);
        handlers.put(FlintConstants.METHOD_DEBUG_EVALUATE, debugHandler);

        // Register LSP handlers
        LspHandler lspHandler = new LspHandler(this);
        handlers.put(FlintConstants.METHOD_LSP_COMPLETION, lspHandler);
        handlers.put(FlintConstants.METHOD_LSP_HOVER, lspHandler);
        handlers.put(FlintConstants.METHOD_LSP_SIGNATURE_HELP, lspHandler);
        handlers.put(FlintConstants.METHOD_LSP_INVALIDATE_CACHE, lspHandler);

        // Register Perspective handlers
        PerspectiveHandler perspectiveHandler = new PerspectiveHandler(this, webSocketServer);
        handlers.put(FlintConstants.METHOD_PERSPECTIVE_IS_AVAILABLE, perspectiveHandler);
        handlers.put(FlintConstants.METHOD_PERSPECTIVE_LIST_SESSIONS, perspectiveHandler);
        handlers.put(FlintConstants.METHOD_PERSPECTIVE_GET_SESSION_PAGES, perspectiveHandler);
        handlers.put(FlintConstants.METHOD_PERSPECTIVE_GET_PAGE_VIEWS, perspectiveHandler);
        handlers.put(FlintConstants.METHOD_PERSPECTIVE_GET_VIEW_COMPONENTS, perspectiveHandler);
        handlers.put(FlintConstants.METHOD_PERSPECTIVE_EXECUTE_SCRIPT, perspectiveHandler);
        handlers.put(
                FlintConstants.METHOD_PERSPECTIVE_GET_COMPONENT_COMPLETIONS, perspectiveHandler);
        handlers.put(FlintConstants.METHOD_PERSPECTIVE_PROFILE_VIEW, perspectiveHandler);
        handlers.put(FlintConstants.METHOD_PERSPECTIVE_START_RECORDING, perspectiveHandler);
        handlers.put(FlintConstants.METHOD_PERSPECTIVE_STOP_RECORDING, perspectiveHandler);
        handlers.put(FlintConstants.METHOD_PERSPECTIVE_POLL_RECORDING, perspectiveHandler);

        // Register browser/CDP handlers
        handlers.put(FlintConstants.METHOD_BROWSER_GET_CDP_INFO, new BrowserCdpInfoHandler(this));

        // Register designer workspace handlers
        handlers.put(FlintConstants.METHOD_DESIGNER_GET_OPEN_TABS, new GetOpenTabsHandler(this));
        handlers.put(FlintConstants.METHOD_DESIGNER_TOGGLE_PREVIEW, new PreviewModeHandler(this));

        // Register view editing handlers
        ViewConfigHandler viewHandler = new ViewConfigHandler(this);
        handlers.put(FlintConstants.METHOD_VIEW_GET_CONFIG, viewHandler);
        handlers.put(FlintConstants.METHOD_VIEW_SET_CONFIG, viewHandler);
        handlers.put(FlintConstants.METHOD_VIEW_GET_COMPONENT, viewHandler);
        handlers.put(FlintConstants.METHOD_VIEW_SET_COMPONENT, viewHandler);
        handlers.put(FlintConstants.METHOD_VIEW_VALIDATE, viewHandler);
        handlers.put(FlintConstants.METHOD_VIEW_GET_TREE, viewHandler);
        handlers.put(FlintConstants.METHOD_VIEW_SAVE, viewHandler);
        handlers.put(FlintConstants.METHOD_VIEW_CREATE, viewHandler);
        handlers.put(FlintConstants.METHOD_VIEW_DELETE, viewHandler);

        // Register component schema handlers
        ComponentSchemaHandler componentSchemaHandler = new ComponentSchemaHandler(this);
        handlers.put(FlintConstants.METHOD_COMPONENT_LIST, componentSchemaHandler);
        handlers.put(FlintConstants.METHOD_COMPONENT_GET_SCHEMA, componentSchemaHandler);

        // Register view catalog handler
        handlers.put(FlintConstants.METHOD_VIEW_CATALOG, new ViewCatalogHandler(this));

        // Register icon registry handlers
        IconRegistryHandler iconHandler = new IconRegistryHandler(this);
        handlers.put(FlintConstants.METHOD_ICON_LIST, iconHandler);
        handlers.put(FlintConstants.METHOD_ICON_SEARCH, iconHandler);

        // Register tag system handlers
        TagHandler tagHandler = new TagHandler(this);
        handlers.put(FlintConstants.METHOD_TAGS_BROWSE, tagHandler);
        handlers.put(FlintConstants.METHOD_TAGS_READ, tagHandler);
        handlers.put(FlintConstants.METHOD_TAGS_WRITE, tagHandler);
        handlers.put(FlintConstants.METHOD_TAGS_GET_CONFIG, tagHandler);
        handlers.put(FlintConstants.METHOD_TAGS_CREATE, tagHandler);
        handlers.put(FlintConstants.METHOD_TAGS_EDIT, tagHandler);
        handlers.put(FlintConstants.METHOD_TAGS_DELETE, tagHandler);
        handlers.put(FlintConstants.METHOD_TAGS_GET_PROVIDERS, tagHandler);

        // Register UDT system handlers
        UdtHandler udtHandler = new UdtHandler(this);
        handlers.put(FlintConstants.METHOD_UDT_GET_DEFINITIONS, udtHandler);
        handlers.put(FlintConstants.METHOD_UDT_GET_DEFINITION, udtHandler);
        handlers.put(FlintConstants.METHOD_UDT_CREATE_DEFINITION, udtHandler);
        handlers.put(FlintConstants.METHOD_UDT_CREATE_INSTANCE, udtHandler);
    }

    /** Handles an incoming message. */
    public void handleMessage(String message) {
        logger.debug("Received message: {}", message);

        JsonRpcRequest request;
        try {
            request = gson.fromJson(message, JsonRpcRequest.class);
        } catch (JsonSyntaxException e) {
            sendError(ErrorCodes.PARSE_ERROR, "Parse error: " + e.getMessage(), null);
            return;
        }

        if (request == null || !request.isValid()) {
            sendError(
                    ErrorCodes.INVALID_REQUEST,
                    "Invalid JSON-RPC request",
                    request != null ? request.getId() : null);
            return;
        }

        // Handle the request
        handleRequest(request);
    }

    /** Handles a parsed JSON-RPC request. */
    private void handleRequest(JsonRpcRequest request) {
        String method = request.getMethod();
        Object id = request.getId();

        logger.debug("Handling method: {}", method);

        // Check authentication for non-auth methods
        if (!FlintConstants.METHOD_AUTHENTICATE.equals(method) && !authenticated) {
            sendError(ErrorCodes.NOT_AUTHENTICATED, "Not authenticated", id);
            return;
        }

        // Find handler
        MethodHandler handler = handlers.get(method);
        if (handler == null) {
            sendError(ErrorCodes.METHOD_NOT_FOUND, "Method not found: " + method, id);
            return;
        }

        // Execute handler
        try {
            JsonRpcResponse response = handler.handle(request);
            if (!request.isNotification() && response != null) {
                sendResponse(response);
            }
        } catch (Exception e) {
            logger.error("Error handling method {}: {}", method, e.getMessage(), e);
            sendError(ErrorCodes.INTERNAL_ERROR, "Internal error: " + e.getMessage(), id);
        }
    }

    /** Sends a JSON-RPC response. */
    public void sendResponse(JsonRpcResponse response) {
        String json = gson.toJson(response);
        logger.debug("Sending response: {}", json);
        conn.send(json);
    }

    /** Sends an error response. */
    public void sendError(int code, String message, Object id) {
        JsonRpcResponse response = JsonRpcResponse.error(code, message, id);
        sendResponse(response);
    }

    // Getters and setters
    public boolean isAuthenticated() {
        return authenticated;
    }

    public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
    }

    public String getSecret() {
        return secret;
    }

    public DesignerContext getContext() {
        return context;
    }

    public WebSocket getConnection() {
        return conn;
    }

    public Gson getGson() {
        return gson;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public String getClientVersion() {
        return clientVersion;
    }

    public void setClientVersion(String clientVersion) {
        this.clientVersion = clientVersion;
    }

    public PlatformResources getPlatformResources() {
        return platformResources;
    }

    /** Overrides the PlatformResources instance. Visible for testing. */
    public void setPlatformResources(PlatformResources platformResources) {
        this.platformResources = platformResources;
    }
}

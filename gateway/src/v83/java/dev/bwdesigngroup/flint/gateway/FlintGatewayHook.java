package dev.bwdesigngroup.flint.gateway;

import com.google.gson.Gson;
import com.inductiveautomation.ignition.common.licensing.LicenseState;
import com.inductiveautomation.ignition.gateway.dataroutes.AccessControlStrategy;
import com.inductiveautomation.ignition.gateway.dataroutes.HttpMethod;
import com.inductiveautomation.ignition.gateway.dataroutes.RouteGroup;
import com.inductiveautomation.ignition.gateway.model.AbstractGatewayModuleHook;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import com.inductiveautomation.ignition.gateway.rpc.GatewayRpcImplementation;
import dev.bwdesigngroup.flint.common.FlintConstants;
import dev.bwdesigngroup.flint.common.rpc.FlintGatewayRpc;
import dev.bwdesigngroup.flint.gateway.auth.ApiTokenAuthenticator;
import dev.bwdesigngroup.flint.gateway.auth.FlintTokenAuthenticator;
import dev.bwdesigngroup.flint.gateway.auth.FlintTokenStore;
import dev.bwdesigngroup.flint.gateway.auth.GatewayAuthenticator;
import dev.bwdesigngroup.flint.gateway.debug.GatewayDebugExecutor;
import dev.bwdesigngroup.flint.gateway.http.FlintHealthRouteHandler;
import dev.bwdesigngroup.flint.gateway.http.FlintRpcRouteHandler;
import dev.bwdesigngroup.flint.gateway.http.GatewayPerspectiveExtension;
import dev.bwdesigngroup.flint.gateway.http.GatewayResourceExtension;
import dev.bwdesigngroup.flint.gateway.http.GatewayRpcDispatcher;
import dev.bwdesigngroup.flint.gateway.http.LspDispatchExtension;
import dev.bwdesigngroup.flint.gateway.lsp.FlintLanguageServer;
import dev.bwdesigngroup.flint.gateway.lsp.ProjectIndex;
import dev.bwdesigngroup.flint.gateway.lsp.V83HintsSource;
import dev.bwdesigngroup.flint.gateway.lsp.ws.FlintLspWebSocketServlet;
import dev.bwdesigngroup.flint.gateway.lsp.ws.LspWebSocketBridge;
import dev.bwdesigngroup.flint.gateway.perspective.GatewayPerspectiveRegistry;
import dev.bwdesigngroup.flint.gateway.perspective.PerspectiveScriptExecutor;
import dev.bwdesigngroup.flint.gateway.perspective.PerspectiveSessionInspector;
import dev.bwdesigngroup.flint.gateway.resources.GatewayResourceService;
import dev.bwdesigngroup.flint.gateway.resources.V83GatewayResources;
import dev.bwdesigngroup.flint.gateway.rpc.FlintGatewayRpcImpl;
import dev.bwdesigngroup.flint.gateway.script.GatewayScriptExecutor;
import dev.bwdesigngroup.flint.gateway.tags.GatewayTagExecutor;
import dev.bwdesigngroup.flint.gateway.view.GatewayViewService;
import jakarta.servlet.ServletContext;
import java.util.Optional;
import org.eclipse.jetty.ee10.websocket.server.JettyWebSocketServerContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gateway hook for the Flint Designer Bridge module. Provides RPC interface for gateway-scope
 * script execution called from Designer.
 */
public class FlintGatewayHook extends AbstractGatewayModuleHook {
    private static final Logger logger = LoggerFactory.getLogger("flint.Gateway");

    private GatewayContext context;
    private GatewayScriptExecutor scriptExecutor;
    private PerspectiveSessionInspector perspectiveInspector;
    private PerspectiveScriptExecutor perspectiveScriptExecutor;
    private GatewayDebugExecutor debugExecutor;
    private GatewayTagExecutor tagExecutor;
    private FlintGatewayRpcImpl rpcHandler;
    private GatewayRpcDispatcher dispatcher;
    private GatewayAuthenticator authenticator;
    private LspWebSocketBridge lspWebSocketBridge;

    @Override
    public void setup(GatewayContext context) {
        this.context = context;
        logger.debug("Flint Designer Bridge gateway hook setup");
    }

    @Override
    public void startup(LicenseState activationState) {
        // Initialize gateway script executor
        this.scriptExecutor = new GatewayScriptExecutor(context);

        // Initialize Perspective components (may not be available if Perspective isn't installed)
        this.perspectiveInspector = new PerspectiveSessionInspector(context);
        this.perspectiveScriptExecutor =
                new PerspectiveScriptExecutor(context, perspectiveInspector);

        // Initialize debug executor for Gateway/Perspective scope debugging
        this.debugExecutor = new GatewayDebugExecutor(context, perspectiveInspector);

        // Initialize tag executor for tag system operations
        this.tagExecutor = new GatewayTagExecutor(context);

        // Create RPC handler for Designer to call
        this.rpcHandler =
                new FlintGatewayRpcImpl(
                        context,
                        scriptExecutor,
                        perspectiveInspector,
                        perspectiveScriptExecutor,
                        debugExecutor,
                        tagExecutor);

        // Build the headless HTTP transport: authenticator (native API token with Flint-token
        // fallback) + the version-neutral JSON-RPC dispatcher over the same executors.
        FlintTokenStore tokenStore = new FlintTokenStore(context);
        this.authenticator =
                new ApiTokenAuthenticator(context, new FlintTokenAuthenticator(tokenStore));
        this.dispatcher = new GatewayRpcDispatcher(context, rpcHandler, new Gson());

        // Wire project resource + view CRUD over the 8.3 resourcecollection APIs.
        GatewayResourceService resourceService =
                new GatewayResourceService(new V83GatewayResources(context));
        this.dispatcher.addExtension(
                new GatewayResourceExtension(
                        resourceService, new GatewayViewService(resourceService, new Gson())));

        // Wire Perspective component registry + icon libraries (headless).
        this.dispatcher.addExtension(
                new GatewayPerspectiveExtension(new GatewayPerspectiveRegistry(context)));

        // Wire the headless Jython language server (lsp.*) with 8.3 system.* hints + project index.
        FlintLanguageServer languageServer =
                new FlintLanguageServer(
                        new V83HintsSource(context), new ProjectIndex(resourceService.getStore()));
        this.dispatcher.addExtension(new LspDispatchExtension(languageServer));

        // Expose the same engine as a raw-LSP endpoint over WebSocket (Jetty 12 ee10 API).
        startLspWebSocket(languageServer);

        logger.info("Flint Designer Bridge module started (Gateway scope)");

        // Note: Perspective availability is checked lazily when functionality is used.
        // This avoids issues with module startup order and works with or without Perspective
        // installed.
    }

    @Override
    public void shutdown() {
        // Tear down the LSP WebSocket transport before the rest of the gateway state.
        stopLspWebSocket();

        // Shutdown debug executor
        if (debugExecutor != null) {
            debugExecutor.shutdown();
            debugExecutor = null;
        }

        // Shutdown Perspective script executor
        if (perspectiveScriptExecutor != null) {
            perspectiveScriptExecutor.shutdown();
            perspectiveScriptExecutor = null;
        }

        // Shutdown gateway script executor
        if (scriptExecutor != null) {
            scriptExecutor.shutdown();
            scriptExecutor = null;
        }

        tagExecutor = null;
        perspectiveInspector = null;
        rpcHandler = null;
        dispatcher = null;
        authenticator = null;
        logger.info("Flint Designer Bridge module stopped (Gateway scope)");
    }

    /**
     * Mounts the raw-LSP-over-WebSocket servlet at {@code /system/flint-lsp} and advertises the
     * capability. Best-effort: guarded by {@code Class.forName} + a broad catch so a gateway
     * missing the Jetty ee10 WebSocket API still loads the module (the HTTP {@code lsp.*} path is
     * unaffected).
     */
    private void startLspWebSocket(FlintLanguageServer languageServer) {
        try {
            Class.forName("org.eclipse.jetty.ee10.websocket.server.JettyWebSocketServerContainer");
            LspWebSocketBridge bridge =
                    new LspWebSocketBridge(
                            languageServer, authenticator, new Gson(), moduleVersion());
            ServletContext servletContext = context.getWebResourceManager().getServletContext();
            servletContext.setAttribute(FlintConstants.LSP_WS_BRIDGE_ATTR, bridge);
            JettyWebSocketServerContainer.ensureContainer(servletContext);
            context.getWebResourceManager()
                    .addServlet(FlintConstants.GATEWAY_WS_LSP_NAME, FlintLspWebSocketServlet.class);
            this.dispatcher.setLspWebSocketPath(FlintConstants.GATEWAY_WS_LSP_PATH);
            this.lspWebSocketBridge = bridge;
            logger.info(
                    "Flint LSP WebSocket transport mounted at {}",
                    FlintConstants.GATEWAY_WS_LSP_PATH);
        } catch (Throwable t) {
            logger.warn(
                    "Flint LSP WebSocket transport unavailable on this gateway: {}", t.toString());
        }
    }

    /** Removes the WebSocket servlet + bridge attribute and shuts the bridge down (idempotent). */
    private void stopLspWebSocket() {
        try {
            context.getWebResourceManager().removeServlet(FlintConstants.GATEWAY_WS_LSP_NAME);
        } catch (Throwable ignored) {
            // Servlet may never have registered; nothing to remove.
        }
        if (lspWebSocketBridge != null) {
            try {
                context.getWebResourceManager()
                        .getServletContext()
                        .removeAttribute(FlintConstants.LSP_WS_BRIDGE_ATTR);
            } catch (Throwable ignored) {
                // Servlet context may already be torn down.
            }
            lspWebSocketBridge.shutdown();
            lspWebSocketBridge = null;
        }
    }

    /** Module version string used as the LSP {@code serverInfo.version}. */
    private String moduleVersion() {
        try {
            String version = getClass().getPackage().getImplementationVersion();
            return version != null ? version : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * Mounts the headless HTTP transport. Routes resolve under {@code /data/flint/*} (see {@link
     * #getMountPathAlias()}).
     */
    @Override
    public void mountRouteHandlers(RouteGroup routes) {
        // OPEN_ROUTE lets the request reach our handler; authentication (native API token or Flint
        // bearer) is enforced inside FlintRpcRouteHandler. /health is intentionally public.
        routes.newRoute(FlintConstants.GATEWAY_ROUTE_RPC)
                .method(HttpMethod.POST)
                .type(RouteGroup.TYPE_JSON)
                .accessControl(AccessControlStrategy.OPEN_ROUTE)
                .handler(new FlintRpcRouteHandler(this))
                .mount();

        routes.newRoute(FlintConstants.GATEWAY_ROUTE_HEALTH)
                .method(HttpMethod.GET)
                .type(RouteGroup.TYPE_JSON)
                .accessControl(AccessControlStrategy.OPEN_ROUTE)
                .handler(new FlintHealthRouteHandler(this))
                .mount();

        logger.info(
                "Flint gateway HTTP transport mounted at /data/{}{}",
                FlintConstants.GATEWAY_ROUTE_ALIAS,
                FlintConstants.GATEWAY_ROUTE_RPC);
    }

    @Override
    public Optional<String> getMountPathAlias() {
        return Optional.of(FlintConstants.GATEWAY_ROUTE_ALIAS);
    }

    /** Accessors used by the version-specific route handlers (resolved lazily per request). */
    public GatewayRpcDispatcher getDispatcher() {
        return dispatcher;
    }

    public GatewayAuthenticator getAuthenticator() {
        return authenticator;
    }

    /**
     * Returns the RPC implementation for Designer-to-Gateway communication. Uses the 8.3+ RPC
     * mechanism with ProtoRpcSerializer.
     */
    @Override
    public Optional<GatewayRpcImplementation> getRpcImplementation() {
        return Optional.of(GatewayRpcImplementation.of(FlintGatewayRpc.SERIALIZER, rpcHandler));
    }

    @Override
    public boolean isMakerEditionCompatible() {
        return true;
    }

    @Override
    public boolean isFreeModule() {
        return true;
    }
}

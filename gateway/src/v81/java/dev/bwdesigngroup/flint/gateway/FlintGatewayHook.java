package dev.bwdesigngroup.flint.gateway;

import com.google.gson.Gson;
import com.inductiveautomation.ignition.common.licensing.LicenseState;
import com.inductiveautomation.ignition.gateway.clientcomm.ClientReqSession;
import com.inductiveautomation.ignition.gateway.dataroutes.HttpMethod;
import com.inductiveautomation.ignition.gateway.dataroutes.RouteGroup;
import com.inductiveautomation.ignition.gateway.model.AbstractGatewayModuleHook;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import dev.bwdesigngroup.flint.common.FlintConstants;
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
import dev.bwdesigngroup.flint.gateway.lsp.V81HintsSource;
import dev.bwdesigngroup.flint.gateway.perspective.GatewayPerspectiveRegistry;
import dev.bwdesigngroup.flint.gateway.perspective.PerspectiveScriptExecutor;
import dev.bwdesigngroup.flint.gateway.perspective.PerspectiveSessionInspector;
import dev.bwdesigngroup.flint.gateway.resources.GatewayResourceService;
import dev.bwdesigngroup.flint.gateway.resources.V81GatewayResources;
import dev.bwdesigngroup.flint.gateway.rpc.FlintGatewayRpcImpl;
import dev.bwdesigngroup.flint.gateway.script.GatewayScriptExecutor;
import dev.bwdesigngroup.flint.gateway.tags.GatewayTagExecutor;
import dev.bwdesigngroup.flint.gateway.view.GatewayViewService;
import java.util.Optional;
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

        // Build the headless HTTP transport. Ignition 8.1 has no native API tokens, so a
        // Flint-managed bearer token is the authenticator.
        FlintTokenStore tokenStore = new FlintTokenStore(context);
        this.authenticator = new FlintTokenAuthenticator(tokenStore);
        this.dispatcher = new GatewayRpcDispatcher(context, rpcHandler, new Gson());

        // Wire project resource + view CRUD over the 8.1 common.project APIs.
        GatewayResourceService resourceService =
                new GatewayResourceService(new V81GatewayResources(context));
        this.dispatcher.addExtension(
                new GatewayResourceExtension(
                        resourceService, new GatewayViewService(resourceService, new Gson())));

        // Wire Perspective component registry + icon libraries (headless).
        this.dispatcher.addExtension(
                new GatewayPerspectiveExtension(new GatewayPerspectiveRegistry(context)));

        // Wire the headless Jython language server (lsp.*) with 8.1 system.* hints + project index.
        this.dispatcher.addExtension(
                new LspDispatchExtension(
                        new FlintLanguageServer(
                                new V81HintsSource(context),
                                new ProjectIndex(resourceService.getStore()))));

        logger.info("Flint Designer Bridge module started (Gateway scope)");

        // Note: Perspective availability is checked lazily when functionality is used.
        // This avoids issues with module startup order and works with or without Perspective
        // installed.
    }

    @Override
    public void shutdown() {
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

    /** Mounts the headless HTTP transport. Routes resolve under {@code /data/flint/*}. */
    @Override
    public void mountRouteHandlers(RouteGroup routes) {
        // restrict(rc -> true) leaves the route open; authentication (Flint bearer token) is
        // enforced inside FlintRpcRouteHandler. /health is intentionally public.
        routes.newRoute(FlintConstants.GATEWAY_ROUTE_RPC)
                .method(HttpMethod.POST)
                .type(RouteGroup.TYPE_JSON)
                .restrict(rc -> true)
                .handler(new FlintRpcRouteHandler(this))
                .mount();

        routes.newRoute(FlintConstants.GATEWAY_ROUTE_HEALTH)
                .method(HttpMethod.GET)
                .type(RouteGroup.TYPE_JSON)
                .restrict(rc -> true)
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
     * Returns the RPC handler for Designer-to-Gateway communication. This is called by the Ignition
     * SDK when a Designer makes an RPC call.
     */
    @Override
    public Object getRPCHandler(ClientReqSession session, String projectName) {
        return rpcHandler;
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

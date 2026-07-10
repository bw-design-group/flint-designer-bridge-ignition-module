package dev.bwdesigngroup.flint.gateway.lsp.ws;

import com.inductiveautomation.ignition.gateway.dataroutes.RequestContext;
import dev.bwdesigngroup.flint.common.FlintConstants;
import dev.bwdesigngroup.flint.gateway.auth.GatewayAuthenticator;
import javax.servlet.ServletException;
import org.eclipse.jetty.websocket.server.JettyServerUpgradeResponse;
import org.eclipse.jetty.websocket.server.JettyWebSocketServlet;
import org.eclipse.jetty.websocket.server.JettyWebSocketServletFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ignition 8.1 (Jetty 10) WebSocket servlet for the raw-LSP endpoint mounted at {@code
 * /system/flint-lsp}. Resolves the hook-owned {@link LspWebSocketBridge} from the {@code
 * ServletContext} in {@code init()} and authenticates each upgrade with the gateway's HTTP
 * authenticator; unauthenticated upgrades get 401 and no endpoint.
 */
public class FlintLspWebSocketServlet extends JettyWebSocketServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger("flint.Gateway.Lsp.Ws");
    private static final long MAX_TEXT_MESSAGE_BYTES = 32L * 1024 * 1024;
    private static final long IDLE_TIMEOUT_MS = 5L * 60 * 1000;

    private transient LspWebSocketBridge bridge;

    @Override
    public void init() throws ServletException {
        super.init();
        Object attribute = getServletContext().getAttribute(FlintConstants.LSP_WS_BRIDGE_ATTR);
        if (attribute instanceof LspWebSocketBridge) {
            bridge = (LspWebSocketBridge) attribute;
        } else {
            logger.warn("Flint LSP WebSocket bridge not found in servlet context");
        }
    }

    @Override
    protected void configure(JettyWebSocketServletFactory factory) {
        factory.setMaxTextMessageSize(MAX_TEXT_MESSAGE_BYTES);
        factory.setIdleTimeout(java.time.Duration.ofMillis(IDLE_TIMEOUT_MS));
        factory.setCreator(
                (upgradeRequest, upgradeResponse) -> {
                    if (bridge == null) {
                        reject(upgradeResponse, 503, "Gateway starting up");
                        return null;
                    }
                    RequestContext ctx =
                            new RequestContext(
                                    upgradeRequest.getHttpServletRequest(),
                                    FlintConstants.GATEWAY_WS_LSP_PATH);
                    GatewayAuthenticator.AuthResult auth = bridge.authenticate(ctx);
                    if (!auth.isOk()) {
                        reject(
                                upgradeResponse,
                                401,
                                auth.getMessage() != null ? auth.getMessage() : "Unauthorized");
                        return null;
                    }
                    return new FlintLspWebSocketEndpoint(bridge);
                });
    }

    /**
     * Fails the upgrade with a committed error response. Jetty 10 answers 503 when a {@code
     * JettyWebSocketCreator} returns null without a committed response, so {@code sendError} (which
     * commits) is required to surface the real status — plain {@code setStatusCode} is not enough.
     */
    private static void reject(
            JettyServerUpgradeResponse response, int statusCode, String message) {
        try {
            response.sendError(statusCode, message);
        } catch (java.io.IOException e) {
            response.setStatusCode(statusCode);
        }
    }
}

package dev.bwdesigngroup.flint.gateway.lsp.ws;

import com.inductiveautomation.ignition.gateway.dataroutes.RequestContext;
import dev.bwdesigngroup.flint.common.FlintConstants;
import dev.bwdesigngroup.flint.gateway.auth.GatewayAuthenticator;
import jakarta.servlet.ServletException;
import org.eclipse.jetty.ee10.websocket.server.JettyWebSocketServlet;
import org.eclipse.jetty.ee10.websocket.server.JettyWebSocketServletFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ignition 8.3 (Jetty 12 ee10) WebSocket servlet for the raw-LSP endpoint mounted at {@code
 * /system/flint-lsp}. Resolves the hook-owned {@link LspWebSocketBridge} from the {@code
 * ServletContext} in {@code init()} and authenticates each upgrade with the gateway's HTTP
 * authenticator (native API token or Flint bearer); unauthenticated upgrades get 401 and no
 * endpoint.
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
                        upgradeResponse.setStatusCode(503);
                        return null;
                    }
                    RequestContext ctx =
                            new RequestContext(
                                    upgradeRequest.getHttpServletRequest(),
                                    FlintConstants.GATEWAY_WS_LSP_PATH);
                    GatewayAuthenticator.AuthResult auth = bridge.authenticate(ctx);
                    if (!auth.isOk()) {
                        upgradeResponse.setStatusCode(401);
                        return null;
                    }
                    return new FlintLspWebSocketEndpoint(bridge);
                });
    }
}

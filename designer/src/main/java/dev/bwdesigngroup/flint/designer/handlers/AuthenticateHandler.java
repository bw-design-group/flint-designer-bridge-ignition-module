package dev.bwdesigngroup.flint.designer.handlers;

import com.inductiveautomation.ignition.client.gateway_interface.GatewayConnectionManager;
import com.inductiveautomation.ignition.client.gateway_interface.GatewayInterface;
import com.inductiveautomation.ignition.designer.model.DesignerContext;
import dev.bwdesigngroup.flint.common.protocol.ErrorCodes;
import dev.bwdesigngroup.flint.common.protocol.JsonRpcRequest;
import dev.bwdesigngroup.flint.common.protocol.JsonRpcResponse;
import dev.bwdesigngroup.flint.common.protocol.methods.AuthenticateParams;
import dev.bwdesigngroup.flint.common.protocol.methods.AuthenticateResult;
import dev.bwdesigngroup.flint.designer.server.FlintWebSocketHandler;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Handler for the authenticate method. */
public class AuthenticateHandler implements MethodHandler {
    private static final Logger logger = LoggerFactory.getLogger("flint.Designer.Auth");

    private final FlintWebSocketHandler handler;

    public AuthenticateHandler(FlintWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public JsonRpcResponse handle(JsonRpcRequest request) {
        Object id = request.getId();

        // Parse params
        AuthenticateParams params;
        try {
            params = handler.getGson().fromJson(request.getParams(), AuthenticateParams.class);
        } catch (Exception e) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS, "Invalid params: " + e.getMessage(), id);
        }

        if (params == null || params.getSecret() == null) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS, "Missing required parameter: secret", id);
        }

        // Validate secret using constant-time comparison
        String expectedSecret = handler.getSecret();
        String providedSecret = params.getSecret();

        if (!constantTimeEquals(expectedSecret, providedSecret)) {
            logger.warn("Authentication failed - invalid secret");
            return JsonRpcResponse.error(ErrorCodes.AUTHENTICATION_FAILED, "Invalid secret", id);
        }

        // Authentication successful
        handler.setAuthenticated(true);
        handler.setClientName(params.getClientName());
        handler.setClientVersion(params.getClientVersion());

        logger.info(
                "Client authenticated: {} v{}", params.getClientName(), params.getClientVersion());

        // Build result with capabilities
        DesignerContext context = handler.getContext();
        List<String> capabilities =
                Arrays.asList(
                        "viewEditing",
                        "cdpBrowser",
                        "iconSearch",
                        "componentSchema",
                        "viewCatalog",
                        "scriptExecution",
                        "gatewayScope",
                        "perspectiveInspector",
                        "debug",
                        "lsp");

        AuthenticateResult result =
                new AuthenticateResult(
                        true,
                        getDesignerVersion(),
                        "0.12.0",
                        context.getProjectName(),
                        getGatewayName(),
                        capabilities);

        return JsonRpcResponse.success(result, id);
    }

    private String getDesignerVersion() {
        return System.getProperty("ignition.version", "8.1.x");
    }

    private String getGatewayName() {
        try {
            GatewayInterface gatewayInterface =
                    GatewayConnectionManager.getInstance().getGatewayInterface();
            URI gatewayUri = new URI(gatewayInterface.getGatewayAddress().toString());
            return gatewayUri.getHost();
        } catch (Exception e) {
            return "Unknown";
        }
    }

    /** Constant-time string comparison to prevent timing attacks. */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }

        byte[] aBytes = a.getBytes();
        byte[] bBytes = b.getBytes();

        if (aBytes.length != bBytes.length) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < aBytes.length; i++) {
            result |= aBytes[i] ^ bBytes[i];
        }
        return result == 0;
    }
}

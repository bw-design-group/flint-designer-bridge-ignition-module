package dev.bwdesigngroup.flint.gateway.auth;

import com.inductiveautomation.ignition.gateway.auth.apitoken.ApiTokenManager;
import com.inductiveautomation.ignition.gateway.dataroutes.RequestContext;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ignition 8.3 authenticator that validates the platform's native API tokens (header {@code
 * X-Ignition-API-Token: keyId:secret}) via {@link ApiTokenManager}. Operators manage these keys in
 * the gateway UI. Falls back to a Flint-managed bearer token so a single client code path works across
 * versions.
 */
public class ApiTokenAuthenticator implements GatewayAuthenticator {

    private static final Logger logger = LoggerFactory.getLogger("flint.Gateway.Auth");

    private final GatewayContext context;
    private final GatewayAuthenticator fallback;

    public ApiTokenAuthenticator(GatewayContext context, GatewayAuthenticator fallback) {
        this.context = context;
        this.fallback = fallback;
    }

    @Override
    public AuthResult authenticate(RequestContext ctx) {
        try {
            ApiTokenManager manager = context.getApiTokenManager();
            if (manager != null) {
                Optional<ApiTokenManager.ApiTokenContext> token = manager.validateRequest(ctx);
                if (token.isPresent()) {
                    return AuthResult.ok(token.get().tokenName());
                }
            }
        } catch (Exception e) {
            logger.debug("API token validation failed: {}", e.getMessage());
        }

        if (fallback != null) {
            return fallback.authenticate(ctx);
        }
        return AuthResult.fail("Invalid or missing API token");
    }
}

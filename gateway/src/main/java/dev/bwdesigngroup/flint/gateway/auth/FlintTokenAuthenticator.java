package dev.bwdesigngroup.flint.gateway.auth;

import com.inductiveautomation.ignition.gateway.dataroutes.RequestContext;
import dev.bwdesigngroup.flint.common.auth.AuthUtil;

/**
 * Authenticates requests carrying a Flint-managed bearer token via the {@code Authorization: Bearer
 * <token>} header. Used as the primary authenticator on Ignition 8.1 and as a portable fallback on
 * 8.3 (after the native API-token check).
 */
public class FlintTokenAuthenticator implements GatewayAuthenticator {

    private static final String BEARER_PREFIX = "Bearer ";

    private final FlintTokenStore tokenStore;

    public FlintTokenAuthenticator(FlintTokenStore tokenStore) {
        this.tokenStore = tokenStore;
    }

    @Override
    public AuthResult authenticate(RequestContext ctx) {
        String header;
        try {
            header = ctx.getRequest().getHeader("Authorization");
        } catch (Exception e) {
            return AuthResult.fail("Unable to read Authorization header");
        }

        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return AuthResult.fail("Missing or malformed Authorization: Bearer token");
        }

        String provided = header.substring(BEARER_PREFIX.length()).trim();
        if (AuthUtil.constantTimeEquals(tokenStore.getToken(), provided)) {
            return AuthResult.ok("flint-token");
        }
        return AuthResult.fail("Invalid bearer token");
    }
}

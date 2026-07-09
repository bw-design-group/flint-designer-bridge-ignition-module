package dev.bwdesigngroup.flint.gateway.auth;

import com.inductiveautomation.ignition.gateway.dataroutes.RequestContext;

/**
 * Strategy for authenticating an incoming Gateway HTTP request. Implementations are
 * version-specific (8.3 validates the platform API token via {@code ApiTokenManager}; 8.1 validates
 * a Flint-managed bearer token) but share this SPI so the route handler has a single code path.
 */
public interface GatewayAuthenticator {

    /** Authenticates the request. Never throws; failures are returned as an unsuccessful result. */
    AuthResult authenticate(RequestContext ctx);

    /** Result of an authentication attempt. */
    final class AuthResult {
        private final boolean ok;
        private final String principal;
        private final String message;

        private AuthResult(boolean ok, String principal, String message) {
            this.ok = ok;
            this.principal = principal;
            this.message = message;
        }

        public static AuthResult ok(String principal) {
            return new AuthResult(true, principal, null);
        }

        public static AuthResult fail(String message) {
            return new AuthResult(false, null, message);
        }

        public boolean isOk() {
            return ok;
        }

        public String getPrincipal() {
            return principal;
        }

        public String getMessage() {
            return message;
        }
    }
}

package dev.bwdesigngroup.flint.gateway.http;

import com.google.gson.Gson;
import com.inductiveautomation.ignition.gateway.dataroutes.RequestContext;
import com.inductiveautomation.ignition.gateway.dataroutes.RouteHandler;
import dev.bwdesigngroup.flint.common.protocol.ErrorCodes;
import dev.bwdesigngroup.flint.common.protocol.JsonRpcResponse;
import dev.bwdesigngroup.flint.gateway.FlintGatewayHook;
import dev.bwdesigngroup.flint.gateway.auth.GatewayAuthenticator;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Ignition 8.3 (jakarta) route handler for {@code POST /rpc}. Authenticates the request, then hands
 * the body to the version-neutral dispatcher and writes the JSON response.
 */
public class FlintRpcRouteHandler implements RouteHandler {

    private static final Gson GSON = new Gson();

    private final FlintGatewayHook hook;

    public FlintRpcRouteHandler(FlintGatewayHook hook) {
        this.hook = hook;
    }

    @Override
    public Object handle(RequestContext ctx, HttpServletResponse res) throws Exception {
        GatewayRpcDispatcher dispatcher = hook.getDispatcher();
        GatewayAuthenticator authenticator = hook.getAuthenticator();
        if (dispatcher == null || authenticator == null) {
            write(res, 503, errorEnvelope(ErrorCodes.GATEWAY_NOT_AVAILABLE, "Gateway starting up"));
            return null;
        }

        GatewayAuthenticator.AuthResult auth = authenticator.authenticate(ctx);
        if (!auth.isOk()) {
            write(
                    res,
                    401,
                    errorEnvelope(
                            ErrorCodes.AUTHENTICATION_FAILED,
                            auth.getMessage() != null ? auth.getMessage() : "Unauthorized"));
            return null;
        }

        String body = ctx.readBody();
        HttpRpcResult result = dispatcher.dispatchHttp(body);
        write(res, result.getStatus(), result.getBody());
        return null;
    }

    private void write(HttpServletResponse res, int status, String body) throws Exception {
        res.setStatus(status);
        res.setContentType("application/json");
        res.getWriter().write(body != null ? body : "");
        res.getWriter().flush();
    }

    private String errorEnvelope(int code, String message) {
        return GSON.toJson(JsonRpcResponse.error(code, message, null));
    }
}

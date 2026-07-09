package dev.bwdesigngroup.flint.gateway.http;

import com.google.gson.Gson;
import com.inductiveautomation.ignition.gateway.dataroutes.RequestContext;
import com.inductiveautomation.ignition.gateway.dataroutes.RouteHandler;
import dev.bwdesigngroup.flint.common.protocol.methods.HealthResult;
import dev.bwdesigngroup.flint.gateway.FlintGatewayHook;
import javax.servlet.http.HttpServletResponse;

/**
 * Ignition 8.1 (javax) route handler for the unauthenticated {@code GET /health} endpoint. Lets a
 * client probe the gateway URL and discover version + capabilities.
 */
public class FlintHealthRouteHandler implements RouteHandler {

    private static final Gson GSON = new Gson();

    private final FlintGatewayHook hook;

    public FlintHealthRouteHandler(FlintGatewayHook hook) {
        this.hook = hook;
    }

    @Override
    public Object handle(RequestContext ctx, HttpServletResponse res) throws Exception {
        GatewayRpcDispatcher dispatcher = hook.getDispatcher();
        HealthResult health = dispatcher != null ? dispatcher.buildHealth() : new HealthResult();
        if (dispatcher == null) {
            health.setStatus("starting");
        }
        res.setStatus(200);
        res.setContentType("application/json");
        res.getWriter().write(GSON.toJson(health));
        res.getWriter().flush();
        return null;
    }
}

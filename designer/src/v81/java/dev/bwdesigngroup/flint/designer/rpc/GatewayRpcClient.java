package dev.bwdesigngroup.flint.designer.rpc;

import com.inductiveautomation.ignition.client.gateway_interface.ModuleRPCFactory;
import dev.bwdesigngroup.flint.common.FlintConstants;
import dev.bwdesigngroup.flint.common.rpc.FlintGatewayRpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client for calling Gateway-scope methods via Ignition's RPC mechanism (8.1). Uses
 * ModuleRPCFactory.create() to create a dynamic proxy.
 */
public class GatewayRpcClient {
    private static final Logger logger = LoggerFactory.getLogger("flint.Designer.RPC");

    private volatile FlintGatewayRpc gatewayRpc;

    /** Initializes the RPC client by creating a proxy to the Gateway RPC interface. */
    public void initialize() {
        try {
            gatewayRpc = ModuleRPCFactory.create(FlintConstants.MODULE_ID, FlintGatewayRpc.class);
            logger.info("Gateway RPC client initialized");
        } catch (Exception e) {
            logger.error("Failed to initialize Gateway RPC client", e);
        }
    }

    /**
     * Gets the RPC proxy, initializing on first use if needed.
     *
     * @return The FlintGatewayRpc proxy
     * @throws IllegalStateException if RPC client could not be initialized
     */
    public FlintGatewayRpc getRpc() {
        if (gatewayRpc == null) {
            initialize();
        }
        if (gatewayRpc == null) {
            throw new IllegalStateException(
                    "Gateway RPC not available - module may not be installed on Gateway");
        }
        return gatewayRpc;
    }
}

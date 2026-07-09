package dev.bwdesigngroup.flint.designer.rpc;

import com.inductiveautomation.ignition.client.gateway_interface.GatewayConnection;
import dev.bwdesigngroup.flint.common.FlintConstants;
import dev.bwdesigngroup.flint.common.rpc.FlintGatewayRpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client for calling Gateway-scope methods via Ignition's RPC mechanism (8.3+). Uses
 * GatewayConnection.getRpcInterface() with the ProtoRpcSerializer defined on the FlintGatewayRpc
 * interface.
 */
public class GatewayRpcClient {
    private static final Logger logger = LoggerFactory.getLogger("flint.Designer.RPC");
    private static final int DEFAULT_TIMEOUT_MS = 120_000;

    private volatile FlintGatewayRpc gatewayRpc;

    /** Initializes the RPC client by creating a proxy to the Gateway RPC interface. */
    public void initialize() {
        try {
            gatewayRpc =
                    GatewayConnection.getRpcInterface(
                            FlintGatewayRpc.SERIALIZER,
                            FlintConstants.MODULE_ID,
                            FlintGatewayRpc.class,
                            DEFAULT_TIMEOUT_MS);
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

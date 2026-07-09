package dev.bwdesigngroup.flint.designer.platform;

import dev.bwdesigngroup.flint.designer.rpc.GatewayRpcClient;

/** Factory for Ignition 8.1 platform implementations. */
public class PlatformFactory {

    private PlatformFactory() {}

    public static PlatformResources createPlatformResources() {
        return new V81PlatformResources();
    }

    public static GatewayRpcClient createGatewayRpcClient() {
        return new GatewayRpcClient();
    }
}

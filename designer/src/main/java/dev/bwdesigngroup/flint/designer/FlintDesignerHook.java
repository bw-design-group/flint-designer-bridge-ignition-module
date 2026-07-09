package dev.bwdesigngroup.flint.designer;

import com.inductiveautomation.ignition.client.gateway_interface.GatewayConnectionManager;
import com.inductiveautomation.ignition.client.gateway_interface.GatewayInterface;
import com.inductiveautomation.ignition.common.licensing.LicenseState;
import com.inductiveautomation.ignition.designer.model.AbstractDesignerModuleHook;
import com.inductiveautomation.ignition.designer.model.DesignerContext;
import dev.bwdesigngroup.flint.common.FlintConstants;
import dev.bwdesigngroup.flint.designer.listeners.ScriptChangeDetector;
import dev.bwdesigngroup.flint.designer.registry.DesignerRegistryManager;
import dev.bwdesigngroup.flint.designer.server.FlintWebSocketServer;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Designer hook for the Flint Designer Bridge module. Manages the WebSocket server and registry
 * file for VS Code discovery.
 */
public class FlintDesignerHook extends AbstractDesignerModuleHook {
    private static final Logger logger = LoggerFactory.getLogger("flint.Designer");

    private DesignerContext context;
    private DesignerRegistryManager registryManager;
    private FlintWebSocketServer webSocketServer;
    private ScriptChangeDetector scriptChangeDetector;

    @Override
    public void startup(DesignerContext context, LicenseState activationState) throws Exception {
        logger.info("Starting Flint Designer Bridge");
        this.context = context;

        // Initialize registry manager
        registryManager = new DesignerRegistryManager();
        if (!registryManager.initialize()) {
            logger.error("Failed to initialize registry manager - module will not function");
            return;
        }

        // Populate registry info from context
        populateRegistryInfo();

        // Try to start WebSocket server on each port in range until one succeeds
        // This eliminates the race condition of testing port separately from binding
        int port = -1;
        for (int tryPort = FlintConstants.PORT_RANGE_START;
                tryPort <= FlintConstants.PORT_RANGE_END;
                tryPort++) {
            try {
                InetSocketAddress address =
                        new InetSocketAddress(InetAddress.getLoopbackAddress(), tryPort);
                webSocketServer =
                        new FlintWebSocketServer(address, context, registryManager.getSecret());
                webSocketServer.startAndWait(2000); // Wait up to 2 seconds per port
                port = tryPort;
                break; // Successfully started
            } catch (Exception e) {
                // Port unavailable or startup failed, try next port
                logger.debug("Port {} unavailable: {}", tryPort, e.getMessage());
                webSocketServer = null;
            }
        }

        if (port == -1) {
            logger.error(
                    "Could not start WebSocket server on any port in range {}-{}",
                    FlintConstants.PORT_RANGE_START,
                    FlintConstants.PORT_RANGE_END);
            registryManager.shutdown();
            return;
        }

        // Update registry with port and write to file
        registryManager.setPort(port);
        registryManager.updateRegistry();

        // Start script change detector to notify VS Code of updates
        try {
            scriptChangeDetector = new ScriptChangeDetector(context, webSocketServer);
            scriptChangeDetector.start();
            logger.info("Started script change detector");
        } catch (Exception e) {
            logger.warn("Could not start script change detector: {}", e.getMessage());
            // Non-fatal - module will still work, just without push notifications
        }

        logger.info("Flint Designer Bridge started successfully - listening on port {}", port);
    }

    @Override
    public void shutdown() {
        logger.info("Shutting down Flint Designer Bridge");

        // Stop script change detector
        if (scriptChangeDetector != null) {
            try {
                scriptChangeDetector.stop();
                logger.info("Stopped script change detector");
            } catch (Exception e) {
                logger.warn("Could not stop script change detector: {}", e.getMessage());
            }
            scriptChangeDetector = null;
        }

        // Stop WebSocket server
        if (webSocketServer != null) {
            try {
                webSocketServer.stop(1000);
                logger.info("WebSocket server stopped");
            } catch (InterruptedException e) {
                logger.error("Error stopping WebSocket server", e);
                Thread.currentThread().interrupt();
            }
        }

        // Shutdown registry manager (releases lock and deletes file)
        if (registryManager != null) {
            registryManager.shutdown();
        }

        logger.info("Flint Designer Bridge shut down");
    }

    /** Populates registry info from the Designer context. */
    private void populateRegistryInfo() {
        // Gateway info from GatewayConnectionManager
        try {
            GatewayInterface gatewayInterface =
                    GatewayConnectionManager.getInstance().getGatewayInterface();
            // Convert to URI for portable parsing via toString()
            URI gatewayUri = new URI(gatewayInterface.getGatewayAddress().toString());
            String gatewayHost = gatewayUri.getHost();
            int gatewayPort = gatewayUri.getPort();
            if (gatewayPort == -1) {
                gatewayPort = "https".equalsIgnoreCase(gatewayUri.getScheme()) ? 443 : 80;
            }
            boolean ssl = "https".equalsIgnoreCase(gatewayUri.getScheme());
            String gatewayName = gatewayHost; // Use host as name for now
            registryManager.setGatewayInfo(gatewayHost, gatewayPort, ssl, gatewayName);
        } catch (Exception e) {
            logger.warn("Could not get gateway info: {}", e.getMessage());
            registryManager.setGatewayInfo("localhost", 8088, false, "Unknown");
        }

        // Project info
        try {
            String projectName = context.getProjectName();
            // Use projectName as title fallback - getProject().getTitle() is version-specific
            // and will be resolved at runtime by the correct SDK version
            String projectTitle = projectName;
            try {
                Object project = context.getProject();
                if (project != null) {
                    java.lang.reflect.Method getTitle = project.getClass().getMethod("getTitle");
                    Object title = getTitle.invoke(project);
                    if (title != null) {
                        projectTitle = title.toString();
                    }
                }
            } catch (Exception titleEx) {
                logger.debug(
                        "Could not get project title via reflection: {}", titleEx.getMessage());
            }
            registryManager.setProjectInfo(projectName, projectTitle);
        } catch (Exception e) {
            logger.warn("Could not get project info: {}", e.getMessage());
            registryManager.setProjectInfo("Unknown", "Unknown");
        }

        // User info - try to get from system properties or environment
        try {
            String username = System.getProperty("user.name", "unknown");
            registryManager.setUserInfo(username);
        } catch (Exception e) {
            logger.warn("Could not get user info: {}", e.getMessage());
            registryManager.setUserInfo("unknown");
        }

        // Version info - get from system properties set by Designer
        try {
            String designerVersion = System.getProperty("ignition.version", "8.3.x");
            registryManager.setDesignerVersion(designerVersion);
        } catch (Exception e) {
            logger.warn("Could not get designer version: {}", e.getMessage());
            registryManager.setDesignerVersion("8.1.x");
        }

        // Module version
        registryManager.setModuleVersion("0.12.0");

        // Capabilities - probe for CDP port
        int cdpPort = 0;
        try {
            URL cdpUrl = new URL("http://localhost:9222/json/version");
            HttpURLConnection cdpConn = (HttpURLConnection) cdpUrl.openConnection();
            cdpConn.setConnectTimeout(1000);
            cdpConn.setReadTimeout(1000);
            try {
                if (cdpConn.getResponseCode() == 200) {
                    cdpPort = 9222;
                    logger.info("CDP endpoint detected on port {}", cdpPort);
                }
            } finally {
                cdpConn.disconnect();
            }
        } catch (Exception e) {
            logger.debug("CDP endpoint not available: {}", e.getMessage());
        }
        registryManager.setCapabilities(true, true, cdpPort);
    }

    /** Gets the Designer context. */
    public DesignerContext getContext() {
        return context;
    }
}

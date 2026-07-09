package dev.bwdesigngroup.flint.designer.handlers;

import com.inductiveautomation.ignition.designer.IgnitionDesigner;
import dev.bwdesigngroup.flint.common.protocol.ErrorCodes;
import dev.bwdesigngroup.flint.common.protocol.JsonRpcRequest;
import dev.bwdesigngroup.flint.common.protocol.JsonRpcResponse;
import dev.bwdesigngroup.flint.common.protocol.methods.ProjectScanResult;
import dev.bwdesigngroup.flint.designer.platform.PlatformFactory;
import dev.bwdesigngroup.flint.designer.rpc.GatewayRpcClient;
import dev.bwdesigngroup.flint.designer.server.FlintWebSocketHandler;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for the project.scan method. Requests a project scan on the Gateway and refreshes the
 * Designer's project view.
 */
public class ProjectScanHandler implements MethodHandler {
    private static final Logger logger = LoggerFactory.getLogger("flint.Designer.ProjectScan");

    private final FlintWebSocketHandler handler;
    private final GatewayRpcClient gatewayRpcClient;

    public ProjectScanHandler(FlintWebSocketHandler handler) {
        this.handler = handler;
        this.gatewayRpcClient = PlatformFactory.createGatewayRpcClient();
        this.gatewayRpcClient.initialize();
    }

    @Override
    public JsonRpcResponse handle(JsonRpcRequest request) {
        Object id = request.getId();

        logger.info("Handling project scan request");

        try {
            // Step 1: Request Gateway to scan project files
            boolean gatewayScanSuccess = gatewayRpcClient.getRpc().requestProjectScan();
            if (gatewayScanSuccess) {
                logger.info("Gateway project scan completed successfully");
            } else {
                logger.warn("Gateway project scan returned false");
            }

            // Step 2: Refresh Designer's project view
            boolean designerRefreshSuccess = refreshDesignerProject();

            // Return combined result
            ProjectScanResult result =
                    ProjectScanResult.success(gatewayScanSuccess, designerRefreshSuccess);
            return JsonRpcResponse.success(result, id);

        } catch (Exception e) {
            logger.error("Project scan failed", e);
            return JsonRpcResponse.error(
                    ErrorCodes.INTERNAL_ERROR, "Project scan failed: " + e.getMessage(), id);
        }
    }

    /**
     * Refreshes the Designer's project view by calling updateProject on the EDT.
     *
     * @return true if refresh was successful
     */
    private boolean refreshDesignerProject() {
        try {
            // Get the IgnitionDesigner frame from the context
            Object frame = handler.getContext().getFrame();
            if (!(frame instanceof IgnitionDesigner)) {
                logger.warn("Designer frame is not an IgnitionDesigner instance");
                return false;
            }

            IgnitionDesigner designer = (IgnitionDesigner) frame;

            // Update project on the EDT (Swing Event Dispatch Thread)
            SwingUtilities.invokeAndWait(
                    () -> {
                        designer.updateProject();
                        logger.info("Designer project refresh completed");
                    });

            return true;
        } catch (Exception e) {
            logger.error("Failed to refresh Designer project", e);
            return false;
        }
    }
}

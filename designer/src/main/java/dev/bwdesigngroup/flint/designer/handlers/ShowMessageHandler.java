package dev.bwdesigngroup.flint.designer.handlers;

import dev.bwdesigngroup.flint.common.protocol.ErrorCodes;
import dev.bwdesigngroup.flint.common.protocol.JsonRpcRequest;
import dev.bwdesigngroup.flint.common.protocol.JsonRpcResponse;
import dev.bwdesigngroup.flint.common.protocol.methods.ShowMessageParams;
import dev.bwdesigngroup.flint.designer.server.FlintWebSocketHandler;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Handler for the showMessage method. Shows a message dialog in the Designer. */
public class ShowMessageHandler implements MethodHandler {
    private static final Logger logger = LoggerFactory.getLogger("flint.Designer.ShowMessage");

    private final FlintWebSocketHandler handler;

    public ShowMessageHandler(FlintWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public JsonRpcResponse handle(JsonRpcRequest request) {
        Object id = request.getId();

        // Parse params
        ShowMessageParams params;
        try {
            params = handler.getGson().fromJson(request.getParams(), ShowMessageParams.class);
        } catch (Exception e) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS, "Invalid params: " + e.getMessage(), id);
        }

        if (params == null || params.getMessage() == null || params.getMessage().isEmpty()) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS, "Missing required parameter: message", id);
        }

        String message = params.getMessage();
        String title = params.getTitle() != null ? params.getTitle() : "Message from Flint";

        logger.info("Showing message: {} (title: {})", message, title);

        // Show the message dialog on the Swing EDT
        SwingUtilities.invokeLater(
                () -> {
                    JOptionPane.showMessageDialog(
                            null, // Use Designer's frame if available
                            message,
                            title,
                            JOptionPane.INFORMATION_MESSAGE);
                });

        // Return success immediately (don't wait for dialog to close)
        return JsonRpcResponse.success(true, id);
    }
}

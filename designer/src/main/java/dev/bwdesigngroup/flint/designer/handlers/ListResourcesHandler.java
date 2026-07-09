package dev.bwdesigngroup.flint.designer.handlers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.inductiveautomation.ignition.designer.model.DesignerContext;
import dev.bwdesigngroup.flint.common.platform.ResourceInfo;
import dev.bwdesigngroup.flint.common.protocol.ErrorCodes;
import dev.bwdesigngroup.flint.common.protocol.JsonRpcRequest;
import dev.bwdesigngroup.flint.common.protocol.JsonRpcResponse;
import dev.bwdesigngroup.flint.designer.platform.PlatformResources;
import dev.bwdesigngroup.flint.designer.server.FlintWebSocketHandler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for the project.listResources method. Lists all resources in the project, optionally
 * filtered by resource type.
 */
public class ListResourcesHandler implements MethodHandler {
    private static final Logger logger = LoggerFactory.getLogger("flint.Designer.ListResources");

    private final FlintWebSocketHandler handler;
    private final PlatformResources platformResources;

    /** Maps Flint resource type IDs to [moduleId, typeId] pairs. */
    private static final Map<String, String[]> RESOURCE_TYPE_MAP = new HashMap<>();

    static {
        // Core Ignition resource types
        RESOURCE_TYPE_MAP.put("script-python", new String[] {"ignition", "script-python"});
        RESOURCE_TYPE_MAP.put("named-query", new String[] {"ignition", "named-query"});

        // Perspective resource types
        RESOURCE_TYPE_MAP.put(
                "perspective-view", new String[] {"com.inductiveautomation.perspective", "views"});
        RESOURCE_TYPE_MAP.put(
                "perspective-style",
                new String[] {"com.inductiveautomation.perspective", "style-classes"});
        RESOURCE_TYPE_MAP.put(
                "perspective-page-config",
                new String[] {"com.inductiveautomation.perspective", "page-config"});
        RESOURCE_TYPE_MAP.put(
                "perspective-session-props",
                new String[] {"com.inductiveautomation.perspective", "session-props"});
        RESOURCE_TYPE_MAP.put(
                "perspective-session-events",
                new String[] {"com.inductiveautomation.perspective", "session-events"});
        RESOURCE_TYPE_MAP.put(
                "perspective-general-props",
                new String[] {"com.inductiveautomation.perspective", "general-props"});

        // Vision resource types
        RESOURCE_TYPE_MAP.put("vision-window", new String[] {"fpmi", "window"});
        RESOURCE_TYPE_MAP.put("vision-template", new String[] {"fpmi", "template"});

        // Reporting resource types
        RESOURCE_TYPE_MAP.put(
                "report", new String[] {"com.inductiveautomation.reporting", "report"});
    }

    public ListResourcesHandler(FlintWebSocketHandler handler) {
        this.handler = handler;
        this.platformResources = handler.getPlatformResources();
    }

    @Override
    public JsonRpcResponse handle(JsonRpcRequest request) {
        Object id = request.getId();

        // Parse optional params
        String filterType = null;
        JsonElement paramsElement = request.getParams();
        if (paramsElement != null && paramsElement.isJsonObject()) {
            JsonObject params = paramsElement.getAsJsonObject();
            if (params.has("resourceType") && !params.get("resourceType").isJsonNull()) {
                filterType = params.get("resourceType").getAsString();
            }
        }

        logger.info(
                "Listing resources{}", filterType != null ? " (filter: " + filterType + ")" : "");

        try {
            DesignerContext context = handler.getContext();

            if (!platformResources.isProjectAvailable(context)) {
                logger.warn("No project available in Designer context");
                return JsonRpcResponse.error(
                        ErrorCodes.INTERNAL_ERROR, "No project available in Designer context", id);
            }

            List<Map<String, Object>> resources = new ArrayList<>();

            for (Map.Entry<String, String[]> entry : RESOURCE_TYPE_MAP.entrySet()) {
                String flintTypeId = entry.getKey();
                String[] moduleAndType = entry.getValue();

                // Apply filter if specified
                if (filterType != null && !filterType.equals(flintTypeId)) {
                    continue;
                }

                try {
                    List<ResourceInfo> projectResources =
                            platformResources.getResourcesOfType(
                                    context, moduleAndType[0], moduleAndType[1]);

                    for (ResourceInfo res : projectResources) {
                        Map<String, Object> resourceInfo = new HashMap<>();
                        resourceInfo.put("resourceType", flintTypeId);
                        resourceInfo.put("resourcePath", res.getPath());
                        resourceInfo.put("moduleId", res.getModuleId());
                        resourceInfo.put("typeId", res.getTypeId());
                        resources.add(resourceInfo);
                    }
                } catch (Exception e) {
                    logger.debug(
                            "Could not list resources of type {}: {}", flintTypeId, e.getMessage());
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("projectName", platformResources.getProjectTitle(context));
            result.put("resources", resources);
            result.put("count", resources.size());

            logger.info("Found {} resources", resources.size());
            return JsonRpcResponse.success(result, id);

        } catch (Exception e) {
            logger.error("Failed to list resources", e);
            return JsonRpcResponse.error(
                    ErrorCodes.INTERNAL_ERROR, "Failed to list resources: " + e.getMessage(), id);
        }
    }
}

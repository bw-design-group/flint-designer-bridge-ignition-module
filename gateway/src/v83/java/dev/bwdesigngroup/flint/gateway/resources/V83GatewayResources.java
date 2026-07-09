package dev.bwdesigngroup.flint.gateway.resources;

import com.inductiveautomation.ignition.common.ImmutableBytes;
import com.inductiveautomation.ignition.common.resourcecollection.ChangeOperation;
import com.inductiveautomation.ignition.common.resourcecollection.Resource;
import com.inductiveautomation.ignition.common.resourcecollection.ResourceCollection;
import com.inductiveautomation.ignition.common.resourcecollection.ResourceId;
import com.inductiveautomation.ignition.common.resourcecollection.ResourcePath;
import com.inductiveautomation.ignition.common.resourcecollection.ResourceType;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import com.inductiveautomation.ignition.gateway.project.ProjectManager;
import dev.bwdesigngroup.flint.common.platform.ResourceInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ignition 8.3 gateway resource store using the {@code resourcecollection} APIs. Reads go through
 * the runtime {@link ResourceCollection}; writes go through {@link ProjectManager#push} + {@code
 * requestScan()}.
 */
public class V83GatewayResources implements GatewayResourceStore {

    private static final Logger logger = LoggerFactory.getLogger("flint.Gateway.Resources.V83");

    private final GatewayContext context;

    public V83GatewayResources(GatewayContext context) {
        this.context = context;
    }

    private ProjectManager pm() {
        return context.getProjectManager();
    }

    private Optional<ResourceCollection> collection(String project) {
        try {
            return pm().find(project).map(rc -> (ResourceCollection) rc);
        } catch (Exception e) {
            logger.debug("Could not resolve project collection {}: {}", project, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public List<String> listProjectNames() {
        try {
            return pm().getNames();
        } catch (Exception e) {
            logger.debug("Could not list project names: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public boolean isProjectAvailable(String project) {
        return project != null && collection(project).isPresent();
    }

    @Override
    public String getProjectTitle(String project) {
        return collection(project).map(ResourceCollection::getTitle).orElse(project);
    }

    @Override
    public List<ResourceInfo> getResourcesOfType(String project, String moduleId, String typeId) {
        List<ResourceInfo> result = new ArrayList<>();
        Optional<ResourceCollection> rc = collection(project);
        if (!rc.isPresent()) {
            return result;
        }

        ResourceType resourceType = new ResourceType(moduleId, typeId);
        try {
            for (Resource resource : rc.get().getResourcesOfType(resourceType)) {
                ResourcePath resourcePath = resource.getResourcePath();
                if (resourcePath == null) {
                    continue;
                }
                Object pathObj = resourcePath.getPath();
                String pathStr = pathObj != null ? pathObj.toString() : null;
                if (pathStr == null || pathStr.isEmpty()) {
                    continue;
                }
                if (pathStr.startsWith("/")) {
                    pathStr = pathStr.substring(1);
                }
                result.add(
                        new ResourceInfo(
                                pathStr,
                                resourceType.moduleId(),
                                resourceType.typeId(),
                                resource,
                                resourcePath));
            }
        } catch (Exception e) {
            logger.debug("Could not list {}/{}: {}", moduleId, typeId, e.getMessage());
        }
        return result;
    }

    @Override
    public byte[] readResourceData(ResourceInfo resource, String dataKey) {
        try {
            Resource nativeResource = (Resource) resource.getNativeResource();
            Optional<ImmutableBytes> data = nativeResource.getData(dataKey);
            return data.map(ImmutableBytes::getBytes).orElse(null);
        } catch (Exception e) {
            logger.error("Failed to read resource data for key {}", dataKey, e);
            return null;
        }
    }

    @Override
    public byte[] readDefaultData(ResourceInfo resource) {
        try {
            Resource nativeResource = (Resource) resource.getNativeResource();
            Optional<ImmutableBytes> data = nativeResource.getData();
            return data.map(ImmutableBytes::getBytes).orElse(null);
        } catch (Exception e) {
            logger.error("Failed to read default resource data", e);
            return null;
        }
    }

    @Override
    public String writeResourceData(
            String project, ResourceInfo resource, String dataKey, byte[] data) {
        try {
            Resource existing = (Resource) resource.getNativeResource();
            Resource updated = existing.toBuilder().putData(dataKey, data).build();
            push(ChangeOperation.newModifyOp(updated, existing.getResourceSignature()));
            requestScan();
            return null;
        } catch (Exception e) {
            logger.error("Failed to write resource data", e);
            return "Failed to write resource: " + e.getMessage();
        }
    }

    @Override
    public String createResource(
            String project,
            ResourceInfo template,
            String moduleId,
            String typeId,
            String path,
            String dataKey,
            byte[] data) {
        try {
            Resource templateResource = (Resource) template.getNativeResource();
            ResourceType resourceType = new ResourceType(moduleId, typeId);
            ResourcePath newPath = new ResourcePath(resourceType, path);
            ResourceId newId = new ResourceId(project, newPath);

            Resource newResource =
                    templateResource.toBuilder()
                            .setResourceId(newId)
                            .setResourceCollectionName(project)
                            .setResourcePath(newPath)
                            .putData(dataKey, data)
                            .build();

            push(ChangeOperation.newCreateOp(newResource));
            requestScan();
            return null;
        } catch (Exception e) {
            logger.error("Failed to create resource {}", path, e);
            return "Failed to create resource: " + e.getMessage();
        }
    }

    @Override
    public String deleteResource(String project, ResourceInfo resource) {
        try {
            Resource existing = (Resource) resource.getNativeResource();
            push(ChangeOperation.newDeleteOp(existing.getResourceSignature()));
            requestScan();
            return null;
        } catch (Exception e) {
            logger.error("Failed to delete resource", e);
            return "Failed to delete resource: " + e.getMessage();
        }
    }

    private void push(ChangeOperation op) throws Exception {
        pm().push(Collections.singletonList(op)).get();
    }

    private void requestScan() {
        try {
            pm().requestScan().get();
        } catch (Exception e) {
            logger.debug(
                    "requestScan after push failed (push may already apply): {}", e.getMessage());
        }
    }
}

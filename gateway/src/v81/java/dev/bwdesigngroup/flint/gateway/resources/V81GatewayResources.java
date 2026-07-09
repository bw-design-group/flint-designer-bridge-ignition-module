package dev.bwdesigngroup.flint.gateway.resources;

import com.inductiveautomation.ignition.common.project.ChangeOperation;
import com.inductiveautomation.ignition.common.project.RuntimeProject;
import com.inductiveautomation.ignition.common.project.resource.ProjectResource;
import com.inductiveautomation.ignition.common.project.resource.ProjectResourceId;
import com.inductiveautomation.ignition.common.project.resource.ResourcePath;
import com.inductiveautomation.ignition.common.project.resource.ResourceType;
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
 * Ignition 8.1 gateway resource store using the {@code common.project} APIs. Reads go through the
 * runtime {@link RuntimeProject}; writes go through {@link ProjectManager#push} + {@code
 * requestScan()}.
 */
public class V81GatewayResources implements GatewayResourceStore {

    private static final Logger logger = LoggerFactory.getLogger("flint.Gateway.Resources.V81");

    private final GatewayContext context;

    public V81GatewayResources(GatewayContext context) {
        this.context = context;
    }

    private ProjectManager pm() {
        return context.getProjectManager();
    }

    private Optional<RuntimeProject> project(String project) {
        try {
            return pm().getProject(project);
        } catch (Exception e) {
            logger.debug("Could not resolve project {}: {}", project, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public List<String> listProjectNames() {
        try {
            return pm().getProjectNames();
        } catch (Exception e) {
            logger.debug("Could not list project names: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public boolean isProjectAvailable(String project) {
        return project != null && project(project).isPresent();
    }

    @Override
    public String getProjectTitle(String project) {
        return project(project).map(RuntimeProject::getName).orElse(project);
    }

    @Override
    public List<ResourceInfo> getResourcesOfType(String project, String moduleId, String typeId) {
        List<ResourceInfo> result = new ArrayList<>();
        Optional<RuntimeProject> rp = project(project);
        if (!rp.isPresent()) {
            return result;
        }

        ResourceType resourceType = new ResourceType(moduleId, typeId);
        try {
            for (ProjectResource resource : rp.get().getResourcesOfType(resourceType)) {
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
                                resourceType.getModuleId(),
                                resourceType.getTypeId(),
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
            ProjectResource nativeResource = (ProjectResource) resource.getNativeResource();
            return nativeResource.getData(dataKey);
        } catch (Exception e) {
            logger.error("Failed to read resource data for key {}", dataKey, e);
            return null;
        }
    }

    @Override
    public byte[] readDefaultData(ResourceInfo resource) {
        try {
            ProjectResource nativeResource = (ProjectResource) resource.getNativeResource();
            return nativeResource.getData();
        } catch (Exception e) {
            logger.error("Failed to read default resource data", e);
            return null;
        }
    }

    @Override
    public String writeResourceData(
            String project, ResourceInfo resource, String dataKey, byte[] data) {
        try {
            ProjectResource existing = (ProjectResource) resource.getNativeResource();
            ProjectResource updated = existing.toBuilder().putData(dataKey, data).build();
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
            ProjectResource templateResource = (ProjectResource) template.getNativeResource();
            ResourceType resourceType = new ResourceType(moduleId, typeId);
            ResourcePath newPath = new ResourcePath(resourceType, path);
            ProjectResourceId newId = new ProjectResourceId(project, newPath);

            ProjectResource newResource =
                    templateResource.toBuilder()
                            .setResourceId(newId)
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
            ProjectResource existing = (ProjectResource) resource.getNativeResource();
            push(ChangeOperation.newDeleteOp(existing.getResourceSignature()));
            requestScan();
            return null;
        } catch (Exception e) {
            logger.error("Failed to delete resource", e);
            return "Failed to delete resource: " + e.getMessage();
        }
    }

    private void push(ChangeOperation op) throws Exception {
        pm().push(Collections.singletonList(op));
    }

    private void requestScan() {
        try {
            pm().requestScan().get();
        } catch (Exception e) {
            logger.debug("requestScan after push failed: {}", e.getMessage());
        }
    }
}

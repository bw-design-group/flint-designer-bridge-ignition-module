package dev.bwdesigngroup.flint.designer.platform;

import com.inductiveautomation.ignition.common.project.Project;
import com.inductiveautomation.ignition.common.project.resource.ProjectResource;
import com.inductiveautomation.ignition.common.project.resource.ResourcePath;
import com.inductiveautomation.ignition.common.project.resource.ResourceType;
import com.inductiveautomation.ignition.designer.model.DesignerContext;
import dev.bwdesigngroup.flint.common.platform.ResourceInfo;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ignition 8.1 implementation of PlatformResources. Uses Project, ProjectResource, and direct
 * byte[] APIs.
 */
public class V81PlatformResources implements PlatformResources {
    private static final Logger logger = LoggerFactory.getLogger("flint.Designer.Platform.V81");

    @Override
    public String getProjectTitle(DesignerContext context) {
        Project project = context.getProject();
        return project != null ? project.getTitle() : null;
    }

    @Override
    public boolean isProjectAvailable(DesignerContext context) {
        return context.getProject() != null;
    }

    @Override
    public List<ResourceInfo> getResourcesOfType(
            DesignerContext context, String moduleId, String typeId) {
        List<ResourceInfo> result = new ArrayList<>();
        Project project = context.getProject();
        if (project == null) {
            return result;
        }

        ResourceType resourceType = new ResourceType(moduleId, typeId);
        try {
            List<ProjectResource> resources = project.getResourcesOfType(resourceType);
            for (ProjectResource resource : resources) {
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
            logger.debug(
                    "Could not list resources of type {}/{}: {}", moduleId, typeId, e.getMessage());
        }

        return result;
    }

    @Override
    public byte[] readResourceData(ResourceInfo resource, String dataKey) {
        try {
            ProjectResource nativeResource = (ProjectResource) resource.getNativeResource();
            // In 8.1, ProjectResource may have getData(String key) returning byte[] directly
            // or getData() for default data. Try reflection for keyed access.
            try {
                Method getData = nativeResource.getClass().getMethod("getData", String.class);
                Object data = getData.invoke(nativeResource, dataKey);
                if (data instanceof byte[]) {
                    return (byte[]) data;
                }
            } catch (NoSuchMethodException e) {
                // Fall through to default getData
            }

            // Fallback: try getData() and assume it returns the content
            return readDefaultData(resource);
        } catch (Exception e) {
            logger.error("Failed to read resource data for key {}", dataKey, e);
            return null;
        }
    }

    @Override
    public byte[] readDefaultData(ResourceInfo resource) {
        try {
            ProjectResource nativeResource = (ProjectResource) resource.getNativeResource();
            Method getData = nativeResource.getClass().getMethod("getData");
            Object data = getData.invoke(nativeResource);
            if (data instanceof byte[]) {
                return (byte[]) data;
            }
            return null;
        } catch (Exception e) {
            logger.error("Failed to read default resource data", e);
            return null;
        }
    }

    @Override
    public boolean writeResourceData(
            DesignerContext context, ResourceInfo resource, String dataKey, byte[] data) {
        try {
            ProjectResource nativeResource = (ProjectResource) resource.getNativeResource();
            Project project = context.getProject();
            if (project == null) {
                return false;
            }

            // Try setData(String key, byte[] data) via reflection
            try {
                Method setData =
                        nativeResource.getClass().getMethod("setData", String.class, byte[].class);
                setData.invoke(nativeResource, dataKey, data);
                return true;
            } catch (NoSuchMethodException e) {
                logger.debug("setData(String, byte[]) not available");
            }

            // Try setData(byte[] data) for default data
            try {
                Method setData = nativeResource.getClass().getMethod("setData", byte[].class);
                setData.invoke(nativeResource, (Object) data);
                return true;
            } catch (NoSuchMethodException e) {
                logger.debug("setData(byte[]) not available");
            }

            // Try modifyResource on project
            try {
                ResourcePath resourcePath = (ResourcePath) resource.getNativeResourcePath();
                Method modifyResource =
                        project.getClass()
                                .getMethod(
                                        "modifyResource",
                                        ResourcePath.class,
                                        java.util.function.Consumer.class);
                java.util.function.Consumer<Object> consumer =
                        builder -> {
                            try {
                                Method putData =
                                        builder.getClass()
                                                .getMethod("putData", String.class, byte[].class);
                                putData.invoke(builder, dataKey, data);
                            } catch (Exception ex) {
                                logger.error("Failed to putData in builder", ex);
                            }
                        };
                modifyResource.invoke(project, resourcePath, consumer);
                return true;
            } catch (NoSuchMethodException e) {
                logger.debug("modifyResource not available on project");
            }

            return false;
        } catch (Exception e) {
            logger.error("Failed to write resource data", e);
            return false;
        }
    }

    @Override
    public String createResource(
            DesignerContext context,
            ResourceInfo template,
            String moduleId,
            String typeId,
            String path,
            String dataKey,
            byte[] data) {
        try {
            Project project = context.getProject();
            if (project == null) {
                return "No project available";
            }

            ProjectResource templateResource = (ProjectResource) template.getNativeResource();
            ResourceType resourceType = new ResourceType(moduleId, typeId);
            ResourcePath newResourcePath = new ResourcePath(resourceType, path);

            // Try toBuilder pattern (may be available in some 8.1 versions)
            try {
                Method toBuilder = templateResource.getClass().getMethod("toBuilder");
                Object builder = toBuilder.invoke(templateResource);

                Method setResourcePath =
                        builder.getClass().getMethod("setResourcePath", ResourcePath.class);
                builder = setResourcePath.invoke(builder, newResourcePath);

                try {
                    Method clearData = builder.getClass().getMethod("clearData");
                    builder = clearData.invoke(builder);
                } catch (NoSuchMethodException e) {
                    // OK
                }

                try {
                    Method putData =
                            builder.getClass().getMethod("putData", String.class, byte[].class);
                    builder = putData.invoke(builder, dataKey, data);
                } catch (NoSuchMethodException e) {
                    return "putData not available on builder";
                }

                Method build = builder.getClass().getMethod("build");
                Object newResource = build.invoke(builder);

                // Add to project
                boolean created = tryCreateOnProject(project, newResource);
                return created ? null : "No create method found on project";
            } catch (NoSuchMethodException e) {
                logger.debug("toBuilder() not available, trying alternative creation");
            }

            return "Could not create resource - toBuilder() not available on template";
        } catch (Exception e) {
            logger.error("Failed to create resource", e);
            return "Failed to create resource: " + e.getMessage();
        }
    }

    @Override
    public String deleteResource(DesignerContext context, ResourceInfo resource) {
        try {
            Project project = context.getProject();
            if (project == null) {
                return "No project available";
            }

            ResourcePath resourcePath = (ResourcePath) resource.getNativeResourcePath();
            ProjectResource nativeResource = (ProjectResource) resource.getNativeResource();

            String[] methodNames = {"deleteResource", "removeResource"};
            for (String methodName : methodNames) {
                try {
                    Method m = project.getClass().getMethod(methodName, ResourcePath.class);
                    m.invoke(project, resourcePath);
                    return null;
                } catch (NoSuchMethodException e) {
                    // Try next
                }
            }

            for (String methodName : methodNames) {
                try {
                    Method m = project.getClass().getMethod(methodName, ProjectResource.class);
                    m.invoke(project, nativeResource);
                    return null;
                } catch (NoSuchMethodException e) {
                    // Try next
                }
            }

            return "No delete method found on project";
        } catch (Exception e) {
            logger.error("Failed to delete resource", e);
            return "Failed to delete resource: " + e.getMessage();
        }
    }

    @Override
    public Class<?> getResourcePathClass() {
        return ResourcePath.class;
    }

    @Override
    public boolean isMatchingResourcePath(Object obj, String moduleId, String typeId, String path) {
        if (!(obj instanceof ResourcePath)) {
            return false;
        }
        ResourcePath rp = (ResourcePath) obj;
        ResourceType expectedType = new ResourceType(moduleId, typeId);
        ResourcePath expected = new ResourcePath(expectedType, path);
        return rp.equals(expected);
    }

    private boolean tryCreateOnProject(Object project, Object resource) {
        String[] methodNames = {"createResource", "createOrModify", "putResource", "addResource"};
        for (String methodName : methodNames) {
            try {
                Method m = project.getClass().getMethod(methodName, resource.getClass());
                m.invoke(project, resource);
                return true;
            } catch (NoSuchMethodException e) {
                // Try next
            } catch (Exception e) {
                logger.debug("{} failed: {}", methodName, e.getMessage());
            }
        }

        // Try with Object parameter
        for (String methodName : methodNames) {
            for (Class<?> paramType : new Class<?>[] {Object.class}) {
                try {
                    Method m = project.getClass().getMethod(methodName, paramType);
                    m.invoke(project, resource);
                    return true;
                } catch (Exception e) {
                    // Try next
                }
            }
        }

        return false;
    }
}

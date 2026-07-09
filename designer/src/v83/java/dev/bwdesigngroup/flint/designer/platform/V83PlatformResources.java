package dev.bwdesigngroup.flint.designer.platform;

import com.inductiveautomation.ignition.common.ImmutableBytes;
import com.inductiveautomation.ignition.common.resourcecollection.Resource;
import com.inductiveautomation.ignition.common.resourcecollection.ResourceCollection;
import com.inductiveautomation.ignition.common.resourcecollection.ResourcePath;
import com.inductiveautomation.ignition.common.resourcecollection.ResourceType;
import com.inductiveautomation.ignition.designer.model.DesignerContext;
import dev.bwdesigngroup.flint.common.platform.ResourceInfo;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ignition 8.3+ implementation of PlatformResources. Uses ResourceCollection, Resource, and
 * ImmutableBytes APIs.
 */
public class V83PlatformResources implements PlatformResources {
    private static final Logger logger = LoggerFactory.getLogger("flint.Designer.Platform.V83");

    @Override
    public String getProjectTitle(DesignerContext context) {
        ResourceCollection project = context.getProject();
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
        ResourceCollection project = context.getProject();
        if (project == null) {
            return result;
        }

        ResourceType resourceType = new ResourceType(moduleId, typeId);
        try {
            List<Resource> resources = project.getResourcesOfType(resourceType);
            for (Resource resource : resources) {
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
            logger.debug(
                    "Could not list resources of type {}/{}: {}", moduleId, typeId, e.getMessage());
        }

        return result;
    }

    @Override
    public byte[] readResourceData(ResourceInfo resource, String dataKey) {
        try {
            Resource nativeResource = (Resource) resource.getNativeResource();
            Optional<ImmutableBytes> dataOpt = nativeResource.getData(dataKey);
            return dataOpt.map(ImmutableBytes::getBytes).orElse(null);
        } catch (Exception e) {
            logger.error("Failed to read resource data for key {}", dataKey, e);
            return null;
        }
    }

    @Override
    public byte[] readDefaultData(ResourceInfo resource) {
        try {
            Resource nativeResource = (Resource) resource.getNativeResource();
            Optional<ImmutableBytes> dataOpt = nativeResource.getData();
            return dataOpt.map(ImmutableBytes::getBytes).orElse(null);
        } catch (Exception e) {
            logger.error("Failed to read default resource data", e);
            return null;
        }
    }

    @Override
    public boolean writeResourceData(
            DesignerContext context, ResourceInfo resource, String dataKey, byte[] data) {
        try {
            Resource nativeResource = (Resource) resource.getNativeResource();
            ResourcePath resourcePath = (ResourcePath) resource.getNativeResourcePath();
            ResourceCollection project = context.getProject();
            if (project == null) {
                return false;
            }

            // Try modifyResource(ResourcePath, Consumer<ResourceBuilder>)
            try {
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
                            } catch (Exception e) {
                                logger.error("Failed to putData in builder", e);
                            }
                        };
                modifyResource.invoke(project, resourcePath, consumer);
                return true;
            } catch (NoSuchMethodException e) {
                logger.debug("modifyResource(ResourcePath, Consumer) not available");
            }

            // Fallback: createOrModify(Resource) with toBuilder
            try {
                Resource newResource = nativeResource.toBuilder().putData(dataKey, data).build();
                Method createOrModify =
                        project.getClass().getMethod("createOrModify", Resource.class);
                createOrModify.invoke(project, newResource);
                return true;
            } catch (Exception e) {
                logger.debug("createOrModify fallback failed: {}", e.getMessage());
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
            ResourceCollection project = context.getProject();
            if (project == null) {
                return "No project available";
            }

            Resource templateResource = (Resource) template.getNativeResource();
            ResourceType resourceType = new ResourceType(moduleId, typeId);
            ResourcePath newResourcePath = new ResourcePath(resourceType, path);

            // Use toBuilder pattern
            Method toBuilder = templateResource.getClass().getMethod("toBuilder");
            Object builder = toBuilder.invoke(templateResource);

            // setResourcePath
            Method setResourcePath =
                    builder.getClass().getMethod("setResourcePath", ResourcePath.class);
            builder = setResourcePath.invoke(builder, newResourcePath);

            // clearData
            try {
                Method clearData = builder.getClass().getMethod("clearData");
                builder = clearData.invoke(builder);
            } catch (NoSuchMethodException e) {
                logger.debug("clearData() not available on builder");
            }

            // putData
            boolean dataSet = false;
            try {
                Method putData =
                        builder.getClass().getMethod("putData", String.class, byte[].class);
                builder = putData.invoke(builder, dataKey, data);
                dataSet = true;
            } catch (NoSuchMethodException e) {
                logger.debug("putData(String, byte[]) not available");
            }

            if (!dataSet) {
                try {
                    Object immutableBytes = createImmutableBytes(data);
                    if (immutableBytes != null) {
                        Method putData =
                                builder.getClass()
                                        .getMethod("putData", String.class, ImmutableBytes.class);
                        builder = putData.invoke(builder, dataKey, immutableBytes);
                        dataSet = true;
                    }
                } catch (NoSuchMethodException e) {
                    logger.debug("putData(String, ImmutableBytes) not available");
                }
            }

            if (!dataSet) {
                return "Could not set data on builder";
            }

            // build
            Method build = builder.getClass().getMethod("build");
            Resource newResource = (Resource) build.invoke(builder);

            // Create in project
            boolean created = false;
            try {
                Method createResource =
                        project.getClass().getMethod("createResource", Resource.class);
                createResource.invoke(project, newResource);
                created = true;
            } catch (NoSuchMethodException e) {
                logger.debug("createResource(Resource) not available");
            }

            if (!created) {
                try {
                    Method createOrModify =
                            project.getClass().getMethod("createOrModify", Resource.class);
                    createOrModify.invoke(project, newResource);
                    created = true;
                } catch (NoSuchMethodException e) {
                    logger.debug("createOrModify(Resource) not available");
                }
            }

            return created ? null : "No create method found on project";
        } catch (Exception e) {
            logger.error("Failed to create resource", e);
            return "Failed to create resource: " + e.getMessage();
        }
    }

    @Override
    public String deleteResource(DesignerContext context, ResourceInfo resource) {
        try {
            ResourceCollection project = context.getProject();
            if (project == null) {
                return "No project available";
            }

            ResourcePath resourcePath = (ResourcePath) resource.getNativeResourcePath();
            Resource nativeResource = (Resource) resource.getNativeResource();

            // Try various delete methods
            String[] pathMethods = {"deleteResource", "removeResource"};
            for (String methodName : pathMethods) {
                try {
                    Method m = project.getClass().getMethod(methodName, ResourcePath.class);
                    m.invoke(project, resourcePath);
                    return null;
                } catch (NoSuchMethodException e) {
                    // Try next
                }
            }

            for (String methodName : pathMethods) {
                try {
                    Method m = project.getClass().getMethod(methodName, Resource.class);
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

    private Object createImmutableBytes(byte[] data) {
        try {
            return ImmutableBytes.class.getConstructor(byte[].class).newInstance((Object) data);
        } catch (Exception e) {
            // Try static factory methods
        }
        for (String methodName : new String[] {"of", "from", "wrap", "copyOf"}) {
            try {
                Method factory = ImmutableBytes.class.getMethod(methodName, byte[].class);
                return factory.invoke(null, (Object) data);
            } catch (Exception e) {
                // Try next
            }
        }
        return null;
    }
}

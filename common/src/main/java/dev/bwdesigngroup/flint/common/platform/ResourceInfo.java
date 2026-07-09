package dev.bwdesigngroup.flint.common.platform;

/**
 * Platform-neutral representation of a project resource. Wraps the version-specific SDK resource
 * objects (Resource in 8.3, ProjectResource in 8.1) so that shared handler and gateway code doesn't
 * need to import version-specific types.
 */
public class ResourceInfo {
    private final String path;
    private final String moduleId;
    private final String typeId;
    private final Object nativeResource;
    private final Object nativeResourcePath;

    public ResourceInfo(
            String path,
            String moduleId,
            String typeId,
            Object nativeResource,
            Object nativeResourcePath) {
        this.path = path;
        this.moduleId = moduleId;
        this.typeId = typeId;
        this.nativeResource = nativeResource;
        this.nativeResourcePath = nativeResourcePath;
    }

    public String getPath() {
        return path;
    }

    public String getModuleId() {
        return moduleId;
    }

    public String getTypeId() {
        return typeId;
    }

    /**
     * Returns the native SDK resource object (Resource or ProjectResource). Should only be used by
     * platform resource implementations.
     */
    public Object getNativeResource() {
        return nativeResource;
    }

    /**
     * Returns the native SDK ResourcePath object. Should only be used by platform resource
     * implementations and resource navigation code.
     */
    public Object getNativeResourcePath() {
        return nativeResourcePath;
    }
}

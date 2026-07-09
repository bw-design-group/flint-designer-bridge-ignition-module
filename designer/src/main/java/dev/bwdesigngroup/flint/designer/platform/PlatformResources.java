package dev.bwdesigngroup.flint.designer.platform;

import com.inductiveautomation.ignition.designer.model.DesignerContext;
import dev.bwdesigngroup.flint.common.platform.ResourceInfo;
import java.util.List;

/**
 * Abstraction layer for Ignition resource APIs that differ between 8.1 and 8.3.
 *
 * <p>In 8.3, resources use {@code ResourceCollection}, {@code Resource}, and {@code
 * ImmutableBytes}. In 8.1, resources use {@code Project}, {@code ProjectResource}, and direct
 * {@code byte[]}.
 *
 * <p>This interface hides those differences so handlers can work with both versions.
 */
public interface PlatformResources {

    /** Gets the project title. */
    String getProjectTitle(DesignerContext context);

    /** Checks if a project is available in the designer context. */
    boolean isProjectAvailable(DesignerContext context);

    /**
     * Lists all resources of the given type.
     *
     * @param context The designer context
     * @param moduleId The module ID (e.g., "ignition", "com.inductiveautomation.perspective")
     * @param typeId The type ID (e.g., "script-python", "views")
     * @return List of ResourceInfo objects, never null
     */
    List<ResourceInfo> getResourcesOfType(DesignerContext context, String moduleId, String typeId);

    /**
     * Reads resource data by key.
     *
     * @param resource The resource to read from
     * @param dataKey The data key (e.g., "view.json")
     * @return The data bytes, or null if not found
     */
    byte[] readResourceData(ResourceInfo resource, String dataKey);

    /**
     * Reads the default data from a resource.
     *
     * @param resource The resource to read from
     * @return The data bytes, or null if not found
     */
    byte[] readDefaultData(ResourceInfo resource);

    /**
     * Writes data to a resource.
     *
     * @param context The designer context
     * @param resource The resource to write to
     * @param dataKey The data key (e.g., "view.json")
     * @param data The data bytes to write
     * @return true if successful
     */
    boolean writeResourceData(
            DesignerContext context, ResourceInfo resource, String dataKey, byte[] data);

    /**
     * Creates a new resource using an existing resource as a template.
     *
     * @param context The designer context
     * @param template An existing resource to use as a builder template
     * @param moduleId The module ID for the new resource
     * @param typeId The type ID for the new resource
     * @param path The path for the new resource
     * @param dataKey The data key to set
     * @param data The data bytes
     * @return null on success, error message on failure
     */
    String createResource(
            DesignerContext context,
            ResourceInfo template,
            String moduleId,
            String typeId,
            String path,
            String dataKey,
            byte[] data);

    /**
     * Deletes a resource.
     *
     * @param context The designer context
     * @param resource The resource to delete
     * @return null on success, error message on failure
     */
    String deleteResource(DesignerContext context, ResourceInfo resource);

    /**
     * Returns the Class object for the platform's ResourcePath type. Used by handlers that need to
     * call workspace.open(ResourcePath) via reflection.
     */
    Class<?> getResourcePathClass();

    /**
     * Checks if an object is a ResourcePath that matches the given module/type/path. Used by
     * OpenResourceHandler when searching the project browser tree.
     */
    boolean isMatchingResourcePath(Object obj, String moduleId, String typeId, String path);
}

package dev.bwdesigngroup.flint.gateway.resources;

import dev.bwdesigngroup.flint.common.platform.ResourceInfo;
import java.util.List;

/**
 * Version-specific gateway-scope project resource access. Reads go through the runtime project;
 * writes go through the project manager's {@code push(List<ChangeOperation>)} + {@code
 * requestScan()} mechanism (8.1 {@code common.project} vs 8.3 {@code common.resourcecollection}).
 *
 * <p>All write methods return {@code null} on success or a human-readable error message on failure.
 * All operations are project-scoped (the gateway hosts multiple projects), unlike the Designer's
 * single open project.
 */
public interface GatewayResourceStore {

    /** Lists the names of all projects on the gateway. */
    List<String> listProjectNames();

    /** True if a project with the given name exists. */
    boolean isProjectAvailable(String project);

    /** The project's display title, or the name if unavailable. */
    String getProjectTitle(String project);

    /** Lists all resources of the given module/type within a project. */
    List<ResourceInfo> getResourcesOfType(String project, String moduleId, String typeId);

    /** Reads a data key (e.g. "view.json") from a resource, or null. */
    byte[] readResourceData(ResourceInfo resource, String dataKey);

    /** Reads the resource's default/primary data, or null. */
    byte[] readDefaultData(ResourceInfo resource);

    /** Writes a data key to an existing resource. Returns null on success or an error message. */
    String writeResourceData(String project, ResourceInfo resource, String dataKey, byte[] data);

    /**
     * Creates a new resource, cloning attributes from an existing template resource in the same
     * project. Returns null on success or an error message.
     */
    String createResource(
            String project,
            ResourceInfo template,
            String moduleId,
            String typeId,
            String path,
            String dataKey,
            byte[] data);

    /** Deletes a resource. Returns null on success or an error message. */
    String deleteResource(String project, ResourceInfo resource);
}

package dev.bwdesigngroup.flint.common.protocol.methods;

/**
 * Parameters for the designer.openResource method. Opens a resource in the Ignition Designer's
 * editor.
 */
public class OpenResourceParams {
    private String resourceType; // e.g., "script-python", "perspective-view"
    private String resourcePath; // e.g., "MyFolder/MyScript"
    private String categoryId; // Optional, for categorized resources
    private String projectName; // Optional, for validation

    public OpenResourceParams() {}

    public OpenResourceParams(String resourceType, String resourcePath) {
        this.resourceType = resourceType;
        this.resourcePath = resourcePath;
    }

    public OpenResourceParams(String resourceType, String resourcePath, String categoryId) {
        this.resourceType = resourceType;
        this.resourcePath = resourcePath;
        this.categoryId = categoryId;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getResourcePath() {
        return resourcePath;
    }

    public void setResourcePath(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }
}

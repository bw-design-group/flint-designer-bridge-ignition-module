package dev.bwdesigngroup.flint.common.protocol.methods;

import java.util.List;

/** Result for the authenticate method. */
public class AuthenticateResult {
    private boolean success;
    private String designerVersion;
    private String moduleVersion;
    private String projectName;
    private String gatewayName;
    private List<String> capabilities;

    public AuthenticateResult() {}

    public AuthenticateResult(
            boolean success,
            String designerVersion,
            String moduleVersion,
            String projectName,
            String gatewayName) {
        this.success = success;
        this.designerVersion = designerVersion;
        this.moduleVersion = moduleVersion;
        this.projectName = projectName;
        this.gatewayName = gatewayName;
    }

    public AuthenticateResult(
            boolean success,
            String designerVersion,
            String moduleVersion,
            String projectName,
            String gatewayName,
            List<String> capabilities) {
        this(success, designerVersion, moduleVersion, projectName, gatewayName);
        this.capabilities = capabilities;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getDesignerVersion() {
        return designerVersion;
    }

    public void setDesignerVersion(String designerVersion) {
        this.designerVersion = designerVersion;
    }

    public String getModuleVersion() {
        return moduleVersion;
    }

    public void setModuleVersion(String moduleVersion) {
        this.moduleVersion = moduleVersion;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getGatewayName() {
        return gatewayName;
    }

    public void setGatewayName(String gatewayName) {
        this.gatewayName = gatewayName;
    }

    public List<String> getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(List<String> capabilities) {
        this.capabilities = capabilities;
    }
}

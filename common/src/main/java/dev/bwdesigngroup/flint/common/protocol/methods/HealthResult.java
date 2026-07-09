package dev.bwdesigngroup.flint.common.protocol.methods;

import java.util.List;

/**
 * Response for the unauthenticated {@code GET /health} endpoint on the Gateway HTTP transport. Lets
 * a client (such as the Flint VS Code extension) probe a gateway URL, learn its Ignition/module
 * version, and enable or hide features based on advertised capabilities.
 */
public class HealthResult {

    private String status = "ok";
    private String module;
    private String moduleVersion;
    private String ignitionVersion;
    private String scope = "gateway";
    private List<String> authSchemes;
    private List<String> capabilities;
    private List<String> unsupported;
    private List<String> projects;
    private String rpcPath;

    public HealthResult() {}

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }

    public String getModuleVersion() {
        return moduleVersion;
    }

    public void setModuleVersion(String moduleVersion) {
        this.moduleVersion = moduleVersion;
    }

    public String getIgnitionVersion() {
        return ignitionVersion;
    }

    public void setIgnitionVersion(String ignitionVersion) {
        this.ignitionVersion = ignitionVersion;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public List<String> getAuthSchemes() {
        return authSchemes;
    }

    public void setAuthSchemes(List<String> authSchemes) {
        this.authSchemes = authSchemes;
    }

    public List<String> getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(List<String> capabilities) {
        this.capabilities = capabilities;
    }

    public List<String> getUnsupported() {
        return unsupported;
    }

    public void setUnsupported(List<String> unsupported) {
        this.unsupported = unsupported;
    }

    public List<String> getProjects() {
        return projects;
    }

    public void setProjects(List<String> projects) {
        this.projects = projects;
    }

    public String getRpcPath() {
        return rpcPath;
    }

    public void setRpcPath(String rpcPath) {
        this.rpcPath = rpcPath;
    }
}

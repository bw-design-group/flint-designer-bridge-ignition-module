package dev.bwdesigngroup.flint.common.registry;

import java.time.Instant;

/** Information about a running Designer instance, written to the registry file. */
public class DesignerRegistryInfo {
    private long pid;
    private int port;
    private String startTime;
    private GatewayInfo gateway;
    private ProjectInfo project;
    private UserInfo user;
    private String designerVersion;
    private String moduleVersion;
    private Capabilities capabilities;
    private String secret;

    public DesignerRegistryInfo() {
        this.startTime = Instant.now().toString();
    }

    // Nested classes for structured info
    public static class GatewayInfo {
        private String host;
        private int port;
        private boolean ssl;
        private String name;

        public GatewayInfo() {}

        public GatewayInfo(String host, int port, boolean ssl, String name) {
            this.host = host;
            this.port = port;
            this.ssl = ssl;
            this.name = name;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public boolean isSsl() {
            return ssl;
        }

        public void setSsl(boolean ssl) {
            this.ssl = ssl;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class ProjectInfo {
        private String name;
        private String title;

        public ProjectInfo() {}

        public ProjectInfo(String name, String title) {
            this.name = name;
            this.title = title;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }
    }

    public static class UserInfo {
        private String username;

        public UserInfo() {}

        public UserInfo(String username) {
            this.username = username;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }
    }

    public static class Capabilities {
        private boolean scriptExecution;
        private boolean gatewayScope;
        private int cdpPort;

        public Capabilities() {
            this.scriptExecution = true;
            this.gatewayScope = true;
            this.cdpPort = 0;
        }

        public Capabilities(boolean scriptExecution, boolean gatewayScope) {
            this.scriptExecution = scriptExecution;
            this.gatewayScope = gatewayScope;
            this.cdpPort = 0;
        }

        public Capabilities(boolean scriptExecution, boolean gatewayScope, int cdpPort) {
            this.scriptExecution = scriptExecution;
            this.gatewayScope = gatewayScope;
            this.cdpPort = cdpPort;
        }

        public boolean isScriptExecution() {
            return scriptExecution;
        }

        public void setScriptExecution(boolean scriptExecution) {
            this.scriptExecution = scriptExecution;
        }

        public boolean isGatewayScope() {
            return gatewayScope;
        }

        public void setGatewayScope(boolean gatewayScope) {
            this.gatewayScope = gatewayScope;
        }

        public int getCdpPort() {
            return cdpPort;
        }

        public void setCdpPort(int cdpPort) {
            this.cdpPort = cdpPort;
        }
    }

    // Getters and Setters
    public long getPid() {
        return pid;
    }

    public void setPid(long pid) {
        this.pid = pid;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public GatewayInfo getGateway() {
        return gateway;
    }

    public void setGateway(GatewayInfo gateway) {
        this.gateway = gateway;
    }

    public ProjectInfo getProject() {
        return project;
    }

    public void setProject(ProjectInfo project) {
        this.project = project;
    }

    public UserInfo getUser() {
        return user;
    }

    public void setUser(UserInfo user) {
        this.user = user;
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

    public Capabilities getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(Capabilities capabilities) {
        this.capabilities = capabilities;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }
}

package dev.bwdesigngroup.flint.common.protocol.methods;

/** Result for the ping method. */
public class PingResult {
    private String status;
    private long timestamp;
    private String projectName;
    private boolean authenticated;

    public PingResult() {}

    public PingResult(String status, long timestamp, String projectName, boolean authenticated) {
        this.status = status;
        this.timestamp = timestamp;
        this.projectName = projectName;
        this.authenticated = authenticated;
    }

    public static PingResult ok(String projectName, boolean authenticated) {
        return new PingResult("ok", System.currentTimeMillis(), projectName, authenticated);
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
    }
}

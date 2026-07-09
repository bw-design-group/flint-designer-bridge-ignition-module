package dev.bwdesigngroup.flint.common.protocol.methods;

/** Result for the project.scan method. */
public class ProjectScanResult {
    private boolean success;
    private boolean gatewayScanSuccess;
    private boolean designerRefreshSuccess;
    private long timestamp;

    public ProjectScanResult() {}

    public ProjectScanResult(
            boolean success,
            boolean gatewayScanSuccess,
            boolean designerRefreshSuccess,
            long timestamp) {
        this.success = success;
        this.gatewayScanSuccess = gatewayScanSuccess;
        this.designerRefreshSuccess = designerRefreshSuccess;
        this.timestamp = timestamp;
    }

    public static ProjectScanResult success(
            boolean gatewayScanSuccess, boolean designerRefreshSuccess) {
        return new ProjectScanResult(
                gatewayScanSuccess && designerRefreshSuccess,
                gatewayScanSuccess,
                designerRefreshSuccess,
                System.currentTimeMillis());
    }

    public static ProjectScanResult failure() {
        return new ProjectScanResult(false, false, false, System.currentTimeMillis());
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public boolean isGatewayScanSuccess() {
        return gatewayScanSuccess;
    }

    public void setGatewayScanSuccess(boolean gatewayScanSuccess) {
        this.gatewayScanSuccess = gatewayScanSuccess;
    }

    public boolean isDesignerRefreshSuccess() {
        return designerRefreshSuccess;
    }

    public void setDesignerRefreshSuccess(boolean designerRefreshSuccess) {
        this.designerRefreshSuccess = designerRefreshSuccess;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}

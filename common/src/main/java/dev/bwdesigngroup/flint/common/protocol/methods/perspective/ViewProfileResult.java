package dev.bwdesigngroup.flint.common.protocol.methods.perspective;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Result of runtime profiling a live Perspective view. */
public class ViewProfileResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private String viewPath;
    private String viewInstanceId;
    private int totalComponentCount;
    private int maxTreeDepth;
    private int totalBindingCount;
    private Map<String, Integer> bindingsByType;
    private int pendingBindingCount;
    private int resolvedBindingCount;
    private int errorBindingCount;
    private long totalPropertySizeBytes;
    private long profilingDurationMs;
    private List<ComponentProfile> components;
    private List<ProfileWarning> warnings;

    public ViewProfileResult() {
        this.bindingsByType = new HashMap<>();
        this.components = new ArrayList<>();
        this.warnings = new ArrayList<>();
    }

    public String getViewPath() {
        return viewPath;
    }

    public void setViewPath(String viewPath) {
        this.viewPath = viewPath;
    }

    public String getViewInstanceId() {
        return viewInstanceId;
    }

    public void setViewInstanceId(String viewInstanceId) {
        this.viewInstanceId = viewInstanceId;
    }

    public int getTotalComponentCount() {
        return totalComponentCount;
    }

    public void setTotalComponentCount(int totalComponentCount) {
        this.totalComponentCount = totalComponentCount;
    }

    public int getMaxTreeDepth() {
        return maxTreeDepth;
    }

    public void setMaxTreeDepth(int maxTreeDepth) {
        this.maxTreeDepth = maxTreeDepth;
    }

    public int getTotalBindingCount() {
        return totalBindingCount;
    }

    public void setTotalBindingCount(int totalBindingCount) {
        this.totalBindingCount = totalBindingCount;
    }

    public Map<String, Integer> getBindingsByType() {
        return bindingsByType;
    }

    public void setBindingsByType(Map<String, Integer> bindingsByType) {
        this.bindingsByType = bindingsByType;
    }

    public int getPendingBindingCount() {
        return pendingBindingCount;
    }

    public void setPendingBindingCount(int pendingBindingCount) {
        this.pendingBindingCount = pendingBindingCount;
    }

    public int getResolvedBindingCount() {
        return resolvedBindingCount;
    }

    public void setResolvedBindingCount(int resolvedBindingCount) {
        this.resolvedBindingCount = resolvedBindingCount;
    }

    public int getErrorBindingCount() {
        return errorBindingCount;
    }

    public void setErrorBindingCount(int errorBindingCount) {
        this.errorBindingCount = errorBindingCount;
    }

    public long getTotalPropertySizeBytes() {
        return totalPropertySizeBytes;
    }

    public void setTotalPropertySizeBytes(long totalPropertySizeBytes) {
        this.totalPropertySizeBytes = totalPropertySizeBytes;
    }

    public long getProfilingDurationMs() {
        return profilingDurationMs;
    }

    public void setProfilingDurationMs(long profilingDurationMs) {
        this.profilingDurationMs = profilingDurationMs;
    }

    public List<ComponentProfile> getComponents() {
        return components;
    }

    public void setComponents(List<ComponentProfile> components) {
        this.components = components;
    }

    public void addComponent(ComponentProfile component) {
        this.components.add(component);
    }

    public List<ProfileWarning> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<ProfileWarning> warnings) {
        this.warnings = warnings;
    }

    public void addWarning(ProfileWarning warning) {
        this.warnings.add(warning);
    }
}

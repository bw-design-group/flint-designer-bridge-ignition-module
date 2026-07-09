package dev.bwdesigngroup.flint.common.protocol.methods.perspective;

import java.io.Serializable;

/** Runtime profile data for a single binding. */
public class BindingProfile implements Serializable {
    private static final long serialVersionUID = 1L;

    private String propertyPath;
    private String bindingType;
    private String state; // pending, good, bad, stale, unknown
    private boolean hasScriptTransform;
    private int transformCount;
    private String lastError;
    private String valueHash;

    public BindingProfile() {}

    public String getPropertyPath() {
        return propertyPath;
    }

    public void setPropertyPath(String propertyPath) {
        this.propertyPath = propertyPath;
    }

    public String getBindingType() {
        return bindingType;
    }

    public void setBindingType(String bindingType) {
        this.bindingType = bindingType;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public boolean hasScriptTransform() {
        return hasScriptTransform;
    }

    public void setHasScriptTransform(boolean hasScriptTransform) {
        this.hasScriptTransform = hasScriptTransform;
    }

    public int getTransformCount() {
        return transformCount;
    }

    public void setTransformCount(int transformCount) {
        this.transformCount = transformCount;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public String getValueHash() {
        return valueHash;
    }

    public void setValueHash(String valueHash) {
        this.valueHash = valueHash;
    }
}

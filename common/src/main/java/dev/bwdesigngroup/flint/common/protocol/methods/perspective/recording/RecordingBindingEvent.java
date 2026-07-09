package dev.bwdesigngroup.flint.common.protocol.methods.perspective.recording;

import java.io.Serializable;

/** A single binding state transition captured during a recording. */
public class RecordingBindingEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    private long timestampMs;
    private long relativeMs;
    private String componentPath;
    private String componentType;
    private String propertyPath;
    private String bindingType;
    private String previousState;
    private String newState;
    private String lastError;
    private boolean baseline;

    public RecordingBindingEvent() {}

    public long getTimestampMs() {
        return timestampMs;
    }

    public void setTimestampMs(long timestampMs) {
        this.timestampMs = timestampMs;
    }

    public long getRelativeMs() {
        return relativeMs;
    }

    public void setRelativeMs(long relativeMs) {
        this.relativeMs = relativeMs;
    }

    public String getComponentPath() {
        return componentPath;
    }

    public void setComponentPath(String componentPath) {
        this.componentPath = componentPath;
    }

    public String getComponentType() {
        return componentType;
    }

    public void setComponentType(String componentType) {
        this.componentType = componentType;
    }

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

    public String getPreviousState() {
        return previousState;
    }

    public void setPreviousState(String previousState) {
        this.previousState = previousState;
    }

    public String getNewState() {
        return newState;
    }

    public void setNewState(String newState) {
        this.newState = newState;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public boolean isBaseline() {
        return baseline;
    }

    public void setBaseline(boolean baseline) {
        this.baseline = baseline;
    }
}

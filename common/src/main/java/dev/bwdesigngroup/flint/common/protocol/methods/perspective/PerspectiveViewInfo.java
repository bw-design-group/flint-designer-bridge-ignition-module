package dev.bwdesigngroup.flint.common.protocol.methods.perspective;

import java.io.Serializable;

/** Information about a view instance within a Perspective page. */
public class PerspectiveViewInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    private String viewInstanceId;
    private String viewPath;
    private int componentCount;
    private String rootComponentType;

    public PerspectiveViewInfo() {}

    public PerspectiveViewInfo(
            String viewInstanceId, String viewPath, int componentCount, String rootComponentType) {
        this.viewInstanceId = viewInstanceId;
        this.viewPath = viewPath;
        this.componentCount = componentCount;
        this.rootComponentType = rootComponentType;
    }

    public String getViewInstanceId() {
        return viewInstanceId;
    }

    public void setViewInstanceId(String viewInstanceId) {
        this.viewInstanceId = viewInstanceId;
    }

    public String getViewPath() {
        return viewPath;
    }

    public void setViewPath(String viewPath) {
        this.viewPath = viewPath;
    }

    public int getComponentCount() {
        return componentCount;
    }

    public void setComponentCount(int componentCount) {
        this.componentCount = componentCount;
    }

    public String getRootComponentType() {
        return rootComponentType;
    }

    public void setRootComponentType(String rootComponentType) {
        this.rootComponentType = rootComponentType;
    }
}

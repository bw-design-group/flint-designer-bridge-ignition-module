package dev.bwdesigngroup.flint.common.protocol.methods.perspective;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Result for listing components within a Perspective view. */
public class PerspectiveListComponentsResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private List<PerspectiveComponentInfo> components;

    public PerspectiveListComponentsResult() {
        this.components = new ArrayList<>();
    }

    public PerspectiveListComponentsResult(List<PerspectiveComponentInfo> components) {
        this.components = components;
    }

    public List<PerspectiveComponentInfo> getComponents() {
        return components;
    }

    public void setComponents(List<PerspectiveComponentInfo> components) {
        this.components = components;
    }
}

package dev.bwdesigngroup.flint.common.protocol.methods.perspective;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Information about a component within a Perspective view. Includes hierarchical children for
 * building component trees.
 */
public class PerspectiveComponentInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    private String path;
    private String type;
    private String name;
    private List<PerspectiveComponentInfo> children;
    private boolean hasScripts;

    public PerspectiveComponentInfo() {
        this.children = new ArrayList<>();
    }

    public PerspectiveComponentInfo(String path, String type, String name, boolean hasScripts) {
        this.path = path;
        this.type = type;
        this.name = name;
        this.hasScripts = hasScripts;
        this.children = new ArrayList<>();
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<PerspectiveComponentInfo> getChildren() {
        return children;
    }

    public void setChildren(List<PerspectiveComponentInfo> children) {
        this.children = children;
    }

    public void addChild(PerspectiveComponentInfo child) {
        this.children.add(child);
    }

    public boolean hasScripts() {
        return hasScripts;
    }

    public void setHasScripts(boolean hasScripts) {
        this.hasScripts = hasScripts;
    }
}

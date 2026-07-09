package dev.bwdesigngroup.flint.common.protocol.methods.perspective;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Runtime profile data for a single Perspective component. */
public class ComponentProfile implements Serializable {
    private static final long serialVersionUID = 1L;

    private String path;
    private String type;
    private String name;
    private int bindingCount;
    private List<BindingProfile> bindings;
    private long propsSizeBytes;
    private long customSizeBytes;
    private int childCount;

    public ComponentProfile() {
        this.bindings = new ArrayList<>();
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

    public int getBindingCount() {
        return bindingCount;
    }

    public void setBindingCount(int bindingCount) {
        this.bindingCount = bindingCount;
    }

    public List<BindingProfile> getBindings() {
        return bindings;
    }

    public void setBindings(List<BindingProfile> bindings) {
        this.bindings = bindings;
    }

    public void addBinding(BindingProfile binding) {
        this.bindings.add(binding);
    }

    public long getPropsSizeBytes() {
        return propsSizeBytes;
    }

    public void setPropsSizeBytes(long propsSizeBytes) {
        this.propsSizeBytes = propsSizeBytes;
    }

    public long getCustomSizeBytes() {
        return customSizeBytes;
    }

    public void setCustomSizeBytes(long customSizeBytes) {
        this.customSizeBytes = customSizeBytes;
    }

    public int getChildCount() {
        return childCount;
    }

    public void setChildCount(int childCount) {
        this.childCount = childCount;
    }
}

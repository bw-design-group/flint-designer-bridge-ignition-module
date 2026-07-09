package dev.bwdesigngroup.flint.common.protocol.methods.tags;

import java.io.Serializable;

/** Result for the tags.getConfig method. Returns the full configuration JSON for a tag. */
public class TagGetConfigResult implements Serializable {
    private static final long serialVersionUID = 1L;
    private String path;
    private String config;

    public TagGetConfigResult() {}

    public TagGetConfigResult(String path, String config) {
        this.path = path;
        this.config = config;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getConfig() {
        return config;
    }

    public void setConfig(String config) {
        this.config = config;
    }
}

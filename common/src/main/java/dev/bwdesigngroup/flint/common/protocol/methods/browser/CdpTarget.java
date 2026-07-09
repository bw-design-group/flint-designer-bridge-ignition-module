package dev.bwdesigngroup.flint.common.protocol.methods.browser;

/** Represents a CDP (Chrome DevTools Protocol) target in Designer's JxBrowser engine. */
public class CdpTarget {
    private String id;
    private String title;
    private String url;
    private String wsDebuggerUrl;
    private String type;
    private String viewPath;

    public CdpTarget() {}

    public CdpTarget(
            String id,
            String title,
            String url,
            String wsDebuggerUrl,
            String type,
            String viewPath) {
        this.id = id;
        this.title = title;
        this.url = url;
        this.wsDebuggerUrl = wsDebuggerUrl;
        this.type = type;
        this.viewPath = viewPath;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getWsDebuggerUrl() {
        return wsDebuggerUrl;
    }

    public void setWsDebuggerUrl(String wsDebuggerUrl) {
        this.wsDebuggerUrl = wsDebuggerUrl;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getViewPath() {
        return viewPath;
    }

    public void setViewPath(String viewPath) {
        this.viewPath = viewPath;
    }
}

package dev.bwdesigngroup.flint.common.protocol.methods.perspective;

import java.io.Serializable;

/** Information about a page within a Perspective session. */
public class PerspectivePageInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    private String pageId;
    private String primaryViewPath;
    private int viewCount;

    public PerspectivePageInfo() {}

    public PerspectivePageInfo(String pageId, String primaryViewPath, int viewCount) {
        this.pageId = pageId;
        this.primaryViewPath = primaryViewPath;
        this.viewCount = viewCount;
    }

    public String getPageId() {
        return pageId;
    }

    public void setPageId(String pageId) {
        this.pageId = pageId;
    }

    public String getPrimaryViewPath() {
        return primaryViewPath;
    }

    public void setPrimaryViewPath(String primaryViewPath) {
        this.primaryViewPath = primaryViewPath;
    }

    public int getViewCount() {
        return viewCount;
    }

    public void setViewCount(int viewCount) {
        this.viewCount = viewCount;
    }
}

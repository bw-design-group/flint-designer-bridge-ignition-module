package dev.bwdesigngroup.flint.common.protocol.methods.perspective;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Result for listing pages within a Perspective session. */
public class PerspectiveListPagesResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private List<PerspectivePageInfo> pages;

    public PerspectiveListPagesResult() {
        this.pages = new ArrayList<>();
    }

    public PerspectiveListPagesResult(List<PerspectivePageInfo> pages) {
        this.pages = pages;
    }

    public List<PerspectivePageInfo> getPages() {
        return pages;
    }

    public void setPages(List<PerspectivePageInfo> pages) {
        this.pages = pages;
    }
}

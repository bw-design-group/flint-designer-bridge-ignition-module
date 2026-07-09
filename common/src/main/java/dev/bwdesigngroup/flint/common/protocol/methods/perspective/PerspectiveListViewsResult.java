package dev.bwdesigngroup.flint.common.protocol.methods.perspective;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Result for listing views within a Perspective page. */
public class PerspectiveListViewsResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private List<PerspectiveViewInfo> views;

    public PerspectiveListViewsResult() {
        this.views = new ArrayList<>();
    }

    public PerspectiveListViewsResult(List<PerspectiveViewInfo> views) {
        this.views = views;
    }

    public List<PerspectiveViewInfo> getViews() {
        return views;
    }

    public void setViews(List<PerspectiveViewInfo> views) {
        this.views = views;
    }
}

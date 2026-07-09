package dev.bwdesigngroup.flint.common.protocol.methods.perspective;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Result for listing active Perspective sessions. */
public class PerspectiveListSessionsResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private List<PerspectiveSessionInfo> sessions;

    public PerspectiveListSessionsResult() {
        this.sessions = new ArrayList<>();
    }

    public PerspectiveListSessionsResult(List<PerspectiveSessionInfo> sessions) {
        this.sessions = sessions;
    }

    public List<PerspectiveSessionInfo> getSessions() {
        return sessions;
    }

    public void setSessions(List<PerspectiveSessionInfo> sessions) {
        this.sessions = sessions;
    }
}

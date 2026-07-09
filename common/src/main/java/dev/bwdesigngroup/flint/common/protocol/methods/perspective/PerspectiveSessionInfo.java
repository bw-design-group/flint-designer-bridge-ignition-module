package dev.bwdesigngroup.flint.common.protocol.methods.perspective;

import java.io.Serializable;

/**
 * Information about an active Perspective session. Used for session discovery in the VS Code
 * extension.
 */
public class PerspectiveSessionInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    private String sessionId;
    private String userName;
    private String projectName;
    private int pageCount;
    private int viewCount;
    private long startTime;
    private String userAgent;
    private String sessionType; // "browser" or "designer"
    private String displayName; // Human-readable display name

    public PerspectiveSessionInfo() {}

    public PerspectiveSessionInfo(
            String sessionId,
            String userName,
            String projectName,
            int pageCount,
            int viewCount,
            long startTime,
            String userAgent) {
        this(
                sessionId,
                userName,
                projectName,
                pageCount,
                viewCount,
                startTime,
                userAgent,
                "browser",
                null);
    }

    public PerspectiveSessionInfo(
            String sessionId,
            String userName,
            String projectName,
            int pageCount,
            int viewCount,
            long startTime,
            String userAgent,
            String sessionType,
            String displayName) {
        this.sessionId = sessionId;
        this.userName = userName;
        this.projectName = projectName;
        this.pageCount = pageCount;
        this.viewCount = viewCount;
        this.startTime = startTime;
        this.userAgent = userAgent;
        this.sessionType = sessionType;
        this.displayName = displayName;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public int getPageCount() {
        return pageCount;
    }

    public void setPageCount(int pageCount) {
        this.pageCount = pageCount;
    }

    public int getViewCount() {
        return viewCount;
    }

    public void setViewCount(int viewCount) {
        this.viewCount = viewCount;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getSessionType() {
        return sessionType;
    }

    public void setSessionType(String sessionType) {
        this.sessionType = sessionType;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}

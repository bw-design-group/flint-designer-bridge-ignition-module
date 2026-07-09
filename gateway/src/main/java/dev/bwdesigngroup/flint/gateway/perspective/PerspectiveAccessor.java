package dev.bwdesigngroup.flint.gateway.perspective;

import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import com.inductiveautomation.perspective.gateway.api.Page;
import com.inductiveautomation.perspective.gateway.api.PerspectiveContext;
import com.inductiveautomation.perspective.gateway.api.Session;
import com.inductiveautomation.perspective.gateway.model.ComponentModel;
import com.inductiveautomation.perspective.gateway.model.PageModel;
import com.inductiveautomation.perspective.gateway.model.ViewModel;
import com.inductiveautomation.perspective.gateway.session.InternalSession;
import com.inductiveautomation.perspective.gateway.session.PerspectiveSessionMonitor;
import dev.bwdesigngroup.flint.common.protocol.methods.perspective.PerspectiveCompletionResult;
import dev.bwdesigngroup.flint.common.protocol.methods.perspective.PerspectiveComponentInfo;
import dev.bwdesigngroup.flint.common.protocol.methods.perspective.PerspectiveListComponentsResult;
import dev.bwdesigngroup.flint.common.protocol.methods.perspective.PerspectiveListPagesResult;
import dev.bwdesigngroup.flint.common.protocol.methods.perspective.PerspectiveListSessionsResult;
import dev.bwdesigngroup.flint.common.protocol.methods.perspective.PerspectiveListViewsResult;
import dev.bwdesigngroup.flint.common.protocol.methods.perspective.PerspectivePageInfo;
import dev.bwdesigngroup.flint.common.protocol.methods.perspective.PerspectiveSessionInfo;
import dev.bwdesigngroup.flint.common.protocol.methods.perspective.PerspectiveViewInfo;
import dev.bwdesigngroup.flint.common.protocol.methods.perspective.ViewProfileResult;
import dev.bwdesigngroup.flint.common.protocol.methods.perspective.recording.RecordingEventBatch;
import dev.bwdesigngroup.flint.common.protocol.methods.perspective.recording.StartRecordingResult;
import dev.bwdesigngroup.flint.common.protocol.methods.perspective.recording.StopRecordingResult;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.python.core.Py;
import org.python.core.PyObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Internal class that directly uses Perspective SDK classes. This class is loaded lazily to avoid
 * NoClassDefFoundError when Perspective isn't available. DO NOT reference this class directly from
 * code that runs at module startup!
 */
class PerspectiveAccessor {
    private static final Logger logger = LoggerFactory.getLogger("flint.Gateway.Perspective");

    private final GatewayContext gatewayContext;
    private final Map<String, BindingRecorder> activeRecorders = new ConcurrentHashMap<>();

    PerspectiveAccessor(GatewayContext gatewayContext) {
        this.gatewayContext = gatewayContext;
    }

    /** Checks if Perspective is fully available. */
    boolean isPerspectiveContextAvailable() {
        try {
            PerspectiveContext perspectiveContext = PerspectiveContext.get(gatewayContext);
            if (perspectiveContext != null) {
                PerspectiveSessionMonitor monitor = perspectiveContext.getSessionMonitor();
                return monitor != null;
            }
            return false;
        } catch (Exception e) {
            logger.debug("Error checking Perspective context: {}", e.getMessage());
            return false;
        }
    }

    /** Lists all active Perspective sessions. */
    PerspectiveListSessionsResult listSessions() {
        List<PerspectiveSessionInfo> sessions = new ArrayList<>();

        try {
            PerspectiveContext perspectiveContext = PerspectiveContext.get(gatewayContext);
            if (perspectiveContext == null) {
                return new PerspectiveListSessionsResult(sessions);
            }

            PerspectiveSessionMonitor sessionMonitor = perspectiveContext.getSessionMonitor();
            if (sessionMonitor == null) {
                return new PerspectiveListSessionsResult(sessions);
            }

            // Access the sessionsById field directly using reflection
            try {
                java.lang.reflect.Field sessionsByIdField =
                        sessionMonitor.getClass().getDeclaredField("sessionsById");
                sessionsByIdField.setAccessible(true);
                Object sessionsMap = sessionsByIdField.get(sessionMonitor);

                if (sessionsMap instanceof Map) {
                    Map<?, ?> sessionMap = (Map<?, ?>) sessionsMap;
                    logger.debug("Found {} sessions in sessionsById map", sessionMap.size());

                    for (Object value : sessionMap.values()) {
                        if (value instanceof Session) {
                            Session session = (Session) value;
                            logger.debug(
                                    "Processing session: {} (project: {})",
                                    session.getSessionId(),
                                    session.getProjectName());
                            PerspectiveSessionInfo info = createSessionInfo(session);
                            sessions.add(info);
                        }
                    }
                } else {
                    logger.warn(
                            "sessionsById is not a Map: {}",
                            sessionsMap != null ? sessionsMap.getClass().getName() : "null");
                }
            } catch (NoSuchFieldException e) {
                // Fallback to getSessionInfos (may not work on all SDK versions)
                logger.debug("sessionsById field not found, using fallback");
                List<com.inductiveautomation.perspective.gateway.session.PerspectiveSessionInfo>
                        sessionInfos = sessionMonitor.getSessionInfos();

                for (com.inductiveautomation.perspective.gateway.session.PerspectiveSessionInfo
                        sdkInfo : sessionInfos) {
                    try {
                        PerspectiveSessionInfo info =
                                createSessionInfoFromSdkInfo(sdkInfo, sessionMonitor);
                        if (info != null) {
                            sessions.add(info);
                        }
                    } catch (Exception ex) {
                        logger.debug("Error processing session info: {}", ex.getMessage());
                    }
                }
            } catch (IllegalAccessException e) {
                logger.warn("Cannot access sessionsById field: {}", e.getMessage());
            }

            logger.debug("Returning {} Perspective sessions", sessions.size());
        } catch (Exception e) {
            logger.error("Error listing Perspective sessions", e);
        }

        return new PerspectiveListSessionsResult(sessions);
    }

    /** Gets pages for a specific session. */
    PerspectiveListPagesResult getSessionPages(String sessionId) {
        List<PerspectivePageInfo> pages = new ArrayList<>();

        try {
            Session session = findSession(sessionId);
            if (session == null) {
                logger.warn("Session not found: {}", sessionId);
                return new PerspectiveListPagesResult(pages);
            }

            List<? extends Page> pageList = session.getPages();
            for (Page page : pageList) {
                try {
                    // Skip the session-props pseudo-page (Designer-specific)
                    String pageId = page.getId();
                    if ("session-props".equals(pageId)) {
                        continue;
                    }

                    PerspectivePageInfo pageInfo = createPageInfo(page);
                    pages.add(pageInfo);
                } catch (Exception e) {
                    logger.warn("Error processing page: {}", e.getMessage());
                }
            }

            logger.debug("Found {} pages in session {}", pages.size(), sessionId);
        } catch (Exception e) {
            logger.error("Error getting session pages", e);
        }

        return new PerspectiveListPagesResult(pages);
    }

    /** Gets views for a specific page. */
    PerspectiveListViewsResult getPageViews(String sessionId, String pageId) {
        List<PerspectiveViewInfo> views = new ArrayList<>();

        try {
            Session session = findSession(sessionId);
            if (session == null) {
                logger.warn("Session not found: {}", sessionId);
                return new PerspectiveListViewsResult(views);
            }

            Optional<? extends Page> pageOpt = session.findPage(pageId);
            if (!pageOpt.isPresent()) {
                logger.warn("Page not found: {} in session {}", pageId, sessionId);
                return new PerspectiveListViewsResult(views);
            }

            Page page = pageOpt.get();
            List<ViewModel> viewModels = page.getViews();

            for (ViewModel view : viewModels) {
                try {
                    PerspectiveViewInfo viewInfo = createViewInfo(view);
                    views.add(viewInfo);
                } catch (Exception e) {
                    logger.warn("Error processing view: {}", e.getMessage());
                }
            }

            logger.debug(
                    "Found {} views on page {} in session {}", views.size(), pageId, sessionId);
        } catch (Exception e) {
            logger.error("Error getting page views", e);
        }

        return new PerspectiveListViewsResult(views);
    }

    /** Gets the component tree for a specific view. */
    PerspectiveListComponentsResult getViewComponents(
            String sessionId, String pageId, String viewInstanceId) {
        List<PerspectiveComponentInfo> components = new ArrayList<>();

        try {
            ViewModel view = findView(sessionId, pageId, viewInstanceId);
            if (view == null) {
                logger.warn(
                        "View not found: {} on page {} in session {}",
                        viewInstanceId,
                        pageId,
                        sessionId);
                return new PerspectiveListComponentsResult(components);
            }

            ComponentModel rootComponent = view.getRootContainer();
            if (rootComponent != null) {
                PerspectiveComponentInfo rootInfo = buildComponentTree(rootComponent);
                components.add(rootInfo);
            }

            logger.debug(
                    "Built component tree for view {} with {} top-level components",
                    viewInstanceId,
                    components.size());
        } catch (Exception e) {
            logger.error("Error getting view components", e);
        }

        return new PerspectiveListComponentsResult(components);
    }

    /** Profiles a live Perspective view for performance metrics. */
    ViewProfileResult profileView(String sessionId, String pageId, String viewInstanceId) {
        ViewProfileResult result = new ViewProfileResult();

        try {
            ViewModel view = findView(sessionId, pageId, viewInstanceId);
            if (view == null) {
                logger.warn(
                        "View not found for profiling: {} on page {} in session {}",
                        viewInstanceId,
                        pageId,
                        sessionId);
                return result;
            }

            String viewPath = getViewPath(view);
            PerspectiveProfiler profiler = new PerspectiveProfiler();
            result = profiler.profileView(view, viewPath);

            logger.debug(
                    "Profiled view {} with {} components, {} bindings",
                    viewPath,
                    result.getTotalComponentCount(),
                    result.getTotalBindingCount());
        } catch (Exception e) {
            logger.error("Error profiling view", e);
        }

        return result;
    }

    // ==================== Recording Methods ====================

    /**
     * Starts a binding recording session for all views on a page. If viewInstanceId is provided,
     * its path is used for display; otherwise the page's primary (root) view path is used.
     */
    StartRecordingResult startRecording(
            String sessionId,
            String pageId,
            String viewInstanceId,
            int pollIntervalMs,
            int maxDurationMs,
            boolean autoStopOnAllResolved,
            int autoStopDelayMs) {

        try {
            // Collect ALL views on the page (includes embedded views)
            Session session = findSession(sessionId);
            if (session == null) {
                return StartRecordingResult.failure("Session not found: " + sessionId);
            }

            Optional<? extends Page> pageOpt = session.findPage(pageId);
            if (!pageOpt.isPresent()) {
                return StartRecordingResult.failure(
                        "Page not found: " + pageId + " in session " + sessionId);
            }

            Page page = pageOpt.get();
            List<ViewModel> initialViews = new ArrayList<>(page.getViews());

            // Determine the display path: use specified view if given, otherwise page's root view
            String viewPath;
            if (viewInstanceId != null && !viewInstanceId.isEmpty()) {
                ViewModel selectedView = findView(sessionId, pageId, viewInstanceId);
                if (selectedView != null) {
                    viewPath = getViewPath(selectedView);
                } else {
                    // Fallback: view not found, use root view path
                    viewPath = findRootViewPath(initialViews);
                }
            } else {
                viewPath = findRootViewPath(initialViews);
            }

            String recordingId = UUID.randomUUID().toString();

            // Pass a supplier so the recorder re-queries views each poll cycle,
            // picking up newly instantiated embedded views (FlexRepeater children, etc.)
            BindingRecorder recorder =
                    new BindingRecorder(
                            () -> new ArrayList<>(page.getViews()),
                            viewPath,
                            pollIntervalMs,
                            maxDurationMs,
                            autoStopOnAllResolved,
                            autoStopDelayMs);
            activeRecorders.put(recordingId, recorder);
            recorder.start();

            logger.info(
                    "Started recording {} for view {} ({} bindings across {} views)",
                    recordingId,
                    viewPath,
                    recorder.getTotalCount(),
                    initialViews.size());

            return StartRecordingResult.success(
                    recordingId,
                    viewPath,
                    recorder.getPendingCount(),
                    recorder.getResolvedCount(),
                    recorder.getErrorCount(),
                    recorder.getTotalCount());

        } catch (Exception e) {
            logger.error("Error starting recording", e);
            return StartRecordingResult.failure("Error starting recording: " + e.getMessage());
        }
    }

    /** Stops an active binding recording session. */
    StopRecordingResult stopRecording(String recordingId) {
        BindingRecorder recorder = activeRecorders.remove(recordingId);
        if (recorder == null) {
            return StopRecordingResult.failure("Recording not found: " + recordingId);
        }

        recorder.stop("manual");
        long durationMs = System.currentTimeMillis() - recorder.getStartTimeMs();

        logger.info(
                "Stopped recording {} after {}ms ({} events, {} polls)",
                recordingId,
                durationMs,
                recorder.getTotalEventsRecorded(),
                recorder.getTotalPollCount());

        return StopRecordingResult.success(
                durationMs, recorder.getTotalEventsRecorded(), recorder.getTotalPollCount());
    }

    /** Polls for binding state transition events from an active recording. */
    RecordingEventBatch pollRecordingEvents(String recordingId) {
        BindingRecorder recorder = activeRecorders.get(recordingId);
        if (recorder == null) {
            RecordingEventBatch batch = new RecordingEventBatch();
            batch.setRecordingId(recordingId);
            batch.setComplete(true);
            batch.setCompletionReason("notFound");
            return batch;
        }

        RecordingEventBatch batch = recorder.pollEvents();
        batch.setRecordingId(recordingId);

        // Clean up completed recordings
        if (recorder.isComplete()) {
            activeRecorders.remove(recordingId);
        }

        return batch;
    }

    /** Finds a session by ID. */
    Session findSession(String sessionId) {
        try {
            PerspectiveContext perspectiveContext = PerspectiveContext.get(gatewayContext);
            if (perspectiveContext == null) {
                return null;
            }

            PerspectiveSessionMonitor sessionMonitor = perspectiveContext.getSessionMonitor();
            UUID uuid = UUID.fromString(sessionId);
            Optional<InternalSession> sessionOpt = sessionMonitor.findSession(uuid);
            return sessionOpt.orElse(null);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid session ID format: {}", sessionId);
            return null;
        } catch (Exception e) {
            logger.error("Error finding session: {}", sessionId, e);
        }
        return null;
    }

    /**
     * Finds a PageModel by session ID and page ID. This is used to set the PageModel.PAGE
     * ThreadLocal for system.perspective.* functions.
     */
    PageModel findPageModel(String sessionId, String pageId) {
        try {
            Session session = findSession(sessionId);
            if (session == null) {
                logger.warn("Session not found: {}", sessionId);
                return null;
            }

            Optional<? extends Page> pageOpt = session.findPage(pageId);
            if (!pageOpt.isPresent()) {
                logger.warn("Page not found: {} in session {}", pageId, sessionId);
                return null;
            }

            Page page = pageOpt.get();
            if (page instanceof PageModel) {
                return (PageModel) page;
            } else {
                logger.debug(
                        "Page is not a PageModel instance: {} (class: {})",
                        pageId,
                        page.getClass().getName());
                return null;
            }
        } catch (Exception e) {
            logger.error("Error finding PageModel: {}", e.getMessage(), e);
            return null;
        }
    }

    /** Finds a view model by session/page/view IDs. */
    ViewModel findView(String sessionId, String pageId, String viewInstanceId) {
        try {
            Session session = findSession(sessionId);
            if (session == null) {
                return null;
            }

            Optional<? extends Page> pageOpt = session.findPage(pageId);
            if (!pageOpt.isPresent()) {
                return null;
            }

            Page page = pageOpt.get();
            for (ViewModel view : page.getViews()) {
                if (view.getId().toString().equals(viewInstanceId)) {
                    return view;
                }
            }
        } catch (Exception e) {
            logger.error("Error finding view", e);
        }
        return null;
    }

    /** Finds a component by path within a view. */
    ComponentModel findComponent(
            String sessionId, String pageId, String viewInstanceId, String componentPath) {
        try {
            ViewModel view = findView(sessionId, pageId, viewInstanceId);
            if (view == null) {
                return null;
            }

            ComponentModel root = view.getRootContainer();
            return findComponentByPath(root, componentPath);
        } catch (Exception e) {
            logger.error("Error finding component: {}", componentPath, e);
        }
        return null;
    }

    private ComponentModel findComponentByPath(ComponentModel component, String path) {
        if (component == null || path == null) {
            return null;
        }

        String componentPath = getComponentPath(component);
        if (componentPath.equals(path) || component.getName().equals(path)) {
            return component;
        }

        Collection<? extends com.inductiveautomation.perspective.gateway.api.Component> children =
                component.getChildren();
        for (com.inductiveautomation.perspective.gateway.api.Component child : children) {
            if (child instanceof ComponentModel) {
                ComponentModel found = findComponentByPath((ComponentModel) child, path);
                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }

    private String getComponentPath(ComponentModel component) {
        try {
            return component.getQualifiedPath();
        } catch (Exception e) {
            return component.getName();
        }
    }

    private PerspectiveSessionInfo createSessionInfoFromSdkInfo(
            com.inductiveautomation.perspective.gateway.session.PerspectiveSessionInfo sdkInfo,
            PerspectiveSessionMonitor sessionMonitor) {
        // Note: This method is a fallback that may not work well with all SDK versions.
        // The SDK's PerspectiveSessionInfo doesn't expose session IDs directly.
        // The primary method uses reflection to access sessionsById field.
        try {
            UUID sessionId = null;

            // Try reflection methods to find session ID
            try {
                java.lang.reflect.Method getSessionId =
                        sdkInfo.getClass().getMethod("getSessionId");
                Object result = getSessionId.invoke(sdkInfo);
                if (result instanceof UUID) {
                    sessionId = (UUID) result;
                }
            } catch (NoSuchMethodException e) {
                try {
                    java.lang.reflect.Method getId = sdkInfo.getClass().getMethod("getId");
                    Object result = getId.invoke(sdkInfo);
                    if (result instanceof UUID) {
                        sessionId = (UUID) result;
                    } else if (result != null) {
                        sessionId = UUID.fromString(result.toString());
                    }
                } catch (Exception e2) {
                    logger.debug("Could not get session ID from SDK info via reflection");
                }
            }

            if (sessionId != null) {
                Optional<InternalSession> sessionOpt = sessionMonitor.findSession(sessionId);
                if (sessionOpt.isPresent()) {
                    return createSessionInfo(sessionOpt.get());
                }
            }

            return null;
        } catch (Exception e) {
            logger.debug("Could not extract session info: {}", e.getMessage());
            return null;
        }
    }

    private PerspectiveSessionInfo createSessionInfo(Session session) {
        String sessionId = session.getSessionId().toString();
        String userName = "anonymous";
        String projectName = "";
        String sessionType = "browser";
        String userAgent = "";

        // Detect session type using multiple strategies
        sessionType = detectSessionType(session);

        // Try to get user agent
        try {
            if (session instanceof InternalSession) {
                InternalSession internalSession = (InternalSession) session;
                try {
                    java.lang.reflect.Method getUserAgent =
                            internalSession.getClass().getMethod("getUserAgent");
                    Object agentObj = getUserAgent.invoke(internalSession);
                    if (agentObj != null) {
                        userAgent = agentObj.toString();
                    }
                } catch (NoSuchMethodException e) {
                    try {
                        java.lang.reflect.Field browserField =
                                internalSession.getClass().getDeclaredField("browser");
                        browserField.setAccessible(true);
                        Object browser = browserField.get(internalSession);
                        if (browser != null) {
                            userAgent = browser.toString();
                        }
                    } catch (Exception e2) {
                        // User agent not available
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Error getting user agent: {}", e.getMessage());
        }

        // Extract username properly
        try {
            var authStatus = session.getWebAuthStatus();
            if (authStatus != null && authStatus.getUser().isPresent()) {
                var user = authStatus.getUser().get();
                userName = extractUsername(user);
            }
        } catch (Exception e) {
            // User info not available
        }

        try {
            projectName = session.getProjectName();
        } catch (Exception e) {
            // Project name not available
        }

        int pageCount = 0;
        int viewCount = 0;
        try {
            List<? extends Page> pages = session.getPages();
            pageCount = pages.size();
            for (Page page : pages) {
                viewCount += page.getViews().size();
            }
        } catch (Exception e) {
            // Count not available
        }

        long startTime = 0;
        try {
            // Try to get session start time
            java.lang.reflect.Method getCreationTime =
                    session.getClass().getMethod("getCreationTime");
            Object timeObj = getCreationTime.invoke(session);
            if (timeObj instanceof Long) {
                startTime = (Long) timeObj;
            }
        } catch (Exception e) {
            // Start time not available
        }

        // Build a clean display name
        String shortSessionId = sessionId.length() > 8 ? sessionId.substring(0, 8) : sessionId;
        String displayName;
        if ("designer".equals(sessionType)) {
            displayName =
                    String.format(
                            "Designer: %s (%s)",
                            projectName.isEmpty() ? "Unknown Project" : projectName, userName);
        } else {
            displayName =
                    String.format(
                            "Browser: %s@%s (%s)",
                            userName, projectName.isEmpty() ? "?" : projectName, shortSessionId);
        }

        return new PerspectiveSessionInfo(
                sessionId,
                userName,
                projectName,
                pageCount,
                viewCount,
                startTime,
                userAgent,
                sessionType,
                displayName);
    }

    /**
     * Detects whether a session is from a Designer or a browser. Uses multiple detection
     * strategies.
     */
    private String detectSessionType(Session session) {
        // Strategy 1: Check the session class name
        String className = session.getClass().getName().toLowerCase();
        if (className.contains("designer") || className.contains("workstation")) {
            return "designer";
        }

        // Strategy 2: Check for DesignerSession interface or subclass
        for (Class<?> iface : session.getClass().getInterfaces()) {
            String ifaceName = iface.getName().toLowerCase();
            if (ifaceName.contains("designer") || ifaceName.contains("workstation")) {
                return "designer";
            }
        }

        // Strategy 3: Check session properties for Designer markers
        if (session instanceof InternalSession) {
            InternalSession internalSession = (InternalSession) session;
            try {
                // Check if isDesignerSession method exists
                java.lang.reflect.Method isDesigner =
                        internalSession.getClass().getMethod("isDesignerSession");
                Object result = isDesigner.invoke(internalSession);
                if (Boolean.TRUE.equals(result)) {
                    return "designer";
                }
            } catch (NoSuchMethodException e) {
                // Method doesn't exist
            } catch (Exception e) {
                logger.debug("Error checking isDesignerSession: {}", e.getMessage());
            }

            try {
                // Check session props object
                java.lang.reflect.Method getProps =
                        internalSession.getClass().getMethod("getSessionProps");
                Object props = getProps.invoke(internalSession);
                if (props != null) {
                    // Check for pageId containing "designer" or check props type
                    String propsClassName = props.getClass().getName().toLowerCase();
                    if (propsClassName.contains("designer")
                            || propsClassName.contains("workstation")) {
                        return "designer";
                    }
                }
            } catch (Exception e) {
                // Props not available
            }

            // Strategy 4: Check the pages - Designer often has "session-props" pseudo-page
            try {
                List<? extends Page> pages = session.getPages();
                for (Page page : pages) {
                    String pageId = page.getId();
                    // Designer sessions have special page IDs that look like view names
                    // while browser sessions have UUID page IDs
                    if (pageId != null && !pageId.contains("-") && pageId.length() < 36) {
                        // Looks like a Designer page ID (not a UUID)
                        // But this alone isn't conclusive
                    }
                }
            } catch (Exception e) {
                // Pages not available
            }
        }

        // Strategy 5: Check for "session-props" page which is Designer-specific
        try {
            List<? extends Page> pages = session.getPages();
            for (Page page : pages) {
                String pageId = page.getId();
                if ("session-props".equals(pageId)) {
                    return "designer";
                }
            }
        } catch (Exception e) {
            // Pages not available
        }

        return "browser";
    }

    /** Extracts username from a user object, handling various SDK versions. */
    private String extractUsername(Object user) {
        if (user == null) {
            return "anonymous";
        }

        // Try common method names in order of preference
        String[] methodNames = {"getUsername", "getUserName", "getName", "getId"};
        for (String methodName : methodNames) {
            try {
                java.lang.reflect.Method method = user.getClass().getMethod(methodName);
                Object result = method.invoke(user);
                if (result != null) {
                    String value = result.toString();
                    // Avoid returning the full object toString
                    if (!value.contains("{") && !value.contains("@") && value.length() < 100) {
                        return value;
                    }
                }
            } catch (Exception e) {
                // Try next method
            }
        }

        // Try to get userName field directly
        try {
            java.lang.reflect.Field field = user.getClass().getDeclaredField("userName");
            field.setAccessible(true);
            Object value = field.get(user);
            if (value != null) {
                return value.toString();
            }
        } catch (Exception e) {
            // Field not available
        }

        // Last resort: parse from toString if it looks like WebAuthUser
        String userStr = user.toString();
        if (userStr.contains("userName=") || userStr.contains("userName='")) {
            // Extract userName from pattern like userName='admin' or userName=admin
            int start = userStr.indexOf("userName=");
            if (start >= 0) {
                start += "userName=".length();
                // Skip quote if present
                if (start < userStr.length()
                        && (userStr.charAt(start) == '\'' || userStr.charAt(start) == '"')) {
                    start++;
                }
                int end = start;
                while (end < userStr.length()) {
                    char c = userStr.charAt(end);
                    if (c == '\'' || c == '"' || c == ',' || c == '}' || c == ' ') {
                        break;
                    }
                    end++;
                }
                if (end > start) {
                    String extracted = userStr.substring(start, end).trim();
                    if (!extracted.isEmpty() && !extracted.equals("null")) {
                        return extracted;
                    }
                }
            }
        }

        return "anonymous";
    }

    private PerspectivePageInfo createPageInfo(Page page) {
        String pageId = page.getId();
        String primaryViewPath = "";
        int viewCount = 0;

        try {
            List<ViewModel> views = page.getViews();
            viewCount = views.size();
            if (!views.isEmpty()) {
                // Find the root view: its qualifiedPath won't contain '$' (embedded marker)
                ViewModel rootView = null;
                for (ViewModel v : views) {
                    String qp = v.getQualifiedPath();
                    if (qp != null && !qp.contains("$")) {
                        rootView = v;
                        break;
                    }
                }
                primaryViewPath = getViewPath(rootView != null ? rootView : views.get(0));
            }
        } catch (Exception e) {
            // View info not available
        }

        return new PerspectivePageInfo(pageId, primaryViewPath, viewCount);
    }

    private String findRootViewPath(List<ViewModel> views) {
        if (views.isEmpty()) {
            return "Page";
        }
        ViewModel rootView = null;
        for (ViewModel v : views) {
            String qp = v.getQualifiedPath();
            if (qp != null && !qp.contains("$")) {
                rootView = v;
                break;
            }
        }
        return getViewPath(rootView != null ? rootView : views.get(0));
    }

    private String getViewPath(ViewModel view) {
        String rawPath = null;

        try {
            var viewId = view.getId();
            if (viewId != null) {
                try {
                    java.lang.reflect.Method getViewPath =
                            viewId.getClass().getMethod("getViewPath");
                    Object pathObj = getViewPath.invoke(viewId);
                    if (pathObj != null) {
                        rawPath = pathObj.toString();
                    }
                } catch (NoSuchMethodException e) {
                    try {
                        java.lang.reflect.Method getPath = viewId.getClass().getMethod("getPath");
                        Object pathObj = getPath.invoke(viewId);
                        if (pathObj != null) {
                            rawPath = pathObj.toString();
                        }
                    } catch (NoSuchMethodException e2) {
                        rawPath = viewId.toString();
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Could not get view path: {}", e.getMessage());
        }

        if (rawPath == null) {
            rawPath = view.getQualifiedPath();
        }

        // Strip the @X instance suffix (e.g., "TestView@C" -> "TestView")
        return stripInstanceSuffix(rawPath);
    }

    /**
     * Strips the @X instance suffix from a view/page path. Perspective uses suffixes
     * like @C, @D, @D$0:2 to identify view instances.
     *
     * @param path The raw path potentially containing an instance suffix
     * @return The clean path without the instance suffix
     */
    private String stripInstanceSuffix(String path) {
        if (path == null) {
            return "";
        }
        // Find the @ symbol that marks the instance suffix
        int atIndex = path.lastIndexOf('@');
        if (atIndex > 0) {
            return path.substring(0, atIndex);
        }
        return path;
    }

    private PerspectiveViewInfo createViewInfo(ViewModel view) {
        String viewInstanceId = view.getId().toString();
        String viewPath = getViewPath(view);
        int componentCount = 0;
        String rootComponentType = "";

        try {
            ComponentModel rootComponent = view.getRootContainer();
            if (rootComponent != null) {
                componentCount = countComponents(rootComponent);
                rootComponentType = rootComponent.getType();
            }
        } catch (Exception e) {
            // Component info not available
        }

        return new PerspectiveViewInfo(viewInstanceId, viewPath, componentCount, rootComponentType);
    }

    private int countComponents(ComponentModel component) {
        int count = 1;
        try {
            Collection<? extends com.inductiveautomation.perspective.gateway.api.Component>
                    children = component.getChildren();
            for (com.inductiveautomation.perspective.gateway.api.Component child : children) {
                if (child instanceof ComponentModel) {
                    count += countComponents((ComponentModel) child);
                }
            }
        } catch (Exception e) {
            // Children not available
        }
        return count;
    }

    private PerspectiveComponentInfo buildComponentTree(ComponentModel component) {
        String path = getComponentPath(component);
        String type = component.getType();
        String name = component.getName();
        boolean hasScripts = false;

        PerspectiveComponentInfo info = new PerspectiveComponentInfo(path, type, name, hasScripts);

        try {
            Collection<? extends com.inductiveautomation.perspective.gateway.api.Component>
                    children = component.getChildren();
            for (com.inductiveautomation.perspective.gateway.api.Component child : children) {
                if (child instanceof ComponentModel) {
                    PerspectiveComponentInfo childInfo = buildComponentTree((ComponentModel) child);
                    info.addChild(childInfo);
                }
            }
        } catch (Exception e) {
            logger.debug("Error building children for component: {}", path);
        }

        return info;
    }

    /**
     * Adds Perspective context variables to the script locals. This allows script execution with
     * Perspective session/page/view/component context.
     */
    void addPerspectiveContextToLocals(
            PyObject locals,
            String perspectiveSessionId,
            String pageId,
            String viewInstanceId,
            String componentPath) {

        try {
            // Find the session
            Session session = findSession(perspectiveSessionId);
            if (session == null) {
                logger.warn("Perspective session not found: {}", perspectiveSessionId);
                return;
            }

            // Add session reference
            locals.__setitem__("session", Py.java2py(session));

            // Add page if specified
            if (pageId != null) {
                java.util.Optional<? extends Page> pageOpt = session.findPage(pageId);
                if (pageOpt.isPresent()) {
                    Page page = pageOpt.get();
                    locals.__setitem__("page", Py.java2py(page));

                    // Add view if specified
                    if (viewInstanceId != null) {
                        ViewModel view = null;
                        for (ViewModel v : page.getViews()) {
                            if (v.getId().toString().equals(viewInstanceId)) {
                                view = v;
                                break;
                            }
                        }

                        if (view != null) {
                            locals.__setitem__("view", Py.java2py(view));

                            // Add component as 'self' if specified
                            if (componentPath != null) {
                                ComponentModel component =
                                        findComponent(
                                                perspectiveSessionId,
                                                pageId,
                                                viewInstanceId,
                                                componentPath);
                                if (component != null) {
                                    locals.__setitem__("self", Py.java2py(component));
                                    logger.debug("Bound component '{}' as 'self'", componentPath);
                                } else {
                                    logger.warn("Component not found at path: {}", componentPath);
                                }
                            }
                        } else {
                            logger.warn("View not found: {} on page {}", viewInstanceId, pageId);
                        }
                    }
                } else {
                    logger.warn("Page not found: {} in session {}", pageId, perspectiveSessionId);
                }
            }

            // Add PerspectiveContext for convenience
            PerspectiveContext perspectiveContext = PerspectiveContext.get(gatewayContext);
            if (perspectiveContext != null) {
                locals.__setitem__("perspectiveContext", Py.java2py(perspectiveContext));
            }

        } catch (Exception e) {
            logger.error("Error adding Perspective context to script locals", e);
        }
    }

    /**
     * Gets completion items for a component's properties.
     *
     * @param sessionId The Perspective session ID
     * @param pageId The page ID
     * @param viewInstanceId The view instance ID
     * @param componentPath The component path
     * @param prefix The property prefix (e.g., "self.props")
     * @return Completion result with property items
     */
    PerspectiveCompletionResult getComponentCompletions(
            String sessionId,
            String pageId,
            String viewInstanceId,
            String componentPath,
            String prefix) {

        PerspectiveCompletionResult result = new PerspectiveCompletionResult();

        try {
            ComponentModel component =
                    findComponent(sessionId, pageId, viewInstanceId, componentPath);
            if (component == null) {
                logger.debug(
                        "Component not found for completions: {} in view {}",
                        componentPath,
                        viewInstanceId);
                return result;
            }

            PerspectiveCompletionService completionService = new PerspectiveCompletionService();
            return completionService.getComponentCompletions(component, prefix);

        } catch (Exception e) {
            logger.error("Error getting component completions", e);
            return result;
        }
    }
}

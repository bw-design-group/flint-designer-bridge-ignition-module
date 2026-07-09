package dev.bwdesigngroup.flint.gateway.perspective;

import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import com.inductiveautomation.perspective.gateway.model.PageModel;
import dev.bwdesigngroup.flint.common.protocol.methods.perspective.PerspectiveCompletionResult;
import dev.bwdesigngroup.flint.common.protocol.methods.perspective.PerspectiveListComponentsResult;
import dev.bwdesigngroup.flint.common.protocol.methods.perspective.PerspectiveListPagesResult;
import dev.bwdesigngroup.flint.common.protocol.methods.perspective.PerspectiveListSessionsResult;
import dev.bwdesigngroup.flint.common.protocol.methods.perspective.PerspectiveListViewsResult;
import dev.bwdesigngroup.flint.common.protocol.methods.perspective.ViewProfileResult;
import dev.bwdesigngroup.flint.common.protocol.methods.perspective.recording.RecordingEventBatch;
import dev.bwdesigngroup.flint.common.protocol.methods.perspective.recording.StartRecordingResult;
import dev.bwdesigngroup.flint.common.protocol.methods.perspective.recording.StopRecordingResult;
import java.util.ArrayList;
import org.python.core.PyObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Inspects active Perspective sessions on the Gateway. This class uses lazy loading to avoid
 * NoClassDefFoundError when Perspective isn't available.
 */
public class PerspectiveSessionInspector {
    private static final Logger logger = LoggerFactory.getLogger("flint.Gateway.Perspective");

    private final GatewayContext gatewayContext;

    // Lazy-loaded accessor - null until Perspective is confirmed available
    private volatile PerspectiveAccessor accessor = null;
    private volatile Boolean perspectiveAvailable = null;

    public PerspectiveSessionInspector(GatewayContext gatewayContext) {
        this.gatewayContext = gatewayContext;
    }

    /**
     * Checks if Perspective module is available on the Gateway. This method uses lazy class loading
     * to avoid NoClassDefFoundError.
     */
    public boolean isPerspectiveAvailable() {
        if (perspectiveAvailable != null) {
            return perspectiveAvailable;
        }

        return checkPerspectiveAvailable(true);
    }

    /**
     * Performs the actual Perspective availability check.
     *
     * @param logDetails whether to log detailed information
     */
    private synchronized boolean checkPerspectiveAvailable(boolean logDetails) {
        // Return cached result if already determined
        if (perspectiveAvailable != null) {
            return perspectiveAvailable;
        }

        try {
            // First check if the Perspective module is loaded via ModuleManager
            boolean perspectiveModuleFound = false;
            try {
                var moduleManager = gatewayContext.getModuleManager();
                if (moduleManager != null) {
                    for (var module : moduleManager.getModules()) {
                        var moduleInfo = module.getInfo();
                        if (moduleInfo != null) {
                            String moduleId = moduleInfo.getId();
                            if (moduleId != null && moduleId.contains("perspective")) {
                                perspectiveModuleFound = true;
                                break;
                            }
                        }
                    }
                    if (!perspectiveModuleFound) {
                        perspectiveAvailable = false;
                        return false;
                    }
                }
            } catch (Exception e) {
                logger.debug("Could not check ModuleManager for Perspective: {}", e.getMessage());
            }

            // Try to load and use the Perspective accessor class
            // The actual class loading happens when PerspectiveAccessor is first instantiated
            // If Perspective SDK classes aren't available, we'll get NoClassDefFoundError
            try {
                accessor = new PerspectiveAccessor(gatewayContext);
                boolean available = accessor.isPerspectiveContextAvailable();
                if (available) {
                    if (logDetails) {
                        logger.debug("Perspective session inspection available");
                    }
                    perspectiveAvailable = true;
                    return true;
                } else {
                    // Don't cache - might become available later
                    accessor = null;
                    return false;
                }
            } catch (NoClassDefFoundError e) {
                logger.debug("Perspective SDK classes not loaded yet: {}", e.getMessage());
                // Don't cache - might become available later after Perspective starts
                return false;
            }
        } catch (Exception e) {
            logger.debug("Perspective not available: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Gets the accessor, initializing it if needed. Returns null if Perspective isn't available.
     */
    private PerspectiveAccessor getAccessor() {
        if (accessor != null) {
            return accessor;
        }

        // Try to initialize
        if (checkPerspectiveAvailable(false)) {
            return accessor;
        }

        return null;
    }

    /** Lists all active Perspective sessions. */
    public PerspectiveListSessionsResult listSessions() {
        PerspectiveAccessor acc = getAccessor();
        if (acc == null) {
            logger.warn("Perspective not available when listing sessions");
            return new PerspectiveListSessionsResult(new ArrayList<>());
        }

        try {
            return acc.listSessions();
        } catch (NoClassDefFoundError e) {
            logger.warn("Perspective classes not available: {}", e.getMessage());
            // Reset state so we can try again later
            accessor = null;
            perspectiveAvailable = null;
            return new PerspectiveListSessionsResult(new ArrayList<>());
        }
    }

    /** Gets pages for a specific session. */
    public PerspectiveListPagesResult getSessionPages(String sessionId) {
        PerspectiveAccessor acc = getAccessor();
        if (acc == null) {
            logger.warn("Perspective not available when getting session pages");
            return new PerspectiveListPagesResult(new ArrayList<>());
        }

        try {
            return acc.getSessionPages(sessionId);
        } catch (NoClassDefFoundError e) {
            logger.warn("Perspective classes not available: {}", e.getMessage());
            accessor = null;
            perspectiveAvailable = null;
            return new PerspectiveListPagesResult(new ArrayList<>());
        }
    }

    /** Gets views for a specific page. */
    public PerspectiveListViewsResult getPageViews(String sessionId, String pageId) {
        PerspectiveAccessor acc = getAccessor();
        if (acc == null) {
            logger.warn("Perspective not available when getting page views");
            return new PerspectiveListViewsResult(new ArrayList<>());
        }

        try {
            return acc.getPageViews(sessionId, pageId);
        } catch (NoClassDefFoundError e) {
            logger.warn("Perspective classes not available: {}", e.getMessage());
            accessor = null;
            perspectiveAvailable = null;
            return new PerspectiveListViewsResult(new ArrayList<>());
        }
    }

    /** Gets the component tree for a specific view. */
    public PerspectiveListComponentsResult getViewComponents(
            String sessionId, String pageId, String viewInstanceId) {
        PerspectiveAccessor acc = getAccessor();
        if (acc == null) {
            logger.warn("Perspective not available when getting view components");
            return new PerspectiveListComponentsResult(new ArrayList<>());
        }

        try {
            return acc.getViewComponents(sessionId, pageId, viewInstanceId);
        } catch (NoClassDefFoundError e) {
            logger.warn("Perspective classes not available: {}", e.getMessage());
            accessor = null;
            perspectiveAvailable = null;
            return new PerspectiveListComponentsResult(new ArrayList<>());
        }
    }

    /** Profiles a live Perspective view for performance metrics. */
    public ViewProfileResult profileView(String sessionId, String pageId, String viewInstanceId) {
        PerspectiveAccessor acc = getAccessor();
        if (acc == null) {
            logger.warn("Perspective not available when profiling view");
            return new ViewProfileResult();
        }

        try {
            return acc.profileView(sessionId, pageId, viewInstanceId);
        } catch (NoClassDefFoundError e) {
            logger.warn("Perspective classes not available: {}", e.getMessage());
            accessor = null;
            perspectiveAvailable = null;
            return new ViewProfileResult();
        }
    }

    // ==================== Recording Methods ====================

    /** Starts a binding recording session for a live view. */
    public StartRecordingResult startRecording(
            String sessionId,
            String pageId,
            String viewInstanceId,
            int pollIntervalMs,
            int maxDurationMs,
            boolean autoStopOnAllResolved,
            int autoStopDelayMs) {
        PerspectiveAccessor acc = getAccessor();
        if (acc == null) {
            logger.warn("Perspective not available when starting recording");
            return StartRecordingResult.failure("Perspective not available");
        }

        try {
            return acc.startRecording(
                    sessionId,
                    pageId,
                    viewInstanceId,
                    pollIntervalMs,
                    maxDurationMs,
                    autoStopOnAllResolved,
                    autoStopDelayMs);
        } catch (NoClassDefFoundError e) {
            logger.warn("Perspective classes not available: {}", e.getMessage());
            accessor = null;
            perspectiveAvailable = null;
            return StartRecordingResult.failure("Perspective classes not available");
        }
    }

    /** Stops an active binding recording session. */
    public StopRecordingResult stopRecording(String recordingId) {
        PerspectiveAccessor acc = getAccessor();
        if (acc == null) {
            logger.warn("Perspective not available when stopping recording");
            return StopRecordingResult.failure("Perspective not available");
        }

        try {
            return acc.stopRecording(recordingId);
        } catch (NoClassDefFoundError e) {
            logger.warn("Perspective classes not available: {}", e.getMessage());
            accessor = null;
            perspectiveAvailable = null;
            return StopRecordingResult.failure("Perspective classes not available");
        }
    }

    /** Polls for binding state transition events from an active recording. */
    public RecordingEventBatch pollRecordingEvents(String recordingId) {
        PerspectiveAccessor acc = getAccessor();
        if (acc == null) {
            return new RecordingEventBatch();
        }

        try {
            return acc.pollRecordingEvents(recordingId);
        } catch (NoClassDefFoundError e) {
            accessor = null;
            perspectiveAvailable = null;
            return new RecordingEventBatch();
        }
    }

    /**
     * Finds a component by path within a view. Returns the component as an Object to avoid exposing
     * Perspective types.
     */
    public Object findComponent(
            String sessionId, String pageId, String viewInstanceId, String componentPath) {
        PerspectiveAccessor acc = getAccessor();
        if (acc == null) {
            return null;
        }

        try {
            return acc.findComponent(sessionId, pageId, viewInstanceId, componentPath);
        } catch (NoClassDefFoundError e) {
            accessor = null;
            perspectiveAvailable = null;
            return null;
        }
    }

    /**
     * Finds a session by ID. Returns the session as an Object to avoid exposing Perspective types.
     */
    public Object findSession(String sessionId) {
        PerspectiveAccessor acc = getAccessor();
        if (acc == null) {
            return null;
        }

        try {
            return acc.findSession(sessionId);
        } catch (NoClassDefFoundError e) {
            accessor = null;
            perspectiveAvailable = null;
            return null;
        }
    }

    /**
     * Finds a view model by session/page/view IDs. Returns the view as an Object to avoid exposing
     * Perspective types.
     */
    public Object findView(String sessionId, String pageId, String viewInstanceId) {
        PerspectiveAccessor acc = getAccessor();
        if (acc == null) {
            return null;
        }

        try {
            return acc.findView(sessionId, pageId, viewInstanceId);
        } catch (NoClassDefFoundError e) {
            accessor = null;
            perspectiveAvailable = null;
            return null;
        }
    }

    /**
     * Adds Perspective context variables to script locals. Delegates to PerspectiveAccessor to
     * avoid direct Perspective SDK dependencies.
     *
     * @param locals The script locals map
     * @param perspectiveSessionId The Perspective session ID
     * @param pageId Optional page ID
     * @param viewInstanceId Optional view instance ID
     * @param componentPath Optional component path to bind as 'self'
     */
    public void addPerspectiveContextToLocals(
            PyObject locals,
            String perspectiveSessionId,
            String pageId,
            String viewInstanceId,
            String componentPath) {

        PerspectiveAccessor acc = getAccessor();
        if (acc == null) {
            logger.warn("Perspective not available when adding context to locals");
            return;
        }

        try {
            acc.addPerspectiveContextToLocals(
                    locals, perspectiveSessionId, pageId, viewInstanceId, componentPath);
        } catch (NoClassDefFoundError e) {
            logger.warn(
                    "Perspective classes not available when adding context: {}", e.getMessage());
            accessor = null;
            perspectiveAvailable = null;
        }
    }

    /**
     * Finds a PageModel by session ID and page ID. This is needed for setting the PageModel.PAGE
     * ThreadLocal to enable system.perspective.* functions.
     *
     * @param sessionId The Perspective session ID
     * @param pageId The page ID
     * @return The PageModel, or null if not found
     */
    public PageModel findPageModel(String sessionId, String pageId) {
        PerspectiveAccessor acc = getAccessor();
        if (acc == null) {
            logger.debug("Perspective not available when finding PageModel");
            return null;
        }

        try {
            return acc.findPageModel(sessionId, pageId);
        } catch (NoClassDefFoundError e) {
            logger.warn(
                    "Perspective classes not available when finding PageModel: {}", e.getMessage());
            accessor = null;
            perspectiveAvailable = null;
            return null;
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
    public PerspectiveCompletionResult getComponentCompletions(
            String sessionId,
            String pageId,
            String viewInstanceId,
            String componentPath,
            String prefix) {

        PerspectiveAccessor acc = getAccessor();
        if (acc == null) {
            logger.warn("Perspective not available when getting component completions");
            return new PerspectiveCompletionResult();
        }

        try {
            return acc.getComponentCompletions(
                    sessionId, pageId, viewInstanceId, componentPath, prefix);
        } catch (NoClassDefFoundError e) {
            logger.warn(
                    "Perspective classes not available when getting completions: {}",
                    e.getMessage());
            accessor = null;
            perspectiveAvailable = null;
            return new PerspectiveCompletionResult();
        }
    }
}

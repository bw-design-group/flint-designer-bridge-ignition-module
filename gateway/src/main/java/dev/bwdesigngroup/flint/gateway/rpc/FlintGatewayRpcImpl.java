package dev.bwdesigngroup.flint.gateway.rpc;

import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import dev.bwdesigngroup.flint.common.debug.DebugCommand;
import dev.bwdesigngroup.flint.common.debug.DebugEventBatch;
import dev.bwdesigngroup.flint.common.protocol.methods.ExecuteScriptResult;
import dev.bwdesigngroup.flint.common.protocol.methods.debug.DebugEvaluateResult;
import dev.bwdesigngroup.flint.common.protocol.methods.debug.DebugScopesResult;
import dev.bwdesigngroup.flint.common.protocol.methods.debug.DebugSetBreakpointsParams.BreakpointInfo;
import dev.bwdesigngroup.flint.common.protocol.methods.debug.DebugStackTraceResult;
import dev.bwdesigngroup.flint.common.protocol.methods.debug.DebugStartSessionResult;
import dev.bwdesigngroup.flint.common.protocol.methods.debug.DebugVariablesResult;
import dev.bwdesigngroup.flint.common.protocol.methods.perspective.PerspectiveCompletionResult;
import dev.bwdesigngroup.flint.common.protocol.methods.perspective.PerspectiveListComponentsResult;
import dev.bwdesigngroup.flint.common.protocol.methods.perspective.PerspectiveListPagesResult;
import dev.bwdesigngroup.flint.common.protocol.methods.perspective.PerspectiveListSessionsResult;
import dev.bwdesigngroup.flint.common.protocol.methods.perspective.PerspectiveListViewsResult;
import dev.bwdesigngroup.flint.common.protocol.methods.perspective.ViewProfileResult;
import dev.bwdesigngroup.flint.common.protocol.methods.perspective.recording.RecordingEventBatch;
import dev.bwdesigngroup.flint.common.protocol.methods.perspective.recording.StartRecordingResult;
import dev.bwdesigngroup.flint.common.protocol.methods.perspective.recording.StopRecordingResult;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.TagBrowseResult;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.TagCreateResult;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.TagDeleteResult;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.TagEditResult;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.TagGetConfigResult;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.TagProvidersResult;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.TagReadResult;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.TagWriteResult;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.UdtDefinitionResult;
import dev.bwdesigngroup.flint.common.protocol.methods.tags.UdtListResult;
import dev.bwdesigngroup.flint.common.rpc.FlintGatewayRpc;
import dev.bwdesigngroup.flint.gateway.debug.GatewayDebugExecutor;
import dev.bwdesigngroup.flint.gateway.debug.GatewayDebugSession;
import dev.bwdesigngroup.flint.gateway.perspective.PerspectiveScriptExecutor;
import dev.bwdesigngroup.flint.gateway.perspective.PerspectiveSessionInspector;
import dev.bwdesigngroup.flint.gateway.script.GatewayScriptExecutor;
import dev.bwdesigngroup.flint.gateway.tags.GatewayTagExecutor;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of the Gateway RPC interface. This is called by the Designer module via Ignition's
 * ModuleRPC mechanism.
 */
public class FlintGatewayRpcImpl implements FlintGatewayRpc {
    private static final Logger logger = LoggerFactory.getLogger("flint.Gateway.RPC");

    private final GatewayContext context;
    private final GatewayScriptExecutor scriptExecutor;
    private final PerspectiveSessionInspector perspectiveInspector;
    private final PerspectiveScriptExecutor perspectiveScriptExecutor;
    private final GatewayDebugExecutor debugExecutor;
    private final GatewayTagExecutor tagExecutor;

    public FlintGatewayRpcImpl(
            GatewayContext context,
            GatewayScriptExecutor scriptExecutor,
            PerspectiveSessionInspector perspectiveInspector,
            PerspectiveScriptExecutor perspectiveScriptExecutor,
            GatewayDebugExecutor debugExecutor,
            GatewayTagExecutor tagExecutor) {
        this.context = context;
        this.scriptExecutor = scriptExecutor;
        this.perspectiveInspector = perspectiveInspector;
        this.perspectiveScriptExecutor = perspectiveScriptExecutor;
        this.debugExecutor = debugExecutor;
        this.tagExecutor = tagExecutor;
    }

    @Override
    public ExecuteScriptResult executeScript(
            String code, int timeoutMs, String sessionId, boolean resetSession) {
        logger.info(
                "Executing gateway script ({} chars, timeout={}ms, session={})",
                code != null ? code.length() : 0,
                timeoutMs,
                sessionId);

        try {
            return scriptExecutor.execute(code, timeoutMs, sessionId, resetSession);
        } catch (Exception e) {
            logger.error("Gateway script execution failed", e);
            return ExecuteScriptResult.failure(
                    "Gateway execution failed: " + e.getMessage(), "", "", 0);
        }
    }

    @Override
    public void resetSession(String sessionId) {
        logger.info("Resetting gateway session: {}", sessionId);
        scriptExecutor.resetSession(sessionId);
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public boolean requestProjectScan() {
        logger.info("Requesting project scan on Gateway");
        try {
            context.getProjectManager().requestScan().get();
            logger.info("Project scan completed successfully");
            return true;
        } catch (Exception e) {
            logger.error("Failed to request project scan", e);
            return false;
        }
    }

    // ==================== Perspective Session Discovery Methods ====================

    @Override
    public PerspectiveListSessionsResult perspectiveListSessions() {
        logger.debug("Listing Perspective sessions");
        try {
            return perspectiveInspector.listSessions();
        } catch (Exception e) {
            logger.error("Failed to list Perspective sessions", e);
            return new PerspectiveListSessionsResult();
        }
    }

    @Override
    public PerspectiveListPagesResult perspectiveGetSessionPages(String sessionId) {
        logger.debug("Getting pages for Perspective session: {}", sessionId);
        try {
            return perspectiveInspector.getSessionPages(sessionId);
        } catch (Exception e) {
            logger.error("Failed to get session pages", e);
            return new PerspectiveListPagesResult();
        }
    }

    @Override
    public PerspectiveListViewsResult perspectiveGetPageViews(String sessionId, String pageId) {
        logger.debug("Getting views for page {} in session {}", pageId, sessionId);
        try {
            return perspectiveInspector.getPageViews(sessionId, pageId);
        } catch (Exception e) {
            logger.error("Failed to get page views", e);
            return new PerspectiveListViewsResult();
        }
    }

    @Override
    public PerspectiveListComponentsResult perspectiveGetViewComponents(
            String sessionId, String pageId, String viewInstanceId) {
        logger.debug(
                "Getting components for view {} on page {} in session {}",
                viewInstanceId,
                pageId,
                sessionId);
        try {
            return perspectiveInspector.getViewComponents(sessionId, pageId, viewInstanceId);
        } catch (Exception e) {
            logger.error("Failed to get view components", e);
            return new PerspectiveListComponentsResult();
        }
    }

    @Override
    public boolean isPerspectiveAvailable() {
        return perspectiveInspector.isPerspectiveAvailable();
    }

    @Override
    public ViewProfileResult perspectiveProfileView(
            String sessionId, String pageId, String viewInstanceId) {
        logger.debug(
                "Profiling view {} on page {} in session {}", viewInstanceId, pageId, sessionId);
        try {
            return perspectiveInspector.profileView(sessionId, pageId, viewInstanceId);
        } catch (Exception e) {
            logger.error("Failed to profile view", e);
            return new ViewProfileResult();
        }
    }

    // ==================== Perspective Recording Methods ====================

    @Override
    public StartRecordingResult perspectiveStartRecording(
            String sessionId,
            String pageId,
            String viewInstanceId,
            int pollIntervalMs,
            int maxDurationMs,
            boolean autoStopOnAllResolved,
            int autoStopDelayMs) {
        logger.debug(
                "Starting recording for view {} on page {} in session {}",
                viewInstanceId,
                pageId,
                sessionId);
        try {
            return perspectiveInspector.startRecording(
                    sessionId,
                    pageId,
                    viewInstanceId,
                    pollIntervalMs,
                    maxDurationMs,
                    autoStopOnAllResolved,
                    autoStopDelayMs);
        } catch (Exception e) {
            logger.error("Failed to start recording", e);
            return StartRecordingResult.failure("Failed to start recording: " + e.getMessage());
        }
    }

    @Override
    public StopRecordingResult perspectiveStopRecording(String recordingId) {
        logger.debug("Stopping recording: {}", recordingId);
        try {
            return perspectiveInspector.stopRecording(recordingId);
        } catch (Exception e) {
            logger.error("Failed to stop recording", e);
            return StopRecordingResult.failure("Failed to stop recording: " + e.getMessage());
        }
    }

    @Override
    public RecordingEventBatch perspectivePollRecordingEvents(String recordingId) {
        try {
            return perspectiveInspector.pollRecordingEvents(recordingId);
        } catch (Exception e) {
            logger.error("Failed to poll recording events", e);
            return new RecordingEventBatch();
        }
    }

    // ==================== Perspective Script Execution Methods ====================

    @Override
    public ExecuteScriptResult perspectiveExecuteScript(
            String code,
            int timeoutMs,
            String sessionId,
            String pageId,
            String viewInstanceId,
            String componentPath,
            String scriptSessionId,
            boolean resetSession) {

        logger.info(
                "Executing Perspective script ({} chars, timeout={}ms, session={}, page={}, view={}, component={})",
                code != null ? code.length() : 0,
                timeoutMs,
                sessionId,
                pageId,
                viewInstanceId,
                componentPath);

        try {
            return perspectiveScriptExecutor.execute(
                    code,
                    timeoutMs,
                    sessionId,
                    pageId,
                    viewInstanceId,
                    componentPath,
                    scriptSessionId,
                    resetSession);
        } catch (Exception e) {
            logger.error("Perspective script execution failed", e);
            return ExecuteScriptResult.failure(
                    "Perspective execution failed: " + e.getMessage(), "", "", 0);
        }
    }

    // ==================== Perspective Completion Methods ====================

    @Override
    public PerspectiveCompletionResult perspectiveGetComponentCompletions(
            String sessionId,
            String pageId,
            String viewInstanceId,
            String componentPath,
            String prefix) {

        logger.debug(
                "Getting component completions for {} with prefix '{}'", componentPath, prefix);

        try {
            return perspectiveInspector.getComponentCompletions(
                    sessionId, pageId, viewInstanceId, componentPath, prefix);
        } catch (Exception e) {
            logger.error("Failed to get component completions", e);
            return new PerspectiveCompletionResult();
        }
    }

    // ==================== Debug Methods ====================

    @Override
    public DebugStartSessionResult debugStartSession(
            String code,
            String filePath,
            String modulePath,
            String scope,
            String perspectiveSessionId,
            String perspectivePageId,
            String perspectiveViewInstanceId,
            String perspectiveComponentPath) {

        logger.info("Starting Gateway debug session (scope={}, file={})", scope, filePath);

        try {
            GatewayDebugSession session =
                    debugExecutor.createSession(code, filePath, modulePath, scope);

            // Set Perspective context if applicable
            if ("perspective".equals(scope)) {
                session.setPerspectiveSessionId(perspectiveSessionId);
                session.setPerspectivePageId(perspectivePageId);
                session.setPerspectiveViewInstanceId(perspectiveViewInstanceId);
                session.setPerspectiveComponentPath(perspectiveComponentPath);
            }

            return DebugStartSessionResult.success(session.getSessionId());
        } catch (Exception e) {
            logger.error("Failed to start debug session", e);
            return DebugStartSessionResult.failure(
                    "Failed to start debug session: " + e.getMessage());
        }
    }

    @Override
    public void debugStopSession(String sessionId) {
        logger.info("Stopping Gateway debug session: {}", sessionId);
        debugExecutor.stopSession(sessionId);
    }

    @Override
    public List<Integer> debugSetBreakpoints(
            String sessionId, String filePath, List<BreakpointInfo> breakpoints) {
        logger.debug(
                "Setting {} breakpoints for session {} at file {}",
                breakpoints != null ? breakpoints.size() : 0,
                sessionId,
                filePath);

        if (breakpoints == null) {
            breakpoints = new ArrayList<>();
        }
        return debugExecutor.setBreakpoints(sessionId, filePath, breakpoints);
    }

    @Override
    public void debugRun(String sessionId) {
        logger.info("Running Gateway debug session: {}", sessionId);
        debugExecutor.runSession(sessionId);
    }

    @Override
    public void debugSendCommand(String sessionId, String command) {
        logger.debug("Sending debug command '{}' to session {}", command, sessionId);

        DebugCommand cmd;
        switch (command.toLowerCase()) {
            case "continue":
                cmd = DebugCommand.continueExecution();
                break;
            case "stepover":
            case "step_over":
                cmd = DebugCommand.stepOver();
                break;
            case "stepinto":
            case "step_into":
                cmd = DebugCommand.stepInto();
                break;
            case "stepout":
            case "step_out":
                cmd = DebugCommand.stepOut();
                break;
            case "pause":
                cmd = DebugCommand.pause();
                break;
            case "terminate":
                cmd = DebugCommand.terminate();
                break;
            default:
                logger.warn("Unknown debug command: {}", command);
                return;
        }

        debugExecutor.sendCommand(sessionId, cmd);
    }

    @Override
    public DebugEventBatch debugPollEvents(String sessionId, long maxWaitMs) {
        return debugExecutor.pollEvents(sessionId, maxWaitMs);
    }

    @Override
    public DebugStackTraceResult debugGetStackTrace(String sessionId) {
        return debugExecutor.getStackTrace(sessionId);
    }

    @Override
    public DebugScopesResult debugGetScopes(String sessionId, int frameId) {
        return debugExecutor.getScopes(sessionId, frameId);
    }

    @Override
    public DebugVariablesResult debugGetVariables(String sessionId, int variablesReference) {
        return debugExecutor.getVariables(sessionId, variablesReference);
    }

    @Override
    public DebugEvaluateResult debugEvaluate(String sessionId, String expression, Integer frameId) {
        return debugExecutor.evaluate(sessionId, expression, frameId);
    }

    // ==================== Tag System Methods ====================

    @Override
    public TagBrowseResult tagBrowse(
            String provider, String parentPath, String typeFilter, String nameFilter) {
        logger.debug("Browsing tags: provider={}, path={}", provider, parentPath);
        try {
            return tagExecutor.browse(provider, parentPath, typeFilter, nameFilter);
        } catch (Exception e) {
            logger.error("Failed to browse tags", e);
            return new TagBrowseResult();
        }
    }

    @Override
    public TagReadResult tagRead(List<String> tagPaths) {
        logger.debug("Reading {} tags", tagPaths != null ? tagPaths.size() : 0);
        try {
            return tagExecutor.read(tagPaths);
        } catch (Exception e) {
            logger.error("Failed to read tags", e);
            return new TagReadResult();
        }
    }

    @Override
    public TagWriteResult tagWrite(
            List<String> paths, List<String> values, List<String> dataTypes) {
        logger.info("Writing {} tags", paths != null ? paths.size() : 0);
        try {
            return tagExecutor.write(paths, values, dataTypes);
        } catch (Exception e) {
            logger.error("Failed to write tags", e);
            return new TagWriteResult();
        }
    }

    @Override
    public TagGetConfigResult tagGetConfig(String tagPath) {
        logger.debug("Getting config for tag: {}", tagPath);
        try {
            return tagExecutor.getConfig(tagPath);
        } catch (Exception e) {
            logger.error("Failed to get tag config", e);
            return new TagGetConfigResult(tagPath, "{}");
        }
    }

    @Override
    public TagCreateResult tagCreate(String parentPath, String tagsJson) {
        logger.info("Creating tags under: {}", parentPath);
        try {
            return tagExecutor.create(parentPath, tagsJson);
        } catch (Exception e) {
            logger.error("Failed to create tags", e);
            return new TagCreateResult();
        }
    }

    @Override
    public TagEditResult tagEdit(String tagPath, String configJson) {
        logger.info("Editing tag: {}", tagPath);
        try {
            return tagExecutor.edit(tagPath, configJson);
        } catch (Exception e) {
            logger.error("Failed to edit tag", e);
            return TagEditResult.failure(e.getMessage());
        }
    }

    @Override
    public TagDeleteResult tagDelete(List<String> tagPaths) {
        logger.info("Deleting {} tags", tagPaths != null ? tagPaths.size() : 0);
        try {
            return tagExecutor.delete(tagPaths);
        } catch (Exception e) {
            logger.error("Failed to delete tags", e);
            return new TagDeleteResult();
        }
    }

    @Override
    public TagProvidersResult tagGetProviders() {
        logger.debug("Getting tag providers");
        try {
            return tagExecutor.getProviders();
        } catch (Exception e) {
            logger.error("Failed to get tag providers", e);
            return new TagProvidersResult();
        }
    }

    // ==================== UDT System Methods ====================

    @Override
    public UdtListResult udtGetDefinitions(String provider) {
        logger.debug("Getting UDT definitions for provider: {}", provider);
        try {
            return tagExecutor.getDefinitions(provider);
        } catch (Exception e) {
            logger.error("Failed to get UDT definitions", e);
            return new UdtListResult();
        }
    }

    @Override
    public UdtDefinitionResult udtGetDefinition(String provider, String typePath) {
        logger.debug("Getting UDT definition: {} on provider: {}", typePath, provider);
        try {
            return tagExecutor.getDefinition(provider, typePath);
        } catch (Exception e) {
            logger.error("Failed to get UDT definition", e);
            return new UdtDefinitionResult();
        }
    }

    @Override
    public TagCreateResult udtCreateDefinition(
            String provider, String name, String parentTypePath, String membersJson) {
        logger.info("Creating UDT definition: {} on provider: {}", name, provider);
        try {
            return tagExecutor.createDefinition(provider, name, parentTypePath, membersJson);
        } catch (Exception e) {
            logger.error("Failed to create UDT definition", e);
            return new TagCreateResult();
        }
    }

    @Override
    public TagCreateResult udtCreateInstance(
            String parentPath, String name, String typeId, String overridesJson) {
        logger.info("Creating UDT instance: {} with type: {}", name, typeId);
        try {
            return tagExecutor.createInstance(parentPath, name, typeId, overridesJson);
        } catch (Exception e) {
            logger.error("Failed to create UDT instance", e);
            return new TagCreateResult();
        }
    }
}

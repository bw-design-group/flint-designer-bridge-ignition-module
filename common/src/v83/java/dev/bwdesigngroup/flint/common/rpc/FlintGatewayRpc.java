package dev.bwdesigngroup.flint.common.rpc;

import com.inductiveautomation.ignition.common.rpc.RpcInterface;
import com.inductiveautomation.ignition.common.rpc.RpcSerializer;
import com.inductiveautomation.ignition.common.rpc.proto.ProtoRpcSerializer;
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
import java.util.List;

/**
 * RPC interface for Gateway scope script execution. This interface is implemented by the Gateway
 * module and called by the Designer module via Ignition's RPC mechanism (8.3+).
 */
@RpcInterface(packageId = "dev.bwdesigngroup.flint")
public interface FlintGatewayRpc {

    RpcSerializer SERIALIZER = ProtoRpcSerializer.newBuilder().build();

    /**
     * Executes a Python script in the Gateway scope.
     *
     * @param code The Python code to execute
     * @param timeoutMs Maximum execution time in milliseconds
     * @param sessionId Optional session ID for variable persistence (null for fresh execution)
     * @param resetSession If true and sessionId is provided, clears the session before execution
     * @return The execution result including stdout, stderr, and timing info
     */
    ExecuteScriptResult executeScript(
            String code, int timeoutMs, String sessionId, boolean resetSession);

    /**
     * Resets a specific session, clearing all stored variables.
     *
     * @param sessionId The session ID to reset
     */
    void resetSession(String sessionId);

    /**
     * Checks if Gateway script execution is available.
     *
     * @return true if Gateway scope execution is supported
     */
    boolean isAvailable();

    /**
     * Requests a project scan on the Gateway. This causes the Gateway to re-read project resources
     * from disk.
     *
     * @return true if scan was triggered successfully
     */
    boolean requestProjectScan();

    // ==================== Perspective Session Discovery Methods ====================

    /**
     * Lists all active Perspective sessions on the Gateway.
     *
     * @return List of active sessions with metadata
     */
    PerspectiveListSessionsResult perspectiveListSessions();

    /**
     * Gets the pages within a specific Perspective session.
     *
     * @param sessionId The Perspective session ID
     * @return List of pages in the session
     */
    PerspectiveListPagesResult perspectiveGetSessionPages(String sessionId);

    /**
     * Gets the views on a specific page within a Perspective session.
     *
     * @param sessionId The Perspective session ID
     * @param pageId The page ID within the session
     * @return List of views on the page
     */
    PerspectiveListViewsResult perspectiveGetPageViews(String sessionId, String pageId);

    /**
     * Gets the component tree for a specific view within a Perspective session.
     *
     * @param sessionId The Perspective session ID
     * @param pageId The page ID within the session
     * @param viewInstanceId The view instance ID
     * @return Component tree for the view
     */
    PerspectiveListComponentsResult perspectiveGetViewComponents(
            String sessionId, String pageId, String viewInstanceId);

    /**
     * Checks if Perspective is available on this Gateway.
     *
     * @return true if Perspective module is installed and running
     */
    boolean isPerspectiveAvailable();

    // ==================== Perspective Profiling Methods ====================

    /**
     * Profiles a live Perspective view, collecting binding states, property sizes, and
     * component-level performance metrics.
     *
     * @param sessionId The Perspective session ID
     * @param pageId The page ID within the session
     * @param viewInstanceId The view instance ID
     * @return Profile data for the view
     */
    ViewProfileResult perspectiveProfileView(
            String sessionId, String pageId, String viewInstanceId);

    // ==================== Perspective Recording Methods ====================

    /**
     * Starts a binding recording session for a live Perspective view. The recording captures
     * binding state transitions at a configurable poll interval.
     *
     * @param sessionId The Perspective session ID
     * @param pageId The page ID within the session
     * @param viewInstanceId The view instance ID
     * @param pollIntervalMs Poll interval in milliseconds (default 50)
     * @param maxDurationMs Maximum recording duration in milliseconds (default 30000)
     * @param autoStopOnAllResolved Whether to auto-stop when all bindings resolve (default true)
     * @param autoStopDelayMs Delay after all bindings resolve before auto-stopping (default 500)
     * @return Recording session details including recording ID and initial binding counts
     */
    dev.bwdesigngroup.flint.common.protocol.methods.perspective.recording.StartRecordingResult
            perspectiveStartRecording(
                    String sessionId,
                    String pageId,
                    String viewInstanceId,
                    int pollIntervalMs,
                    int maxDurationMs,
                    boolean autoStopOnAllResolved,
                    int autoStopDelayMs);

    /**
     * Stops an active binding recording session.
     *
     * @param recordingId The recording session ID
     * @return Stop result with duration and event counts
     */
    dev.bwdesigngroup.flint.common.protocol.methods.perspective.recording.StopRecordingResult
            perspectiveStopRecording(String recordingId);

    /**
     * Polls for binding state transition events from an active recording.
     *
     * @param recordingId The recording session ID
     * @return Batch of events since last poll, with snapshot counts
     */
    dev.bwdesigngroup.flint.common.protocol.methods.perspective.recording.RecordingEventBatch
            perspectivePollRecordingEvents(String recordingId);

    // ==================== Perspective Script Execution Methods ====================

    /**
     * Executes a Python script in the context of a Perspective session. Optionally binds component
     * references to the script's locals.
     *
     * @param code The Python code to execute
     * @param timeoutMs Maximum execution time in milliseconds
     * @param sessionId The Perspective session ID for context
     * @param pageId Optional page ID for context
     * @param viewInstanceId Optional view instance ID for context
     * @param componentPath Optional component path to bind as 'self'
     * @param scriptSessionId Optional script session ID for variable persistence
     * @param resetSession If true, clears the script session before execution
     * @return The execution result including stdout, stderr, and timing info
     */
    ExecuteScriptResult perspectiveExecuteScript(
            String code,
            int timeoutMs,
            String sessionId,
            String pageId,
            String viewInstanceId,
            String componentPath,
            String scriptSessionId,
            boolean resetSession);

    // ==================== Perspective Completion Methods ====================

    /**
     * Gets completion items for a Perspective component's properties. Used to provide
     * autocompletion for self.props.*, self.custom.*, etc.
     *
     * @param sessionId The Perspective session ID
     * @param pageId The page ID within the session
     * @param viewInstanceId The view instance ID
     * @param componentPath The component path to get completions for
     * @param prefix The property prefix (e.g., "self.props", "self.custom")
     * @return Completion items for the component properties
     */
    PerspectiveCompletionResult perspectiveGetComponentCompletions(
            String sessionId,
            String pageId,
            String viewInstanceId,
            String componentPath,
            String prefix);

    // ==================== Tag System Methods ====================

    /**
     * Browse tags at a specific level in the tag tree.
     *
     * @param provider Tag provider name (e.g., "default")
     * @param parentPath Parent path to browse under (empty for root)
     * @param typeFilter Optional tag type filter (AtomicTag, Folder, UdtType, UdtInstance)
     * @param nameFilter Optional name filter pattern
     * @return List of tag nodes at the browsed level
     */
    TagBrowseResult tagBrowse(
            String provider, String parentPath, String typeFilter, String nameFilter);

    /**
     * Read current values for one or more tags.
     *
     * @param tagPaths List of tag paths to read
     * @return Values with quality and timestamp for each path
     */
    TagReadResult tagRead(List<String> tagPaths);

    /**
     * Write values to one or more tags.
     *
     * @param paths Tag paths to write to
     * @param values String-encoded values to write
     * @param dataTypes Data type hints for value coercion
     * @return Write status for each path
     */
    TagWriteResult tagWrite(List<String> paths, List<String> values, List<String> dataTypes);

    /**
     * Get the full configuration for a tag.
     *
     * @param tagPath Full tag path
     * @return Tag configuration as JSON string
     */
    TagGetConfigResult tagGetConfig(String tagPath);

    /**
     * Create one or more tags under a parent path.
     *
     * @param parentPath Parent path for new tags
     * @param tagsJson JSON array of tag configurations
     * @return Creation status for each tag
     */
    TagCreateResult tagCreate(String parentPath, String tagsJson);

    /**
     * Edit an existing tag's configuration.
     *
     * @param tagPath Full tag path
     * @param configJson JSON object with partial configuration to merge
     * @return Edit result
     */
    TagEditResult tagEdit(String tagPath, String configJson);

    /**
     * Delete one or more tags.
     *
     * @param tagPaths Tag paths to delete
     * @return Deletion status for each path
     */
    TagDeleteResult tagDelete(List<String> tagPaths);

    /**
     * List all available tag providers.
     *
     * @return List of provider names and types
     */
    TagProvidersResult tagGetProviders();

    // ==================== UDT System Methods ====================

    /**
     * List all UDT definitions on a tag provider.
     *
     * @param provider Tag provider name (e.g., "default")
     * @return List of UDT definitions with names and paths
     */
    UdtListResult udtGetDefinitions(String provider);

    /**
     * Get a UDT definition with its member structure.
     *
     * @param provider Tag provider name
     * @param typePath Path to the UDT type under _types_ (e.g., "Motor" or "Equipment/Motor")
     * @return Definition config with member list
     */
    UdtDefinitionResult udtGetDefinition(String provider, String typePath);

    /**
     * Create a new UDT definition with member tags.
     *
     * @param provider Tag provider name
     * @param name Name for the new UDT definition
     * @param parentTypePath Parent path under _types_ for nested UDTs (empty for root)
     * @param membersJson JSON array of member tag configurations
     * @return Creation status for the definition and its members
     */
    TagCreateResult udtCreateDefinition(
            String provider, String name, String parentTypePath, String membersJson);

    /**
     * Create an instance of a UDT definition.
     *
     * @param parentPath Parent path for the new instance
     * @param name Name for the new instance
     * @param typeId UDT type ID (definition path, e.g., "Motor" or "Equipment/Motor")
     * @param overridesJson Optional JSON object with property overrides
     * @return Creation status
     */
    TagCreateResult udtCreateInstance(
            String parentPath, String name, String typeId, String overridesJson);

    // ==================== Debug Methods ====================

    /**
     * Starts a debug session in the Gateway or Perspective scope.
     *
     * @param code The Python code to debug
     * @param filePath The file path for breakpoint matching
     * @param modulePath The module path
     * @param scope The execution scope: "gateway" or "perspective"
     * @param perspectiveSessionId Perspective session ID (only for perspective scope)
     * @param perspectivePageId Perspective page ID (only for perspective scope)
     * @param perspectiveViewInstanceId Perspective view instance ID (only for perspective scope)
     * @param perspectiveComponentPath Perspective component path (only for perspective scope)
     * @return The result containing the session ID
     */
    DebugStartSessionResult debugStartSession(
            String code,
            String filePath,
            String modulePath,
            String scope,
            String perspectiveSessionId,
            String perspectivePageId,
            String perspectiveViewInstanceId,
            String perspectiveComponentPath);

    /**
     * Stops a debug session.
     *
     * @param sessionId The debug session ID
     */
    void debugStopSession(String sessionId);

    /**
     * Sets breakpoints for a debug session.
     *
     * @param sessionId The debug session ID
     * @param filePath The file path
     * @param breakpoints The breakpoints to set
     * @return List of verified breakpoint IDs
     */
    List<Integer> debugSetBreakpoints(
            String sessionId, String filePath, List<BreakpointInfo> breakpoints);

    /**
     * Starts execution of a debug session.
     *
     * @param sessionId The debug session ID
     */
    void debugRun(String sessionId);

    /**
     * Sends a debug command (continue, stepOver, stepInto, stepOut, pause).
     *
     * @param sessionId The debug session ID
     * @param command The command name
     */
    void debugSendCommand(String sessionId, String command);

    /**
     * Polls for debug events.
     *
     * @param sessionId The debug session ID
     * @param maxWaitMs Maximum time to wait for events
     * @return Batch of debug events
     */
    DebugEventBatch debugPollEvents(String sessionId, long maxWaitMs);

    /**
     * Gets the current stack trace for a debug session.
     *
     * @param sessionId The debug session ID
     * @return The stack trace result
     */
    DebugStackTraceResult debugGetStackTrace(String sessionId);

    /**
     * Gets scopes for a stack frame.
     *
     * @param sessionId The debug session ID
     * @param frameId The frame ID
     * @return The scopes result
     */
    DebugScopesResult debugGetScopes(String sessionId, int frameId);

    /**
     * Gets variables for a variable reference.
     *
     * @param sessionId The debug session ID
     * @param variablesReference The variables reference ID
     * @return The variables result
     */
    DebugVariablesResult debugGetVariables(String sessionId, int variablesReference);

    /**
     * Evaluates an expression in the debug session.
     *
     * @param sessionId The debug session ID
     * @param expression The expression to evaluate
     * @param frameId Optional frame ID for context
     * @return The evaluation result
     */
    DebugEvaluateResult debugEvaluate(String sessionId, String expression, Integer frameId);
}

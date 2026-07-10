package dev.bwdesigngroup.flint.common;

import java.nio.file.Path;
import java.nio.file.Paths;

/** Constants used throughout the Flint Designer Bridge module. */
public final class FlintConstants {

    private FlintConstants() {
        // Prevent instantiation
    }

    /** Module identification */
    public static final String MODULE_ID = "dev.bwdesigngroup.flint.FlintDesignerBridge";

    public static final String MODULE_NAME = "Flint Designer Bridge";

    /** Registry directory for Designer discovery */
    public static final String FLINT_REGISTRY_DIR = ".ignition/flint/designers";

    /** Port range for WebSocket server */
    public static final int PORT_RANGE_START = 52400;

    public static final int PORT_RANGE_END = 52500;

    /** Authentication timeout in milliseconds */
    public static final int AUTH_TIMEOUT_MS = 5000;

    /** Secret length for authentication */
    public static final int SECRET_LENGTH = 32;

    /** JSON-RPC method names */
    public static final String METHOD_AUTHENTICATE = "authenticate";

    public static final String METHOD_PING = "ping";
    public static final String METHOD_EXECUTE_SCRIPT = "executeScript";
    public static final String METHOD_SHOW_MESSAGE = "showMessage";
    public static final String METHOD_PROJECT_SCAN = "project.scan";

    /** Designer navigation JSON-RPC method names */
    public static final String METHOD_OPEN_RESOURCE = "designer.openResource";

    /** Project JSON-RPC method names */
    public static final String METHOD_LIST_RESOURCES = "project.listResources";

    /** Debug JSON-RPC method names */
    public static final String METHOD_DEBUG_START_SESSION = "debug.startSession";

    public static final String METHOD_DEBUG_STOP_SESSION = "debug.stopSession";
    public static final String METHOD_DEBUG_SET_BREAKPOINTS = "debug.setBreakpoints";
    public static final String METHOD_DEBUG_RUN = "debug.run";
    public static final String METHOD_DEBUG_CONTINUE = "debug.continue";
    public static final String METHOD_DEBUG_STEP_OVER = "debug.stepOver";
    public static final String METHOD_DEBUG_STEP_INTO = "debug.stepInto";
    public static final String METHOD_DEBUG_STEP_OUT = "debug.stepOut";
    public static final String METHOD_DEBUG_PAUSE = "debug.pause";
    public static final String METHOD_DEBUG_GET_STACK_TRACE = "debug.getStackTrace";
    public static final String METHOD_DEBUG_GET_SCOPES = "debug.getScopes";
    public static final String METHOD_DEBUG_GET_VARIABLES = "debug.getVariables";
    public static final String METHOD_DEBUG_EVALUATE = "debug.evaluate";
    public static final String METHOD_DEBUG_POLL_EVENTS = "debug.pollEvents";

    /** Debug timeout in milliseconds (30 minutes for debug sessions) */
    public static final int DEBUG_TIMEOUT_MS = 30 * 60 * 1000;

    /** LSP JSON-RPC method names */
    public static final String METHOD_LSP_COMPLETION = "lsp.completion";

    public static final String METHOD_LSP_HOVER = "lsp.hover";
    public static final String METHOD_LSP_SIGNATURE_HELP = "lsp.signatureHelp";
    public static final String METHOD_LSP_DEFINITION = "lsp.definition";
    public static final String METHOD_LSP_REFERENCES = "lsp.references";
    public static final String METHOD_LSP_INVALIDATE_CACHE = "lsp.invalidateCache";

    /** LSP document sync + feature methods for the headless language server */
    public static final String METHOD_LSP_DID_OPEN = "lsp.didOpen";

    public static final String METHOD_LSP_DID_CHANGE = "lsp.didChange";
    public static final String METHOD_LSP_DID_CLOSE = "lsp.didClose";
    public static final String METHOD_LSP_DIAGNOSTICS = "lsp.diagnostics";
    public static final String METHOD_LSP_DOCUMENT_SYMBOL = "lsp.documentSymbol";
    public static final String METHOD_LSP_WORKSPACE_SYMBOL = "lsp.workspaceSymbol";
    public static final String METHOD_LSP_REINDEX = "lsp.reindex";

    /** LSP notification method names (push from Designer to VS Code) */
    public static final String NOTIFICATION_LSP_CACHE_INVALIDATED = "lsp.cacheInvalidated";

    /** Perspective JSON-RPC method names */
    public static final String METHOD_PERSPECTIVE_IS_AVAILABLE = "perspective.isAvailable";

    public static final String METHOD_PERSPECTIVE_LIST_SESSIONS = "perspective.listSessions";
    public static final String METHOD_PERSPECTIVE_GET_SESSION_PAGES = "perspective.getSessionPages";
    public static final String METHOD_PERSPECTIVE_GET_PAGE_VIEWS = "perspective.getPageViews";
    public static final String METHOD_PERSPECTIVE_GET_VIEW_COMPONENTS =
            "perspective.getViewComponents";
    public static final String METHOD_PERSPECTIVE_EXECUTE_SCRIPT = "perspective.executeScript";
    public static final String METHOD_PERSPECTIVE_GET_COMPONENT_COMPLETIONS =
            "perspective.getComponentCompletions";
    public static final String METHOD_PERSPECTIVE_PROFILE_VIEW = "perspective.profileView";
    public static final String METHOD_PERSPECTIVE_START_RECORDING = "perspective.startRecording";
    public static final String METHOD_PERSPECTIVE_STOP_RECORDING = "perspective.stopRecording";
    public static final String METHOD_PERSPECTIVE_POLL_RECORDING = "perspective.pollRecording";

    /** Perspective recording notification method names (push from Designer to VS Code) */
    public static final String NOTIFICATION_PERSPECTIVE_RECORDING_EVENT =
            "perspective.recordingEvent";

    public static final String NOTIFICATION_PERSPECTIVE_RECORDING_COMPLETE =
            "perspective.recordingComplete";

    /** Browser/CDP JSON-RPC method names */
    public static final String METHOD_BROWSER_GET_CDP_INFO = "browser.getCdpInfo";

    /** Designer workspace JSON-RPC method names */
    public static final String METHOD_DESIGNER_GET_OPEN_TABS = "designer.getOpenTabs";

    public static final String METHOD_DESIGNER_TOGGLE_PREVIEW = "designer.togglePreviewMode";

    /** Component schema JSON-RPC method names */
    public static final String METHOD_COMPONENT_LIST = "component.list";

    public static final String METHOD_COMPONENT_GET_SCHEMA = "component.getSchema";

    /** View catalog JSON-RPC method names */
    public static final String METHOD_VIEW_CATALOG = "project.getViewCatalog";

    /** Icon registry JSON-RPC method names */
    public static final String METHOD_ICON_LIST = "icon.list";

    public static final String METHOD_ICON_SEARCH = "icon.search";

    /** Tag system JSON-RPC method names */
    public static final String METHOD_TAGS_BROWSE = "tags.browse";

    public static final String METHOD_TAGS_READ = "tags.read";
    public static final String METHOD_TAGS_WRITE = "tags.write";
    public static final String METHOD_TAGS_GET_CONFIG = "tags.getConfig";
    public static final String METHOD_TAGS_CREATE = "tags.create";
    public static final String METHOD_TAGS_EDIT = "tags.edit";
    public static final String METHOD_TAGS_DELETE = "tags.delete";
    public static final String METHOD_TAGS_GET_PROVIDERS = "tags.getProviders";

    /** UDT system JSON-RPC method names */
    public static final String METHOD_UDT_GET_DEFINITIONS = "udt.getDefinitions";

    public static final String METHOD_UDT_GET_DEFINITION = "udt.getDefinition";
    public static final String METHOD_UDT_CREATE_DEFINITION = "udt.createDefinition";
    public static final String METHOD_UDT_CREATE_INSTANCE = "udt.createInstance";

    /** View editing JSON-RPC method names */
    public static final String METHOD_VIEW_GET_CONFIG = "view.getConfig";

    public static final String METHOD_VIEW_SET_CONFIG = "view.setConfig";
    public static final String METHOD_VIEW_GET_COMPONENT = "view.getComponent";
    public static final String METHOD_VIEW_SET_COMPONENT = "view.setComponent";
    public static final String METHOD_VIEW_VALIDATE = "view.validate";
    public static final String METHOD_VIEW_GET_TREE = "view.getTree";
    public static final String METHOD_VIEW_SAVE = "view.save";
    public static final String METHOD_VIEW_CREATE = "view.create";
    public static final String METHOD_VIEW_DELETE = "view.delete";

    /** LSP completion item kinds (based on LSP spec) */
    public static final int COMPLETION_KIND_TEXT = 1;

    public static final int COMPLETION_KIND_METHOD = 2;
    public static final int COMPLETION_KIND_FUNCTION = 3;
    public static final int COMPLETION_KIND_CONSTRUCTOR = 4;
    public static final int COMPLETION_KIND_FIELD = 5;
    public static final int COMPLETION_KIND_VARIABLE = 6;
    public static final int COMPLETION_KIND_CLASS = 7;
    public static final int COMPLETION_KIND_INTERFACE = 8;
    public static final int COMPLETION_KIND_MODULE = 9;
    public static final int COMPLETION_KIND_PROPERTY = 10;
    public static final int COMPLETION_KIND_CONSTANT = 21;

    /** Reset session JSON-RPC method name (gateway HTTP transport) */
    public static final String METHOD_RESET_SESSION = "resetSession";

    /** Gateway HTTP transport constants */
    public static final String GATEWAY_ROUTE_ALIAS = "flint";

    public static final String GATEWAY_ROUTE_RPC = "/rpc";
    public static final String GATEWAY_ROUTE_HEALTH = "/health";

    /**
     * Raw-LSP-over-WebSocket transport constants. The servlet mounts at {@code /system/<name>} on
     * the gateway (same port/TLS as the HTTP transport) so LSP clients connect directly to the
     * engine.
     */
    public static final String GATEWAY_WS_LSP_NAME = "flint-lsp";

    public static final String GATEWAY_WS_LSP_PATH = "/system/" + GATEWAY_WS_LSP_NAME;

    /**
     * {@code ServletContext} attribute under which the gateway hook publishes the {@code
     * LspWebSocketBridge} so the container-instantiated WebSocket servlet can find it in {@code
     * init()}.
     */
    public static final String LSP_WS_BRIDGE_ATTR = "dev.bwdesigngroup.flint.lspWebSocketBridge";

    /**
     * System property / environment variable that lets an operator supply the gateway API token
     * instead of using the module-generated one. Useful for containers.
     */
    public static final String GATEWAY_API_TOKEN_PROPERTY = "flint.gateway.apiToken";

    /** JSON-RPC version */
    public static final String JSONRPC_VERSION = "2.0";

    /**
     * Gets the path to the registry directory.
     *
     * @return Path to ~/.ignition/flint/designers/
     */
    public static Path getRegistryDirectory() {
        String userHome = System.getProperty("user.home");
        return Paths.get(userHome, FLINT_REGISTRY_DIR);
    }

    /**
     * Gets the registry file name for a specific Designer instance.
     *
     * @param pid The process ID of the Designer
     * @return The registry file name
     */
    public static String getRegistryFileName(long pid) {
        return "designer-" + pid + ".json";
    }

    /**
     * Gets the full path to a Designer's registry file.
     *
     * @param pid The process ID of the Designer
     * @return The full path to the registry file
     */
    public static Path getRegistryFilePath(long pid) {
        return getRegistryDirectory().resolve(getRegistryFileName(pid));
    }

    /**
     * Gets the lock file name for a specific Designer instance.
     *
     * @param pid The process ID of the Designer
     * @return The lock file name
     */
    public static String getLockFileName(long pid) {
        return "designer-" + pid + ".lock";
    }

    /**
     * Gets the full path to a Designer's lock file.
     *
     * @param pid The process ID of the Designer
     * @return The full path to the lock file
     */
    public static Path getLockFilePath(long pid) {
        return getRegistryDirectory().resolve(getLockFileName(pid));
    }
}

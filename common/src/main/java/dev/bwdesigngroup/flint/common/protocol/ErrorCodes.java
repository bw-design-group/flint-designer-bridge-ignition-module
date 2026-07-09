package dev.bwdesigngroup.flint.common.protocol;

/** JSON-RPC 2.0 error codes. */
public final class ErrorCodes {

    private ErrorCodes() {
        // Prevent instantiation
    }

    // Standard JSON-RPC 2.0 error codes
    public static final int PARSE_ERROR = -32700;
    public static final int INVALID_REQUEST = -32600;
    public static final int METHOD_NOT_FOUND = -32601;
    public static final int INVALID_PARAMS = -32602;
    public static final int INTERNAL_ERROR = -32603;

    // Custom error codes (range: -32000 to -32099)
    public static final int NOT_AUTHENTICATED = -32000;
    public static final int AUTHENTICATION_FAILED = -32001;
    public static final int AUTHENTICATION_TIMEOUT = -32002;
    public static final int SCRIPT_EXECUTION_ERROR = -32010;
    public static final int SCRIPT_TIMEOUT = -32011;

    // Gateway scope error codes
    public static final int GATEWAY_RPC_ERROR = -32020;
    public static final int GATEWAY_NOT_AVAILABLE = -32021;
    public static final int GATEWAY_SCOPE_NOT_SUPPORTED = -32022;

    // Browser/CDP error codes
    public static final int CDP_NOT_AVAILABLE = -32030;

    // View editing error codes
    public static final int VIEW_NOT_FOUND = -32040;
    public static final int VIEW_VALIDATION_ERROR = -32041;
    public static final int VIEW_WRITE_ERROR = -32042;
    public static final int COMPONENT_NOT_FOUND = -32043;
    public static final int VIEW_ALREADY_EXISTS = -32044;

    // Tag system error codes
    public static final int TAG_NOT_FOUND = -32050;
    public static final int TAG_READ_ERROR = -32051;
    public static final int TAG_WRITE_ERROR = -32052;
    public static final int TAG_CONFIG_ERROR = -32053;
    public static final int TAG_CREATE_ERROR = -32054;
    public static final int TAG_DELETE_ERROR = -32055;
    public static final int TAG_PROVIDER_ERROR = -32056;
}

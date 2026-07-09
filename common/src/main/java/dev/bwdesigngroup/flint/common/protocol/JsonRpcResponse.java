package dev.bwdesigngroup.flint.common.protocol;

/** Represents a JSON-RPC 2.0 response. */
public class JsonRpcResponse {
    private final String jsonrpc = "2.0";
    private Object result;
    private JsonRpcError error;
    private Object id;

    private JsonRpcResponse() {}

    /** Creates a successful response. */
    public static JsonRpcResponse success(Object result, Object id) {
        JsonRpcResponse response = new JsonRpcResponse();
        response.result = result;
        response.id = id;
        return response;
    }

    /** Creates an error response. */
    public static JsonRpcResponse error(JsonRpcError error, Object id) {
        JsonRpcResponse response = new JsonRpcResponse();
        response.error = error;
        response.id = id;
        return response;
    }

    /** Creates an error response from error code and message. */
    public static JsonRpcResponse error(int code, String message, Object id) {
        return error(new JsonRpcError(code, message), id);
    }

    /** Creates an error response with additional data. */
    public static JsonRpcResponse error(int code, String message, Object data, Object id) {
        return error(new JsonRpcError(code, message, data), id);
    }

    public String getJsonrpc() {
        return jsonrpc;
    }

    public Object getResult() {
        return result;
    }

    public JsonRpcError getError() {
        return error;
    }

    public Object getId() {
        return id;
    }

    public boolean isSuccess() {
        return error == null;
    }
}

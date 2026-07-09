package dev.bwdesigngroup.flint.common.protocol;

/**
 * Represents a JSON-RPC 2.0 notification. Notifications are requests without an id field, meaning
 * no response is expected.
 */
public class JsonRpcNotification {
    private String jsonrpc;
    private String method;
    private Object params;

    public JsonRpcNotification() {}

    public JsonRpcNotification(String method, Object params) {
        this.jsonrpc = "2.0";
        this.method = method;
        this.params = params;
    }

    public String getJsonrpc() {
        return jsonrpc;
    }

    public void setJsonrpc(String jsonrpc) {
        this.jsonrpc = jsonrpc;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public Object getParams() {
        return params;
    }

    public void setParams(Object params) {
        this.params = params;
    }

    public boolean isValid() {
        return "2.0".equals(jsonrpc) && method != null && !method.isEmpty();
    }
}

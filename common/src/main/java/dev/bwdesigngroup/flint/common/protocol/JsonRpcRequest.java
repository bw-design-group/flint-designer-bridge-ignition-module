package dev.bwdesigngroup.flint.common.protocol;

import com.google.gson.JsonElement;

/** Represents a JSON-RPC 2.0 request. */
public class JsonRpcRequest {
    private String jsonrpc;
    private String method;
    private JsonElement params;
    private Object id;

    public JsonRpcRequest() {}

    public JsonRpcRequest(String method, JsonElement params, Object id) {
        this.jsonrpc = "2.0";
        this.method = method;
        this.params = params;
        this.id = id;
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

    public JsonElement getParams() {
        return params;
    }

    public void setParams(JsonElement params) {
        this.params = params;
    }

    public Object getId() {
        return id;
    }

    public void setId(Object id) {
        this.id = id;
    }

    public boolean isValid() {
        return "2.0".equals(jsonrpc) && method != null && !method.isEmpty();
    }

    public boolean isNotification() {
        return id == null;
    }
}

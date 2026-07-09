package dev.bwdesigngroup.flint.gateway.http;

/**
 * Servlet-free carrier for an HTTP response produced by the version-neutral dispatcher. The
 * version-specific route handler writes this to the (javax/jakarta) {@code HttpServletResponse}.
 */
public class HttpRpcResult {

    private final int status;
    private final String body;

    public HttpRpcResult(int status, String body) {
        this.status = status;
        this.body = body;
    }

    public int getStatus() {
        return status;
    }

    public String getBody() {
        return body;
    }
}

package dev.bwdesigngroup.flint.gateway.lsp.ws;

/**
 * Transport-agnostic sink for outbound LSP frames, implemented by the per-target WebSocket
 * endpoints (Jetty 10 on 8.1, Jetty 12 ee10 on 8.3). Keeps the shared session/router/codec free of
 * any Jetty types so they compile identically under both build targets.
 */
public interface MessageSink {

    /** Sends one already-framed text message to the client. Must not throw on a closed sink. */
    void send(String message);

    /** Initiates a WebSocket close with the given status code and reason. */
    void close(int statusCode, String reason);

    /** Whether the underlying socket is still open for writes. */
    boolean isOpen();
}

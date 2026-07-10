package dev.bwdesigngroup.flint.gateway.lsp.ws;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Stateful codec for LSP messages over a WebSocket text channel.
 *
 * <p>Two client conventions are supported and auto-detected from the first frame:
 *
 * <ul>
 *   <li><b>Content-Length framing</b> (the base LSP transport, used by {@code
 *       vscode-languageclient} over a socket duplex): {@code Content-Length: N\r\n\r\n<body>}. A
 *       single logical message may be split across WebSocket frames, and several messages may
 *       arrive coalesced in one frame; both are handled by buffering.
 *   <li><b>Bare JSON</b> (monaco-style clients): one complete JSON object per WebSocket text frame,
 *       no headers. Detected when a fresh frame's first non-whitespace byte is <code>{</code>.
 * </ul>
 *
 * <p>The codec remembers which convention it saw so {@link #encode(String)} replies symmetrically.
 * All framing math is done in UTF-8 bytes because {@code Content-Length} counts bytes, not chars.
 * Pure and unit-testable — it never touches the network.
 */
public final class LspFrameCodec {

    /** Default cap on a single buffered message (and on unframed buffer growth). */
    public static final int DEFAULT_MAX_MESSAGE_BYTES = 32 * 1024 * 1024;

    private static final byte[] HEADER_SEPARATOR = {'\r', '\n', '\r', '\n'};
    private static final String CONTENT_LENGTH_HEADER = "content-length:";

    private final int maxMessageBytes;

    /** Pending, not-yet-complete bytes for the Content-Length path. */
    private byte[] buffer = new byte[0];

    /** null until the first frame decides the convention; TRUE=framed, FALSE=bare JSON. */
    private Boolean framed;

    public LspFrameCodec() {
        this(DEFAULT_MAX_MESSAGE_BYTES);
    }

    public LspFrameCodec(int maxMessageBytes) {
        this.maxMessageBytes = maxMessageBytes;
    }

    /**
     * Feeds one inbound WebSocket <b>text</b> frame; see {@link #decode(byte[])}. A convenience for
     * clients that send LSP as UTF-8 text frames.
     */
    public List<String> decode(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return new ArrayList<>();
        }
        return decode(chunk.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Feeds one inbound WebSocket frame (text or <b>binary</b>) and returns any complete LSP
     * message bodies (raw JSON strings) it yields. May return zero (partial framed message) or
     * several (coalesced) messages. Binary frames matter because {@code ws}'s {@code
     * createWebSocketStream} duplex — used by the VS Code client — sends binary by default.
     *
     * @throws IllegalStateException if a message or the pending buffer exceeds the configured max.
     */
    public List<String> decode(byte[] chunk) {
        List<String> out = new ArrayList<>();
        if (chunk == null || chunk.length == 0) {
            return out;
        }

        // Bare-JSON fast path: a fresh frame (nothing buffered) that starts with '{' is one
        // message.
        if (buffer.length == 0 && startsWithJsonObject(chunk)) {
            markFramed(false);
            out.add(new String(chunk, StandardCharsets.UTF_8).trim());
            return out;
        }

        markFramed(true);
        append(chunk);
        drainFramed(out);
        return out;
    }

    /**
     * Wraps a JSON message for the wire using the convention detected on decode (framed default).
     */
    public String encode(String json) {
        String body = json != null ? json : "";
        if (Boolean.FALSE.equals(framed)) {
            return body;
        }
        int length = body.getBytes(StandardCharsets.UTF_8).length;
        return "Content-Length: " + length + "\r\n\r\n" + body;
    }

    private void drainFramed(List<String> out) {
        while (true) {
            int headerEnd = indexOf(buffer, HEADER_SEPARATOR);
            if (headerEnd < 0) {
                if (buffer.length > maxMessageBytes) {
                    throw new IllegalStateException(
                            "LSP header exceeds max message size (" + maxMessageBytes + " bytes)");
                }
                return; // need more bytes for a complete header
            }

            int contentLength =
                    parseContentLength(new String(buffer, 0, headerEnd, StandardCharsets.US_ASCII));
            if (contentLength < 0) {
                // Malformed/absent Content-Length: drop the header and resynchronize.
                buffer = slice(buffer, headerEnd + HEADER_SEPARATOR.length, buffer.length);
                continue;
            }
            if (contentLength > maxMessageBytes) {
                throw new IllegalStateException(
                        "LSP message length " + contentLength + " exceeds max " + maxMessageBytes);
            }

            int bodyStart = headerEnd + HEADER_SEPARATOR.length;
            if (buffer.length - bodyStart < contentLength) {
                return; // body not fully arrived yet
            }

            out.add(new String(buffer, bodyStart, contentLength, StandardCharsets.UTF_8));
            buffer = slice(buffer, bodyStart + contentLength, buffer.length);
        }
    }

    private void append(byte[] incoming) {
        if (buffer.length == 0) {
            buffer = incoming;
            return;
        }
        byte[] combined = new byte[buffer.length + incoming.length];
        System.arraycopy(buffer, 0, combined, 0, buffer.length);
        System.arraycopy(incoming, 0, combined, buffer.length, incoming.length);
        buffer = combined;
    }

    private void markFramed(boolean value) {
        if (framed == null) {
            framed = value;
        }
    }

    private static boolean startsWithJsonObject(byte[] chunk) {
        for (byte b : chunk) {
            if (b == ' ' || b == '\t' || b == '\n' || b == '\r') {
                continue;
            }
            return b == '{';
        }
        return false;
    }

    /**
     * Parses the byte count from an LSP header block, or -1 if no valid Content-Length is present.
     */
    private static int parseContentLength(String headerBlock) {
        for (String line : headerBlock.split("\r\n")) {
            String trimmed = line.trim();
            if (trimmed.toLowerCase().startsWith(CONTENT_LENGTH_HEADER)) {
                String value = trimmed.substring(CONTENT_LENGTH_HEADER.length()).trim();
                try {
                    int length = Integer.parseInt(value);
                    return length >= 0 ? length : -1;
                } catch (NumberFormatException e) {
                    return -1;
                }
            }
        }
        return -1;
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static byte[] slice(byte[] source, int from, int to) {
        byte[] result = new byte[to - from];
        System.arraycopy(source, from, result, 0, result.length);
        return result;
    }
}

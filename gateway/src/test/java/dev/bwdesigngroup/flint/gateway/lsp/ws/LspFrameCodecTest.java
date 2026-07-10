package dev.bwdesigngroup.flint.gateway.lsp.ws;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("LspFrameCodec")
class LspFrameCodecTest {

    @Nested
    @DisplayName("Content-Length framing")
    class ContentLengthFraming {

        @Test
        @DisplayName("decodes a single framed message")
        void decodesSingleMessage() {
            LspFrameCodec codec = new LspFrameCodec();
            List<String> messages = codec.decode("Content-Length: 7\r\n\r\n{\"a\":1}");
            assertEquals(List.of("{\"a\":1}"), messages);
        }

        @Test
        @DisplayName("reassembles a message split across frames")
        void reassemblesSplitMessage() {
            LspFrameCodec codec = new LspFrameCodec();
            assertTrue(codec.decode("Content-Length: 7\r\n").isEmpty());
            assertTrue(codec.decode("\r\n{\"a\"").isEmpty());
            assertEquals(List.of("{\"a\":1}"), codec.decode(":1}"));
        }

        @Test
        @DisplayName("splits coalesced messages arriving in one frame")
        void splitsCoalescedMessages() {
            LspFrameCodec codec = new LspFrameCodec();
            String wire =
                    "Content-Length: 7\r\n\r\n{\"a\":1}" + "Content-Length: 7\r\n\r\n{\"b\":2}";
            assertEquals(List.of("{\"a\":1}", "{\"b\":2}"), codec.decode(wire));
        }

        @Test
        @DisplayName("counts Content-Length in UTF-8 bytes, not chars")
        void countsBytesNotChars() {
            LspFrameCodec codec = new LspFrameCodec();
            String body = "{\"s\":\"é\"}"; // 'é' is two UTF-8 bytes
            int byteLength = body.getBytes(StandardCharsets.UTF_8).length;
            List<String> messages =
                    codec.decode("Content-Length: " + byteLength + "\r\n\r\n" + body);
            assertEquals(List.of(body), messages);
        }

        @Test
        @DisplayName("encodes with a byte-accurate Content-Length header")
        void encodesFramed() {
            LspFrameCodec codec = new LspFrameCodec();
            String body = "{\"s\":\"é\"}";
            int byteLength = body.getBytes(StandardCharsets.UTF_8).length;
            assertEquals("Content-Length: " + byteLength + "\r\n\r\n" + body, codec.encode(body));
        }
    }

    @Nested
    @DisplayName("bare JSON auto-detection")
    class BareJson {

        @Test
        @DisplayName("treats a frame starting with '{' as one message")
        void decodesBareFrame() {
            LspFrameCodec codec = new LspFrameCodec();
            assertEquals(List.of("{\"jsonrpc\":\"2.0\"}"), codec.decode("{\"jsonrpc\":\"2.0\"}"));
        }

        @Test
        @DisplayName("ignores leading whitespace when detecting bare JSON")
        void detectsAfterWhitespace() {
            LspFrameCodec codec = new LspFrameCodec();
            assertEquals(List.of("{\"a\":1}"), codec.decode("  \n{\"a\":1}"));
        }

        @Test
        @DisplayName("encodes bare after a bare frame was decoded")
        void encodesBareAfterBareDecode() {
            LspFrameCodec codec = new LspFrameCodec();
            codec.decode("{\"a\":1}");
            assertEquals("{\"b\":2}", codec.encode("{\"b\":2}"));
        }
    }

    @Nested
    @DisplayName("binary frames")
    class BinaryFrames {

        @Test
        @DisplayName("decodes a Content-Length message delivered as bytes")
        void decodesFramedBinary() {
            LspFrameCodec codec = new LspFrameCodec();
            byte[] frame = "Content-Length: 7\r\n\r\n{\"a\":1}".getBytes(StandardCharsets.UTF_8);
            assertEquals(List.of("{\"a\":1}"), codec.decode(frame));
        }

        @Test
        @DisplayName("decodes a bare-JSON binary frame")
        void decodesBareBinary() {
            LspFrameCodec codec = new LspFrameCodec();
            byte[] frame = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);
            assertEquals(List.of("{\"a\":1}"), codec.decode(frame));
        }

        @Test
        @DisplayName("reassembles a framed message split across binary chunks")
        void reassemblesSplitBinary() {
            LspFrameCodec codec = new LspFrameCodec();
            assertTrue(
                    codec.decode("Content-Length: 7\r\n\r\n".getBytes(StandardCharsets.UTF_8))
                            .isEmpty());
            assertEquals(
                    List.of("{\"a\":1}"),
                    codec.decode("{\"a\":1}".getBytes(StandardCharsets.UTF_8)));
        }

        @Test
        @DisplayName("mixes binary and text chunks of one framed message")
        void mixesBinaryAndText() {
            LspFrameCodec codec = new LspFrameCodec();
            assertTrue(
                    codec.decode("Content-Length: 7\r\n\r\n{\"a\"".getBytes(StandardCharsets.UTF_8))
                            .isEmpty());
            assertEquals(List.of("{\"a\":1}"), codec.decode(":1}"));
        }
    }

    @Nested
    @DisplayName("guards")
    class Guards {

        @Test
        @DisplayName("rejects a message whose declared length exceeds the max")
        void rejectsOversizeMessage() {
            LspFrameCodec codec = new LspFrameCodec(10);
            assertThrows(
                    IllegalStateException.class,
                    () -> codec.decode("Content-Length: 1000\r\n\r\n"));
        }

        @Test
        @DisplayName("rejects an unterminated header that overflows the buffer")
        void rejectsOversizeHeader() {
            LspFrameCodec codec = new LspFrameCodec(8);
            assertThrows(
                    IllegalStateException.class,
                    () -> codec.decode("Content-Length: 5 with no terminator yet"));
        }

        @Test
        @DisplayName("returns nothing for empty or null input")
        void handlesEmptyInput() {
            LspFrameCodec codec = new LspFrameCodec();
            assertTrue(codec.decode("").isEmpty());
            assertTrue(codec.decode((String) null).isEmpty());
            assertTrue(codec.decode((byte[]) null).isEmpty());
        }
    }
}

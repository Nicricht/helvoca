package cl.helvoca.ai.gemini;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Reassembles Gemini Live JSON messages regardless of whether Google delivers
 * them as WebSocket text or binary frames. Gemini Live commonly returns its
 * server JSON, including setupComplete, as binary UTF-8 payloads.
 */
final class GeminiWebSocketJsonFrames {
    private static final int MAX_MESSAGE_BYTES = 4 * 1024 * 1024;

    private final StringBuilder textBuffer = new StringBuilder();
    private final ByteArrayOutputStream binaryBuffer = new ByteArrayOutputStream();

    synchronized String acceptText(CharSequence data, boolean last) {
        if (data != null) {
            if (textBuffer.length() + data.length() > MAX_MESSAGE_BYTES) {
                reset();
                throw new IllegalArgumentException("Gemini text message exceeded safe size");
            }
            textBuffer.append(data);
        }
        if (!last) return null;

        String payload = textBuffer.toString();
        textBuffer.setLength(0);
        return payload;
    }

    synchronized String acceptBinary(ByteBuffer data, boolean last) {
        if (data != null) {
            int remaining = data.remaining();
            if (binaryBuffer.size() + remaining > MAX_MESSAGE_BYTES) {
                reset();
                throw new IllegalArgumentException("Gemini binary message exceeded safe size");
            }
            byte[] chunk = new byte[remaining];
            data.get(chunk);
            binaryBuffer.writeBytes(chunk);
        }
        if (!last) return null;

        String payload = binaryBuffer.toString(StandardCharsets.UTF_8);
        binaryBuffer.reset();
        return payload;
    }

    synchronized void reset() {
        textBuffer.setLength(0);
        binaryBuffer.reset();
    }
}

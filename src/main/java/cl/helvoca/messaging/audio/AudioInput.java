package cl.helvoca.messaging.audio;

import java.util.UUID;

public record AudioInput(
        byte[] bytes,
        String mimeType,
        String languageHint,
        UUID businessId,
        String messageId,
        String correlationId) {
}

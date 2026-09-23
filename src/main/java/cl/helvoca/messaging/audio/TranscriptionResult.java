package cl.helvoca.messaging.audio;

import java.time.Duration;

public record TranscriptionResult(
        String text,
        String providerId,
        String modelId,
        Duration providerLatency,
        int attemptCount) {
}

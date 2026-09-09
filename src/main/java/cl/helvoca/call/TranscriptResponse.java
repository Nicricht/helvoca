package cl.helvoca.call;

import java.time.Instant;
import java.util.UUID;

public record TranscriptResponse(UUID id, String speaker, String content, int sequenceNumber, Instant createdAt) {
    static TranscriptResponse from(CallTranscript item) {
        return new TranscriptResponse(item.getId(), item.getSpeaker(), item.getContent(),
                item.getSequenceNumber(), item.getCreatedAt());
    }
}

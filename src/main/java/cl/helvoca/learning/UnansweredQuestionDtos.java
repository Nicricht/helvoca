package cl.helvoca.learning;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;

public final class UnansweredQuestionDtos {
    private UnansweredQuestionDtos() {}

    public record Answer(@NotBlank String answer) {}

    public record Response(
            UUID id,
            UUID callId,
            UUID customerId,
            String question,
            int occurrences,
            QuestionStatus status,
            String answer,
            UUID knowledgeItemId,
            Instant firstSeenAt,
            Instant lastSeenAt,
            Instant answeredAt
    ) {
        public static Response from(UnansweredQuestion q) {
            return new Response(q.getId(), q.getCallId(), q.getCustomerId(), q.getQuestion(), q.getOccurrences(),
                    q.getStatus(), q.getAnswer(), q.getKnowledgeItemId(), q.getFirstSeenAt(), q.getLastSeenAt(), q.getAnsweredAt());
        }
    }
}

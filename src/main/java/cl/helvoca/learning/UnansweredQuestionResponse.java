package cl.helvoca.learning;

import java.time.Instant;
import java.util.UUID;

public record UnansweredQuestionResponse(
        UUID id,
        UUID customerId,
        UUID callId,
        String question,
        String answer,
        UnansweredQuestionStatus status,
        int occurrences,
        Instant firstAskedAt,
        Instant lastAskedAt,
        Instant answeredAt
) {
    public static UnansweredQuestionResponse from(UnansweredQuestion item) {
        return new UnansweredQuestionResponse(
                item.getId(), item.getCustomerId(), item.getCallId(), item.getQuestion(), item.getAnswer(),
                item.getStatus(), item.getOccurrences(), item.getFirstAskedAt(), item.getLastAskedAt(), item.getAnsweredAt());
    }
}

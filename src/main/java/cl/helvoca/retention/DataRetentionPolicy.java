package cl.helvoca.retention;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public final class DataRetentionPolicy {
    public static final Duration CALL_CONTENT_RETENTION = Duration.ofDays(90);
    public static final Duration CALL_SESSION_RETENTION = Duration.ofDays(180);
    public static final Duration MESSAGE_CONTENT_RETENTION = Duration.ofDays(90);
    public static final Duration CONVERSATION_RETENTION = Duration.ofDays(180);
    public static final int CUSTOMER_INACTIVITY_REVIEW_MONTHS = 24;
    public static final int OPERATION_RETENTION_MONTHS = 24;
    public static final int AUDIT_RETENTION_MONTHS = 24;

    private DataRetentionPolicy() {}

    public static Cutoffs cutoffs(Instant now) {
        if (now == null) throw new IllegalArgumentException("now is required");
        OffsetDateTime utc = OffsetDateTime.ofInstant(now, ZoneOffset.UTC);
        return new Cutoffs(
                now.minus(CALL_CONTENT_RETENTION),
                now.minus(CALL_SESSION_RETENTION),
                now.minus(MESSAGE_CONTENT_RETENTION),
                now.minus(CONVERSATION_RETENTION),
                utc.minusMonths(CUSTOMER_INACTIVITY_REVIEW_MONTHS).toInstant(),
                utc.minusMonths(OPERATION_RETENTION_MONTHS).toInstant(),
                utc.minusMonths(AUDIT_RETENTION_MONTHS).toInstant());
    }

    public record Cutoffs(
            Instant callContentBefore,
            Instant callSessionsBefore,
            Instant messageContentBefore,
            Instant conversationsBefore,
            Instant customerReviewBefore,
            Instant operationsBefore,
            Instant auditBefore
    ) {}
}

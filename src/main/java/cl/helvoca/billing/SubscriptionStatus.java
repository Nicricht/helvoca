package cl.helvoca.billing;

import java.time.Instant;

public enum SubscriptionStatus {
    TRIALING,
    ACTIVE,
    PAST_DUE,
    SUSPENDED,
    CANCELED;

    public boolean allowsService(Instant now, Instant graceUntil) {
        return switch (this) {
            case TRIALING, ACTIVE -> true;
            case PAST_DUE -> graceUntil != null && !now.isAfter(graceUntil);
            case SUSPENDED, CANCELED -> false;
        };
    }
}

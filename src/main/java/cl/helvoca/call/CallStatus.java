package cl.helvoca.call;

public enum CallStatus {
    QUEUED,
    RINGING,
    IN_PROGRESS,
    COMPLETED,
    BUSY,
    FAILED,
    NO_ANSWER,
    CANCELED,
    UNKNOWN;

    public boolean terminal() {
        return this == COMPLETED || this == BUSY || this == FAILED ||
                this == NO_ANSWER || this == CANCELED;
    }
}

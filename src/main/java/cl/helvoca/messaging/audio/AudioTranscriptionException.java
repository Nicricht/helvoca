package cl.helvoca.messaging.audio;

public class AudioTranscriptionException extends RuntimeException {
    private final String code;
    private final String providerId;
    private final Integer httpStatus;
    private final boolean retryable;

    public AudioTranscriptionException(
            String code,
            String providerId,
            Integer httpStatus,
            boolean retryable,
            String message) {
        super(message);
        this.code = code;
        this.providerId = providerId;
        this.httpStatus = httpStatus;
        this.retryable = retryable;
    }

    public AudioTranscriptionException(
            String code,
            String providerId,
            Integer httpStatus,
            boolean retryable,
            String message,
            Throwable cause) {
        super(message, cause);
        this.code = code;
        this.providerId = providerId;
        this.httpStatus = httpStatus;
        this.retryable = retryable;
    }

    public String code() {
        return code;
    }

    public String providerId() {
        return providerId;
    }

    public Integer httpStatus() {
        return httpStatus;
    }

    public boolean retryable() {
        return retryable;
    }
}

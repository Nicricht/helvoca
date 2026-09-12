package cl.helvoca.ai.live;

/**
 * Represents a decision response returned by the OpenAI Live control plane.
 * Terminal errors are acknowledged to the webhook sender because redelivering
 * the same incoming SIP decision cannot repair billing, auth or an expired
 * session. Transient upstream errors may still be retried by webhook delivery.
 */
public class OpenAiLiveProviderException extends IllegalStateException {
    private final int httpStatus;
    private final String errorCode;
    private final boolean terminal;

    public OpenAiLiveProviderException(String message,
                                       int httpStatus,
                                       String errorCode,
                                       boolean terminal) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode == null ? "" : errorCode;
        this.terminal = terminal;
    }

    public int httpStatus() { return httpStatus; }
    public String errorCode() { return errorCode; }
    public boolean terminal() { return terminal; }
}

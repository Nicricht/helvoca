package cl.helvoca.messaging.meta;

import java.util.Locale;

public final class MetaWhatsAppApiException extends IllegalStateException {
    private final int httpStatus;
    private final String errorCode;
    private final String errorSubcode;
    private final boolean transientFailure;
    private final String errorType;
    private final String traceId;

    public MetaWhatsAppApiException(
            int httpStatus,
            String errorCode,
            String errorSubcode,
            boolean transientFailure,
            String errorType,
            String traceId) {
        super(summary(httpStatus, errorCode, errorSubcode, transientFailure, errorType, traceId));
        this.httpStatus = httpStatus;
        this.errorCode = clean(errorCode, 40);
        this.errorSubcode = clean(errorSubcode, 40);
        this.transientFailure = transientFailure;
        this.errorType = clean(errorType, 80);
        this.traceId = clean(traceId, 100);
    }

    public int httpStatus() { return httpStatus; }
    public String errorCode() { return errorCode; }
    public String errorSubcode() { return errorSubcode; }
    public boolean transientFailure() { return transientFailure; }
    public String errorType() { return errorType; }
    public String traceId() { return traceId; }

    public boolean retryable() {
        return transientFailure || httpStatus == 429 || httpStatus >= 500;
    }

    public String failureCode() {
        String code = errorCode == null ? "HTTP_" + httpStatus : errorCode;
        String value = "META_" + code;
        if (errorSubcode != null) value += "_SUB_" + errorSubcode;
        value = value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]", "_");
        return value.length() <= 80 ? value : value.substring(0, 80);
    }

    private static String summary(
            int httpStatus,
            String errorCode,
            String errorSubcode,
            boolean transientFailure,
            String errorType,
            String traceId) {
        StringBuilder out = new StringBuilder("Meta WhatsApp API rejected request http=")
                .append(httpStatus);
        String code = clean(errorCode, 40);
        String subcode = clean(errorSubcode, 40);
        String type = clean(errorType, 80);
        String trace = clean(traceId, 100);
        if (code != null) out.append(" code=").append(code);
        if (subcode != null) out.append(" subcode=").append(subcode);
        out.append(" transient=").append(transientFailure);
        if (type != null) out.append(" type=").append(type);
        if (trace != null) out.append(" trace=").append(trace);
        return out.toString();
    }

    private static String clean(String value, int max) {
        if (value == null) return null;
        String cleaned = value.trim().replaceAll("[^A-Za-z0-9._:-]", "_");
        if (cleaned.isBlank()) return null;
        return cleaned.length() <= max ? cleaned : cleaned.substring(0, max);
    }
}

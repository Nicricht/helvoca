package cl.helvoca.messaging.outbound;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

@Service
public class TwilioWhatsAppDeliveryStatusService {
    public enum Result { UPDATED, IGNORED, NOT_FOUND }

    private final OutboundMessageRepository messages;

    public TwilioWhatsAppDeliveryStatusService(OutboundMessageRepository messages) {
        this.messages = messages;
    }

    @Transactional
    public Result apply(String messageSid, String rawStatus, String errorCode) {
        String sid = trim(messageSid);
        String next = normalizeStatus(rawStatus);
        if (sid == null || next == null) return Result.IGNORED;

        OutboundMessage message = messages
                .findTopByProviderAndProviderMessageIdOrderByUpdatedAtDesc(
                        TwilioWhatsAppMessagingProvider.ID, sid)
                .orElse(null);
        if (message == null) return Result.NOT_FOUND;

        String current = message.getProviderDeliveryStatus();
        if (!shouldAdvance(current, next)) return Result.IGNORED;

        Instant now = Instant.now();
        message.setProviderDeliveryStatus(next);
        message.setDeliveryUpdatedAt(now);

        if ("DELIVERED".equals(next)) {
            if (message.getDeliveredAt() == null) message.setDeliveredAt(now);
            message.setFailureCode(null);
        } else if ("READ".equals(next)) {
            if (message.getDeliveredAt() == null) message.setDeliveredAt(now);
            if (message.getReadAt() == null) message.setReadAt(now);
            message.setFailureCode(null);
        } else if (isFailure(next)) {
            message.setFailureCode(failureCode(next, errorCode));
        }

        messages.saveAndFlush(message);
        return Result.UPDATED;
    }

    static String normalizeStatus(String value) {
        String status = trim(value);
        if (status == null) return null;
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "accepted", "scheduled", "queued", "sending" -> "QUEUED";
            case "sent" -> "SENT";
            case "delivered" -> "DELIVERED";
            case "read" -> "READ";
            case "failed" -> "FAILED";
            case "undelivered" -> "UNDELIVERED";
            case "canceled", "cancelled" -> "CANCELED";
            default -> null;
        };
    }

    static boolean isFailure(String status) {
        return "FAILED".equals(status) || "UNDELIVERED".equals(status) || "CANCELED".equals(status);
    }

    private static boolean shouldAdvance(String current, String next) {
        if (current == null || current.isBlank()) return true;
        if (current.equals(next)) return false;
        if ("READ".equals(current) || isFailure(current)) return false;
        if ("DELIVERED".equals(current)) return "READ".equals(next);
        if (isFailure(next)) return true;
        return rank(next) >= rank(current);
    }

    private static int rank(String status) {
        return switch (status) {
            case "QUEUED" -> 1;
            case "SENT" -> 2;
            case "DELIVERED" -> 3;
            case "READ" -> 4;
            default -> 0;
        };
    }

    private static String failureCode(String status, String rawErrorCode) {
        String code = trim(rawErrorCode);
        if (code == null) code = status;
        String safe = code.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]", "_");
        String value = "TWILIO_" + safe;
        return value.length() <= 80 ? value : value.substring(0, 80);
    }

    private static String trim(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

package cl.helvoca.messaging.meta;

import java.time.Instant;

public record MetaWhatsAppDeliveryStatus(
        String messageId,
        String phoneNumberId,
        String status,
        Instant occurredAt,
        String errorCode) {
}

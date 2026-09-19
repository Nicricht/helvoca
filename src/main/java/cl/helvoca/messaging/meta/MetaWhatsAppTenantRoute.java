package cl.helvoca.messaging.meta;

import java.util.UUID;

public record MetaWhatsAppTenantRoute(
        UUID businessId,
        UUID phoneNumberId,
        String recipientPhone) {
}

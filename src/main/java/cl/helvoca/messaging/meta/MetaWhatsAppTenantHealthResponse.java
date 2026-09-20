package cl.helvoca.messaging.meta;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record MetaWhatsAppTenantHealthResponse(
        String state,
        boolean configured,
        boolean tenantEnabled,
        boolean phoneActive,
        boolean credentialAvailable,
        boolean webhookSecurityReady,
        boolean integrationEnabled,
        boolean outboundDeliveryEnabled,
        boolean certified,
        String provider,
        UUID phoneRecordId,
        @JsonProperty("phone_number_id") String providerPhoneNumberId,
        Instant certifiedAt
) {
}

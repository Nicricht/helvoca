package cl.helvoca.messaging.meta;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record MetaWhatsAppTenantConfigurationResponse(
        UUID phoneRecordId,
        String provider,
        @JsonProperty("phone_number_id") String providerPhoneNumberId,
        @JsonProperty("waba_id") String wabaId,
        String credentialRef,
        boolean enabled
) {
}

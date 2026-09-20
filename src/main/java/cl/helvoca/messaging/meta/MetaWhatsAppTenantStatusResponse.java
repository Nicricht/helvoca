package cl.helvoca.messaging.meta;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record MetaWhatsAppTenantStatusResponse(
        String status,
        boolean configured,
        boolean enabled,
        String provider,
        UUID phoneRecordId,
        String phoneNumber,
        @JsonProperty("phone_number_id") String providerPhoneNumberId,
        boolean credentialReferenceConfigured,
        Instant certifiedAt
) {
    static MetaWhatsAppTenantStatusResponse notConfigured() {
        return new MetaWhatsAppTenantStatusResponse(
                "NOT_CONFIGURED",
                false,
                false,
                null,
                null,
                null,
                null,
                false,
                null);
    }

    static MetaWhatsAppTenantStatusResponse incomplete(
            PhoneView phone,
            boolean credentialReferenceConfigured) {
        return new MetaWhatsAppTenantStatusResponse(
                "INCOMPLETE",
                false,
                false,
                phone == null ? null : phone.provider(),
                phone == null ? null : phone.phoneRecordId(),
                phone == null ? null : phone.phoneNumber(),
                phone == null ? null : phone.providerPhoneNumberId(),
                credentialReferenceConfigured,
                phone == null ? null : phone.certifiedAt());
    }

    static MetaWhatsAppTenantStatusResponse configured(
            PhoneView phone,
            boolean enabled) {
        return new MetaWhatsAppTenantStatusResponse(
                enabled ? "CONFIGURED_ENABLED" : "CONFIGURED_DISABLED",
                true,
                enabled,
                phone.provider(),
                phone.phoneRecordId(),
                phone.phoneNumber(),
                phone.providerPhoneNumberId(),
                true,
                phone.certifiedAt());
    }

    record PhoneView(
            UUID phoneRecordId,
            String provider,
            String providerPhoneNumberId,
            String phoneNumber,
            Instant certifiedAt) {
    }
}

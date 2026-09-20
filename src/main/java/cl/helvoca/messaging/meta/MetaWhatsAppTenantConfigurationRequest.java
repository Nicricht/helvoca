package cl.helvoca.messaging.meta;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record MetaWhatsAppTenantConfigurationRequest(
        @NotNull UUID phoneRecordId,
        @NotBlank String provider,
        @JsonProperty("phone_number_id")
        @NotBlank
        @Pattern(regexp = "^[0-9]{5,30}$")
        String providerPhoneNumberId,
        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9_]{2,80}$")
        String credentialRef
) {
}

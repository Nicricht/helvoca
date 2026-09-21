package cl.helvoca.messaging.meta;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record MetaWhatsAppEmbeddedSignupPhoneFinalizeRequest(
        @JsonProperty("phoneRecordId")
        @NotNull
        UUID phoneRecordId,
        @JsonProperty("wabaId")
        @NotBlank
        @Pattern(regexp = "[0-9]{1,80}")
        String wabaId,
        @JsonProperty("phoneNumberId")
        @NotBlank
        @Pattern(regexp = "[0-9]{1,80}")
        String phoneNumberId,
        @JsonProperty("credentialRef")
        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9_]{2,80}")
        String credentialRef,
        @JsonProperty("pin")
        @NotBlank
        @Pattern(regexp = "[0-9]{6}")
        String pin
) {
    public MetaWhatsAppEmbeddedSignupPhoneFinalizeRequest {
        wabaId = wabaId == null ? null : wabaId.trim();
        phoneNumberId = phoneNumberId == null ? null : phoneNumberId.trim();
        credentialRef = credentialRef == null ? null : credentialRef.trim();
        pin = pin == null ? null : pin.trim();
    }

    @Override
    public String toString() {
        return "MetaWhatsAppEmbeddedSignupPhoneFinalizeRequest["
                + "phoneRecordId=" + phoneRecordId
                + ", wabaId=" + wabaId
                + ", phoneNumberId=" + phoneNumberId
                + ", credentialRef=" + credentialRef
                + ", pin=REDACTED]";
    }
}

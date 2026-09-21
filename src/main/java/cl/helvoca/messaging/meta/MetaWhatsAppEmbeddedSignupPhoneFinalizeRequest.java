package cl.helvoca.messaging.meta;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record MetaWhatsAppEmbeddedSignupPhoneFinalizeRequest(
        @JsonProperty("wabaId")
        @NotBlank
        @Pattern(regexp = "[0-9]{1,80}")
        String wabaId,
        @JsonProperty("phoneNumberId")
        @NotBlank
        @Pattern(regexp = "[0-9]{1,80}")
        String phoneNumberId,
        @JsonProperty("pin")
        @NotBlank
        @Pattern(regexp = "[0-9]{6}")
        String pin
) {
    public MetaWhatsAppEmbeddedSignupPhoneFinalizeRequest {
        wabaId = wabaId == null ? null : wabaId.trim();
        phoneNumberId = phoneNumberId == null ? null : phoneNumberId.trim();
        pin = pin == null ? null : pin.trim();
    }

    @Override
    public String toString() {
        return "MetaWhatsAppEmbeddedSignupPhoneFinalizeRequest["
                + "wabaId=" + wabaId
                + ", phoneNumberId=" + phoneNumberId
                + ", pin=REDACTED]";
    }
}

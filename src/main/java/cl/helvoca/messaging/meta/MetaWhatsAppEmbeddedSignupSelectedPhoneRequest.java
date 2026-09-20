package cl.helvoca.messaging.meta;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record MetaWhatsAppEmbeddedSignupSelectedPhoneRequest(
        @JsonProperty("wabaId")
        @NotBlank
        @Pattern(regexp = "[0-9]{1,80}")
        String wabaId,
        @JsonProperty("phoneNumberId")
        @NotBlank
        @Pattern(regexp = "[0-9]{1,80}")
        String phoneNumberId
) {
    public MetaWhatsAppEmbeddedSignupSelectedPhoneRequest {
        wabaId = wabaId == null ? null : wabaId.trim();
        phoneNumberId = phoneNumberId == null ? null : phoneNumberId.trim();
    }
}

package cl.helvoca.messaging.meta;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record MetaWhatsAppEmbeddedSignupSelectedWabaRequest(
        @JsonProperty("wabaId")
        @NotBlank
        @Pattern(regexp = "[0-9]{1,80}")
        String wabaId
) {
    public MetaWhatsAppEmbeddedSignupSelectedWabaRequest {
        wabaId = wabaId == null ? null : wabaId.trim();
    }
}

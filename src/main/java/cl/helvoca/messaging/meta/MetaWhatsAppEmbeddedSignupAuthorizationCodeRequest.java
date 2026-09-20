package cl.helvoca.messaging.meta;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MetaWhatsAppEmbeddedSignupAuthorizationCodeRequest(
        @JsonProperty("code")
        @NotBlank
        @Size(max = 4096)
        String code
) {
    public MetaWhatsAppEmbeddedSignupAuthorizationCodeRequest {
        code = code == null ? null : code.trim();
    }

    @Override
    public String toString() {
        return "MetaWhatsAppEmbeddedSignupAuthorizationCodeRequest[code=REDACTED]";
    }
}

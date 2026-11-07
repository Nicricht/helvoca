package cl.helvoca.messaging.meta;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record MetaWhatsAppCoexistenceStageRequest(
        @NotBlank @Pattern(regexp = "[0-9]{5,30}") String wabaId,
        @NotBlank @Pattern(regexp = "[0-9]{5,30}") String phoneNumberId
) {
}

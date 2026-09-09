package cl.helvoca.phone;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PhoneNumberRequest(
        @NotBlank
        @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "phoneNumber must use E.164 format")
        String phoneNumber,
        String externalId,
        Boolean active
) {}

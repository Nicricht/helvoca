package cl.helvoca.phone;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record ProvisionPhoneNumberRequest(
        @NotBlank
        @Pattern(regexp = "^\\+[1-9][0-9]{7,14}$", message = "must be a valid E.164 phone number")
        String phoneNumber,
        @NotNull Boolean confirmPurchase
) {}

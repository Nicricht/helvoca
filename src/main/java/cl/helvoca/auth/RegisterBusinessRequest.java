package cl.helvoca.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterBusinessRequest(
        @NotBlank @Size(max = 150) String adminName,
        @NotBlank @Email @Size(max = 180) String email,
        @NotBlank @Size(min = 10, max = 72) String password,
        @NotBlank @Size(max = 150) String businessName,
        @NotBlank @Size(max = 60) String timezone,
        @NotBlank @Size(max = 10) String language,
        @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "humanTransferPhone must use E.164 format")
        String humanTransferPhone
) {}

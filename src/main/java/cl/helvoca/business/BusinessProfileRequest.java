package cl.helvoca.business;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BusinessProfileRequest(
        @Size(max = 60)
        @Pattern(regexp = "^[A-Za-z0-9_-]*$", message = "presetKey contains invalid characters")
        String presetKey,

        @Size(max = 4000)
        String publicDescription,

        @Size(max = 32)
        @Pattern(regexp = "^$|^\\+[1-9]\\d{7,14}$", message = "publicPhone must use E.164 format")
        String publicPhone,

        @Email
        @Size(max = 180)
        String publicEmail,

        @Size(max = 500)
        @Pattern(regexp = "^$|^https?://.+$", message = "websiteUrl must use http or https")
        String websiteUrl,

        @Size(max = 250)
        String addressLine,

        @Size(max = 120)
        String commune,

        @Size(max = 120)
        String city,

        @Size(max = 120)
        String region,

        @Pattern(regexp = "^$|^[A-Za-z]{2}$", message = "countryCode must contain two letters")
        String countryCode,

        @Pattern(regexp = "^$|^[A-Za-z]{3}$", message = "defaultCurrency must contain three letters")
        String defaultCurrency
) {
}

package cl.helvoca.phone;

public record AvailablePhoneNumberResponse(
        String phoneNumber,
        String friendlyName,
        String locality,
        String region,
        String postalCode,
        String isoCountry,
        String addressRequirements,
        boolean voiceCapable
) {}

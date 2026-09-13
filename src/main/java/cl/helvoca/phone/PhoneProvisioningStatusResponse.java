package cl.helvoca.phone;

public record PhoneProvisioningStatusResponse(
        boolean enabled,
        boolean configured,
        boolean purchaseAvailable,
        String provider,
        String message
) {}

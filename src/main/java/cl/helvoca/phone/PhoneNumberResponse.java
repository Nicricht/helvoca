package cl.helvoca.phone;

import java.time.Instant;
import java.util.UUID;

public record PhoneNumberResponse(
        UUID id,
        String provider,
        String externalId,
        String phoneNumber,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    static PhoneNumberResponse from(PhoneNumber phone) {
        return new PhoneNumberResponse(
                phone.getId(), phone.getProvider(), phone.getExternalId(), phone.getPhoneNumber(),
                phone.isActive(), phone.getCreatedAt(), phone.getUpdatedAt());
    }
}

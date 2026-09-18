package cl.helvoca.business;

import java.util.UUID;

public record BusinessProfileResponse(
        UUID businessId,
        String presetKey,
        String publicDescription,
        String publicPhone,
        String publicEmail,
        String websiteUrl,
        String addressLine,
        String commune,
        String city,
        String region,
        String countryCode,
        String defaultCurrency
) {
    static BusinessProfileResponse from(BusinessProfile profile) {
        return new BusinessProfileResponse(
                profile.getBusinessId(),
                profile.getPresetKey(),
                profile.getPublicDescription(),
                profile.getPublicPhone(),
                profile.getPublicEmail(),
                profile.getWebsiteUrl(),
                profile.getAddressLine(),
                profile.getCommune(),
                profile.getCity(),
                profile.getRegion(),
                profile.getCountryCode(),
                profile.getDefaultCurrency()
        );
    }

    static BusinessProfileResponse empty(UUID businessId) {
        return new BusinessProfileResponse(
                businessId, null, null, null, null, null,
                null, null, null, null, null, "CLP"
        );
    }
}

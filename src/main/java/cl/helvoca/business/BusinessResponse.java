package cl.helvoca.business;

import java.util.UUID;

public record BusinessResponse(
        UUID id,
        String name,
        String timezone,
        String language,
        String humanTransferPhone,
        String appearanceTheme,
        BusinessStatus status
) {
    public static BusinessResponse from(Business business) {
        return new BusinessResponse(
                business.getId(),
                business.getName(),
                business.getTimezone(),
                business.getLanguage(),
                business.getHumanTransferPhone(),
                business.getAppearanceTheme(),
                business.getStatus());
    }
}

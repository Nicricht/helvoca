package cl.helvoca.calendar;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Component
public class CalendarProviderRegistry {
    private final List<CalendarProvider> providers;

    public CalendarProviderRegistry(List<CalendarProvider> providers) {
        this.providers = providers == null ? List.of() : List.copyOf(providers);
    }

    public CalendarProvider require(UUID businessId, String providerCode) {
        if (businessId == null || providerCode == null || providerCode.isBlank()) {
            throw new IllegalArgumentException("businessId and providerCode are required");
        }
        List<CalendarProvider> matches = providers.stream()
                .filter(provider -> provider.code().equalsIgnoreCase(providerCode.trim()))
                .filter(provider -> provider.supports(businessId))
                .toList();
        if (matches.size() != 1) {
            throw new IllegalStateException("Calendar provider is unavailable or ambiguous for tenant: "
                    + providerCode.trim().toUpperCase(Locale.ROOT));
        }
        return matches.getFirst();
    }
}

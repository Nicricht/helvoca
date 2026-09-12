package cl.helvoca.voice;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class VoiceAiProviderRegistry {
    private final List<VoiceAiProvider> providers;
    private final VoiceProviderProperties properties;

    public VoiceAiProviderRegistry(List<VoiceAiProvider> providers,
                                   VoiceProviderProperties properties) {
        this.providers = List.copyOf(providers);
        this.properties = properties;
    }

    public VoiceAiProvider active() {
        return require(properties.getAiProvider());
    }

    public VoiceAiProvider require(String providerId) {
        String configuredId = normalize(providerId);
        return providers.stream()
                .filter(provider -> normalize(provider.id()).equals(configuredId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Unknown voice AI provider '" + providerId + "'. Available: " + availableIds()));
    }

    public boolean configured() {
        return active().configured();
    }

    public String activeProviderId() {
        return active().id();
    }

    public List<String> providerIds() {
        return providers.stream().map(VoiceAiProvider::id).sorted().toList();
    }

    private String availableIds() {
        return providerIds().toString();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}

package cl.helvoca.messaging.outbound;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MessagingProviderRegistry {
    private final List<MessagingProvider> providers;

    public MessagingProviderRegistry(List<MessagingProvider> providers) {
        this.providers = providers == null ? List.of() : List.copyOf(providers);
    }

    public MessagingProvider require(String id, OutboundMessage.Channel channel) {
        if (id == null || id.isBlank() || "NONE".equalsIgnoreCase(id)) {
            throw new IllegalStateException("No outbound messaging provider is configured");
        }
        return providers.stream()
                .filter(provider -> provider.id().equalsIgnoreCase(id) && provider.supports(channel))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Configured outbound messaging provider is unavailable"));
    }
}

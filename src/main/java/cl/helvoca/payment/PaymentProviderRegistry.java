package cl.helvoca.payment;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class PaymentProviderRegistry {
    private final List<PaymentProviderAdapter> adapters;

    public PaymentProviderRegistry(List<PaymentProviderAdapter> adapters) {
        this.adapters = adapters == null ? List.of() : List.copyOf(adapters);
    }

    /**
     * Resolves exactly one provider configured for the tenant. Ambiguous or
     * missing configuration fails closed.
     */
    public Optional<PaymentProviderAdapter> resolve(UUID businessId) {
        if (businessId == null) return Optional.empty();
        List<PaymentProviderAdapter> supported = adapters.stream()
                .filter(adapter -> adapter.supports(businessId))
                .toList();
        return supported.size() == 1 ? Optional.of(supported.get(0)) : Optional.empty();
    }

    public Optional<PaymentProviderAdapter> byCode(UUID businessId, String providerCode) {
        if (businessId == null || providerCode == null || providerCode.isBlank()) return Optional.empty();
        return adapters.stream()
                .filter(adapter -> providerCode.equalsIgnoreCase(adapter.providerCode()))
                .filter(adapter -> adapter.supports(businessId))
                .findFirst();
    }
}

package cl.helvoca.operations;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * High-level commercial capabilities enabled explicitly per tenant.
 *
 * These are intentionally separate from AiCapability. AiCapability controls
 * the legacy receptionist tools while this enum controls business operations
 * that are not universally meaningful (orders, delivery, quotes and leads).
 */
public enum BusinessOperationCapability {
    CATALOG(Set.of("list_catalog")),
    ORDER(Set.of("quote_order", "create_order", "get_order_status", "cancel_order")),
    DELIVERY(Set.of("list_delivery_zones", "validate_delivery_address")),
    QUOTE(Set.of("create_quote")),
    LEAD(Set.of("create_lead"));

    private final Set<String> toolNames;

    BusinessOperationCapability(Set<String> toolNames) {
        this.toolNames = Set.copyOf(toolNames);
    }

    public Set<String> toolNames() {
        return toolNames;
    }

    public static Optional<BusinessOperationCapability> fromToolName(String toolName) {
        if (toolName == null) return Optional.empty();
        return Arrays.stream(values())
                .filter(capability -> capability.toolNames.contains(toolName))
                .findFirst();
    }

    public static Set<String> toolNamesFor(Set<BusinessOperationCapability> capabilities) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (capabilities == null) return Set.of();
        capabilities.forEach(capability -> out.addAll(capability.toolNames));
        return Set.copyOf(out);
    }
}

package cl.helvoca.operations;

import cl.helvoca.agent.AiCapability;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * High-level business presets. Runtime tool authorization is persisted and
 * enforced by AiAgent/AiCapability, so there is one authority per tenant.
 */
public enum BusinessOperationCapability {
    CATALOG(Set.of(AiCapability.LIST_CATALOG)),
    ORDER(Set.of(
            AiCapability.QUOTE_ORDER,
            AiCapability.UPDATE_ORDER,
            AiCapability.CREATE_ORDER,
            AiCapability.GET_ORDER_STATUS,
            AiCapability.CANCEL_ORDER)),
    DELIVERY(Set.of(
            AiCapability.LIST_DELIVERY_ZONES,
            AiCapability.VALIDATE_DELIVERY_ADDRESS,
            AiCapability.QUOTE_DELIVERY,
            AiCapability.UPDATE_DELIVERY,
            AiCapability.CREATE_DELIVERY,
            AiCapability.GET_DELIVERY_STATUS,
            AiCapability.CANCEL_DELIVERY)),
    QUOTE(Set.of(AiCapability.CREATE_QUOTE)),
    LEAD(Set.of(AiCapability.CREATE_LEAD)),
    PAYMENT(Set.of(
            AiCapability.QUOTE_PAYMENT,
            AiCapability.UPDATE_PAYMENT,
            AiCapability.CREATE_PAYMENT,
            AiCapability.GET_PAYMENT_STATUS,
            AiCapability.CANCEL_PAYMENT));

    private final Set<AiCapability> aiCapabilities;

    BusinessOperationCapability(Set<AiCapability> aiCapabilities) {
        this.aiCapabilities = Set.copyOf(aiCapabilities);
    }

    public Set<AiCapability> aiCapabilities() { return aiCapabilities; }

    public Set<String> toolNames() {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        aiCapabilities.forEach(capability -> out.add(capability.toolName()));
        return Set.copyOf(out);
    }

    public static Optional<BusinessOperationCapability> fromToolName(String toolName) {
        if (toolName == null) return Optional.empty();
        return Arrays.stream(values()).filter(capability -> capability.toolNames().contains(toolName)).findFirst();
    }

    public static Set<String> toolNamesFor(Set<BusinessOperationCapability> capabilities) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (capabilities == null) return Set.of();
        capabilities.forEach(capability -> out.addAll(capability.toolNames()));
        return Set.copyOf(out);
    }

    public static Set<AiCapability> aiCapabilitiesFor(Set<BusinessOperationCapability> capabilities) {
        EnumSet<AiCapability> out = EnumSet.noneOf(AiCapability.class);
        if (capabilities != null) capabilities.forEach(capability -> out.addAll(capability.aiCapabilities));
        return Set.copyOf(out);
    }

    public static Set<BusinessOperationCapability> fromAiCapabilities(Set<AiCapability> capabilities) {
        EnumSet<BusinessOperationCapability> out = EnumSet.noneOf(BusinessOperationCapability.class);
        if (capabilities == null) return Set.of();
        for (BusinessOperationCapability capability : values()) {
            if (capabilities.containsAll(capability.aiCapabilities)) out.add(capability);
        }
        return Set.copyOf(out);
    }

    public static boolean isCommercialToolName(String toolName) { return fromToolName(toolName).isPresent(); }
}

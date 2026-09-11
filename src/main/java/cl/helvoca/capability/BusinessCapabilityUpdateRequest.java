package cl.helvoca.capability;

import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record BusinessCapabilityUpdateRequest(
        @NotNull Set<BusinessCapabilityCode> enabled
) {}

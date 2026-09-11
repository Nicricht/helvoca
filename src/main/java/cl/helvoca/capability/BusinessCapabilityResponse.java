package cl.helvoca.capability;

import java.util.Map;

public record BusinessCapabilityResponse(Map<BusinessCapabilityCode, Boolean> capabilities) {}

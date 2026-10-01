package cl.helvoca.platform;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Narrow runtime admission gate for automatic inbound DEMO activation.
 *
 * The platform readiness service remains the single source of truth. Using an
 * ObjectProvider here intentionally defers that heavyweight graph until a real
 * inbound admission check runs, so voice-provider construction does not form a
 * Spring bean cycle through CallLifecycleService.
 */
@Component
public class DemoInboundSafetyReadiness {
    private final ObjectProvider<PlatformDemoReadinessService> readinessProvider;

    public DemoInboundSafetyReadiness(ObjectProvider<PlatformDemoReadinessService> readinessProvider) {
        this.readinessProvider = readinessProvider;
    }

    public Optional<Map<String, Object>> safeSnapshot(UUID runtimeId) {
        if (runtimeId == null) return Optional.empty();

        PlatformDemoReadinessService readiness = readinessProvider.getIfAvailable();
        if (readiness == null) return Optional.empty();

        PlatformDemoReadinessResponse value = readiness.readiness();
        if (!safeForInboundVoice(runtimeId, value)) return Optional.empty();

        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("runtimeBusinessId", value.runtimeBusinessId());
        snapshot.put("runtime", value.runtime().state());
        snapshot.put("voiceNumber", value.voiceNumber().state());
        snapshot.put("voiceAi", value.voiceAi().state());
        snapshot.put("businessData", value.businessData().state());
        snapshot.put("operations", value.operations().state());
        snapshot.put("payment", value.payment().state());
        snapshot.put("externalEffects", value.externalEffects().state());
        return Optional.of(snapshot);
    }

    private static boolean safeForInboundVoice(UUID runtimeId, PlatformDemoReadinessResponse value) {
        return value != null
                && value.runtimeConfigured()
                && runtimeId.equals(value.runtimeBusinessId())
                && ready(value.runtime())
                && ready(value.voiceNumber())
                && ready(value.voiceAi())
                && ready(value.businessData())
                && ready(value.operations())
                && value.payment() != null
                && "SANDBOX_ONLY".equals(value.payment().state())
                && value.externalEffects() != null
                && "DISARMED".equals(value.externalEffects().state());
    }

    private static boolean ready(PlatformDemoReadinessResponse.ReadinessItem item) {
        return item != null && "READY".equals(item.state());
    }
}

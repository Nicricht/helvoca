package cl.helvoca.voice;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Small in-memory circuit breaker shared by telephony routing and realtime
 * providers. It prevents repeated customer calls from being sent to a provider
 * that just proved unusable (no credits, invalid auth, rate limits or upstream
 * failures). Configuration readiness is still evaluated by each provider.
 */
@Component
public class VoiceProviderHealthRegistry {
    private final Map<String, Circuit> circuits = new ConcurrentHashMap<>();

    public boolean allow(String providerId, boolean configured) {
        if (!configured) return false;
        Circuit circuit = circuits.get(normalize(providerId));
        if (circuit == null) return true;
        Instant blockedUntil = circuit.blockedUntil();
        if (blockedUntil == null || !Instant.now().isBefore(blockedUntil)) {
            circuits.remove(normalize(providerId), circuit);
            return true;
        }
        return false;
    }

    public void success(String providerId) {
        circuits.remove(normalize(providerId));
    }

    public void failure(String providerId, FailureKind kind, String reason) {
        Duration duration = switch (kind) {
            case NO_CREDITS, AUTH -> Duration.ofMinutes(15);
            case RATE_LIMIT -> Duration.ofMinutes(1);
            case SESSION, UPSTREAM -> Duration.ofSeconds(30);
            case UNKNOWN -> Duration.ofSeconds(15);
        };
        circuits.put(normalize(providerId), new Circuit(kind, safe(reason), Instant.now().plus(duration)));
    }

    public ProviderHealth snapshot(String providerId, boolean configured) {
        String id = normalize(providerId);
        if (!configured) {
            return new ProviderHealth(id, false, false, "UNCONFIGURED", "Provider credentials/configuration are incomplete", null);
        }
        Circuit circuit = circuits.get(id);
        if (circuit == null) {
            return new ProviderHealth(id, true, true, "READY", "No active circuit breaker", null);
        }
        if (!Instant.now().isBefore(circuit.blockedUntil())) {
            circuits.remove(id, circuit);
            return new ProviderHealth(id, true, true, "HALF_OPEN", "Circuit cooldown elapsed; next call may probe the provider", null);
        }
        return new ProviderHealth(id, true, false, "OPEN", circuit.kind().name() + ": " + circuit.reason(), circuit.blockedUntil());
    }

    public enum FailureKind {
        NO_CREDITS,
        AUTH,
        RATE_LIMIT,
        SESSION,
        UPSTREAM,
        UNKNOWN
    }

    public record ProviderHealth(String providerId,
                                 boolean configured,
                                 boolean available,
                                 String state,
                                 String detail,
                                 Instant blockedUntil) {
    }

    private record Circuit(FailureKind kind, String reason, Instant blockedUntil) {
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static String safe(String value) {
        if (value == null || value.isBlank()) return "provider failure";
        return value.length() <= 240 ? value : value.substring(0, 240);
    }
}

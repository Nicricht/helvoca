package cl.helvoca.voice;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
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
    private MeterRegistry meters;

    @Autowired(required = false)
    void setMeters(MeterRegistry meters) {
        this.meters = meters;
    }

    private void record(String metric, String key, String value) {
        if (meters != null) {
            meters.counter(metric, key, value).increment();
        }
    }

    public boolean allow(String providerId, boolean configured) {
        if (!configured) {
            record("helvoca.voice.provider.admission.rejected", "reason", "unconfigured");
            return false;
        }
        Circuit circuit = circuits.get(normalize(providerId));
        if (circuit == null) return true;
        Instant blockedUntil = circuit.blockedUntil();
        if (blockedUntil == null || !Instant.now().isBefore(blockedUntil)) {
            circuits.remove(normalize(providerId), circuit);
            return true;
        }
        record("helvoca.voice.provider.admission.rejected", "reason", "circuit_open");
        return false;
    }

    public void success(String providerId) {
        if (circuits.remove(normalize(providerId)) != null) {
            record("helvoca.voice.provider.circuit.recovered", "reason", "success");
        }
    }

    public void failure(String providerId, FailureKind kind, String reason) {
        Duration duration = switch (kind) {
            case NO_CREDITS, AUTH -> Duration.ofMinutes(15);
            case RATE_LIMIT -> Duration.ofMinutes(1);
            case SESSION, UPSTREAM -> Duration.ofSeconds(30);
            case UNKNOWN -> Duration.ofSeconds(15);
        };
        // Raw provider errors may include secrets or caller data.
        circuits.put(normalize(providerId), new Circuit(kind, safe(reason), Instant.now().plus(duration)));
        record("helvoca.voice.provider.circuit.opened", "kind", kind.name().toLowerCase(Locale.ROOT));
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
        return "Provider temporarily unavailable";
    }
}

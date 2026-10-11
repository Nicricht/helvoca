package cl.helvoca.voice;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VoiceProviderHealthRegistryTest {

    @Test
    void noCreditsImmediatelyOpensCircuit() {
        VoiceProviderHealthRegistry registry = new VoiceProviderHealthRegistry();

        assertTrue(registry.allow("openai-live", true));
        registry.failure("openai-live", VoiceProviderHealthRegistry.FailureKind.NO_CREDITS,
                "credit_balance_exhausted");

        assertFalse(registry.allow("openai-live", true));
        var health = registry.snapshot("openai-live", true);
        assertEquals("OPEN", health.state());
        assertFalse(health.available());
        assertTrue(health.detail().contains("NO_CREDITS"));
        assertNotNull(health.blockedUntil());
    }

    @Test
    void successClosesCircuit() {
        VoiceProviderHealthRegistry registry = new VoiceProviderHealthRegistry();
        registry.failure("gemini", VoiceProviderHealthRegistry.FailureKind.UPSTREAM, "socket closed");
        assertFalse(registry.allow("gemini", true));

        registry.success("gemini");

        assertTrue(registry.allow("gemini", true));
        assertEquals("READY", registry.snapshot("gemini", true).state());
    }

    @Test
    void unconfiguredProviderIsNeverRoutable() {
        VoiceProviderHealthRegistry registry = new VoiceProviderHealthRegistry();
        assertFalse(registry.allow("gemini", false));
        var health = registry.snapshot("gemini", false);
        assertEquals("UNCONFIGURED", health.state());
        assertFalse(health.available());
    }


    @Test
    void providerErrorsNeverExposeSecretsAndMetricsHaveOnlyBoundedLabels() {
        var meters = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        VoiceProviderHealthRegistry health = new VoiceProviderHealthRegistry();
        health.setMeters(meters);
        String secret = "sk-test-secret-123; caller=+56988887777; SID=PNsecret";
        health.failure("gemini", VoiceProviderHealthRegistry.FailureKind.AUTH, secret);
        var diagnostic = health.snapshot("gemini", true);
        assertEquals("OPEN", diagnostic.state());
        assertEquals("AUTH: Provider temporarily unavailable", diagnostic.detail());
        assertFalse(diagnostic.toString().contains(secret));
        assertFalse(health.allow("gemini", true));
        assertFalse(health.allow("gemini", false));
        assertEquals(1, meters.get("helvoca.voice.provider.circuit.opened")
                .tag("kind", "auth").counter().count());
        assertEquals(1, meters.get("helvoca.voice.provider.admission.rejected")
                .tag("reason", "circuit_open").counter().count());
        assertEquals(1, meters.get("helvoca.voice.provider.admission.rejected")
                .tag("reason", "unconfigured").counter().count());
        assertTrue(meters.getMeters().stream().noneMatch(m ->
                m.getId().toString().contains(secret) || m.getId().toString().contains("gemini")));
        health.success("gemini");
        assertEquals(1, meters.get("helvoca.voice.provider.circuit.recovered")
                .tag("reason", "success").counter().count());
        health.success("gemini");
        assertEquals(1, meters.get("helvoca.voice.provider.circuit.recovered")
                .tag("reason", "success").counter().count());
        assertTrue(health.allow("gemini", true));
        meters.close();
    }

    @Test
    void allFailureKindsHaveBoundedCircuitTelemetryAndNoRawMessage() {
        var meters = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        VoiceProviderHealthRegistry health = new VoiceProviderHealthRegistry();
        health.setMeters(meters);
        for (VoiceProviderHealthRegistry.FailureKind kind : VoiceProviderHealthRegistry.FailureKind.values()) {
            health.failure("gemini", kind, "authorization=secret-" + kind);
            assertTrue(health.snapshot("gemini", true).detail().startsWith(kind.name() + ":"));
            assertFalse(health.snapshot("gemini", true).detail().contains("secret"));
            assertEquals(1, meters.get("helvoca.voice.provider.circuit.opened")
                    .tag("kind", kind.name().toLowerCase(java.util.Locale.ROOT)).counter().count());
        }
        meters.close();
    }

}

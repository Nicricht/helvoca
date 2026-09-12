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
}

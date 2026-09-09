package cl.helvoca.voice;

import cl.helvoca.ai.realtime.RealtimeCallContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoiceAiProviderRegistryTest {

    @Test
    void selectsConfiguredProviderCaseInsensitively() {
        VoiceProviderProperties properties = new VoiceProviderProperties();
        properties.setAiProvider(" SECOND ");
        VoiceAiProviderRegistry registry = new VoiceAiProviderRegistry(
                List.of(provider("first", true), provider("second", true)), properties);

        assertEquals("second", registry.activeProviderId());
        assertTrue(registry.configured());
    }

    @Test
    void rejectsUnknownProvider() {
        VoiceProviderProperties properties = new VoiceProviderProperties();
        properties.setAiProvider("missing");
        VoiceAiProviderRegistry registry = new VoiceAiProviderRegistry(
                List.of(provider("openai", true)), properties);

        assertThrows(IllegalStateException.class, registry::active);
    }

    private static VoiceAiProvider provider(String id, boolean configured) {
        return new VoiceAiProvider() {
            @Override
            public String id() { return id; }

            @Override
            public boolean configured() { return configured; }

            @Override
            public VoiceAiSession createSession(RealtimeCallContext context, VoiceTransportSession transport) {
                throw new UnsupportedOperationException("Not needed for registry test");
            }
        };
    }
}

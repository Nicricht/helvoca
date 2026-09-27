package cl.helvoca.voice;

import cl.helvoca.ai.gemini.GeminiLiveProperties;
import cl.helvoca.ai.gemini.GeminiLiveVoiceProvider;
import cl.helvoca.ai.realtime.OpenAiRealtimeBridgeFactory;
import cl.helvoca.ai.realtime.OpenAiRealtimeProperties;
import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.ai.realtime.RealtimeToolService;
import cl.helvoca.call.CallCertificationService;
import cl.helvoca.call.CallSummaryService;
import cl.helvoca.call.CallTranscriptService;
import cl.helvoca.telephony.CallLifecycleService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

class VoiceProviderObservabilityTest {

    @Test
    void openAiRealtimeProviderReportsConfiguredModel() {
        OpenAiRealtimeProperties properties = new OpenAiRealtimeProperties();
        properties.setRealtimeModel("gpt-realtime-observability");

        OpenAiRealtimeBridgeFactory provider = new OpenAiRealtimeBridgeFactory(
                properties,
                mock(RealtimeToolService.class),
                mock(CallTranscriptService.class),
                mock(CallSummaryService.class));

        assertEquals("gpt-realtime-observability", provider.modelId());
    }

    @Test
    void geminiLiveProviderReportsConfiguredModel() {
        GeminiLiveProperties properties = new GeminiLiveProperties();
        properties.setModel("gemini-live-observability");

        GeminiLiveVoiceProvider provider = new GeminiLiveVoiceProvider(
                properties,
                mock(RealtimeToolService.class),
                mock(CallTranscriptService.class),
                mock(CallSummaryService.class),
                mock(CallLifecycleService.class),
                mock(CallCertificationService.class),
                mock(VoiceProviderHealthRegistry.class));

        assertEquals("gemini-live-observability", provider.modelId());
    }

    @Test
    void providerWithoutModelMetadataKeepsBackwardCompatibleNullDefault() {
        VoiceAiProvider provider = new VoiceAiProvider() {
            @Override
            public String id() {
                return "test";
            }

            @Override
            public boolean configured() {
                return true;
            }

            @Override
            public VoiceAiSession createSession(RealtimeCallContext context, VoiceTransportSession transport) {
                return null;
            }
        };

        assertNull(provider.modelId());
    }
}

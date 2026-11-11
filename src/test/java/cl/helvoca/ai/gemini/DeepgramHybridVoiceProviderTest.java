package cl.helvoca.ai.gemini;

import cl.helvoca.ai.deepgram.DeepgramVoiceProperties;
import cl.helvoca.ai.deepgram.EconomicVoiceFactsService;
import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.ai.realtime.RealtimeToolService;
import cl.helvoca.call.CallSummaryService;
import cl.helvoca.call.CallTranscriptService;
import cl.helvoca.security.TenantDatabaseContext;
import cl.helvoca.telephony.CallLifecycleService;
import cl.helvoca.voice.VoiceProviderHealthRegistry;
import cl.helvoca.voice.VoiceTransportSession;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeepgramHybridVoiceProviderTest {
    @Test
    void doesNotExposeTheProviderUntilKeyEnablementAndDataApprovalAreAllPresent() {
        DeepgramVoiceProperties properties = new DeepgramVoiceProperties();
        DeepgramHybridVoiceProvider provider = provider(properties);

        assertEquals("deepgram", provider.id());
        assertFalse(provider.configured());
        assertThrows(IllegalStateException.class, () -> provider.createSession(
                context(), mock(VoiceTransportSession.class)));

        properties.setApiKey("test-key");
        properties.setEnabled(true);
        assertFalse(provider.configured(), "The external data-processing approval is mandatory");

        properties.setDataProcessingApproved(true);
        assertTrue(provider.configured());
        assertEquals("nova-3+aura-2-celeste-es", provider.modelId());
    }

    @Test
    void createsASessionWithoutOpeningAnyExternalSocket() {
        DeepgramVoiceProperties properties = new DeepgramVoiceProperties();
        properties.setApiKey("test-key");
        properties.setEnabled(true);
        properties.setDataProcessingApproved(true);
        DeepgramHybridVoiceProvider provider = provider(properties);

        assertNotNull(provider.createSession(context(), mock(VoiceTransportSession.class)));
    }

    private static DeepgramHybridVoiceProvider provider(DeepgramVoiceProperties properties) {
        GeminiLiveVoiceProvider gemini = mock(GeminiLiveVoiceProvider.class);
        RealtimeToolService tools = mock(RealtimeToolService.class);
        EconomicVoiceFactsService facts = mock(EconomicVoiceFactsService.class);
        return new DeepgramHybridVoiceProvider(
                properties,
                gemini,
                tools,
                facts,
                mock(CallTranscriptService.class),
                mock(CallSummaryService.class),
                mock(CallLifecycleService.class),
                new VoiceProviderHealthRegistry(),
                new TenantDatabaseContext());
    }

    private static RealtimeCallContext context() {
        return new RealtimeCallContext(
                UUID.randomUUID(), UUID.randomUUID(), null,
                "+56911112222", "+56220000000", "stream");
    }
}

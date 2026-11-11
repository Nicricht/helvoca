package cl.helvoca.voice;

import cl.helvoca.ai.gemini.GeminiLiveVoiceProvider;
import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.ai.realtime.RealtimeToolService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeepgramEconomyVoiceProviderTest {
    private static final String PHONE = "+56911112222";

    @Test
    void providerHasNoUnscopedReadinessEvenWithConfiguredDependencies() {
        DeepgramEconomyVoiceProperties properties = mock(DeepgramEconomyVoiceProperties.class);
        GeminiLiveVoiceProvider gemini = mock(GeminiLiveVoiceProvider.class);
        RealtimeToolService tools = mock(RealtimeToolService.class);
        DeepgramEconomyVoiceProvider provider = new DeepgramEconomyVoiceProvider(properties, tools, gemini);

        assertEquals("deepgram-economy", provider.id());
        assertEquals("nova-3-multi+aura-2-selena-es", provider.modelId());
        assertFalse(provider.configured());
        when(properties.configuredFor(PHONE)).thenReturn(true);
        when(gemini.configuredFor(PHONE)).thenReturn(true);
        assertTrue(provider.configuredFor(PHONE));
        verifyNoInteractions(tools);
    }

    @Test
    void disconnectedGeminiFallbackOrWrongPhoneNeverCreatesPaidSession() {
        DeepgramEconomyVoiceProperties properties = mock(DeepgramEconomyVoiceProperties.class);
        GeminiLiveVoiceProvider gemini = mock(GeminiLiveVoiceProvider.class);
        RealtimeToolService tools = mock(RealtimeToolService.class);
        DeepgramEconomyVoiceProvider provider = new DeepgramEconomyVoiceProvider(properties, tools, gemini);
        RealtimeCallContext context = context();
        VoiceTransportSession transport = mock(VoiceTransportSession.class);

        assertFalse(provider.configuredFor(PHONE));
        assertThrows(IllegalStateException.class, () -> provider.createSession(context, transport));
        when(properties.permits(context)).thenReturn(true);
        when(properties.configuredFor(PHONE)).thenReturn(true);
        assertFalse(provider.configuredFor(PHONE));
        assertThrows(IllegalStateException.class, () -> provider.createSession(context, transport));
        verifyNoInteractions(tools);
    }

    @Test
    void disabledAgentCannotCreateSessionAndActiveAgentCanCreateOfflineSession() {
        DeepgramEconomyVoiceProperties properties = mock(DeepgramEconomyVoiceProperties.class);
        GeminiLiveVoiceProvider gemini = mock(GeminiLiveVoiceProvider.class);
        RealtimeToolService tools = mock(RealtimeToolService.class);
        DeepgramEconomyVoiceProvider provider = new DeepgramEconomyVoiceProvider(properties, tools, gemini);
        RealtimeCallContext context = context();
        VoiceTransportSession transport = mock(VoiceTransportSession.class);
        when(properties.permits(context)).thenReturn(true);
        when(gemini.configuredFor(PHONE)).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> provider.createSession(context, transport));
        when(tools.agentActive(context)).thenReturn(true);
        VoiceAiSession session = provider.createSession(context, transport);
        assertInstanceOf(DeepgramEconomyVoiceSession.class, session);
        verify(tools, times(2)).agentActive(context);
        session.close();
    }

    private static RealtimeCallContext context() {
        return new RealtimeCallContext(UUID.randomUUID(), UUID.randomUUID(), null,
                "+56955556666", PHONE, "MZ-deepgram");
    }
}

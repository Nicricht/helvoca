package cl.helvoca.voice;

import cl.helvoca.ai.live.OpenAiLiveSipService;
import cl.helvoca.telephony.twilio.TwilioMediaStreamTwimlFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VoiceCallRouterTest {
    private static final String CALL_SID = "CA0123456789abcdef0123456789abcdef";

    @Test
    void selectsGeminiFirstWhenHealthy() {
        VoiceProviderProperties properties = new VoiceProviderProperties();
        properties.setProviderOrder(List.of("gemini", "openai-live"));
        VoiceAiProviderRegistry providers = mock(VoiceAiProviderRegistry.class);
        VoiceAiProvider gemini = mock(VoiceAiProvider.class);
        VoiceProviderHealthRegistry health = new VoiceProviderHealthRegistry();
        TwilioMediaStreamTwimlFactory media = mock(TwilioMediaStreamTwimlFactory.class);
        OpenAiLiveSipService openAi = mock(OpenAiLiveSipService.class);

        when(providers.require("gemini")).thenReturn(gemini);
        when(gemini.id()).thenReturn("gemini");
        when(gemini.configured()).thenReturn(true);
        when(media.twiml("+14355652512", "+56911111111", CALL_SID, "gemini"))
                .thenReturn("<Response><Connect><Stream/></Connect></Response>");

        VoiceCallRouter router = new VoiceCallRouter(properties, providers, health, media, openAi);
        var decision = router.route("+14355652512", "+56911111111", CALL_SID).orElseThrow();

        assertEquals("gemini", decision.providerId());
        assertEquals(VoiceCallRouter.RouteMode.MEDIA_STREAM, decision.mode());
        verifyNoInteractions(openAi);
    }

    @Test
    void openGeminiCircuitFallsBackToOpenAiLiveSip() {
        VoiceProviderProperties properties = new VoiceProviderProperties();
        properties.setProviderOrder(List.of("gemini", "openai-live"));
        VoiceAiProviderRegistry providers = mock(VoiceAiProviderRegistry.class);
        VoiceAiProvider gemini = mock(VoiceAiProvider.class);
        VoiceProviderHealthRegistry health = new VoiceProviderHealthRegistry();
        TwilioMediaStreamTwimlFactory media = mock(TwilioMediaStreamTwimlFactory.class);
        OpenAiLiveSipService openAi = mock(OpenAiLiveSipService.class);

        when(providers.require("gemini")).thenReturn(gemini);
        when(gemini.id()).thenReturn("gemini");
        when(gemini.configured()).thenReturn(true);
        health.failure("gemini", VoiceProviderHealthRegistry.FailureKind.UPSTREAM, "down");
        when(openAi.isReady()).thenReturn(true);
        when(openAi.twiml("+14355652512", "+56911111111", CALL_SID))
                .thenReturn("<Response><Dial><Sip>sip:test</Sip></Dial></Response>");

        VoiceCallRouter router = new VoiceCallRouter(properties, providers, health, media, openAi);
        var decision = router.route("+14355652512", "+56911111111", CALL_SID).orElseThrow();

        assertEquals("openai-live", decision.providerId());
        assertEquals(VoiceCallRouter.RouteMode.SIP, decision.mode());
        verify(openAi).twiml("+14355652512", "+56911111111", CALL_SID);
        verifyNoInteractions(media);
    }

    @Test
    void returnsEmptyWhenNoProviderIsHealthy() {
        VoiceProviderProperties properties = new VoiceProviderProperties();
        properties.setProviderOrder(List.of("gemini", "openai-live"));
        VoiceAiProviderRegistry providers = mock(VoiceAiProviderRegistry.class);
        VoiceAiProvider gemini = mock(VoiceAiProvider.class);
        VoiceProviderHealthRegistry health = new VoiceProviderHealthRegistry();
        TwilioMediaStreamTwimlFactory media = mock(TwilioMediaStreamTwimlFactory.class);
        OpenAiLiveSipService openAi = mock(OpenAiLiveSipService.class);

        when(providers.require("gemini")).thenReturn(gemini);
        when(gemini.id()).thenReturn("gemini");
        when(gemini.configured()).thenReturn(false);
        when(openAi.isReady()).thenReturn(false);

        VoiceCallRouter router = new VoiceCallRouter(properties, providers, health, media, openAi);

        assertTrue(router.route("+14355652512", "+56911111111", CALL_SID).isEmpty());
        assertFalse(router.readiness().ready());
        assertNull(router.readiness().selectedProvider());
    }
}

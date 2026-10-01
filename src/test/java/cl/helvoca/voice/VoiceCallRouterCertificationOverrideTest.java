package cl.helvoca.voice;

import cl.helvoca.ai.live.OpenAiLiveSipService;
import cl.helvoca.telephony.twilio.TwilioMediaStreamTwimlFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VoiceCallRouterCertificationOverrideTest {
    private static final String CALL_SID = "CA0123456789abcdef0123456789abcdef";

    @Test
    void certificationPinSelectsOpenAiEvenWhenGeminiIsFirstAndHealthy() {
        VoiceProviderProperties properties = new VoiceProviderProperties();
        properties.setProviderOrder(List.of("gemini", "openai-live"));
        VoiceAiProviderRegistry providers = mock(VoiceAiProviderRegistry.class);
        VoiceProviderHealthRegistry health = new VoiceProviderHealthRegistry();
        TwilioMediaStreamTwimlFactory media = mock(TwilioMediaStreamTwimlFactory.class);
        OpenAiLiveSipService openAi = mock(OpenAiLiveSipService.class);

        when(openAi.isReady()).thenReturn(true);
        when(openAi.twiml("+14355652512", "+56911111111", CALL_SID))
                .thenReturn("<Response><Dial><Sip>sip:test</Sip></Dial></Response>");

        VoiceCallRouter router = new VoiceCallRouter(properties, providers, health, media, openAi);
        var decision = router.route(
                "+14355652512", "+56911111111", CALL_SID, "openai-live", null).orElseThrow();

        assertEquals("openai-live", decision.providerId());
        assertEquals(VoiceCallRouter.RouteMode.SIP, decision.mode());
        verify(openAi).twiml("+14355652512", "+56911111111", CALL_SID);
        verifyNoInteractions(providers, media);
    }

    @Test
    void certificationPinSelectsGeminiEvenWhenOpenAiIsFirst() {
        VoiceProviderProperties properties = new VoiceProviderProperties();
        properties.setProviderOrder(List.of("openai-live", "gemini"));
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
        var decision = router.route(
                "+14355652512", "+56911111111", CALL_SID, "gemini", null).orElseThrow();

        assertEquals("gemini", decision.providerId());
        assertEquals(VoiceCallRouter.RouteMode.MEDIA_STREAM, decision.mode());
        verify(media).twiml("+14355652512", "+56911111111", CALL_SID, "gemini");
        verifyNoInteractions(openAi);
    }

    @Test
    void configuredCertificationProviderMustAlsoExistInProductionProviderOrder() {
        VoiceProviderProperties properties = new VoiceProviderProperties();
        properties.setProviderOrder(List.of("gemini"));
        VoiceAiProviderRegistry providers = mock(VoiceAiProviderRegistry.class);
        VoiceProviderHealthRegistry health = new VoiceProviderHealthRegistry();
        TwilioMediaStreamTwimlFactory media = mock(TwilioMediaStreamTwimlFactory.class);
        OpenAiLiveSipService openAi = mock(OpenAiLiveSipService.class);

        VoiceCallRouter router = new VoiceCallRouter(properties, providers, health, media, openAi);

        assertTrue(router.route(
                "+14355652512", "+56911111111", CALL_SID, "openai-live", null).isEmpty());
        verifyNoInteractions(providers, media, openAi);
    }

    @Test
    void unsupportedCertificationProviderFailsClosed() {
        VoiceProviderProperties properties = new VoiceProviderProperties();
        VoiceAiProviderRegistry providers = mock(VoiceAiProviderRegistry.class);
        VoiceProviderHealthRegistry health = new VoiceProviderHealthRegistry();
        TwilioMediaStreamTwimlFactory media = mock(TwilioMediaStreamTwimlFactory.class);
        OpenAiLiveSipService openAi = mock(OpenAiLiveSipService.class);

        VoiceCallRouter router = new VoiceCallRouter(properties, providers, health, media, openAi);

        assertTrue(router.route(
                "+14355652512", "+56911111111", CALL_SID, "unknown-provider", null).isEmpty());
        verifyNoInteractions(providers, media, openAi);
    }

    @Test
    void geminiVoiceCandidateCannotBeSentToOpenAiCertificationPin() {
        VoiceProviderProperties properties = new VoiceProviderProperties();
        VoiceAiProviderRegistry providers = mock(VoiceAiProviderRegistry.class);
        VoiceProviderHealthRegistry health = new VoiceProviderHealthRegistry();
        TwilioMediaStreamTwimlFactory media = mock(TwilioMediaStreamTwimlFactory.class);
        OpenAiLiveSipService openAi = mock(OpenAiLiveSipService.class);

        VoiceCallRouter router = new VoiceCallRouter(properties, providers, health, media, openAi);

        assertTrue(router.route(
                "+14355652512", "+56911111111", CALL_SID, "openai-live", "Sulafat").isEmpty());
        verifyNoInteractions(providers, media, openAi);
    }

    @Test
    void certificationProviderValidationAcceptsOnlyProductionVoiceProviders() {
        assertTrue(VoiceCallRouter.validCertificationProviderOverride(""));
        assertTrue(VoiceCallRouter.validCertificationProviderOverride(" gemini "));
        assertTrue(VoiceCallRouter.validCertificationProviderOverride(" OPENAI-LIVE "));
        assertFalse(VoiceCallRouter.validCertificationProviderOverride("openai-realtime"));
        assertFalse(VoiceCallRouter.validCertificationProviderOverride("elevenlabs"));

        assertEquals("gemini", VoiceCallRouter.normalizeCertificationProviderOverride(" GEMINI "));
        assertEquals("openai-live", VoiceCallRouter.normalizeCertificationProviderOverride(" openai-live "));
        assertNull(VoiceCallRouter.normalizeCertificationProviderOverride(""));
        assertNull(VoiceCallRouter.normalizeCertificationProviderOverride("elevenlabs"));
    }
}

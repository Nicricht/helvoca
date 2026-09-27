package cl.helvoca.ai.gemini;

import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.ai.realtime.RealtimeToolService;
import cl.helvoca.call.CallCertificationService;
import cl.helvoca.call.CallSummaryService;
import cl.helvoca.call.CallTranscriptService;
import cl.helvoca.telephony.CallLifecycleService;
import cl.helvoca.voice.VoiceProviderHealthRegistry;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class GeminiLiveVoiceProviderBakeOffTest {

    @Test
    void signedBakeOffVoiceWinsOnlyForThatCall() {
        GeminiLiveProperties properties = new GeminiLiveProperties();
        properties.setEnabled(true);
        properties.setApiKey("test-key");
        properties.setModel("gemini-3.8-live");
        properties.setVoice("Leda");

        RealtimeToolService tools = mock(RealtimeToolService.class);
        GeminiLiveVoiceProvider provider = new GeminiLiveVoiceProvider(
                properties,
                tools,
                mock(CallTranscriptService.class),
                mock(CallSummaryService.class),
                mock(CallLifecycleService.class),
                mock(CallCertificationService.class),
                new VoiceProviderHealthRegistry());

        RealtimeCallContext normal = new RealtimeCallContext(
                UUID.randomUUID(), UUID.randomUUID(), null,
                "+56911111111", "+14355652512", "MZ-normal");
        when(tools.agentVoice(normal, null)).thenReturn("seductive_female");

        RealtimeCallContext bakeOff = new RealtimeCallContext(
                normal.callId(), normal.businessId(), normal.customerId(),
                normal.callerNumber(), normal.destinationNumber(), "MZ-bakeoff", "Sadachbia");
        when(tools.agentVoice(bakeOff, null)).thenReturn("seductive_female");

        assertEquals("Leda", provider.sessionProperties(normal).getVoice());
        assertEquals("Sadachbia", provider.sessionProperties(bakeOff).getVoice());
    }

    @Test
    void bakeOffContextIsClassifiedAsCertificationSession() {
        GeminiLiveProperties properties = new GeminiLiveProperties();
        RealtimeToolService tools = mock(RealtimeToolService.class);
        GeminiLiveVoiceProvider provider = new GeminiLiveVoiceProvider(
                properties,
                tools,
                mock(CallTranscriptService.class),
                mock(CallSummaryService.class),
                mock(CallLifecycleService.class),
                mock(CallCertificationService.class),
                new VoiceProviderHealthRegistry());

        RealtimeCallContext bakeOff = new RealtimeCallContext(
                UUID.randomUUID(), UUID.randomUUID(), null,
                "+56966939611", "+14355652512", "MZ-bakeoff", "Achird");
        RealtimeCallContext normal = new RealtimeCallContext(
                UUID.randomUUID(), UUID.randomUUID(), null,
                "+56911111111", "+14355652512", "MZ-normal");

        assertEquals(true, provider.certificationSession(bakeOff));
        assertEquals(false, provider.certificationSession(normal));
    }

    @Test
    void unknownOverrideCannotBypassCuratedCatalog() {
        GeminiLiveProperties properties = new GeminiLiveProperties();
        properties.setEnabled(true);
        properties.setApiKey("test-key");
        properties.setModel("gemini-3.8-live");
        properties.setVoice("Leda");

        RealtimeToolService tools = mock(RealtimeToolService.class);
        GeminiLiveVoiceProvider provider = new GeminiLiveVoiceProvider(
                properties,
                tools,
                mock(CallTranscriptService.class),
                mock(CallSummaryService.class),
                mock(CallLifecycleService.class),
                mock(CallCertificationService.class),
                new VoiceProviderHealthRegistry());

        RealtimeCallContext context = new RealtimeCallContext(
                UUID.randomUUID(), UUID.randomUUID(), null,
                "+56911111111", "+14355652512", "MZ-test", "NotARealCandidate");
        when(tools.agentVoice(context, null)).thenReturn("seductive_female");

        assertEquals("Leda", provider.sessionProperties(context).getVoice());
    }
}

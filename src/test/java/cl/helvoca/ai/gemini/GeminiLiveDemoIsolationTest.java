package cl.helvoca.ai.gemini;

import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.ai.realtime.RealtimeToolService;
import cl.helvoca.call.CallCertificationService;
import cl.helvoca.call.CallSummaryService;
import cl.helvoca.call.CallTranscriptService;
import cl.helvoca.telephony.CallLifecycleService;
import cl.helvoca.telephony.twilio.TwilioCertificationDemoBindingGate;
import cl.helvoca.voice.VoiceProviderHealthRegistry;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GeminiLiveDemoIsolationTest {
    @Test
    void disabledGlobalGeminiOpensOnlyForCertifiedDemoPhoneAndMatchingCallTenant() {
        var properties = new GeminiLiveProperties();
        properties.setApiKey("synthetic-test-key");
        properties.setModel("gemini-3.8-live");
        var binding = mock(TwilioCertificationDemoBindingGate.class);
        UUID demo = UUID.randomUUID();
        String demoPhone = "+12025550123";
        when(binding.pinnedBusinessId()).thenReturn(demo);
        when(binding.allowsNumber(demoPhone)).thenReturn(true);
        var provider = new GeminiLiveVoiceProvider(properties, mock(RealtimeToolService.class),
                mock(CallTranscriptService.class), mock(CallSummaryService.class),
                mock(CallLifecycleService.class), mock(CallCertificationService.class),
                mock(VoiceProviderHealthRegistry.class), binding, true);

        assertFalse(provider.configured());
        assertTrue(provider.configuredFor(demoPhone));
        assertFalse(provider.configuredFor("+12025550999"));
        assertTrue(provider.sessionProperties(context(demo, demoPhone)).ready());
        assertFalse(provider.sessionProperties(context(UUID.randomUUID(), demoPhone)).ready());
        assertFalse(provider.sessionProperties(context(demo, "+12025550999")).ready());
    }

    @Test
    void demoFeatureFlagOffRejectsEvenCarrierBoundNumber() {
        var properties = new GeminiLiveProperties();
        properties.setApiKey("synthetic-test-key");
        var binding = mock(TwilioCertificationDemoBindingGate.class);
        when(binding.allowsNumber("+12025550123")).thenReturn(true);
        var provider = new GeminiLiveVoiceProvider(properties, mock(RealtimeToolService.class),
                mock(CallTranscriptService.class), mock(CallSummaryService.class),
                mock(CallLifecycleService.class), mock(CallCertificationService.class),
                mock(VoiceProviderHealthRegistry.class), binding, false);

        assertFalse(provider.configuredFor("+12025550123"));
        assertFalse(provider.sessionProperties(context(UUID.randomUUID(), "+12025550123")).ready());
    }

    @Test
    void incompleteCredentialsMissingGateAndNullContextCannotEnableDemo() {
        var properties = new GeminiLiveProperties();
        var binding = mock(TwilioCertificationDemoBindingGate.class);
        var provider = provider(properties, binding, true);
        assertFalse(provider.configuredFor("+12025550123"));
        properties.setApiKey("synthetic-test-key");
        assertFalse(provider.configuredFor("+12025550123"));
        assertFalse(provider(properties, null, true).configuredFor("+12025550123"));
        assertFalse(provider.sessionProperties(null).ready());
        assertFalse(provider.sessionProperties(context(null, "+12025550123")).ready());
        when(binding.pinnedBusinessId()).thenReturn(UUID.randomUUID());
        assertFalse(provider.sessionProperties(context(UUID.randomUUID(), "+12025550123")).ready());
        UUID demo = UUID.randomUUID();
        when(binding.pinnedBusinessId()).thenReturn(demo);
        assertFalse(provider.sessionProperties(context(demo, "+12025550123")).ready());
    }

    @Test
    void ordinaryGlobalGeminiReadinessRemainsAvailable() {
        var properties = new GeminiLiveProperties();
        properties.setApiKey("synthetic-test-key");
        properties.setEnabled(true);
        var provider = provider(properties, null, false);
        assertTrue(provider.configuredFor("+12025550999"));
        assertTrue(provider.sessionProperties(context(UUID.randomUUID(), "+12025550999")).ready());
    }

    private static GeminiLiveVoiceProvider provider(GeminiLiveProperties properties,
            TwilioCertificationDemoBindingGate binding, boolean enabled) {
        return new GeminiLiveVoiceProvider(properties, mock(RealtimeToolService.class),
                mock(CallTranscriptService.class), mock(CallSummaryService.class),
                mock(CallLifecycleService.class), mock(CallCertificationService.class),
                mock(VoiceProviderHealthRegistry.class), binding, enabled);
    }

    private static RealtimeCallContext context(UUID businessId, String destination) {
        return new RealtimeCallContext(UUID.randomUUID(), businessId, null,
                "+56911111111", destination, "MZsynthetic");
    }
}

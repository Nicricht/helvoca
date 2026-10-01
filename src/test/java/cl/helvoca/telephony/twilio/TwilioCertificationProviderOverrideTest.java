package cl.helvoca.telephony.twilio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TwilioCertificationProviderOverrideTest {

    @Test
    void providerPinIsAllowedOnlyForOutboundCertification() {
        assertTrue(TwilioCertificationStartupRunner.validProviderOverride("", "outbound-test"));
        assertTrue(TwilioCertificationStartupRunner.validProviderOverride("gemini", "outbound-test"));
        assertTrue(TwilioCertificationStartupRunner.validProviderOverride(" OPENAI-LIVE ", "outbound-test"));

        assertFalse(TwilioCertificationStartupRunner.validProviderOverride("elevenlabs", "outbound-test"));
        assertFalse(TwilioCertificationStartupRunner.validProviderOverride("gemini", "inbound-certification"));
        assertFalse(TwilioCertificationStartupRunner.validProviderOverride("openai-live", "inbound-certification"));
    }

    @Test
    void providerAndVoiceOverridesMustBeCompatible() {
        assertTrue(TwilioCertificationStartupRunner.validProviderVoiceCombination(
                "gemini", "Sulafat", "outbound-test"));
        assertTrue(TwilioCertificationStartupRunner.validProviderVoiceCombination(
                "openai-live", "", "outbound-test"));
        assertTrue(TwilioCertificationStartupRunner.validProviderVoiceCombination(
                "", "Sadachbia", "outbound-test"));

        assertFalse(TwilioCertificationStartupRunner.validProviderVoiceCombination(
                "openai-live", "Sulafat", "outbound-test"));
        assertFalse(TwilioCertificationStartupRunner.validProviderVoiceCombination(
                "elevenlabs", "", "outbound-test"));
    }
}

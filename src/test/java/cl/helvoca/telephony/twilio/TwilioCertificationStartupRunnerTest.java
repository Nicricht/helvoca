package cl.helvoca.telephony.twilio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TwilioCertificationStartupRunnerTest {

    @Test
    void certificationCallbackBaseUrlStripsAllAndOnlyTrailingSlashes() {
        assertEquals("https://example.test",
                TwilioCertificationStartupRunner.trimTrailingSlash("https://example.test"));
        assertEquals("https://example.test",
                TwilioCertificationStartupRunner.trimTrailingSlash("https://example.test/"));
        assertEquals("https://example.test",
                TwilioCertificationStartupRunner.trimTrailingSlash("https://example.test////"));
        assertEquals("https://example.test/sub/path",
                TwilioCertificationStartupRunner.trimTrailingSlash("https://example.test/sub/path//"));
        assertEquals("",
                TwilioCertificationStartupRunner.trimTrailingSlash("/"));
        assertEquals("",
                TwilioCertificationStartupRunner.trimTrailingSlash(""));
    }

    @Test
    void twilioCertificationFormEncodingProtectsPhoneAndCallbackParameters() {
        assertEquals("To=%2B56912345678",
                TwilioCertificationStartupRunner.form("To", "+56912345678"));
        assertEquals("StatusCallbackEvent=call+answered",
                TwilioCertificationStartupRunner.form("StatusCallbackEvent", "call answered"));
        assertEquals("a%26b=x%3D1%26y%3D%2B",
                TwilioCertificationStartupRunner.form("a&b", "x=1&y=+"));
        assertEquals("voice=Caf%C3%A9",
                TwilioCertificationStartupRunner.form("voice", "Café"));
        assertEquals("empty=",
                TwilioCertificationStartupRunner.form("empty", ""));
    }

    @Test
    void maskedCertificationLogNumbersNeverDiscloseFullNumbers() {
        assertEquals("***", TwilioCertificationStartupRunner.mask(null));
        assertEquals("***", TwilioCertificationStartupRunner.mask(""));
        assertEquals("***", TwilioCertificationStartupRunner.mask("1234"));
        assertEquals("***5678", TwilioCertificationStartupRunner.mask("12345678"));
        assertEquals("***4321", TwilioCertificationStartupRunner.mask("+56912344321"));
    }

    @Test
    void twilioCreateCallErrorDiagnosticsNeverExposeProviderBody() {
        String sensitiveBody = "{\"message\":\"synthetic-caller@example.test token=private-token\"}";
        assertEquals("[provider response omitted]",
                TwilioCertificationStartupRunner.truncate(sensitiveBody));
        assertEquals("[provider response omitted]",
                TwilioCertificationStartupRunner.truncate("X".repeat(1000)));
        assertEquals("[provider response omitted]",
                TwilioCertificationStartupRunner.truncate(""));
        assertEquals("[provider response omitted]",
                TwilioCertificationStartupRunner.truncate(null));
    }

    @Test
    void normalizesInboundCertificationDirection() {
        assertEquals("inbound-certification",
                TwilioCertificationStartupRunner.normalizeDirection(" INBOUND-CERTIFICATION "));
    }

    @Test
    void fallsBackToOutboundTestForUnknownDirection() {
        assertEquals("outbound-test", TwilioCertificationStartupRunner.normalizeDirection("unexpected"));
        assertEquals("outbound-test", TwilioCertificationStartupRunner.normalizeDirection("inbound"));
        assertEquals("outbound-test", TwilioCertificationStartupRunner.normalizeDirection(null));
    }

    @Test
    void bakeOffVoiceMustBeCuratedAndOutboundOnly() {
        assertTrue(TwilioCertificationStartupRunner.validVoiceOverride("", "outbound-test"));
        assertTrue(TwilioCertificationStartupRunner.validVoiceOverride("Sadachbia", "outbound-test"));
        assertTrue(TwilioCertificationStartupRunner.validVoiceOverride(" leda ", "outbound-test"));
        assertFalse(TwilioCertificationStartupRunner.validVoiceOverride("Despina", "outbound-test"));
        assertFalse(TwilioCertificationStartupRunner.validVoiceOverride("Sadachbia", "inbound-certification"));
    }

    @Test
    void outboundTestAlwaysKeepsSafetyHangup() {
        assertTrue(TwilioCertificationStartupRunner.shouldScheduleSafetyHangup("outbound-test"));
        assertTrue(TwilioCertificationStartupRunner.shouldScheduleSafetyHangup("unexpected"));
    }

    @Test
    void inboundCertificationKeepsSafetyHangup() {
        assertTrue(TwilioCertificationStartupRunner.shouldScheduleSafetyHangup("inbound-certification"));
    }

    @Test
    void certificationTargetMustMatchExplicitAllowlist() {
        assertTrue(TwilioCertificationStartupRunner.isAllowedTarget("+56911111111", "+56911111111"));
        assertFalse(TwilioCertificationStartupRunner.isAllowedTarget("+56922222222", "+56911111111"));
        assertFalse(TwilioCertificationStartupRunner.isAllowedTarget("+56911111111", ""));
    }

    @Test
    void blocksExplicitlyForbiddenCertificationTarget() {
        assertTrue(TwilioCertificationStartupRunner.isForbiddenTarget("+56975856664", "+56975856664"));
        assertFalse(TwilioCertificationStartupRunner.isForbiddenTarget("+56911111111", "+56975856664"));
        assertFalse(TwilioCertificationStartupRunner.isForbiddenTarget("+56911111111", ""));
    }
}

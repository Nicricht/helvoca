package cl.helvoca.telephony.twilio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TwilioCertificationStartupRunnerTest {

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

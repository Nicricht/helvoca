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
    void outboundTestLetsHumanControlHangup() {
        assertFalse(TwilioCertificationStartupRunner.shouldScheduleSafetyHangup("outbound-test"));
        assertFalse(TwilioCertificationStartupRunner.shouldScheduleSafetyHangup("unexpected"));
    }

    @Test
    void inboundCertificationKeepsSafetyHangup() {
        assertTrue(TwilioCertificationStartupRunner.shouldScheduleSafetyHangup("inbound-certification"));
    }

    @Test
    void blocksExplicitlyForbiddenCertificationTarget() {
        assertTrue(TwilioCertificationStartupRunner.isForbiddenTarget("+56975856664", "+56975856664"));
        assertFalse(TwilioCertificationStartupRunner.isForbiddenTarget("+56911111111", "+56975856664"));
        assertFalse(TwilioCertificationStartupRunner.isForbiddenTarget("+56911111111", ""));
    }
}

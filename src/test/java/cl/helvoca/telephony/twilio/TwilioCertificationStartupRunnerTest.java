package cl.helvoca.telephony.twilio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}

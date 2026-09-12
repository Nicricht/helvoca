package cl.helvoca.telephony.twilio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TwilioCertificationStartupRunnerTest {

    @Test
    void normalizesInboundDirection() {
        assertEquals("inbound", TwilioCertificationStartupRunner.normalizeDirection(" INBOUND "));
    }

    @Test
    void fallsBackToOutboundTestForUnknownDirection() {
        assertEquals("outbound-test", TwilioCertificationStartupRunner.normalizeDirection("unexpected"));
        assertEquals("outbound-test", TwilioCertificationStartupRunner.normalizeDirection(null));
    }
}

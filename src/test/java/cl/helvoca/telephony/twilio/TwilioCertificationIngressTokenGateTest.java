package cl.helvoca.telephony.twilio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class TwilioCertificationIngressTokenGateTest {
    private static final String TOKEN = "11111111-1111-1111-1111-111111111111";
    private static final String CALL_SID = "CA0123456789abcdef0123456789abcdef";
    private static final String FROM = "+14355652512";
    private static final String ALLOWED_TO = "+56966939611";
    private static final String FORBIDDEN_TO = "+56975856664";

    @Test
    void validOneShotTokenIsConsumedAgainstExactCallSid() {
        TwilioCertificationCommandStore store = mock(TwilioCertificationCommandStore.class);
        when(store.consumeIngressToken(TOKEN, CALL_SID)).thenReturn(true);
        TwilioCertificationIngressTokenGate gate =
                new TwilioCertificationIngressTokenGate(store, FROM, ALLOWED_TO, FORBIDDEN_TO);

        assertTrue(gate.authorize(TOKEN, CALL_SID, FROM, ALLOWED_TO));
        verify(store).consumeIngressToken(TOKEN, CALL_SID);
    }

    @Test
    void wrongBusinessNumberFailsClosedBeforeDatabase() {
        TwilioCertificationCommandStore store = mock(TwilioCertificationCommandStore.class);
        TwilioCertificationIngressTokenGate gate =
                new TwilioCertificationIngressTokenGate(store, FROM, ALLOWED_TO, FORBIDDEN_TO);

        assertFalse(gate.authorize(TOKEN, CALL_SID, "+14350000000", ALLOWED_TO));
        verifyNoInteractions(store);
    }

    @Test
    void nonAllowlistedOrForbiddenTargetFailsClosed() {
        TwilioCertificationCommandStore store = mock(TwilioCertificationCommandStore.class);
        TwilioCertificationIngressTokenGate gate =
                new TwilioCertificationIngressTokenGate(store, FROM, ALLOWED_TO, FORBIDDEN_TO);

        assertFalse(gate.authorize(TOKEN, CALL_SID, FROM, "+56911111111"));
        assertFalse(gate.authorize(TOKEN, CALL_SID, FROM, FORBIDDEN_TO));
        verifyNoInteractions(store);
    }

    @Test
    void malformedTokenOrCallSidFailsClosed() {
        TwilioCertificationCommandStore store = mock(TwilioCertificationCommandStore.class);
        TwilioCertificationIngressTokenGate gate =
                new TwilioCertificationIngressTokenGate(store, FROM, ALLOWED_TO, FORBIDDEN_TO);

        assertFalse(gate.authorize("bad", CALL_SID, FROM, ALLOWED_TO));
        assertFalse(gate.authorize(TOKEN, "not-a-call-sid", FROM, ALLOWED_TO));
        verifyNoInteractions(store);
    }

    @Test
    void consumedOrExpiredTokenIsRejectedByStore() {
        TwilioCertificationCommandStore store = mock(TwilioCertificationCommandStore.class);
        when(store.consumeIngressToken(TOKEN, CALL_SID)).thenReturn(false);
        TwilioCertificationIngressTokenGate gate =
                new TwilioCertificationIngressTokenGate(store, FROM, ALLOWED_TO, FORBIDDEN_TO);

        assertFalse(gate.authorize(TOKEN, CALL_SID, FROM, ALLOWED_TO));
    }
}

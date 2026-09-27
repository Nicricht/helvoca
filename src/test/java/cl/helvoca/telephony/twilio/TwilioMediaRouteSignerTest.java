package cl.helvoca.telephony.twilio;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TwilioMediaRouteSignerTest {

    @Test
    void bakeOffTokenIsBoundToExactVoiceCandidate() {
        TwilioProperties properties = new TwilioProperties();
        properties.setAuthToken("twilio-test-secret");
        TwilioMediaRouteSigner signer = new TwilioMediaRouteSigner(properties);
        String callSid = "CA0123456789abcdef0123456789abcdef";
        long issuedAt = Instant.now().getEpochSecond();

        String token = signer.sign(
                "+14355652512", "+56911111111", callSid, "gemini", "Leda", issuedAt);

        assertTrue(signer.verify(
                "+14355652512", "+56911111111", callSid, "gemini", "Leda",
                String.valueOf(issuedAt), token));
        assertFalse(signer.verify(
                "+14355652512", "+56911111111", callSid, "gemini", "Sadachbia",
                String.valueOf(issuedAt), token));
        assertFalse(signer.verify(
                "+14355652512", "+56911111111", callSid, "gemini", null,
                String.valueOf(issuedAt), token));
    }

    @Test
    void routeTokenIsBoundToCallProviderAndExpiry() {
        TwilioProperties properties = new TwilioProperties();
        properties.setAuthToken("twilio-test-secret");
        TwilioMediaRouteSigner signer = new TwilioMediaRouteSigner(properties);
        String callSid = "CA0123456789abcdef0123456789abcdef";
        long issuedAt = Instant.now().getEpochSecond();

        String token = signer.sign(
                "+14355652512", "+56911111111", callSid, "gemini", issuedAt);

        assertTrue(signer.verify(
                "+14355652512", "+56911111111", callSid, "gemini", String.valueOf(issuedAt), token));
        assertFalse(signer.verify(
                "+14355652512", "+56911111111", callSid, "openai", String.valueOf(issuedAt), token));
        assertFalse(signer.verify(
                "+14355652512", "+56911111111", "CAffffffffffffffffffffffffffffffff",
                "gemini", String.valueOf(issuedAt), token));
        assertFalse(signer.verify(
                "+14355652512", "+56911111111", callSid, "gemini", String.valueOf(issuedAt - 600), token));
    }
}

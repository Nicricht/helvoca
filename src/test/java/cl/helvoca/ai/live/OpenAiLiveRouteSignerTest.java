package cl.helvoca.ai.live;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class OpenAiLiveRouteSignerTest {

    @Test
    void tokenIsBoundToBusinessCallerCallSidAndTimestamp() {
        OpenAiLiveProperties properties = new OpenAiLiveProperties();
        properties.setWebhookSecret("route-secret");
        OpenAiLiveRouteSigner signer = new OpenAiLiveRouteSigner(properties);
        long issuedAt = Instant.now().getEpochSecond();
        String callSid = "CA0123456789abcdef0123456789abcdef";

        String token = signer.sign("+14355652512", "+56911111111", callSid, issuedAt);

        assertTrue(signer.verify("+14355652512", "+56911111111", callSid,
                String.valueOf(issuedAt), token));
        assertFalse(signer.verify("+14355652512", "+56999999999", callSid,
                String.valueOf(issuedAt), token));
        assertFalse(signer.verify("+19999999999", "+56911111111", callSid,
                String.valueOf(issuedAt), token));
        assertFalse(signer.verify("+14355652512", "+56911111111",
                "CAffffffffffffffffffffffffffffffff", String.valueOf(issuedAt), token));
        assertFalse(signer.verify("+14355652512", "+56911111111", callSid,
                String.valueOf(issuedAt - 600), token));
    }
}

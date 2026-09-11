package cl.helvoca.ai.live;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OpenAiLiveRouteSignerTest {

    @Test
    void tokenIsBoundToBusinessAndCaller() {
        OpenAiLiveProperties properties = new OpenAiLiveProperties();
        properties.setWebhookSecret("route-secret");
        OpenAiLiveRouteSigner signer = new OpenAiLiveRouteSigner(properties);

        String token = signer.sign("+14355652512", "+56911111111");

        assertTrue(signer.verify("+14355652512", "+56911111111", token));
        assertFalse(signer.verify("+14355652512", "+56999999999", token));
        assertFalse(signer.verify("+19999999999", "+56911111111", token));
    }
}

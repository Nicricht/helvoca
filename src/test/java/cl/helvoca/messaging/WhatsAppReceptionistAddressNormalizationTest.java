package cl.helvoca.messaging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WhatsAppReceptionistAddressNormalizationTest {

    @Test
    void metaDigitsOnlyInternationalSenderIsCanonicalizedBeforeConversationLookup() {
        assertEquals("+56911112222", WhatsAppReceptionistService.normalizeAddress("56911112222"));
    }

    @Test
    void alreadyCanonicalAndTwilioAddressesRemainCanonical() {
        assertEquals("+56911112222", WhatsAppReceptionistService.normalizeAddress("+56911112222"));
        assertEquals("+56911112222", WhatsAppReceptionistService.normalizeAddress("whatsapp:+56911112222"));
    }

    @Test
    void malformedAddressIsNotPromotedToATrustedInternationalPhone() {
        assertEquals("569-1111", WhatsAppReceptionistService.normalizeAddress("569-1111"));
    }
}

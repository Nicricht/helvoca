package cl.helvoca.messaging.meta;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class MetaWhatsAppWebhookControllerTest {

    @Test
    void matchingSubscribeChallengeIsReturned() {
        MetaWhatsAppProperties properties = new MetaWhatsAppProperties();
        properties.setVerifyToken("recepvoz-verify");

        var response = new MetaWhatsAppWebhookController(properties, mock(MetaWhatsAppTenantResolver.class))
                .verify("subscribe", "recepvoz-verify", "123456");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("123456", response.getBody());
    }

    @Test
    void wrongVerifyTokenIsRejected() {
        MetaWhatsAppProperties properties = new MetaWhatsAppProperties();
        properties.setVerifyToken("recepvoz-verify");

        var response = new MetaWhatsAppWebhookController(properties, mock(MetaWhatsAppTenantResolver.class))
                .verify("subscribe", "wrong-token", "123456");

        assertEquals(403, response.getStatusCode().value());
    }

    @Test
    void missingConfiguredTokenFailsClosed() {
        MetaWhatsAppProperties properties = new MetaWhatsAppProperties();

        var response = new MetaWhatsAppWebhookController(properties, mock(MetaWhatsAppTenantResolver.class))
                .verify("subscribe", "", "123456");

        assertEquals(403, response.getStatusCode().value());
    }

    @Test
    void nonSubscribeModeIsRejected() {
        MetaWhatsAppProperties properties = new MetaWhatsAppProperties();
        properties.setVerifyToken("recepvoz-verify");

        var response = new MetaWhatsAppWebhookController(properties, mock(MetaWhatsAppTenantResolver.class))
                .verify("unsubscribe", "recepvoz-verify", "123456");

        assertEquals(403, response.getStatusCode().value());
    }

    @Test
    void blankChallengeIsRejected() {
        MetaWhatsAppProperties properties = new MetaWhatsAppProperties();
        properties.setVerifyToken("recepvoz-verify");

        var response = new MetaWhatsAppWebhookController(properties, mock(MetaWhatsAppTenantResolver.class))
                .verify("subscribe", "recepvoz-verify", " ");

        assertEquals(403, response.getStatusCode().value());
    }
}

package cl.helvoca.messaging.meta;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MetaWhatsAppWebhookPostTest {

    @Test
    void validSignatureIsAccepted() {
        MetaWhatsAppProperties properties = new MetaWhatsAppProperties();
        properties.setAppSecret("unit-test-key");
        byte[] body = "{\"entry\":[]}".getBytes(StandardCharsets.UTF_8);

        var response = new MetaWhatsAppWebhookController(properties)
                .inbound("sha256=af0607e7ca1f292e213d64327ce97a36c7d316af967ebe890972c787cd234ac4", body);

        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void invalidSignatureIsRejected() {
        MetaWhatsAppProperties properties = new MetaWhatsAppProperties();
        properties.setAppSecret("unit-test-key");

        var response = new MetaWhatsAppWebhookController(properties)
                .inbound("sha256=" + "0".repeat(64), "{}".getBytes(StandardCharsets.UTF_8));

        assertEquals(403, response.getStatusCode().value());
    }

    @Test
    void missingAppSecretFailsClosed() {
        MetaWhatsAppProperties properties = new MetaWhatsAppProperties();

        var response = new MetaWhatsAppWebhookController(properties)
                .inbound("sha256=" + "0".repeat(64), "{}".getBytes(StandardCharsets.UTF_8));

        assertEquals(403, response.getStatusCode().value());
    }

    @Test
    void malformedSignatureIsRejected() {
        MetaWhatsAppProperties properties = new MetaWhatsAppProperties();
        properties.setAppSecret("unit-test-key");

        var response = new MetaWhatsAppWebhookController(properties)
                .inbound("sha1=deadbeef", "{}".getBytes(StandardCharsets.UTF_8));

        assertEquals(403, response.getStatusCode().value());
    }
}

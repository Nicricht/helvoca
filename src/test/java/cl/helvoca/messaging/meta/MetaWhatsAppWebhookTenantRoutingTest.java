package cl.helvoca.messaging.meta;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class MetaWhatsAppWebhookTenantRoutingTest {

    @Test
    void enabledWebhookResolvesEachTextMessageByMetaPhoneNumberId() {
        MetaWhatsAppProperties properties = new MetaWhatsAppProperties();
        properties.setEnabled(true);
        properties.setWebhookValidationEnabled(false);

        MetaWhatsAppTenantResolver resolver = mock(MetaWhatsAppTenantResolver.class);
        when(resolver.resolveBusinessId("PHONE-123"))
                .thenReturn(Optional.of(UUID.randomUUID()));

        byte[] body = """
                {
                  "entry": [{
                    "changes": [{
                      "value": {
                        "metadata": {"phone_number_id": "PHONE-123"},
                        "messages": [{
                          "from": "56911111111",
                          "id": "wamid.TEST-1",
                          "type": "text",
                          "text": {"body": "Hola"}
                        }]
                      }
                    }]
                  }]
                }
                """.getBytes(StandardCharsets.UTF_8);

        var response = new MetaWhatsAppWebhookController(properties, resolver)
                .inbound(null, body);

        assertEquals(200, response.getStatusCode().value());
        verify(resolver).resolveBusinessId("PHONE-123");
        verifyNoMoreInteractions(resolver);
    }

    @Test
    void disabledIntegrationNeverAttemptsTenantResolution() {
        MetaWhatsAppProperties properties = new MetaWhatsAppProperties();
        properties.setEnabled(false);
        properties.setWebhookValidationEnabled(false);
        MetaWhatsAppTenantResolver resolver = mock(MetaWhatsAppTenantResolver.class);

        var response = new MetaWhatsAppWebhookController(properties, resolver)
                .inbound(null, "{}".getBytes(StandardCharsets.UTF_8));

        assertEquals(200, response.getStatusCode().value());
        verifyNoInteractions(resolver);
    }
}

package cl.helvoca.messaging.meta;

import cl.helvoca.messaging.WhatsAppReceptionistService;
import cl.helvoca.security.TenantDatabaseContext;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class MetaWhatsAppWebhookTenantRoutingTest {

    @Test
    void enabledWebhookProcessesResolvedMessageInsideTenantScope() {
        MetaWhatsAppProperties properties = new MetaWhatsAppProperties();
        properties.setEnabled(true);
        properties.setWebhookValidationEnabled(false);

        MetaWhatsAppTenantResolver resolver = mock(MetaWhatsAppTenantResolver.class);
        WhatsAppReceptionistService receptionist = mock(WhatsAppReceptionistService.class);
        TenantDatabaseContext context = new TenantDatabaseContext();

        UUID businessId = UUID.randomUUID();
        UUID phoneRecordId = UUID.randomUUID();
        when(resolver.resolveRoute("PHONE-123"))
                .thenReturn(Optional.of(new MetaWhatsAppTenantRoute(
                        businessId,
                        phoneRecordId,
                        "+56955555555")));

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

        var response = new MetaWhatsAppWebhookController(properties, resolver, context, receptionist)
                .inbound(null, body);

        assertEquals(200, response.getStatusCode().value());
        verify(resolver).resolveRoute("PHONE-123");
        verify(receptionist).handleResolved(
                "wamid.TEST-1",
                businessId,
                phoneRecordId,
                "56911111111",
                "Hola");
    }

    @Test
    void unresolvedMetaPhoneDoesNotCallReceptionist() {
        MetaWhatsAppProperties properties = new MetaWhatsAppProperties();
        properties.setEnabled(true);
        properties.setWebhookValidationEnabled(false);

        MetaWhatsAppTenantResolver resolver = mock(MetaWhatsAppTenantResolver.class);
        WhatsAppReceptionistService receptionist = mock(WhatsAppReceptionistService.class);
        when(resolver.resolveRoute("UNKNOWN")).thenReturn(Optional.empty());

        byte[] body = """
                {
                  "entry": [{
                    "changes": [{
                      "value": {
                        "metadata": {"phone_number_id": "UNKNOWN"},
                        "messages": [{
                          "from": "56911111111",
                          "id": "wamid.TEST-2",
                          "type": "text",
                          "text": {"body": "Hola"}
                        }]
                      }
                    }]
                  }]
                }
                """.getBytes(StandardCharsets.UTF_8);

        var response = new MetaWhatsAppWebhookController(
                properties,
                resolver,
                new TenantDatabaseContext(),
                receptionist)
                .inbound(null, body);

        assertEquals(200, response.getStatusCode().value());
        verifyNoInteractions(receptionist);
    }

    @Test
    void processingFailureReturnsServerErrorSoMetaCanRetry() {
        MetaWhatsAppProperties properties = new MetaWhatsAppProperties();
        properties.setEnabled(true);
        properties.setWebhookValidationEnabled(false);

        MetaWhatsAppTenantResolver resolver = mock(MetaWhatsAppTenantResolver.class);
        WhatsAppReceptionistService receptionist = mock(WhatsAppReceptionistService.class);
        UUID businessId = UUID.randomUUID();
        UUID phoneRecordId = UUID.randomUUID();

        when(resolver.resolveRoute("PHONE-123"))
                .thenReturn(Optional.of(new MetaWhatsAppTenantRoute(
                        businessId,
                        phoneRecordId,
                        "+56955555555")));
        when(receptionist.handleResolved(
                "wamid.TEST-3",
                businessId,
                phoneRecordId,
                "56911111111",
                "Hola"))
                .thenThrow(new IllegalStateException("database unavailable"));

        byte[] body = """
                {
                  "entry": [{
                    "changes": [{
                      "value": {
                        "metadata": {"phone_number_id": "PHONE-123"},
                        "messages": [{
                          "from": "56911111111",
                          "id": "wamid.TEST-3",
                          "type": "text",
                          "text": {"body": "Hola"}
                        }]
                      }
                    }]
                  }]
                }
                """.getBytes(StandardCharsets.UTF_8);

        var response = new MetaWhatsAppWebhookController(
                properties,
                resolver,
                new TenantDatabaseContext(),
                receptionist)
                .inbound(null, body);

        assertEquals(500, response.getStatusCode().value());
    }

    @Test
    void disabledIntegrationNeverAttemptsTenantResolutionOrProcessing() {
        MetaWhatsAppProperties properties = new MetaWhatsAppProperties();
        properties.setEnabled(false);
        properties.setWebhookValidationEnabled(false);

        MetaWhatsAppTenantResolver resolver = mock(MetaWhatsAppTenantResolver.class);
        WhatsAppReceptionistService receptionist = mock(WhatsAppReceptionistService.class);

        var response = new MetaWhatsAppWebhookController(
                properties,
                resolver,
                new TenantDatabaseContext(),
                receptionist)
                .inbound(null, "{}".getBytes(StandardCharsets.UTF_8));

        assertEquals(200, response.getStatusCode().value());
        verifyNoInteractions(resolver, receptionist);
    }
    @Test
    void audioMessageIsTranscribedThenProcessedByTheSameReceptionistFlow() {
        MetaWhatsAppProperties properties = new MetaWhatsAppProperties();
        properties.setEnabled(true);
        properties.setWebhookValidationEnabled(false);

        MetaWhatsAppTenantResolver resolver = mock(MetaWhatsAppTenantResolver.class);
        WhatsAppReceptionistService receptionist = mock(WhatsAppReceptionistService.class);
        MetaWhatsAppAudioTranscriptionService audio = mock(MetaWhatsAppAudioTranscriptionService.class);
        TenantDatabaseContext context = new TenantDatabaseContext();

        UUID businessId = UUID.randomUUID();
        UUID phoneRecordId = UUID.randomUUID();
        when(resolver.resolveRoute("PHONE-123"))
                .thenReturn(Optional.of(new MetaWhatsAppTenantRoute(
                        businessId,
                        phoneRecordId,
                        "+56955555555")));
        when(audio.transcribe(businessId, "123456789012345"))
                .thenReturn("Quiero reservar hoy a las cuatro");

        byte[] body = """
                {
                  "entry": [{
                    "changes": [{
                      "value": {
                        "metadata": {"phone_number_id": "PHONE-123"},
                        "messages": [{
                          "from": "56911111111",
                          "id": "wamid.AUDIO-1",
                          "type": "audio",
                          "audio": {
                            "id": "123456789012345",
                            "mime_type": "audio/ogg; codecs=opus"
                          }
                        }]
                      }
                    }]
                  }]
                }
                """.getBytes(StandardCharsets.UTF_8);

        MetaWhatsAppWebhookController controller =
                new MetaWhatsAppWebhookController(properties, resolver, context, receptionist);
        controller.setAudioTranscription(audio);

        var response = controller.inbound(null, body);

        assertEquals(200, response.getStatusCode().value());
        verify(audio).transcribe(businessId, "123456789012345");
        verify(receptionist).handleResolved(
                "wamid.AUDIO-1",
                businessId,
                phoneRecordId,
                "56911111111",
                "Quiero reservar hoy a las cuatro");
    }

}

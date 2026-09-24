package cl.helvoca.messaging.meta;

import cl.helvoca.jobs.PersistentJobProperties;
import cl.helvoca.messaging.WhatsAppReceptionistService;
import cl.helvoca.security.TenantDatabaseContext;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class MetaWhatsAppWebhookAsyncAudioTest {

    @Test
    void asyncAudioEnqueuesDurablyAndDoesNotRunSttInsideWebhook() {
        MetaWhatsAppProperties properties = enabledProperties();
        MetaWhatsAppInboundProperties inboundProperties = new MetaWhatsAppInboundProperties();
        inboundProperties.setAsyncAudioEnabled(true);
        PersistentJobProperties jobProperties = new PersistentJobProperties();
        jobProperties.setEnabled(true);

        MetaWhatsAppTenantResolver resolver = mock(MetaWhatsAppTenantResolver.class);
        WhatsAppReceptionistService receptionist = mock(WhatsAppReceptionistService.class);
        MetaWhatsAppInboundJobService inboundJobs = mock(MetaWhatsAppInboundJobService.class);
        MetaWhatsAppAudioTranscriptionService audio = mock(MetaWhatsAppAudioTranscriptionService.class);
        UUID businessId = UUID.randomUUID();
        UUID phoneRecordId = UUID.randomUUID();
        MetaWhatsAppTenantRoute route = new MetaWhatsAppTenantRoute(
                businessId, phoneRecordId, "+56955555555");
        MetaWhatsAppInboundAudio parsed = new MetaWhatsAppInboundAudio(
                "wamid.AUDIO-ASYNC",
                "PHONE-123",
                "56911111111",
                "123456789012345",
                "audio/ogg; codecs=opus");
        when(resolver.resolveRoute("PHONE-123")).thenReturn(Optional.of(route));

        MetaWhatsAppWebhookController controller = new MetaWhatsAppWebhookController(
                properties,
                resolver,
                new TenantDatabaseContext(),
                receptionist,
                inboundJobs,
                inboundProperties,
                jobProperties);
        controller.setAudioTranscription(audio);

        var response = controller.inbound(null, audioBody("wamid.AUDIO-ASYNC"));

        assertEquals(200, response.getStatusCode().value());
        verify(inboundJobs).enqueueAudio(route, parsed);
        verifyNoInteractions(audio, receptionist);
    }

    @Test
    void asyncAudioReturns503WhenDurableWorkerIsDisabledWithoutRunningStt() {
        MetaWhatsAppProperties properties = enabledProperties();
        MetaWhatsAppInboundProperties inboundProperties = new MetaWhatsAppInboundProperties();
        inboundProperties.setAsyncAudioEnabled(true);
        PersistentJobProperties jobProperties = new PersistentJobProperties();
        jobProperties.setEnabled(false);

        MetaWhatsAppTenantResolver resolver = mock(MetaWhatsAppTenantResolver.class);
        WhatsAppReceptionistService receptionist = mock(WhatsAppReceptionistService.class);
        MetaWhatsAppInboundJobService inboundJobs = mock(MetaWhatsAppInboundJobService.class);
        MetaWhatsAppAudioTranscriptionService audio = mock(MetaWhatsAppAudioTranscriptionService.class);
        when(resolver.resolveRoute("PHONE-123")).thenReturn(Optional.of(new MetaWhatsAppTenantRoute(
                UUID.randomUUID(), UUID.randomUUID(), "+56955555555")));

        MetaWhatsAppWebhookController controller = new MetaWhatsAppWebhookController(
                properties,
                resolver,
                new TenantDatabaseContext(),
                receptionist,
                inboundJobs,
                inboundProperties,
                jobProperties);
        controller.setAudioTranscription(audio);

        var response = controller.inbound(null, audioBody("wamid.AUDIO-JOBS-OFF"));

        assertEquals(503, response.getStatusCode().value());
        verifyNoInteractions(inboundJobs, audio, receptionist);
    }

    private static MetaWhatsAppProperties enabledProperties() {
        MetaWhatsAppProperties properties = new MetaWhatsAppProperties();
        properties.setEnabled(true);
        properties.setWebhookValidationEnabled(false);
        return properties;
    }

    private static byte[] audioBody(String messageId) {
        return ("""
                {
                  "entry": [{
                    "changes": [{
                      "value": {
                        "metadata": {"phone_number_id": "PHONE-123"},
                        "messages": [{
                          "from": "56911111111",
                          "id": "%s",
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
                """.formatted(messageId)).getBytes(StandardCharsets.UTF_8);
    }
}

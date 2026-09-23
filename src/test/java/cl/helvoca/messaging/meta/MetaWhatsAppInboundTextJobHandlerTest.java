package cl.helvoca.messaging.meta;

import cl.helvoca.jobs.PersistentJob;
import cl.helvoca.jobs.PersistentJobHandler;
import cl.helvoca.messaging.WhatsAppReceptionistService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class MetaWhatsAppInboundTextJobHandlerTest {

    @Test
    void validPayloadCallsResolvedReceptionistExactlyOnce() {
        WhatsAppReceptionistService receptionist = mock(WhatsAppReceptionistService.class);
        MetaWhatsAppInboundTextJobHandler handler = new MetaWhatsAppInboundTextJobHandler(receptionist);
        UUID businessId = UUID.randomUUID();
        UUID phoneNumberId = UUID.randomUUID();

        PersistentJob job = job(businessId, """
                {
                  "messageId":"wamid.TEXT-ASYNC",
                  "phoneNumberId":"%s",
                  "from":"56911111111",
                  "text":"Hola",
                  "correlationId":"%s"
                }
                """.formatted(phoneNumberId, UUID.randomUUID()));

        assertEquals(PersistentJob.Type.WHATSAPP_INBOUND_TEXT_PROCESS, handler.type());
        handler.handle(job);

        verify(receptionist, times(1)).handleResolved(
                "wamid.TEXT-ASYNC",
                businessId,
                phoneNumberId,
                "56911111111",
                "Hola");
        verifyNoMoreInteractions(receptionist);
    }

    @Test
    void malformedPayloadIsPermanentAndNeverCallsReceptionist() {
        WhatsAppReceptionistService receptionist = mock(WhatsAppReceptionistService.class);
        MetaWhatsAppInboundTextJobHandler handler = new MetaWhatsAppInboundTextJobHandler(receptionist);
        UUID businessId = UUID.randomUUID();

        PersistentJob job = job(businessId, """
                {
                  "messageId":"wamid.TEXT-BAD",
                  "phoneNumberId":"not-a-uuid",
                  "from":"56911111111",
                  "text":"Hola"
                }
                """);

        assertThrows(PersistentJobHandler.PermanentJobException.class, () -> handler.handle(job));
        verifyNoInteractions(receptionist);
    }

    private static PersistentJob job(UUID businessId, String payloadJson) {
        Instant now = Instant.now();
        return new PersistentJob(
                UUID.randomUUID(),
                businessId,
                null,
                PersistentJob.Type.WHATSAPP_INBOUND_TEXT_PROCESS,
                PersistentJob.Status.RUNNING,
                "wa-in-text:wamid.TEST",
                payloadJson,
                1,
                5,
                now,
                "test-worker",
                now.plusSeconds(60),
                null,
                null,
                null,
                now,
                now);
    }
}

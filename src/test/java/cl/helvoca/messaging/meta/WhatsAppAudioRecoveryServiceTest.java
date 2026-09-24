package cl.helvoca.messaging.meta;

import cl.helvoca.jobs.PersistentJob;
import cl.helvoca.jobs.PersistentJobService;
import cl.helvoca.messaging.WhatsAppReceptionistService;
import cl.helvoca.messaging.outbound.MetaWhatsAppMessagingProvider;
import cl.helvoca.messaging.outbound.WhatsAppRecoveryReplyDeliveryService;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WhatsAppAudioRecoveryServiceTest {

    @Test
    void repeatedRecoveryUsesSameDurableRecoveryKeyAndNeverInvokesAiPath() {
        UUID businessId = UUID.randomUUID();
        UUID phoneNumberId = UUID.randomUUID();
        UUID persistedMessageId = UUID.randomUUID();
        String wamid = "wamid.AUDIO-FAIL";
        String from = "+56911111111";

        WhatsAppReceptionistService receptionist = mock(WhatsAppReceptionistService.class);
        PersistentJobService jobs = mock(PersistentJobService.class);
        WhatsAppRecoveryReplyDeliveryService delivery = new WhatsAppRecoveryReplyDeliveryService(jobs);
        WhatsAppAudioRecoveryService recovery = new WhatsAppAudioRecoveryService(receptionist, delivery);

        when(receptionist.recordResolvedSystemReply(
                eq(wamid),
                eq(businessId),
                eq(phoneNumberId),
                eq(from),
                eq(WhatsAppAudioRecoveryService.SOURCE_CONTENT),
                eq(WhatsAppAudioRecoveryService.RECOVERY_REPLY),
                eq(WhatsAppAudioRecoveryService.FAILURE_CODE),
                eq(MetaWhatsAppMessagingProvider.ID)))
                .thenReturn(new WhatsAppReceptionistService.ResolvedSystemReply(
                        persistedMessageId,
                        from,
                        MetaWhatsAppMessagingProvider.ID));

        recovery.recover(businessId, phoneNumberId, wamid, from);
        recovery.recover(businessId, phoneNumberId, wamid, from);

        verify(receptionist, times(2)).recordResolvedSystemReply(
                wamid,
                businessId,
                phoneNumberId,
                from,
                WhatsAppAudioRecoveryService.SOURCE_CONTENT,
                WhatsAppAudioRecoveryService.RECOVERY_REPLY,
                WhatsAppAudioRecoveryService.FAILURE_CODE,
                MetaWhatsAppMessagingProvider.ID);
        verify(jobs, times(2)).enqueue(
                eq(businessId),
                isNull(),
                eq(PersistentJob.Type.META_WHATSAPP_RECOVERY_REPLY),
                eq("wa-audio-recovery:wamid.AUDIO-FAIL"),
                argThat(payload -> persistedMessageId.toString().equals(
                        new JSONObject(payload).getString("messageId"))));
        verifyNoMoreInteractions(receptionist, jobs);
    }
}

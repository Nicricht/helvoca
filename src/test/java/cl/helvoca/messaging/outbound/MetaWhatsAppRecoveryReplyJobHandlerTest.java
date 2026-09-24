package cl.helvoca.messaging.outbound;

import cl.helvoca.jobs.PersistentJob;
import cl.helvoca.jobs.PersistentJobHandler;
import cl.helvoca.messaging.MessagingMessage;
import cl.helvoca.messaging.MessagingMessageRepository;
import cl.helvoca.messaging.meta.WhatsAppAudioRecoveryService;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class MetaWhatsAppRecoveryReplyJobHandlerTest {

    @Test
    void acceptsOnlyRecoveryKeyMatchingPersistedExternalMessageId() {
        UUID businessId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        MessagingMessage inbound = recoveryMessage(messageId, "wamid.AUDIO-FAIL");
        MessagingMessageRepository messages = mock(MessagingMessageRepository.class);
        MetaWhatsAppPersistedReplySender sender = mock(MetaWhatsAppPersistedReplySender.class);
        when(messages.findById(messageId)).thenReturn(Optional.of(inbound));

        MetaWhatsAppRecoveryReplyJobHandler handler =
                new MetaWhatsAppRecoveryReplyJobHandler(messages, sender);
        PersistentJob job = job(
                businessId,
                "wa-audio-recovery:wamid.AUDIO-FAIL",
                messageId);

        handler.handle(job);

        verify(sender).send(job, inbound);
    }

    @Test
    void rejectsNormalAiReplyKey() {
        UUID businessId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        MessagingMessage inbound = recoveryMessage(messageId, "wamid.AUDIO-FAIL");
        MessagingMessageRepository messages = mock(MessagingMessageRepository.class);
        MetaWhatsAppPersistedReplySender sender = mock(MetaWhatsAppPersistedReplySender.class);
        when(messages.findById(messageId)).thenReturn(Optional.of(inbound));

        MetaWhatsAppRecoveryReplyJobHandler handler =
                new MetaWhatsAppRecoveryReplyJobHandler(messages, sender);

        assertThrows(
                PersistentJobHandler.PermanentJobException.class,
                () -> handler.handle(job(
                        businessId,
                        "meta-ai-reply:wamid.AUDIO-FAIL",
                        messageId)));
        verifyNoInteractions(sender);
    }

    @Test
    void rejectsSourceWithoutAudioExhaustionFailureCode() {
        UUID businessId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        MessagingMessage inbound = recoveryMessage(messageId, "wamid.AUDIO-FAIL");
        inbound.setFailureCode("OTHER_FAILURE");
        MessagingMessageRepository messages = mock(MessagingMessageRepository.class);
        MetaWhatsAppPersistedReplySender sender = mock(MetaWhatsAppPersistedReplySender.class);
        when(messages.findById(messageId)).thenReturn(Optional.of(inbound));

        MetaWhatsAppRecoveryReplyJobHandler handler =
                new MetaWhatsAppRecoveryReplyJobHandler(messages, sender);

        assertThrows(
                PersistentJobHandler.PermanentJobException.class,
                () -> handler.handle(job(
                        businessId,
                        "wa-audio-recovery:wamid.AUDIO-FAIL",
                        messageId)));
        verifyNoInteractions(sender);
    }

    private static MessagingMessage recoveryMessage(UUID messageId, String wamid) {
        MessagingMessage message = new MessagingMessage();
        ReflectionTestUtils.setField(message, "id", messageId);
        message.setExternalMessageId(wamid);
        message.setDirection("INBOUND");
        message.setRole("USER");
        message.setContent(WhatsAppAudioRecoveryService.SOURCE_CONTENT);
        message.setReplyText(WhatsAppAudioRecoveryService.RECOVERY_REPLY);
        message.setFailureCode(WhatsAppAudioRecoveryService.FAILURE_CODE);
        return message;
    }

    private static PersistentJob job(UUID businessId, String key, UUID messageId) {
        Instant now = Instant.now();
        return new PersistentJob(
                UUID.randomUUID(),
                businessId,
                null,
                PersistentJob.Type.META_WHATSAPP_RECOVERY_REPLY,
                PersistentJob.Status.RUNNING,
                key,
                new JSONObject().put("messageId", messageId.toString()).toString(),
                1,
                5,
                now,
                "test-worker",
                now.plusSeconds(30),
                null,
                null,
                null,
                now,
                now);
    }
}

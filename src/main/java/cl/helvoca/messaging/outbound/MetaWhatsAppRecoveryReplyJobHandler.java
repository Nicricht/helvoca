package cl.helvoca.messaging.outbound;

import cl.helvoca.jobs.PersistentJob;
import cl.helvoca.jobs.PersistentJobHandler;
import cl.helvoca.messaging.MessagingMessage;
import cl.helvoca.messaging.MessagingMessageRepository;
import cl.helvoca.messaging.meta.MetaWhatsAppJobKeys;
import cl.helvoca.messaging.meta.WhatsAppAudioRecoveryService;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class MetaWhatsAppRecoveryReplyJobHandler implements PersistentJobHandler {
    private final MessagingMessageRepository messages;
    private final MetaWhatsAppPersistedReplySender sender;

    public MetaWhatsAppRecoveryReplyJobHandler(
            MessagingMessageRepository messages,
            MetaWhatsAppPersistedReplySender sender) {
        this.messages = messages;
        this.sender = sender;
    }

    @Override
    public PersistentJob.Type type() {
        return PersistentJob.Type.META_WHATSAPP_RECOVERY_REPLY;
    }

    @Override
    public void handle(PersistentJob job) {
        UUID messageId = messageId(job);
        MessagingMessage inbound = messages.findById(messageId)
                .orElseThrow(() -> new PermanentJobException(
                        "Meta recovery reply source message was not found"));
        if (!"INBOUND".equalsIgnoreCase(inbound.getDirection())) {
            throw new PermanentJobException("Meta recovery reply source must be an inbound message");
        }
        if (inbound.getExternalMessageId() == null || inbound.getExternalMessageId().isBlank()) {
            throw new PermanentJobException("Meta recovery reply source has no external message id");
        }
        if (!WhatsAppAudioRecoveryService.FAILURE_CODE.equals(inbound.getFailureCode())) {
            throw new PermanentJobException("Meta recovery reply source is not an exhausted audio message");
        }

        String expectedKey = MetaWhatsAppJobKeys.recovery(inbound.getExternalMessageId());
        if (!expectedKey.equals(job.idempotencyKey())) {
            throw new PermanentJobException("Meta recovery reply idempotency key does not match source message");
        }

        sender.send(job, inbound);
    }

    private static UUID messageId(PersistentJob job) {
        try {
            return UUID.fromString(new JSONObject(job.payloadJson()).getString("messageId"));
        } catch (Exception failure) {
            throw new PermanentJobException("Meta recovery durable job payload is invalid", failure);
        }
    }
}

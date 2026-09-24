package cl.helvoca.messaging.outbound;

import cl.helvoca.jobs.PersistentJob;
import cl.helvoca.jobs.PersistentJobService;
import cl.helvoca.messaging.meta.MetaWhatsAppJobKeys;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class WhatsAppRecoveryReplyDeliveryService {
    private final PersistentJobService jobs;

    public WhatsAppRecoveryReplyDeliveryService(PersistentJobService jobs) {
        this.jobs = jobs;
    }

    public void schedule(UUID businessId, UUID messageId, String inboundMessageId) {
        if (businessId == null || messageId == null) {
            throw new IllegalArgumentException("Meta recovery reply identifiers are required");
        }
        if (inboundMessageId == null || inboundMessageId.isBlank()) {
            throw new IllegalArgumentException("Meta recovery source message id is required");
        }

        jobs.enqueue(
                businessId,
                null,
                PersistentJob.Type.META_WHATSAPP_RECOVERY_REPLY,
                MetaWhatsAppJobKeys.recovery(inboundMessageId),
                new JSONObject().put("messageId", messageId.toString()).toString());
    }
}

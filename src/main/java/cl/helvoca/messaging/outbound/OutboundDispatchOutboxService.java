package cl.helvoca.messaging.outbound;

import cl.helvoca.jobs.PersistentJob;
import cl.helvoca.jobs.PersistentJobService;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class OutboundDispatchOutboxService {
    private final OutboundMessageRepository messages;
    private final PersistentJobService jobs;

    public OutboundDispatchOutboxService(OutboundMessageRepository messages, PersistentJobService jobs) {
        this.messages = messages;
        this.jobs = jobs;
    }

    @Transactional
    public PersistentJob queue(UUID businessId, UUID messageId) {
        if (businessId == null || messageId == null) throw new IllegalArgumentException("businessId and messageId are required");
        OutboundMessage message = messages.findByIdAndBusinessId(messageId, businessId)
                .orElseThrow(() -> new IllegalArgumentException("Outbound message not found"));
        if (message.getStatus() == OutboundMessage.Status.SENT) {
            throw new IllegalStateException("Sent messages do not require an outbound job");
        }
        if (message.getStatus() == OutboundMessage.Status.CANCELLED
                || message.getStatus() == OutboundMessage.Status.BLOCKED) {
            throw new IllegalStateException("Outbound message cannot be queued from current state");
        }

        PersistentJob job = jobs.enqueue(
                businessId,
                message.getOperationId(),
                PersistentJob.Type.OUTBOUND_MESSAGE_DISPATCH,
                "outbound-message-dispatch:" + message.getId(),
                new JSONObject().put("messageId", message.getId().toString()).toString());

        if (job.status() == PersistentJob.Status.DEAD_LETTER
                || job.status() == PersistentJob.Status.CANCELLED) {
            throw new IllegalStateException("Existing outbound durable job is terminal and requires operator review");
        }

        if (message.getStatus() != OutboundMessage.Status.QUEUED) {
            message.setStatus(OutboundMessage.Status.QUEUED);
            messages.saveAndFlush(message);
        }
        return job;
    }
}

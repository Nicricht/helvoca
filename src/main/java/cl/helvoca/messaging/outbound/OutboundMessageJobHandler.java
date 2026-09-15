package cl.helvoca.messaging.outbound;

import cl.helvoca.jobs.PersistentJob;
import cl.helvoca.jobs.PersistentJobHandler;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class OutboundMessageJobHandler implements PersistentJobHandler {
    private final OutboundMessagingService outbound;

    public OutboundMessageJobHandler(OutboundMessagingService outbound) {
        this.outbound = outbound;
    }

    @Override
    public PersistentJob.Type type() {
        return PersistentJob.Type.OUTBOUND_MESSAGE_DISPATCH;
    }

    @Override
    public void handle(PersistentJob job) {
        UUID messageId;
        try {
            JSONObject payload = new JSONObject(job.payloadJson());
            messageId = UUID.fromString(payload.getString("messageId"));
        } catch (Exception e) {
            throw new PermanentJobException("Outbound durable job payload is invalid", e);
        }

        OutboundMessage message;
        try {
            message = outbound.dispatch(job.businessId(), messageId);
        } catch (IllegalArgumentException e) {
            throw new PermanentJobException(e.getMessage(), e);
        } catch (IllegalStateException e) {
            // Disabled delivery or an invalid message state requires an operator or
            // configuration change, not an automatic retry storm.
            throw new PermanentJobException(e.getMessage(), e);
        }

        if (message.getStatus() == OutboundMessage.Status.SENT) return;
        if (message.getStatus() == OutboundMessage.Status.FAILED) {
            throw new RetryableJobException("Outbound provider failed: " +
                    (message.getFailureCode() == null ? "UNKNOWN" : message.getFailureCode()));
        }
        throw new PermanentJobException("Outbound message ended in unexpected state " + message.getStatus());
    }
}

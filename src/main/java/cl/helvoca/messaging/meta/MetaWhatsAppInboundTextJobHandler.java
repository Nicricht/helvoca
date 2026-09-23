package cl.helvoca.messaging.meta;

import cl.helvoca.jobs.PersistentJob;
import cl.helvoca.jobs.PersistentJobHandler;
import cl.helvoca.messaging.WhatsAppReceptionistService;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class MetaWhatsAppInboundTextJobHandler implements PersistentJobHandler {
    private final WhatsAppReceptionistService receptionist;

    public MetaWhatsAppInboundTextJobHandler(WhatsAppReceptionistService receptionist) {
        this.receptionist = receptionist;
    }

    @Override
    public PersistentJob.Type type() {
        return PersistentJob.Type.WHATSAPP_INBOUND_TEXT_PROCESS;
    }

    @Override
    public void handle(PersistentJob job) {
        if (job == null || job.businessId() == null) {
            throw new PermanentJobException("Meta WhatsApp inbound text job is incomplete");
        }

        try {
            JSONObject payload = new JSONObject(job.payloadJson());
            String messageId = required(payload, "messageId");
            UUID phoneNumberId = UUID.fromString(required(payload, "phoneNumberId"));
            String from = required(payload, "from");
            String text = required(payload, "text");

            receptionist.handleResolved(
                    messageId,
                    job.businessId(),
                    phoneNumberId,
                    from,
                    text);
        } catch (PermanentJobException e) {
            throw e;
        } catch (JSONException | IllegalArgumentException e) {
            throw new PermanentJobException("Meta WhatsApp inbound text job payload is invalid", e);
        } catch (IllegalStateException e) {
            throw new RetryableJobException("Meta WhatsApp inbound text processing failed", e);
        } catch (RuntimeException e) {
            throw new RetryableJobException("Meta WhatsApp inbound text processing failed", e);
        }
    }

    private static String required(JSONObject payload, String field) {
        String value = payload.getString(field);
        if (value == null || value.isBlank()) {
            throw new PermanentJobException("Meta WhatsApp inbound text job field is blank: " + field);
        }
        return value;
    }
}

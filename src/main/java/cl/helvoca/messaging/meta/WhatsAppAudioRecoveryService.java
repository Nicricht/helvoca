package cl.helvoca.messaging.meta;

import cl.helvoca.messaging.WhatsAppReceptionistService;
import cl.helvoca.messaging.outbound.MetaWhatsAppMessagingProvider;
import cl.helvoca.messaging.outbound.WhatsAppRecoveryReplyDeliveryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class WhatsAppAudioRecoveryService {
    public static final String SOURCE_CONTENT = "[audio no transcrito]";
    public static final String FAILURE_CODE = "AUDIO_TRANSCRIPTION_EXHAUSTED";
    public static final String RECOVERY_REPLY =
            "No pude escuchar bien ese audio en este momento. ¿Puedes escribir el mensaje o enviarlo nuevamente?";

    private final WhatsAppReceptionistService receptionist;
    private final WhatsAppRecoveryReplyDeliveryService delivery;

    public WhatsAppAudioRecoveryService(
            WhatsAppReceptionistService receptionist,
            WhatsAppRecoveryReplyDeliveryService delivery) {
        this.receptionist = receptionist;
        this.delivery = delivery;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recover(UUID businessId,
                        UUID phoneNumberId,
                        String wamid,
                        String from) {
        WhatsAppReceptionistService.ResolvedSystemReply persisted =
                receptionist.recordResolvedSystemReply(
                        wamid,
                        businessId,
                        phoneNumberId,
                        from,
                        SOURCE_CONTENT,
                        RECOVERY_REPLY,
                        FAILURE_CODE,
                        MetaWhatsAppMessagingProvider.ID);

        delivery.schedule(businessId, persisted.messageId(), wamid);
    }
}

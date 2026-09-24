package cl.helvoca.messaging.meta;

import cl.helvoca.jobs.PersistentJob;
import cl.helvoca.jobs.PersistentJobHandler;
import cl.helvoca.messaging.WhatsAppReceptionistService;
import cl.helvoca.messaging.audio.AudioInput;
import cl.helvoca.messaging.audio.AudioTranscriber;
import cl.helvoca.messaging.audio.AudioTranscriptionException;
import cl.helvoca.messaging.audio.TranscriptionResult;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class MetaWhatsAppInboundAudioJobHandler implements PersistentJobHandler {
    private final MetaWhatsAppAudioMediaService media;
    private final AudioTranscriber transcriber;
    private final WhatsAppReceptionistService receptionist;
    private final WhatsAppAudioRecoveryService recovery;

    public MetaWhatsAppInboundAudioJobHandler(
            MetaWhatsAppAudioMediaService media,
            AudioTranscriber transcriber,
            @Lazy WhatsAppReceptionistService receptionist,
            WhatsAppAudioRecoveryService recovery) {
        this.media = media;
        this.transcriber = transcriber;
        this.receptionist = receptionist;
        this.recovery = recovery;
    }

    @Override
    public PersistentJob.Type type() {
        return PersistentJob.Type.WHATSAPP_INBOUND_AUDIO_PROCESS;
    }

    @Override
    public void handle(PersistentJob job) {
        Payload payload = parse(job);
        try {
            MetaWhatsAppAudioMediaService.DownloadedAudio downloaded =
                    media.download(job.businessId(), payload.mediaId());
            TranscriptionResult result = transcriber.transcribe(new AudioInput(
                    downloaded.bytes(),
                    downloaded.mimeType(),
                    "",
                    job.businessId(),
                    payload.messageId(),
                    payload.correlationId().toString()));

            if (result == null || result.text() == null || result.text().isBlank()) {
                recoverAndStop(job, payload, "WhatsApp audio transcription returned no usable text", null);
                return;
            }

            receptionist.handleResolved(
                    payload.messageId(),
                    job.businessId(),
                    payload.phoneNumberId(),
                    payload.from(),
                    result.text().trim());
        } catch (MetaWhatsAppAudioMediaService.AudioMediaException failure) {
            classify(job, payload, failure.retryable(), "WhatsApp audio media download failed", failure);
        } catch (AudioTranscriptionException failure) {
            classify(job, payload, failure.retryable(), "WhatsApp audio transcription failed", failure);
        } catch (PermanentJobException failure) {
            throw failure;
        } catch (RetryableJobException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            classify(job, payload, true, "WhatsApp audio processing failed", failure);
        }
    }

    private void classify(
            PersistentJob job,
            Payload payload,
            boolean retryable,
            String safeMessage,
            Throwable cause) {
        if (retryable && job.attemptCount() < job.maxAttempts()) {
            throw new RetryableJobException(safeMessage, cause);
        }
        recoverAndStop(job, payload, safeMessage, cause);
    }

    private void recoverAndStop(
            PersistentJob job,
            Payload payload,
            String safeMessage,
            Throwable cause) {
        try {
            recovery.recover(
                    job.businessId(),
                    payload.phoneNumberId(),
                    payload.messageId(),
                    payload.from());
        } catch (RuntimeException recoveryFailure) {
            throw new PermanentJobException(
                    "WhatsApp audio recovery persistence failed",
                    recoveryFailure);
        }
        throw new PermanentJobException(safeMessage, cause);
    }

    private static Payload parse(PersistentJob job) {
        if (job == null || job.businessId() == null) {
            throw new PermanentJobException("Meta WhatsApp inbound audio job is incomplete");
        }
        try {
            JSONObject json = new JSONObject(job.payloadJson());
            String messageId = required(json, "messageId");
            UUID phoneNumberId = UUID.fromString(required(json, "phoneNumberId"));
            String from = required(json, "from");
            String mediaId = required(json, "mediaId");
            UUID correlationId = UUID.fromString(required(json, "correlationId"));
            return new Payload(messageId, phoneNumberId, from, mediaId, correlationId);
        } catch (PermanentJobException failure) {
            throw failure;
        } catch (JSONException | IllegalArgumentException failure) {
            throw new PermanentJobException(
                    "Meta WhatsApp inbound audio job payload is invalid",
                    failure);
        }
    }

    private static String required(JSONObject payload, String field) {
        String value = payload.getString(field);
        if (value == null || value.isBlank()) {
            throw new PermanentJobException(
                    "Meta WhatsApp inbound audio job field is blank: " + field);
        }
        return value.trim();
    }

    private record Payload(
            String messageId,
            UUID phoneNumberId,
            String from,
            String mediaId,
            UUID correlationId) {
    }
}

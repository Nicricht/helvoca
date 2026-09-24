package cl.helvoca.messaging.meta;

import cl.helvoca.jobs.PersistentJob;
import cl.helvoca.jobs.PersistentJobStore;
import cl.helvoca.messaging.audio.AudioInput;
import cl.helvoca.messaging.audio.AudioTranscriber;
import cl.helvoca.messaging.audio.AudioTranscriptionException;
import cl.helvoca.messaging.audio.TranscriptionResult;
import cl.helvoca.security.TenantDatabaseContext;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class WhatsAppAudioJobDiagnosticStartupRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(WhatsAppAudioJobDiagnosticStartupRunner.class);

    private final boolean enabled;
    private final String businessId;
    private final String jobId;
    private final TenantDatabaseContext databaseContext;
    private final PersistentJobStore jobs;
    private final MetaWhatsAppAudioMediaService media;
    private final AudioTranscriber transcriber;

    public WhatsAppAudioJobDiagnosticStartupRunner(
            @Value("${HELVOCA_WHATSAPP_AUDIO_JOB_DIAGNOSTIC_ON_STARTUP:false}") boolean enabled,
            @Value("${HELVOCA_WHATSAPP_AUDIO_JOB_DIAGNOSTIC_BUSINESS_ID:}") String businessId,
            @Value("${HELVOCA_WHATSAPP_AUDIO_JOB_DIAGNOSTIC_JOB_ID:}") String jobId,
            TenantDatabaseContext databaseContext,
            PersistentJobStore jobs,
            MetaWhatsAppAudioMediaService media,
            AudioTranscriber transcriber) {
        this.enabled = enabled;
        this.businessId = clean(businessId);
        this.jobId = clean(jobId);
        this.databaseContext = databaseContext;
        this.jobs = jobs;
        this.media = media;
        this.transcriber = transcriber;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) return;
        UUID tenant = parseUuid(businessId, "HELVOCA_WHATSAPP_AUDIO_JOB_DIAGNOSTIC_BUSINESS_ID");
        UUID durableJobId = parseUuid(jobId, "HELVOCA_WHATSAPP_AUDIO_JOB_DIAGNOSTIC_JOB_ID");
        databaseContext.runAsTenant(tenant, () -> diagnose(tenant, durableJobId));
    }

    void diagnose(UUID businessId, UUID jobId) {
        PersistentJob job = jobs.findById(businessId, jobId)
                .orElseThrow(() -> new IllegalStateException("Audio diagnostic job was not found"));
        if (job.jobType() != PersistentJob.Type.WHATSAPP_INBOUND_AUDIO_PROCESS) {
            throw new IllegalStateException("Audio diagnostic requires a WhatsApp inbound audio job");
        }
        if (job.status() != PersistentJob.Status.DEAD_LETTER) {
            throw new IllegalStateException("Audio diagnostic only runs against a DEAD_LETTER job");
        }

        String mediaId;
        String messageId;
        String correlationId;
        try {
            JSONObject payload = new JSONObject(job.payloadJson());
            mediaId = required(payload, "mediaId");
            messageId = required(payload, "messageId");
            correlationId = required(payload, "correlationId");
        } catch (RuntimeException failure) {
            log.warn(
                    "WHATSAPP_AUDIO_JOB_DIAGNOSTIC result=INVALID_PAYLOAD jobId={} status={} attempts={}/{}",
                    job.id(), job.status(), job.attemptCount(), job.maxAttempts());
            return;
        }

        MetaWhatsAppAudioMediaService.DownloadedAudio downloaded;
        try {
            downloaded = media.download(businessId, mediaId);
        } catch (MetaWhatsAppAudioMediaService.AudioMediaException failure) {
            log.warn(
                    "WHATSAPP_AUDIO_JOB_DIAGNOSTIC result=MEDIA_FAILURE jobId={} status={} attempts={}/{} code={} retryable={}",
                    job.id(),
                    job.status(),
                    job.attemptCount(),
                    job.maxAttempts(),
                    failure.code(),
                    failure.retryable());
            return;
        } catch (RuntimeException failure) {
            log.warn(
                    "WHATSAPP_AUDIO_JOB_DIAGNOSTIC result=MEDIA_FAILURE jobId={} status={} attempts={}/{} code={} retryable=true",
                    job.id(),
                    job.status(),
                    job.attemptCount(),
                    job.maxAttempts(),
                    failure.getClass().getSimpleName());
            return;
        }

        try {
            TranscriptionResult result = transcriber.transcribe(new AudioInput(
                    downloaded.bytes(),
                    downloaded.mimeType(),
                    "",
                    businessId,
                    messageId,
                    correlationId));
            log.info(
                    "WHATSAPP_AUDIO_JOB_DIAGNOSTIC result=TRANSCRIBED jobId={} status={} attempts={}/{} provider={} model={} chars={} providerAttempts={}",
                    job.id(),
                    job.status(),
                    job.attemptCount(),
                    job.maxAttempts(),
                    safe(result.providerId()),
                    safe(result.modelId()),
                    result.text() == null ? 0 : result.text().length(),
                    result.attemptCount());
        } catch (AudioTranscriptionException failure) {
            log.warn(
                    "WHATSAPP_AUDIO_JOB_DIAGNOSTIC result=TRANSCRIPTION_FAILURE jobId={} status={} attempts={}/{} code={} provider={} httpStatus={} retryable={} causeCode={}",
                    job.id(),
                    job.status(),
                    job.attemptCount(),
                    job.maxAttempts(),
                    safe(failure.code()),
                    safe(failure.providerId()),
                    failure.httpStatus() == null ? "NONE" : failure.httpStatus(),
                    failure.retryable(),
                    nestedAudioCode(failure));
        } catch (RuntimeException failure) {
            log.warn(
                    "WHATSAPP_AUDIO_JOB_DIAGNOSTIC result=TRANSCRIPTION_FAILURE jobId={} status={} attempts={}/{} code={} provider=unknown httpStatus=NONE retryable=true causeCode=NONE",
                    job.id(),
                    job.status(),
                    job.attemptCount(),
                    job.maxAttempts(),
                    failure.getClass().getSimpleName());
        }
    }

    private static String nestedAudioCode(Throwable failure) {
        Throwable current = failure == null ? null : failure.getCause();
        while (current != null) {
            if (current instanceof AudioTranscriptionException audio) {
                return safe(audio.code());
            }
            current = current.getCause();
        }
        return "NONE";
    }

    private static UUID parseUuid(String value, String name) {
        try {
            return UUID.fromString(value);
        } catch (Exception failure) {
            throw new IllegalStateException(name + " must be a valid UUID", failure);
        }
    }

    private static String required(JSONObject payload, String key) {
        String value = payload.optString(key, "");
        if (value.isBlank()) throw new IllegalStateException("Missing " + key);
        return value.trim();
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "NONE" : value.trim();
    }
}

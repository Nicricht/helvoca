package cl.helvoca.messaging.meta;

import cl.helvoca.jobs.PersistentJob;
import cl.helvoca.jobs.PersistentJobStore;
import cl.helvoca.messaging.audio.AudioTranscriber;
import cl.helvoca.messaging.audio.TranscriptionResult;
import cl.helvoca.security.TenantDatabaseContext;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WhatsAppAudioJobDiagnosticStartupRunnerTest {

    @Test
    void diagnosticReadsDeadLetterAudioWithoutInvokingReceptionistOrMutatingJobs() {
        UUID businessId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();

        PersistentJobStore jobs = mock(PersistentJobStore.class);
        MetaWhatsAppAudioMediaService media = mock(MetaWhatsAppAudioMediaService.class);
        AudioTranscriber transcriber = mock(AudioTranscriber.class);

        JSONObject payload = new JSONObject()
                .put("mediaId", "media-1")
                .put("messageId", "wamid.test")
                .put("correlationId", UUID.randomUUID().toString());

        PersistentJob job = new PersistentJob(
                jobId,
                businessId,
                null,
                PersistentJob.Type.WHATSAPP_INBOUND_AUDIO_PROCESS,
                PersistentJob.Status.DEAD_LETTER,
                "wa-audio:test",
                payload.toString(),
                5,
                5,
                Instant.now(),
                null,
                null,
                "PERMANENTJOBEXCEPTION",
                "safe",
                Instant.now(),
                Instant.now(),
                Instant.now());

        when(jobs.findById(businessId, jobId)).thenReturn(Optional.of(job));
        when(media.download(businessId, "media-1"))
                .thenReturn(new MetaWhatsAppAudioMediaService.DownloadedAudio(
                        new byte[]{1, 2, 3},
                        "audio/ogg"));
        when(transcriber.transcribe(any()))
                .thenReturn(new TranscriptionResult(
                        "texto de prueba",
                        "gemini",
                        "model",
                        Duration.ofMillis(50),
                        1));

        WhatsAppAudioJobDiagnosticStartupRunner runner =
                new WhatsAppAudioJobDiagnosticStartupRunner(
                        true,
                        businessId.toString(),
                        jobId.toString(),
                        mock(TenantDatabaseContext.class),
                        jobs,
                        media,
                        transcriber);

        runner.diagnose(businessId, jobId);

        verify(jobs).findById(businessId, jobId);
        verify(media).download(businessId, "media-1");
        verify(transcriber).transcribe(any());
        verifyNoMoreInteractions(jobs, media, transcriber);
    }

    @Test
    void refusesToProbeAnActiveAudioJob() {
        UUID businessId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        PersistentJobStore jobs = mock(PersistentJobStore.class);

        PersistentJob job = new PersistentJob(
                jobId,
                businessId,
                null,
                PersistentJob.Type.WHATSAPP_INBOUND_AUDIO_PROCESS,
                PersistentJob.Status.FAILED,
                "wa-audio:test",
                "{}",
                2,
                5,
                Instant.now(),
                null,
                null,
                "RETRYABLEJOBEXCEPTION",
                "safe",
                null,
                Instant.now(),
                Instant.now());

        when(jobs.findById(businessId, jobId)).thenReturn(Optional.of(job));

        WhatsAppAudioJobDiagnosticStartupRunner runner =
                new WhatsAppAudioJobDiagnosticStartupRunner(
                        true,
                        businessId.toString(),
                        jobId.toString(),
                        mock(TenantDatabaseContext.class),
                        jobs,
                        mock(MetaWhatsAppAudioMediaService.class),
                        mock(AudioTranscriber.class));

        assertThrows(
                IllegalStateException.class,
                () -> runner.diagnose(businessId, jobId));
    }
}

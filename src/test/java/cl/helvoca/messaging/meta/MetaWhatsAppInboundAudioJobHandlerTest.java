package cl.helvoca.messaging.meta;

import cl.helvoca.jobs.PersistentJob;
import cl.helvoca.jobs.PersistentJobHandler;
import cl.helvoca.messaging.WhatsAppReceptionistService;
import cl.helvoca.messaging.audio.AudioTranscriber;
import cl.helvoca.messaging.audio.AudioTranscriptionException;
import cl.helvoca.messaging.audio.TranscriptionResult;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class MetaWhatsAppInboundAudioJobHandlerTest {

    @Test
    void successDownloadsTranscribesAndProcessesInsideWorker() {
        UUID businessId = UUID.randomUUID();
        UUID phoneNumberId = UUID.randomUUID();
        MetaWhatsAppAudioMediaService media = mock(MetaWhatsAppAudioMediaService.class);
        AudioTranscriber transcriber = mock(AudioTranscriber.class);
        WhatsAppReceptionistService receptionist = mock(WhatsAppReceptionistService.class);
        WhatsAppAudioRecoveryService recovery = mock(WhatsAppAudioRecoveryService.class);
        byte[] bytes = new byte[]{1, 2, 3};

        when(media.download(businessId, "MEDIA-1"))
                .thenReturn(new MetaWhatsAppAudioMediaService.DownloadedAudio(bytes, "audio/ogg"));
        when(transcriber.transcribe(any()))
                .thenReturn(new TranscriptionResult(
                        "Quiero reservar mañana",
                        "deepgram",
                        "nova-3",
                        Duration.ofMillis(320),
                        1));

        MetaWhatsAppInboundAudioJobHandler handler =
                new MetaWhatsAppInboundAudioJobHandler(media, transcriber, receptionist, recovery);
        handler.handle(job(businessId, phoneNumberId, 1, 5));

        verify(media).download(businessId, "MEDIA-1");
        verify(transcriber).transcribe(argThat(input ->
                java.util.Arrays.equals(bytes, input.bytes())
                        && "audio/ogg".equals(input.mimeType())
                        && businessId.equals(input.businessId())
                        && "wamid.AUDIO-WORKER".equals(input.messageId())
                        && CORRELATION_ID.toString().equals(input.correlationId())));
        verify(receptionist).handleResolved(
                "wamid.AUDIO-WORKER",
                businessId,
                phoneNumberId,
                "56911111111",
                "Quiero reservar mañana");
        verifyNoInteractions(recovery);
    }

    @Test
    void retryableTranscriptionFailureBeforeFinalAttemptRetriesWithoutRecovery() {
        UUID businessId = UUID.randomUUID();
        UUID phoneNumberId = UUID.randomUUID();
        MetaWhatsAppAudioMediaService media = mock(MetaWhatsAppAudioMediaService.class);
        AudioTranscriber transcriber = mock(AudioTranscriber.class);
        WhatsAppReceptionistService receptionist = mock(WhatsAppReceptionistService.class);
        WhatsAppAudioRecoveryService recovery = mock(WhatsAppAudioRecoveryService.class);
        when(media.download(businessId, "MEDIA-1"))
                .thenReturn(new MetaWhatsAppAudioMediaService.DownloadedAudio(new byte[]{1}, "audio/ogg"));
        when(transcriber.transcribe(any())).thenThrow(new AudioTranscriptionException(
                "AUDIO_TRANSCRIPTION_EXHAUSTED", "router", 503, true, "sanitized"));

        MetaWhatsAppInboundAudioJobHandler handler =
                new MetaWhatsAppInboundAudioJobHandler(media, transcriber, receptionist, recovery);

        assertThrows(PersistentJobHandler.RetryableJobException.class,
                () -> handler.handle(job(businessId, phoneNumberId, 2, 5)));
        verifyNoInteractions(receptionist, recovery);
    }

    @Test
    void permanentMediaFailureRecoversOnceAndStopsRetrying() {
        UUID businessId = UUID.randomUUID();
        UUID phoneNumberId = UUID.randomUUID();
        MetaWhatsAppAudioMediaService media = mock(MetaWhatsAppAudioMediaService.class);
        AudioTranscriber transcriber = mock(AudioTranscriber.class);
        WhatsAppReceptionistService receptionist = mock(WhatsAppReceptionistService.class);
        WhatsAppAudioRecoveryService recovery = mock(WhatsAppAudioRecoveryService.class);
        when(media.download(businessId, "MEDIA-1"))
                .thenThrow(new MetaWhatsAppAudioMediaService.AudioMediaException(
                        "META_CREDENTIAL_MISSING", false, "sanitized"));

        MetaWhatsAppInboundAudioJobHandler handler =
                new MetaWhatsAppInboundAudioJobHandler(media, transcriber, receptionist, recovery);

        assertThrows(PersistentJobHandler.PermanentJobException.class,
                () -> handler.handle(job(businessId, phoneNumberId, 1, 5)));
        verify(recovery).recover(
                businessId,
                phoneNumberId,
                "wamid.AUDIO-WORKER",
                "56911111111");
        verifyNoInteractions(transcriber, receptionist);
    }

    @Test
    void finalRetryableFailureRecoversThenBecomesPermanent() {
        UUID businessId = UUID.randomUUID();
        UUID phoneNumberId = UUID.randomUUID();
        MetaWhatsAppAudioMediaService media = mock(MetaWhatsAppAudioMediaService.class);
        AudioTranscriber transcriber = mock(AudioTranscriber.class);
        WhatsAppReceptionistService receptionist = mock(WhatsAppReceptionistService.class);
        WhatsAppAudioRecoveryService recovery = mock(WhatsAppAudioRecoveryService.class);
        when(media.download(businessId, "MEDIA-1"))
                .thenReturn(new MetaWhatsAppAudioMediaService.DownloadedAudio(new byte[]{1}, "audio/ogg"));
        when(transcriber.transcribe(any())).thenThrow(new AudioTranscriptionException(
                "AUDIO_TRANSCRIPTION_EXHAUSTED", "router", 503, true, "sanitized"));

        MetaWhatsAppInboundAudioJobHandler handler =
                new MetaWhatsAppInboundAudioJobHandler(media, transcriber, receptionist, recovery);

        assertThrows(PersistentJobHandler.PermanentJobException.class,
                () -> handler.handle(job(businessId, phoneNumberId, 5, 5)));
        verify(recovery).recover(
                businessId,
                phoneNumberId,
                "wamid.AUDIO-WORKER",
                "56911111111");
        verifyNoInteractions(receptionist);
    }

    @Test
    void malformedPayloadFailsPermanentlyWithoutProviderOrRecovery() {
        MetaWhatsAppAudioMediaService media = mock(MetaWhatsAppAudioMediaService.class);
        AudioTranscriber transcriber = mock(AudioTranscriber.class);
        WhatsAppReceptionistService receptionist = mock(WhatsAppReceptionistService.class);
        WhatsAppAudioRecoveryService recovery = mock(WhatsAppAudioRecoveryService.class);
        MetaWhatsAppInboundAudioJobHandler handler =
                new MetaWhatsAppInboundAudioJobHandler(media, transcriber, receptionist, recovery);
        Instant now = Instant.now();
        PersistentJob malformed = new PersistentJob(
                UUID.randomUUID(), UUID.randomUUID(), null,
                PersistentJob.Type.WHATSAPP_INBOUND_AUDIO_PROCESS,
                PersistentJob.Status.RUNNING,
                "wa-in-audio:bad",
                "{\"messageId\":\"bad\"}",
                1, 5, now, "worker", now.plusSeconds(30),
                null, null, null, now, now);

        assertThrows(PersistentJobHandler.PermanentJobException.class,
                () -> handler.handle(malformed));
        verifyNoInteractions(media, transcriber, receptionist, recovery);
    }

    private static final UUID CORRELATION_ID =
            UUID.fromString("9b6571bd-b193-3ca4-8dd7-bcfe68c70615");

    private static PersistentJob job(
            UUID businessId,
            UUID phoneNumberId,
            int attemptCount,
            int maxAttempts) {
        Instant now = Instant.now();
        String payload = new JSONObject()
                .put("messageId", "wamid.AUDIO-WORKER")
                .put("phoneNumberId", phoneNumberId.toString())
                .put("from", "56911111111")
                .put("mediaId", "MEDIA-1")
                .put("mimeType", "audio/ogg; codecs=opus")
                .put("correlationId", CORRELATION_ID.toString())
                .toString();
        return new PersistentJob(
                UUID.randomUUID(),
                businessId,
                null,
                PersistentJob.Type.WHATSAPP_INBOUND_AUDIO_PROCESS,
                PersistentJob.Status.RUNNING,
                "wa-in-audio:wamid.AUDIO-WORKER",
                payload,
                attemptCount,
                maxAttempts,
                now,
                "worker",
                now.plusSeconds(30),
                null,
                null,
                null,
                now,
                now);
    }
}

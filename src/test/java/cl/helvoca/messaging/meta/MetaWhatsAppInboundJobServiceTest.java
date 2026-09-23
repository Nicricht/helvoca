package cl.helvoca.messaging.meta;

import cl.helvoca.jobs.PersistentJob;
import cl.helvoca.jobs.PersistentJobService;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MetaWhatsAppInboundJobServiceTest {

    @Test
    void textEnqueueUsesDeterministicTenantScopedIdentityAndSafePayload() {
        PersistentJobService jobs = mock(PersistentJobService.class);
        MetaWhatsAppInboundProperties properties = new MetaWhatsAppInboundProperties();
        properties.setTextMaxAttempts(7);
        MetaWhatsAppInboundJobService service = new MetaWhatsAppInboundJobService(jobs, properties);

        UUID businessId = UUID.randomUUID();
        UUID phoneNumberId = UUID.randomUUID();
        MetaWhatsAppTenantRoute route = new MetaWhatsAppTenantRoute(businessId, phoneNumberId, "+56999999999");
        MetaWhatsAppInboundMessage message = new MetaWhatsAppInboundMessage(
                "wamid.TEST-1", phoneNumberId.toString(), "56911111111", "Hola");

        PersistentJob expected = job(UUID.randomUUID(), businessId, PersistentJob.Type.WHATSAPP_INBOUND_TEXT_PROCESS, "wa-in-text:wamid.TEST-1", 7);
        when(jobs.enqueue(eq(businessId), isNull(), eq(PersistentJob.Type.WHATSAPP_INBOUND_TEXT_PROCESS),
                eq("wa-in-text:wamid.TEST-1"), anyString(), eq(7), any(Instant.class)))
                .thenReturn(expected);

        PersistentJob result = service.enqueueText(route, message);
        assertSame(expected, result);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(jobs).enqueue(eq(businessId), isNull(), eq(PersistentJob.Type.WHATSAPP_INBOUND_TEXT_PROCESS),
                eq("wa-in-text:wamid.TEST-1"), payloadCaptor.capture(), eq(7), any(Instant.class));

        JSONObject payload = new JSONObject(payloadCaptor.getValue());
        assertEquals("wamid.TEST-1", payload.getString("messageId"));
        assertEquals(phoneNumberId.toString(), payload.getString("phoneNumberId"));
        assertEquals("56911111111", payload.getString("from"));
        assertEquals("Hola", payload.getString("text"));
        assertEquals(expectedCorrelation(businessId, "wamid.TEST-1"), UUID.fromString(payload.getString("correlationId")));
        assertFalse(payload.has("accessToken"));
        assertFalse(payload.has("apiKey"));
        assertFalse(payload.has("credentialRef"));

        assertEquals(
                MetaWhatsAppJobKeys.correlationId(businessId, "wamid.TEST-1"),
                MetaWhatsAppJobKeys.correlationId(businessId, new String("wamid.TEST-1")));
    }

    @Test
    void audioEnqueueUsesAudioKeyAndContainsNoCredentialMaterial() {
        PersistentJobService jobs = mock(PersistentJobService.class);
        MetaWhatsAppInboundProperties properties = new MetaWhatsAppInboundProperties();
        properties.setAudioMaxAttempts(9);
        MetaWhatsAppInboundJobService service = new MetaWhatsAppInboundJobService(jobs, properties);

        UUID businessId = UUID.randomUUID();
        UUID phoneNumberId = UUID.randomUUID();
        MetaWhatsAppTenantRoute route = new MetaWhatsAppTenantRoute(businessId, phoneNumberId, "+56999999999");
        MetaWhatsAppInboundAudio audio = new MetaWhatsAppInboundAudio(
                "wamid.AUDIO-1", phoneNumberId.toString(), "56922222222", "media-123", "audio/ogg");

        PersistentJob expected = job(UUID.randomUUID(), businessId, PersistentJob.Type.WHATSAPP_INBOUND_AUDIO_PROCESS, "wa-in-audio:wamid.AUDIO-1", 9);
        when(jobs.enqueue(eq(businessId), isNull(), eq(PersistentJob.Type.WHATSAPP_INBOUND_AUDIO_PROCESS),
                eq("wa-in-audio:wamid.AUDIO-1"), anyString(), eq(9), any(Instant.class)))
                .thenReturn(expected);

        service.enqueueAudio(route, audio);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(jobs).enqueue(eq(businessId), isNull(), eq(PersistentJob.Type.WHATSAPP_INBOUND_AUDIO_PROCESS),
                eq("wa-in-audio:wamid.AUDIO-1"), payloadCaptor.capture(), eq(9), any(Instant.class));

        JSONObject payload = new JSONObject(payloadCaptor.getValue());
        assertEquals("wamid.AUDIO-1", payload.getString("messageId"));
        assertEquals(phoneNumberId.toString(), payload.getString("phoneNumberId"));
        assertEquals("56922222222", payload.getString("from"));
        assertEquals("media-123", payload.getString("mediaId"));
        assertEquals("audio/ogg", payload.getString("mimeType"));
        assertEquals(expectedCorrelation(businessId, "wamid.AUDIO-1"), UUID.fromString(payload.getString("correlationId")));
        assertFalse(payload.has("accessToken"));
        assertFalse(payload.has("apiKey"));
        assertFalse(payload.has("credentialRef"));
    }

    @Test
    void jobKeysSanitizeAndRejectBlankWamids() {
        assertEquals("wa-in-text:wamid.bad_value", MetaWhatsAppJobKeys.text(" wamid.bad value "));
        assertEquals("wa-in-audio:wamid.bad_value", MetaWhatsAppJobKeys.audio(" wamid.bad value "));
        assertEquals("wa-audio-recovery:wamid.bad_value", MetaWhatsAppJobKeys.recovery(" wamid.bad value "));
        assertThrows(IllegalArgumentException.class, () -> MetaWhatsAppJobKeys.text("   "));
        assertTrue(MetaWhatsAppJobKeys.text("x".repeat(200)).length() <= "wa-in-text:".length() + 120);
    }

    private static UUID expectedCorrelation(UUID businessId, String wamid) {
        return UUID.nameUUIDFromBytes(("wa:" + businessId + ":" + wamid).getBytes(StandardCharsets.UTF_8));
    }

    private static PersistentJob job(UUID id, UUID businessId, PersistentJob.Type type, String key, int maxAttempts) {
        Instant now = Instant.now();
        return new PersistentJob(id, businessId, null, type, PersistentJob.Status.PENDING, key, "{}",
                0, maxAttempts, now, null, null, null, null, null, now, now);
    }
}

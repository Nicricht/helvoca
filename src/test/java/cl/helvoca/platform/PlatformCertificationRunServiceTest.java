package cl.helvoca.platform;

import cl.helvoca.common.ConflictException;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.telephony.twilio.TwilioCertificationCommandStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlatformCertificationRunServiceTest {

    @Test
    void createsCommandWithAuthenticatedActorAndReturnsStatus() {
        TwilioCertificationCommandStore store = mock(TwilioCertificationCommandStore.class);
        PlatformCertificationRunService service = new PlatformCertificationRunService(store);
        String runId = "latency-api-20260927-001";
        Instant now = Instant.now();
        var status = new TwilioCertificationCommandStore.CommandStatus(
                runId, "PENDING", "platform-user", now, null, null, null, null, null);

        when(store.enqueue(runId, "platform-user")).thenReturn(true);
        when(store.find(runId)).thenReturn(Optional.of(status));

        PlatformCertificationRunResponse response = service.create(runId, "platform-user");

        assertEquals(runId, response.runId());
        assertEquals("PENDING", response.status());
        assertEquals("platform-user", response.requestedBy());
        assertNull(response.providerCallSid());
        verify(store).enqueue(runId, "platform-user");
    }

    @Test
    void duplicateRunIdIsConflict() {
        TwilioCertificationCommandStore store = mock(TwilioCertificationCommandStore.class);
        when(store.enqueue("latency-api-20260927-002", "platform-user")).thenReturn(false);
        PlatformCertificationRunService service = new PlatformCertificationRunService(store);

        assertThrows(ConflictException.class,
                () -> service.create("latency-api-20260927-002", "platform-user"));
        verify(store, never()).find(anyString());
    }

    @Test
    void invalidRunIdFailsBeforeDatabase() {
        TwilioCertificationCommandStore store = mock(TwilioCertificationCommandStore.class);
        PlatformCertificationRunService service = new PlatformCertificationRunService(store);

        assertThrows(IllegalArgumentException.class, () -> service.create("bad", "platform-user"));
        verifyNoInteractions(store);
    }

    @Test
    void missingRunIsNotFound() {
        TwilioCertificationCommandStore store = mock(TwilioCertificationCommandStore.class);
        when(store.find("latency-api-20260927-003")).thenReturn(Optional.empty());
        PlatformCertificationRunService service = new PlatformCertificationRunService(store);

        assertThrows(NotFoundException.class,
                () -> service.get("latency-api-20260927-003"));
    }

    @Test
    void responseNeverContainsCallbackToken() {
        assertTrue(Arrays.stream(PlatformCertificationRunResponse.class.getRecordComponents())
                .noneMatch(field -> "callbackToken".equals(field.getName())
                        || "token".equals(field.getName())));
    }
}

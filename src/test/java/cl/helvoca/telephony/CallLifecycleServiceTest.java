package cl.helvoca.telephony;

import cl.helvoca.call.CallSession;
import cl.helvoca.call.CallSessionRepository;
import cl.helvoca.call.CallStatus;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.phone.PhoneNumberRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CallLifecycleServiceTest {

    @Test
    void carrierCompletionDoesNotOverwriteApplicationFailure() {
        CallSessionRepository calls = mock(CallSessionRepository.class);
        CallLifecycleService lifecycle = new CallLifecycleService(
                mock(PhoneNumberRepository.class), mock(CustomerRepository.class), calls);

        CallSession call = new CallSession();
        call.setProviderCallId("CA0123456789abcdef0123456789abcdef");
        call.setStatus(CallStatus.FAILED);
        call.setStartedAt(Instant.now().minusSeconds(10));
        when(calls.findByProviderCallId(call.getProviderCallId())).thenReturn(Optional.of(call));

        lifecycle.updateStatus(call.getProviderCallId(), "completed", 10);

        assertEquals(CallStatus.FAILED, call.getStatus());
        assertEquals(10, call.getDurationSeconds());
        assertNotNull(call.getEndedAt());
    }

    @Test
    void internalLiveCompletionCanFinishAnInProgressCall() {
        CallSessionRepository calls = mock(CallSessionRepository.class);
        CallLifecycleService lifecycle = new CallLifecycleService(
                mock(PhoneNumberRepository.class), mock(CustomerRepository.class), calls);

        UUID callId = UUID.randomUUID();
        CallSession call = new CallSession();
        call.setStatus(CallStatus.IN_PROGRESS);
        call.setStartedAt(Instant.now().minusSeconds(5));
        when(calls.findById(callId)).thenReturn(Optional.of(call));

        lifecycle.updateStatus(callId, "completed", null);

        assertEquals(CallStatus.COMPLETED, call.getStatus());
        assertNotNull(call.getEndedAt());
        assertNotNull(call.getDurationSeconds());
    }
}

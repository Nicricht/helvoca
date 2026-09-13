package cl.helvoca.telephony;

import cl.helvoca.call.CallSession;
import cl.helvoca.call.CallSessionRepository;
import cl.helvoca.call.CallStatus;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.phone.PhoneNumber;
import cl.helvoca.phone.PhoneNumberRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

class CallLifecycleServiceTest {

    @Test
    void carrierCompletionDoesNotOverwriteApplicationFailure() {
        CallSessionRepository calls = mock(CallSessionRepository.class);
        CallLifecycleService lifecycle = lifecycle(
                mock(PhoneNumberRepository.class), mock(CustomerRepository.class), calls, new CallCommercialProperties());

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
        CallLifecycleService lifecycle = lifecycle(
                mock(PhoneNumberRepository.class), mock(CustomerRepository.class), calls, new CallCommercialProperties());

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

    @Test
    void completedCallPersistsConfigurableEstimatedCosts() {
        CallSessionRepository calls = mock(CallSessionRepository.class);
        CallCommercialProperties properties = new CallCommercialProperties();
        properties.setTelephonyCostPerMinuteUsd(new BigDecimal("0.020000"));
        properties.setAiCostPerMinuteUsd(new BigDecimal("0.030000"));
        CallLifecycleService lifecycle = lifecycle(
                mock(PhoneNumberRepository.class), mock(CustomerRepository.class), calls, properties);

        UUID callId = UUID.randomUUID();
        CallSession call = new CallSession();
        call.setStatus(CallStatus.IN_PROGRESS);
        call.setAiProvider("gemini");
        call.setStartedAt(Instant.now().minusSeconds(120));
        when(calls.findById(callId)).thenReturn(Optional.of(call));

        lifecycle.updateStatus(callId, "completed", 120);

        assertEquals(new BigDecimal("0.040000"), call.getEstimatedTelephonyCostUsd());
        assertEquals(new BigDecimal("0.060000"), call.getEstimatedAiCostUsd());
        assertEquals(new BigDecimal("0.100000"), call.getEstimatedTotalCostUsd());
        verify(calls).saveAndFlush(call);
    }

    @Test
    void tenantCapacityRejectsCallBeforePersistingAnotherSession() {
        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        CustomerRepository customers = mock(CustomerRepository.class);
        CallSessionRepository calls = mock(CallSessionRepository.class);
        CallCommercialProperties properties = new CallCommercialProperties();
        properties.setMaxConcurrentPerBusiness(2);
        CallLifecycleService lifecycle = lifecycle(phones, customers, calls, properties);

        UUID businessId = UUID.randomUUID();
        PhoneNumber phone = mock(PhoneNumber.class);
        when(phone.getBusinessId()).thenReturn(businessId);
        when(phones.findByPhoneNumberAndActiveTrue("+14355550000")).thenReturn(Optional.of(phone));
        when(calls.findByProviderCallId("CA-capacity")).thenReturn(Optional.empty());
        when(calls.countByBusinessIdAndStatusIn(eq(businessId), anyCollection())).thenReturn(2L);

        assertThrows(CallCapacityExceededException.class,
                () -> lifecycle.startInboundCall("twilio", "CA-capacity", "+56911111111", "+14355550000"));

        verify(calls, never()).saveAndFlush(any(CallSession.class));
    }

    private static CallLifecycleService lifecycle(PhoneNumberRepository phones,
                                                  CustomerRepository customers,
                                                  CallSessionRepository calls,
                                                  CallCommercialProperties properties) {
        return new CallLifecycleService(
                phones, customers, calls, mock(JdbcTemplate.class), properties, new SimpleMeterRegistry());
    }
}

package cl.helvoca.telephony;

import cl.helvoca.billing.BusinessSubscriptionService;
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
import java.util.List;
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
    void subscriptionCapacityRejectsCallBeforePersistingAnotherSession() {
        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        CustomerRepository customers = mock(CustomerRepository.class);
        CallSessionRepository calls = mock(CallSessionRepository.class);
        BusinessSubscriptionService subscriptions = mock(BusinessSubscriptionService.class);
        CallCommercialProperties properties = new CallCommercialProperties();
        properties.setMaxConcurrentPerBusiness(100);
        CallLifecycleService lifecycle = lifecycle(phones, customers, calls, properties);
        lifecycle.setSubscriptions(subscriptions);

        UUID businessId = UUID.randomUUID();
        PhoneNumber phone = mock(PhoneNumber.class);
        when(phone.getBusinessId()).thenReturn(businessId);
        when(phones.findByPhoneNumberAndActiveTrue("+14355550000")).thenReturn(Optional.of(phone));
        when(calls.findByProviderCallId("CA-capacity")).thenReturn(Optional.empty());
        when(calls.countByBusinessIdAndStatusIn(eq(businessId), anyCollection())).thenReturn(2L);
        when(subscriptions.view(businessId)).thenReturn(activeSubscription(businessId, 2));

        assertThrows(CallCapacityExceededException.class,
                () -> lifecycle.startInboundCall("twilio", "CA-capacity", "+56911111111", "+14355550000"));

        verify(calls).countByBusinessIdAndStatusIn(eq(businessId), anyCollection());
        verify(calls, never()).saveAndFlush(any(CallSession.class));
    }

    @Test
    void missingSubscriptionServiceFailsClosedBeforeLegacyCapacityFallback() {
        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        CallSessionRepository calls = mock(CallSessionRepository.class);
        CallCommercialProperties properties = new CallCommercialProperties();
        properties.setMaxConcurrentPerBusiness(100);
        CallLifecycleService lifecycle = lifecycle(
                phones, mock(CustomerRepository.class), calls, properties);

        UUID businessId = UUID.randomUUID();
        PhoneNumber phone = mock(PhoneNumber.class);
        when(phone.getBusinessId()).thenReturn(businessId);
        when(phones.findByPhoneNumberAndActiveTrue("+14355550003")).thenReturn(Optional.of(phone));
        when(calls.findByProviderCallId("CA-no-subscription-service")).thenReturn(Optional.empty());

        assertThrows(CallCapacityExceededException.class, () -> lifecycle.startInboundCall(
                "twilio", "CA-no-subscription-service", "+56911111112", "+14355550003"));

        verify(calls, never()).countByBusinessIdAndStatusIn(eq(businessId), anyCollection());
        verify(calls, never()).saveAndFlush(any(CallSession.class));
    }

    private static BusinessSubscriptionService.SubscriptionView activeSubscription(UUID businessId,
                                                                                    int maxConcurrentCalls) {
        Instant now = Instant.now();
        return new BusinessSubscriptionService.SubscriptionView(
                businessId, "BASIC", "EMPRENDE", "Emprende", "ACTIVE", true,
                maxConcurrentCalls, 300, 0, 0,
                now.minusSeconds(60), now.plusSeconds(3600), null, false, List.of(), false);
    }

    private static CallLifecycleService lifecycle(PhoneNumberRepository phones,
                                                  CustomerRepository customers,
                                                  CallSessionRepository calls,
                                                  CallCommercialProperties properties) {
        return new CallLifecycleService(
                phones, customers, calls, mock(JdbcTemplate.class), properties, new SimpleMeterRegistry());
    }
}

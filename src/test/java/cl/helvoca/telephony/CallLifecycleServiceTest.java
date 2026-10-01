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
import org.springframework.test.util.ReflectionTestUtils;

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
    void completedCallUsesModelSpecificAiRateBeforeProviderAndLegacyFallbacks() {
        CallSessionRepository calls = mock(CallSessionRepository.class);
        CallCommercialProperties properties = new CallCommercialProperties();
        properties.setTelephonyCostPerMinuteUsd(new BigDecimal("0.010000"));
        properties.setAiCostPerMinuteUsd(new BigDecimal("0.030000"));
        properties.setAiProviderCostPerMinuteUsd(java.util.Map.of(
                "gemini", new BigDecimal("0.025000")));
        properties.setAiModelCostPerMinuteUsd(java.util.Map.of(
                "gemini-3.8-live", new BigDecimal("0.020000")));
        CallLifecycleService lifecycle = lifecycle(
                mock(PhoneNumberRepository.class), mock(CustomerRepository.class), calls, properties);

        UUID callId = UUID.randomUUID();
        CallSession call = new CallSession();
        call.setStatus(CallStatus.IN_PROGRESS);
        call.setAiProvider("gemini");
        call.setAiModel("gemini-3.8-live");
        call.setStartedAt(Instant.now().minusSeconds(120));
        when(calls.findById(callId)).thenReturn(Optional.of(call));

        lifecycle.updateStatus(callId, "completed", 120);

        assertEquals(new BigDecimal("0.020000"), call.getEstimatedTelephonyCostUsd());
        assertEquals(new BigDecimal("0.040000"), call.getEstimatedAiCostUsd());
        assertEquals(new BigDecimal("0.060000"), call.getEstimatedTotalCostUsd());
    }

    @Test
    void selectedAiModelIsPersistedAsDiagnosticMetadata() {
        CallSessionRepository calls = mock(CallSessionRepository.class);
        CallLifecycleService lifecycle = lifecycle(
                mock(PhoneNumberRepository.class), mock(CustomerRepository.class), calls, new CallCommercialProperties());

        UUID callId = UUID.randomUUID();
        CallSession call = new CallSession();
        when(calls.findById(callId)).thenReturn(Optional.of(call));

        lifecycle.markAiModel(callId, "  gpt-realtime-2.1  ");

        assertEquals("gpt-realtime-2.1", call.getAiModel());
        verify(calls).saveAndFlush(call);
    }

    @Test
    void aiModelMetadataHandlesNoopAndLengthGuardBranches() {
        CallSessionRepository calls = mock(CallSessionRepository.class);
        CallLifecycleService lifecycle = lifecycle(
                mock(PhoneNumberRepository.class), mock(CustomerRepository.class), calls, new CallCommercialProperties());
        UUID callId = UUID.randomUUID();

        lifecycle.markAiModel(callId, null);
        lifecycle.markAiModel(callId, "   ");
        verifyNoInteractions(calls);

        CallSession call = new CallSession();
        when(calls.findById(callId)).thenReturn(Optional.of(call));
        String oversized = "x".repeat(140);

        lifecycle.markAiModel(callId, oversized);
        assertEquals(120, call.getAiModel().length());
        verify(calls, times(1)).saveAndFlush(call);

        lifecycle.markAiModel(callId, oversized);
        verify(calls, times(1)).saveAndFlush(call);
    }

    @Test
    void inboundAndStatusLifecyclePublishIdempotentObserverHooks() {
        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        CustomerRepository customers = mock(CustomerRepository.class);
        CallSessionRepository calls = mock(CallSessionRepository.class);
        BusinessSubscriptionService subscriptions = mock(BusinessSubscriptionService.class);
        CallLifecycleObserver observer = mock(CallLifecycleObserver.class);
        CallLifecycleService lifecycle = lifecycle(phones, customers, calls, new CallCommercialProperties());
        lifecycle.setSubscriptions(subscriptions);
        lifecycle.setLifecycleObservers(List.of(observer));

        UUID businessId = UUID.randomUUID();
        UUID callId = UUID.randomUUID();
        PhoneNumber phone = mock(PhoneNumber.class);
        when(phone.getBusinessId()).thenReturn(businessId);
        when(phone.getId()).thenReturn(UUID.randomUUID());
        when(phones.findByPhoneNumberAndActiveTrue("+14355550004")).thenReturn(Optional.of(phone));
        when(calls.findByProviderCallId("CA-observer"))
                .thenReturn(Optional.empty(), Optional.empty());
        when(calls.countByBusinessIdAndStatusIn(eq(businessId), anyCollection())).thenReturn(0L);
        when(subscriptions.view(businessId)).thenReturn(activeSubscription(businessId, 2));
        when(calls.saveAndFlush(any(CallSession.class))).thenAnswer(invocation -> {
            CallSession saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", callId);
            return saved;
        });

        UUID started = lifecycle.startInboundCall(
                "twilio", "CA-observer", "+56911111113", "+14355550004");

        assertEquals(callId, started);
        verify(observer).onInboundCallStarted(argThat(call -> callId.equals(call.getId())));

        CallSession persisted = new CallSession();
        ReflectionTestUtils.setField(persisted, "id", callId);
        persisted.setBusinessId(businessId);
        persisted.setProviderCallId("CA-observer");
        persisted.setStatus(CallStatus.IN_PROGRESS);
        persisted.setStartedAt(Instant.now().minusSeconds(5));
        when(calls.findByProviderCallId("CA-observer")).thenReturn(Optional.of(persisted));

        lifecycle.updateStatus("CA-observer", "completed", 5);

        verify(observer).onCallUpdated(persisted);
        assertEquals(CallStatus.COMPLETED, persisted.getStatus());
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

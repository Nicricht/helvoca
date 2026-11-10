package cl.helvoca.telephony;

import cl.helvoca.billing.BusinessSubscriptionService;
import cl.helvoca.call.CallSession;
import cl.helvoca.call.CallSessionRepository;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.phone.PhoneNumber;
import cl.helvoca.phone.PhoneNumberRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.mockito.ArgumentCaptor;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CallSubscriptionAdmissionTest {


    @Test
    void unknownDestinationRejectsBeforeAnyCallOrSubscriptionIsCreated() {
        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        CallSessionRepository calls = mock(CallSessionRepository.class);
        when(calls.findByProviderCallId("CA-unregistered-destination")).thenReturn(Optional.empty());
        when(phones.findByPhoneNumberAndActiveTrue("+14355551009")).thenReturn(Optional.empty());

        CallLifecycleService lifecycle = lifecycle(phones, calls);

        assertThrows(NotFoundException.class, () -> lifecycle.startInboundCall(
                "twilio", "CA-unregistered-destination", "+56910000009", "+14355551009"));
        verify(calls, never()).saveAndFlush(any(CallSession.class));
        verify(calls, never()).countByBusinessIdAndStatusIn(any(UUID.class), anyCollection());
        verify(phones).findByPhoneNumberAndActiveTrue("+14355551009");
    }

    @Test
    void suspendedSubscriptionRejectsBeforePersistingCall() {
        UUID businessId = UUID.randomUUID();
        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        CallSessionRepository calls = mock(CallSessionRepository.class);
        BusinessSubscriptionService subscriptions = mock(BusinessSubscriptionService.class);
        PhoneNumber phone = mock(PhoneNumber.class);

        when(phone.getBusinessId()).thenReturn(businessId);
        when(phones.findByPhoneNumberAndActiveTrue("+14355551000")).thenReturn(Optional.of(phone));
        when(calls.findByProviderCallId("CA-subscription-blocked")).thenReturn(Optional.empty());
        when(subscriptions.view(businessId)).thenReturn(view(businessId, false, 2));

        CallLifecycleService lifecycle = lifecycle(phones, calls);
        lifecycle.setSubscriptions(subscriptions);

        assertThrows(CallCapacityExceededException.class, () -> lifecycle.startInboundCall(
                "twilio", "CA-subscription-blocked", "+56910000000", "+14355551000"));

        verify(calls, never()).saveAndFlush(any(CallSession.class));
        verify(calls, never()).countByBusinessIdAndStatusIn(eq(businessId), anyCollection());
    }

    @Test
    void planConcurrentLimitOverridesGlobalLimit() {
        UUID businessId = UUID.randomUUID();
        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        CallSessionRepository calls = mock(CallSessionRepository.class);
        BusinessSubscriptionService subscriptions = mock(BusinessSubscriptionService.class);
        PhoneNumber phone = mock(PhoneNumber.class);

        when(phone.getBusinessId()).thenReturn(businessId);
        when(phones.findByPhoneNumberAndActiveTrue("+14355551001")).thenReturn(Optional.of(phone));
        when(calls.findByProviderCallId("CA-basic-full")).thenReturn(Optional.empty());
        when(calls.countByBusinessIdAndStatusIn(eq(businessId), anyCollection())).thenReturn(2L);
        when(subscriptions.view(businessId)).thenReturn(view(businessId, true, 2));

        CallCommercialProperties properties = new CallCommercialProperties();
        properties.setMaxConcurrentPerBusiness(100);
        CallLifecycleService lifecycle = lifecycle(phones, calls, properties);
        lifecycle.setSubscriptions(subscriptions);

        assertThrows(CallCapacityExceededException.class, () -> lifecycle.startInboundCall(
                "twilio", "CA-basic-full", "+56910000001", "+14355551001"));
        verify(calls, never()).saveAndFlush(any(CallSession.class));
    }

    @Test
    void missingCommercialConfigurationRejectsAsSubscriptionFailure() {
        UUID businessId = UUID.randomUUID();
        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        CallSessionRepository calls = mock(CallSessionRepository.class);
        BusinessSubscriptionService subscriptions = mock(BusinessSubscriptionService.class);
        PhoneNumber phone = mock(PhoneNumber.class);

        when(phone.getBusinessId()).thenReturn(businessId);
        when(phones.findByPhoneNumberAndActiveTrue("+14355551002")).thenReturn(Optional.of(phone));
        when(calls.findByProviderCallId("CA-missing-commercial")).thenReturn(Optional.empty());
        when(subscriptions.view(businessId)).thenThrow(new IllegalStateException("Business subscription is not initialized"));

        CallLifecycleService lifecycle = lifecycle(phones, calls);
        lifecycle.setSubscriptions(subscriptions);

        assertThrows(CallCapacityExceededException.class, () -> lifecycle.startInboundCall(
                "twilio", "CA-missing-commercial", "+56910000002", "+14355551002"));
        verify(calls, never()).countByBusinessIdAndStatusIn(eq(businessId), anyCollection());
        verify(calls, never()).saveAndFlush(any(CallSession.class));
    }


    @Test
    void estimatedDailyBudgetRejectsMisconfigurationBeforeAnyDbSpendRead() {
        UUID businessId = UUID.randomUUID();
        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        CallSessionRepository calls = mock(CallSessionRepository.class);
        BusinessSubscriptionService subscriptions = mock(BusinessSubscriptionService.class);
        PhoneNumber phone = mock(PhoneNumber.class);
        when(phone.getBusinessId()).thenReturn(businessId);
        when(phones.findByPhoneNumberAndActiveTrue("+14355551901"))
                .thenReturn(Optional.of(phone));
        when(subscriptions.view(businessId)).thenReturn(view(businessId, true, 2));
        CallCommercialProperties props = new CallCommercialProperties();
        props.setMaxEstimatedCostPerBusinessPerUtcDayUsd(new java.math.BigDecimal("0.50"));
        CallLifecycleService lifecycle = lifecycle(phones, calls, props);
        lifecycle.setSubscriptions(subscriptions);
        assertThrows(CallCapacityExceededException.class, () -> lifecycle.startInboundCall(
                "twilio", "CA-budget-invalid", "+56910001901", "+14355551901"));
        props.setEstimatedNewCallReservationUsd(new java.math.BigDecimal("0.51"));
        assertThrows(CallCapacityExceededException.class, () -> lifecycle.startInboundCall(
                "twilio", "CA-budget-invalid-2", "+56910001901", "+14355551901"));
        verify(calls, never()).sumReservedEstimatedCostByBusinessAndUtcDay(
                any(UUID.class), any(Instant.class), any(Instant.class),
                any(java.math.BigDecimal.class));
        verify(calls, never()).saveAndFlush(any(CallSession.class));
    }

    @Test
    void estimatedDailyBudgetRespectsInclusiveThresholdAndFailClosedUnknownTotals() {
        UUID businessId = UUID.randomUUID();
        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        CallSessionRepository calls = mock(CallSessionRepository.class);
        BusinessSubscriptionService subscriptions = mock(BusinessSubscriptionService.class);
        PhoneNumber phone = mock(PhoneNumber.class);
        when(phone.getBusinessId()).thenReturn(businessId);
        when(phones.findByPhoneNumberAndActiveTrue("+14355551902"))
                .thenReturn(Optional.of(phone));
        when(subscriptions.view(businessId)).thenReturn(view(businessId, true, 2));
        CallCommercialProperties props = new CallCommercialProperties();
        props.setMaxEstimatedCostPerBusinessPerUtcDayUsd(new java.math.BigDecimal("0.90"));
        props.setEstimatedNewCallReservationUsd(new java.math.BigDecimal("0.20"));
        CallLifecycleService lifecycle = lifecycle(phones, calls, props);
        lifecycle.setSubscriptions(subscriptions);
        when(calls.sumReservedEstimatedCostByBusinessAndUtcDay(eq(businessId),
                any(Instant.class), any(Instant.class),
                eq(new java.math.BigDecimal("0.20"))))
                .thenReturn(new java.math.BigDecimal("0.80"))
                .thenReturn(new java.math.BigDecimal("0.70"))
                .thenReturn(null);
        assertThrows(CallCapacityExceededException.class, () -> lifecycle.startInboundCall(
                "twilio", "CA-budget-over", "+56910001902", "+14355551902"));
        when(calls.saveAndFlush(any(CallSession.class))).thenAnswer(i -> i.getArgument(0));
        lifecycle.startInboundCall("twilio", "CA-budget-equal", "+56910001902", "+14355551902");
        verify(calls, times(1)).saveAndFlush(any(CallSession.class));
        assertThrows(CallCapacityExceededException.class, () -> lifecycle.startInboundCall(
                "twilio", "CA-budget-unknown", "+56910001902", "+14355551902"));
        verify(calls, times(1)).saveAndFlush(any(CallSession.class));
    }

    @Test
    void perTenantUtcDayLimitBlocksNewCallBeforePersistingOrContactingAi() {
        UUID businessId = UUID.randomUUID();
        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        CallSessionRepository calls = mock(CallSessionRepository.class);
        BusinessSubscriptionService subscriptions = mock(BusinessSubscriptionService.class);
        PhoneNumber phone = mock(PhoneNumber.class);
        when(phone.getBusinessId()).thenReturn(businessId);
        when(phones.findByPhoneNumberAndActiveTrue("+14355551003")).thenReturn(Optional.of(phone));
        when(calls.findByProviderCallId("CA-daily-full")).thenReturn(Optional.empty());
        when(subscriptions.view(businessId)).thenReturn(view(businessId, true, 2));
        when(calls.countByBusinessIdAndStartedAtGreaterThanEqualAndStartedAtLessThan(
                eq(businessId), any(Instant.class), any(Instant.class))).thenReturn(8L);

        CallCommercialProperties properties = new CallCommercialProperties();
        properties.setMaxCallsPerBusinessPerUtcDay(8);
        CallLifecycleService lifecycle = lifecycle(phones, calls, properties);
        lifecycle.setSubscriptions(subscriptions);

        assertThrows(CallCapacityExceededException.class, () -> lifecycle.startInboundCall(
                "twilio", "CA-daily-full", "+56910000003", "+14355551003"));
        verify(calls, never()).saveAndFlush(any(CallSession.class));
        ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> to = ArgumentCaptor.forClass(Instant.class);
        verify(calls).countByBusinessIdAndStartedAtGreaterThanEqualAndStartedAtLessThan(
                eq(businessId), from.capture(), to.capture());
        assertEquals(from.getValue().truncatedTo(ChronoUnit.DAYS), from.getValue());
        assertEquals(from.getValue().plus(1, ChronoUnit.DAYS), to.getValue());
    }

    @Test
    void belowDailyThresholdAdmitsCallerAndDoesNotResetCounterForFailedCalls() {
        UUID businessId = UUID.randomUUID();
        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        CallSessionRepository calls = mock(CallSessionRepository.class);
        BusinessSubscriptionService subscriptions = mock(BusinessSubscriptionService.class);
        PhoneNumber phone = mock(PhoneNumber.class);
        when(phone.getBusinessId()).thenReturn(businessId);
        when(phones.findByPhoneNumberAndActiveTrue("+14355551004")).thenReturn(Optional.of(phone));
        when(calls.findByProviderCallId("CA-daily-seventh")).thenReturn(Optional.empty());
        when(subscriptions.view(businessId)).thenReturn(view(businessId, true, 2));
        when(calls.countByBusinessIdAndStartedAtGreaterThanEqualAndStartedAtLessThan(
                eq(businessId), any(Instant.class), any(Instant.class))).thenReturn(7L);
        when(calls.saveAndFlush(any(CallSession.class))).thenAnswer(a -> a.getArgument(0));

        CallCommercialProperties properties = new CallCommercialProperties();
        properties.setMaxCallsPerBusinessPerUtcDay(8);
        CallLifecycleService lifecycle = lifecycle(phones, calls, properties);
        lifecycle.setSubscriptions(subscriptions);
        lifecycle.startInboundCall("twilio", "CA-daily-seventh", "+56910000004", "+14355551004");

        verify(calls, times(1)).saveAndFlush(any(CallSession.class));
    }

    @Test
    void disabledDailyLimitLeavesPriorCallAdmittanceUnchanged() {
        UUID businessId = UUID.randomUUID();
        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        CallSessionRepository calls = mock(CallSessionRepository.class);
        BusinessSubscriptionService subscriptions = mock(BusinessSubscriptionService.class);
        PhoneNumber phone = mock(PhoneNumber.class);
        when(phone.getBusinessId()).thenReturn(businessId);
        when(phones.findByPhoneNumberAndActiveTrue("+14355551005")).thenReturn(Optional.of(phone));
        when(calls.findByProviderCallId("CA-unlimited")).thenReturn(Optional.empty());
        when(subscriptions.view(businessId)).thenReturn(view(businessId, true, 2));
        when(calls.saveAndFlush(any(CallSession.class))).thenAnswer(a -> a.getArgument(0));

        CallCommercialProperties properties = new CallCommercialProperties();
        properties.setMaxCallsPerBusinessPerUtcDay(-20);
        assertEquals(0, properties.getMaxCallsPerBusinessPerUtcDay());
        CallLifecycleService lifecycle = lifecycle(phones, calls, properties);
        lifecycle.setSubscriptions(subscriptions);
        lifecycle.startInboundCall("twilio", "CA-unlimited", "+56910000005", "+14355551005");

        verify(calls, never()).countByBusinessIdAndStartedAtGreaterThanEqualAndStartedAtLessThan(
                any(UUID.class), any(Instant.class), any(Instant.class));
        verify(calls).saveAndFlush(any(CallSession.class));
    }

    @Test
    void sameCarrierSidRemainsIdempotentEvenWhenDailyLimitReached() {
        UUID businessId = UUID.randomUUID();
        UUID savedCallId = UUID.randomUUID();
        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        CallSessionRepository calls = mock(CallSessionRepository.class);
        CallSession existing = mock(CallSession.class);
        when(existing.getId()).thenReturn(savedCallId);
        when(calls.findByProviderCallId("CA-existing-idempotent")).thenReturn(Optional.of(existing));

        CallCommercialProperties properties = new CallCommercialProperties();
        properties.setMaxCallsPerBusinessPerUtcDay(1);
        CallLifecycleService lifecycle = lifecycle(phones, calls, properties);
        assertEquals(savedCallId, lifecycle.startInboundCall(
                "twilio", "CA-existing-idempotent", "+56910000006", "+14355551006"));
        verify(calls, never()).countByBusinessIdAndStartedAtGreaterThanEqualAndStartedAtLessThan(
                any(UUID.class), any(Instant.class), any(Instant.class));
        verify(calls, never()).saveAndFlush(any(CallSession.class));
        verifyNoInteractions(phones);
    }

    private static BusinessSubscriptionService.SubscriptionView view(UUID businessId,
                                                                     boolean allowed,
                                                                     int maxConcurrent) {
        Instant now = Instant.now();
        return new BusinessSubscriptionService.SubscriptionView(
                businessId, "BASIC", "EMPRENDE", "Emprende",
                allowed ? "ACTIVE" : "SUSPENDED", allowed,
                maxConcurrent, 300, 0, 0,
                now.minusSeconds(60), now.plusSeconds(3600), null, false, List.of(), false);
    }

    private static CallLifecycleService lifecycle(PhoneNumberRepository phones,
                                                  CallSessionRepository calls) {
        return lifecycle(phones, calls, new CallCommercialProperties());
    }

    private static CallLifecycleService lifecycle(PhoneNumberRepository phones,
                                                  CallSessionRepository calls,
                                                  CallCommercialProperties properties) {
        return new CallLifecycleService(
                phones,
                mock(CustomerRepository.class),
                calls,
                mock(JdbcTemplate.class),
                properties,
                new SimpleMeterRegistry());
    }
}

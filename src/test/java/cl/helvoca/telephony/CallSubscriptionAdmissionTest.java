package cl.helvoca.telephony;

import cl.helvoca.billing.BusinessSubscriptionService;
import cl.helvoca.call.CallSession;
import cl.helvoca.call.CallSessionRepository;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.phone.PhoneNumber;
import cl.helvoca.phone.PhoneNumberRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CallSubscriptionAdmissionTest {

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

    private static BusinessSubscriptionService.SubscriptionView view(UUID businessId,
                                                                     boolean allowed,
                                                                     int maxConcurrent) {
        Instant now = Instant.now();
        return new BusinessSubscriptionService.SubscriptionView(
                businessId, "BASIC", allowed ? "ACTIVE" : "SUSPENDED", allowed,
                maxConcurrent, 300, 0, 0,
                now.minusSeconds(60), now.plusSeconds(3600), null, false, false);
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

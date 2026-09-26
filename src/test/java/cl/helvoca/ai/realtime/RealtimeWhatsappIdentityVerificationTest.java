package cl.helvoca.ai.realtime;

import cl.helvoca.booking.BookingRepository;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.call.CallSession;
import cl.helvoca.call.CallSessionRepository;
import cl.helvoca.customer.Customer;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.knowledge.KnowledgeItemRepository;
import cl.helvoca.learning.UnansweredQuestionService;
import cl.helvoca.omnichannel.CustomerIdentity;
import cl.helvoca.omnichannel.CustomerIdentityService;
import cl.helvoca.request.BusinessRequestService;
import cl.helvoca.schedule.BusinessScheduleService;
import cl.helvoca.servicecatalog.ServiceItemRepository;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RealtimeWhatsappIdentityVerificationTest {

    @Test
    void explicitSameNumberConfirmationVerifiesCurrentCallerForWhatsapp() {
        BusinessRepository businesses = mock(BusinessRepository.class);
        CustomerRepository customers = mock(CustomerRepository.class);
        ServiceItemRepository services = mock(ServiceItemRepository.class);
        KnowledgeItemRepository knowledge = mock(KnowledgeItemRepository.class);
        BookingRepository bookings = mock(BookingRepository.class);
        CallSessionRepository calls = mock(CallSessionRepository.class);
        BusinessScheduleService schedule = mock(BusinessScheduleService.class);
        BusinessRequestService requests = mock(BusinessRequestService.class);
        UnansweredQuestionService unanswered = mock(UnansweredQuestionService.class);
        CustomerIdentityService identities = mock(CustomerIdentityService.class);

        RealtimeToolService service = new RealtimeToolService(
                businesses, customers, services, knowledge, bookings, calls,
                schedule, requests, unanswered);
        ReflectionTestUtils.setField(service, "customerIdentities", identities);

        UUID businessId = UUID.randomUUID();
        UUID callId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID identityId = UUID.randomUUID();

        CallSession call = new CallSession();
        call.setBusinessId(businessId);
        call.setCallerNumber("+56911112222");
        call.setStreamSid("MZ-live");

        Customer customer = mock(Customer.class);
        when(customer.getId()).thenReturn(customerId);

        CustomerIdentity identity = mock(CustomerIdentity.class);
        when(identity.getId()).thenReturn(identityId);

        when(calls.findByIdAndBusinessId(callId, businessId)).thenReturn(Optional.of(call));
        when(customers.findFirstByBusinessIdAndPhone(businessId, "+56911112222"))
                .thenReturn(Optional.of(customer));
        when(identities.verifyPhone(
                eq(businessId),
                eq(customerId),
                eq("+56911112222"),
                eq(CustomerIdentity.VerificationStatus.CUSTOMER_VERIFIED),
                eq("VOICE_EXPLICIT_WHATSAPP_CONFIRMATION")))
                .thenReturn(identity);

        RealtimeCallContext context = new RealtimeCallContext(
                callId, businessId, customerId,
                "+56911112222", "+56220000000", "MZ-live");

        JSONObject result = new JSONObject(service.execute(
                context,
                "verify_caller_whatsapp",
                new JSONObject().put("confirmedSameNumber", true).toString()));

        assertTrue(result.getBoolean("success"), result::toString);
        assertTrue(result.getJSONObject("data").getBoolean("verified"));
        assertEquals(identityId.toString(), result.getJSONObject("data").getString("identityId"));
        assertEquals(customerId, call.getCustomerId());
        verify(calls).save(same(call));
        verify(identities).verifyPhone(
                businessId,
                customerId,
                "+56911112222",
                CustomerIdentity.VerificationStatus.CUSTOMER_VERIFIED,
                "VOICE_EXPLICIT_WHATSAPP_CONFIRMATION");
    }

    @Test
    void missingExplicitConfirmationFailsClosedBeforeIdentityMutation() {
        CustomerRepository customers = mock(CustomerRepository.class);
        CallSessionRepository calls = mock(CallSessionRepository.class);
        CustomerIdentityService identities = mock(CustomerIdentityService.class);

        RealtimeToolService service = new RealtimeToolService(
                mock(BusinessRepository.class),
                customers,
                mock(ServiceItemRepository.class),
                mock(KnowledgeItemRepository.class),
                mock(BookingRepository.class),
                calls,
                mock(BusinessScheduleService.class),
                mock(BusinessRequestService.class),
                mock(UnansweredQuestionService.class));
        ReflectionTestUtils.setField(service, "customerIdentities", identities);

        RealtimeCallContext context = new RealtimeCallContext(
                UUID.randomUUID(), UUID.randomUUID(), null,
                "+56911112222", "+56220000000", "MZ-live");

        JSONObject result = new JSONObject(service.execute(
                context,
                "verify_caller_whatsapp",
                new JSONObject().put("confirmedSameNumber", false).toString()));

        assertFalse(result.getBoolean("success"));
        assertEquals(
                "WHATSAPP_CONFIRMATION_REQUIRED",
                result.getJSONObject("error").getString("code"));
        verifyNoInteractions(identities, calls, customers);
    }
}

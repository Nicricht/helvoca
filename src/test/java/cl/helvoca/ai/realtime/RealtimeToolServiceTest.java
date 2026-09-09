package cl.helvoca.ai.realtime;

import cl.helvoca.agent.AiAgent;
import cl.helvoca.agent.AiAgentService;
import cl.helvoca.agent.AiCapability;
import cl.helvoca.booking.BookingRepository;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.call.CallSession;
import cl.helvoca.call.CallSessionRepository;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.knowledge.KnowledgeItemRepository;
import cl.helvoca.schedule.BusinessHourRepository;
import cl.helvoca.schedule.BusinessScheduleExceptionRepository;
import cl.helvoca.schedule.SchedulePolicyService;
import cl.helvoca.servicecatalog.ServiceItemRepository;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RealtimeToolServiceTest {
    @Test
    void modelCannotSelectAnotherTenant() {
        UUID trustedBusiness = UUID.randomUUID();
        UUID attackerBusiness = UUID.randomUUID();
        Fixture fixture = fixture(trustedBusiness);
        when(fixture.services().findAllByBusinessIdOrderByNameAsc(trustedBusiness)).thenReturn(List.of());

        RealtimeCallContext context = new RealtimeCallContext(
                UUID.randomUUID(), trustedBusiness, null, "+56911111111", "+56222222222", "MZstream");
        String result = fixture.tools().execute(context, "list_services",
                new JSONObject().put("businessId", attackerBusiness.toString()).toString());

        assertTrue(new JSONObject(result).getBoolean("success"));
        verify(fixture.services()).findAllByBusinessIdOrderByNameAsc(trustedBusiness);
        verify(fixture.services(), never()).findAllByBusinessIdOrderByNameAsc(attackerBusiness);
    }

    @Test
    void disabledCapabilityFailsClosed() {
        UUID businessId = UUID.randomUUID();
        Fixture fixture = fixture(businessId);
        AiAgent restricted = activeAgent(businessId);
        restricted.setCapabilities(EnumSet.of(AiCapability.GET_BUSINESS_INFORMATION));
        when(fixture.agents().runtime(businessId)).thenReturn(restricted);

        RealtimeCallContext context = new RealtimeCallContext(
                UUID.randomUUID(), businessId, null, "+56911111111", "+56222222222", "MZstream");
        JSONObject result = new JSONObject(fixture.tools().execute(context, "create_booking", "{}"));

        assertFalse(result.getBoolean("success"));
        assertEquals("TOOL_DISABLED", result.getJSONObject("error").getString("code"));
        verify(fixture.bookings(), never()).saveAndFlush(any());
    }

    @Test
    void createBookingFailsClosedWhenCallerIsNotRegistered() {
        UUID businessId = UUID.randomUUID();
        UUID callId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        String streamSid = "MZstream";
        Fixture fixture = fixture(businessId);

        cl.helvoca.servicecatalog.ServiceItem service = new cl.helvoca.servicecatalog.ServiceItem();
        service.setBusinessId(businessId);
        service.setName("Consulta");
        service.setDurationMinutes(30);
        service.setActive(true);
        when(fixture.services().findByIdAndBusinessId(serviceId, businessId)).thenReturn(Optional.of(service));

        CallSession call = new CallSession();
        call.setBusinessId(businessId);
        call.setStreamSid(streamSid);
        when(fixture.calls().findByIdAndBusinessId(callId, businessId)).thenReturn(Optional.of(call));
        when(fixture.customers().findFirstByBusinessIdAndPhone(businessId, "+56911111111")).thenReturn(Optional.empty());

        RealtimeCallContext context = new RealtimeCallContext(
                callId, businessId, null, "+56911111111", "+56222222222", streamSid);
        JSONObject args = new JSONObject()
                .put("serviceId", serviceId.toString())
                .put("startAt", java.time.Instant.now().plusSeconds(7200).toString());

        JSONObject result = new JSONObject(fixture.tools().execute(context, "create_booking", args.toString()));

        assertFalse(result.getBoolean("success"));
        assertEquals("CUSTOMER_NOT_REGISTERED", result.getJSONObject("error").getString("code"));
        verify(fixture.bookings(), never()).saveAndFlush(any());
    }

    private static Fixture fixture(UUID businessId) {
        BusinessRepository businesses = mock(BusinessRepository.class);
        CustomerRepository customers = mock(CustomerRepository.class);
        ServiceItemRepository services = mock(ServiceItemRepository.class);
        KnowledgeItemRepository knowledge = mock(KnowledgeItemRepository.class);
        BookingRepository bookings = mock(BookingRepository.class);
        CallSessionRepository calls = mock(CallSessionRepository.class);
        AiAgentService agents = mock(AiAgentService.class);
        BusinessHourRepository hours = mock(BusinessHourRepository.class);
        BusinessScheduleExceptionRepository exceptions = mock(BusinessScheduleExceptionRepository.class);
        SchedulePolicyService schedulePolicy = mock(SchedulePolicyService.class);
        when(agents.runtime(businessId)).thenReturn(activeAgent(businessId));

        RealtimeToolService tools = new RealtimeToolService(
                businesses, customers, services, knowledge, bookings, calls,
                agents, hours, exceptions, schedulePolicy);
        return new Fixture(tools, customers, services, bookings, calls, agents);
    }

    private static AiAgent activeAgent(UUID businessId) {
        AiAgent agent = new AiAgent();
        agent.setBusinessId(businessId);
        agent.setName("Helvoca");
        agent.setLanguage("es");
        agent.setVoice("marin");
        agent.setGreeting("Hola");
        agent.setActive(true);
        agent.setCapabilities(EnumSet.allOf(AiCapability.class));
        return agent;
    }

    private record Fixture(
            RealtimeToolService tools,
            CustomerRepository customers,
            ServiceItemRepository services,
            BookingRepository bookings,
            CallSessionRepository calls,
            AiAgentService agents) {}
}

package cl.helvoca.ai.realtime;

import cl.helvoca.booking.BookingRepository;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.call.CallAction;
import cl.helvoca.call.CallActionRepository;
import cl.helvoca.call.CallSession;
import cl.helvoca.call.CallSessionRepository;
import cl.helvoca.call.CallTraceService;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.knowledge.KnowledgeItemRepository;
import cl.helvoca.learning.UnansweredQuestionService;
import cl.helvoca.request.BusinessRequestService;
import cl.helvoca.schedule.BusinessScheduleService;
import cl.helvoca.servicecatalog.ServiceItemRepository;
import cl.helvoca.telephony.twilio.TwilioCertificationCommandStore;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CertificationGuardedRealtimeToolServiceTest {

    @Test
    void latencyCertificationInstructionsEnterReadOnlyMode() {
        Fixture f = new Fixture();
        TwilioCertificationCommandStore commands = mock(TwilioCertificationCommandStore.class);
        f.call.setProviderCallId("CA0123456789abcdef0123456789abcdef");
        when(commands.isLatencyCertificationProviderCall(f.call.getProviderCallId())).thenReturn(true);
        f.service.setCertificationCommands(commands);

        Business business = new Business();
        business.setName("Negocio Demo");
        business.setTimezone("America/Santiago");
        business.setLanguage("es-CL");
        when(f.businesses.findById(f.businessId)).thenReturn(Optional.of(business));

        String instructions = f.service.buildInstructions(f.context);

        assertTrue(instructions.contains("MODO CERTIFICACIÓN DE LATENCIA READ-ONLY"));
        assertTrue(instructions.contains("Usa como máximo una herramienta de solo lectura"));
        assertTrue(instructions.contains("no intentes reservar")
                || instructions.contains("No intentes reservar"));
    }

    @Test
    void latencyCertificationExposesOnlyMinimalReadOnlyTools() {
        Fixture f = new Fixture();
        TwilioCertificationCommandStore commands = mock(TwilioCertificationCommandStore.class);
        f.call.setProviderCallId("CA0123456789abcdef0123456789abcdef");
        when(commands.isLatencyCertificationProviderCall(f.call.getProviderCallId())).thenReturn(true);
        f.service.setCertificationCommands(commands);

        JSONArray definitions = f.service.toolDefinitions(f.context);
        Set<String> names = new HashSet<>();
        for (int i = 0; i < definitions.length(); i++) {
            names.add(definitions.getJSONObject(i).getString("name"));
        }

        assertEquals(Set.of(
                "get_business_information",
                "list_services",
                "search_knowledge",
                "find_caller",
                "end_call"), names);
        assertFalse(names.contains("create_booking"));
        assertFalse(names.contains("reschedule_booking"));
        assertFalse(names.contains("cancel_booking"));
    }

    @Test
    void latencyCertificationBlocksHiddenMutationBeforeBackendExecution() {
        Fixture f = new Fixture();
        TwilioCertificationCommandStore commands = mock(TwilioCertificationCommandStore.class);
        f.call.setProviderCallId("CA0123456789abcdef0123456789abcdef");
        when(commands.isLatencyCertificationProviderCall(f.call.getProviderCallId())).thenReturn(true);
        f.service.setCertificationCommands(commands);

        JSONObject result = new JSONObject(f.service.execute(
                f.context,
                "create_booking",
                new JSONObject().put("serviceId", UUID.randomUUID().toString()).toString()));

        assertFalse(result.getBoolean("success"));
        assertEquals("LATENCY_CERTIFICATION_READ_ONLY",
                result.getJSONObject("error").getString("code"));
        verifyNoInteractions(f.bookings);
        verifyNoInteractions(f.jdbc);
        verify(f.trace).recordTool(eq(f.businessId), eq(f.callId), eq("create_booking"), any(JSONObject.class), anyLong());
    }

    @Test
    void latencyCertificationModeIsCachedPerCallAfterFirstLookup() {
        Fixture f = new Fixture();
        TwilioCertificationCommandStore commands = mock(TwilioCertificationCommandStore.class);
        f.call.setProviderCallId("CA0123456789abcdef0123456789abcdef");
        when(commands.isLatencyCertificationProviderCall(f.call.getProviderCallId())).thenReturn(true);
        f.service.setCertificationCommands(commands);

        f.service.toolDefinitions(f.context);
        f.service.toolDefinitions(f.context);

        verify(commands, times(1)).isLatencyCertificationProviderCall(f.call.getProviderCallId());
    }

    @Test
    void certificationCreateRequiresCustomerBeforeTouchingBookingBackend() {
        Fixture f = new Fixture();
        when(f.customers.findFirstByBusinessIdAndPhone(f.businessId, f.context.callerNumber()))
                .thenReturn(Optional.empty());

        JSONObject result = new JSONObject(f.service.execute(f.context, "create_booking", "{}"));

        assertEquals(false, result.getBoolean("success"));
        assertEquals("CERTIFICATION_CUSTOMER_REQUIRED", result.getJSONObject("error").getString("code"));
        verifyNoInteractions(f.bookings);
        verifyNoInteractions(f.jdbc);
        verify(f.trace).recordTool(eq(f.businessId), eq(f.callId), eq("create_booking"), any(JSONObject.class), anyLong());
    }

    @Test
    void certificationCreateRequiresCatalogThenAvailability() {
        Fixture f = new Fixture();
        f.call.setCustomerId(UUID.randomUUID());
        when(f.actions.findAllByCallIdOrderByCreatedAtAsc(f.callId))
                .thenReturn(List.of(success("SERVICES_LISTED")));

        JSONObject result = new JSONObject(f.service.execute(f.context, "create_booking", "{}"));

        assertEquals("CERTIFICATION_AVAILABILITY_REQUIRED", result.getJSONObject("error").getString("code"));
        verifyNoInteractions(f.bookings);
        verifyNoInteractions(f.jdbc);
    }

    @Test
    void certificationCreateRejectsAvailabilityThatPrecedesCatalog() {
        Fixture f = new Fixture();
        f.call.setCustomerId(UUID.randomUUID());
        when(f.actions.findAllByCallIdOrderByCreatedAtAsc(f.callId))
                .thenReturn(List.of(success("AVAILABILITY_CHECKED"), success("SERVICES_LISTED")));

        JSONObject result = new JSONObject(f.service.execute(f.context, "create_booking", "{}"));

        assertEquals("CERTIFICATION_AVAILABILITY_REQUIRED", result.getJSONObject("error").getString("code"));
        verifyNoInteractions(f.bookings);
        verifyNoInteractions(f.jdbc);
    }

    @Test
    void certificationCancelRequiresBookingCreatedSuccessfullyInSameCall() {
        Fixture f = new Fixture();
        UUID requested = UUID.randomUUID();
        CallAction otherBooking = success("BOOKING_CREATED");
        otherBooking.setEntityId(UUID.randomUUID());
        when(f.actions.findAllByCallIdOrderByCreatedAtAsc(f.callId)).thenReturn(List.of(otherBooking));

        JSONObject result = new JSONObject(f.service.execute(
                f.context,
                "cancel_booking",
                new JSONObject().put("bookingId", requested.toString()).toString()));

        assertEquals("CERTIFICATION_BOOKING_REQUIRED", result.getJSONObject("error").getString("code"));
        verifyNoInteractions(f.bookings);
    }

    private static CallAction success(String type) {
        CallAction action = new CallAction();
        action.setActionType(type);
        action.setSuccess(true);
        return action;
    }

    private static final class Fixture {
        final UUID callId = UUID.randomUUID();
        final UUID businessId = UUID.randomUUID();
        final RealtimeCallContext context = new RealtimeCallContext(
                callId, businessId, null, "+56911111111", "+14355652512", "MZ-test");
        final BusinessRepository businesses = mock(BusinessRepository.class);
        final CustomerRepository customers = mock(CustomerRepository.class);
        final ServiceItemRepository services = mock(ServiceItemRepository.class);
        final KnowledgeItemRepository knowledge = mock(KnowledgeItemRepository.class);
        final BookingRepository bookings = mock(BookingRepository.class);
        final CallSessionRepository calls = mock(CallSessionRepository.class);
        final BusinessScheduleService schedule = mock(BusinessScheduleService.class);
        final BusinessRequestService requests = mock(BusinessRequestService.class);
        final UnansweredQuestionService unanswered = mock(UnansweredQuestionService.class);
        final CallActionRepository actions = mock(CallActionRepository.class);
        final CallTraceService trace = mock(CallTraceService.class);
        final JdbcTemplate jdbc = mock(JdbcTemplate.class);
        final CallSession call = new CallSession();
        final CertificationGuardedRealtimeToolService service;

        Fixture() {
            call.setCertification(true);
            call.setBusinessId(businessId);
            call.setStreamSid(context.streamSid());
            when(calls.findByIdAndBusinessId(callId, businessId)).thenReturn(Optional.of(call));
            service = new CertificationGuardedRealtimeToolService(
                    businesses, customers, services, knowledge, bookings, calls, schedule, requests, unanswered,
                    actions, trace, jdbc);
        }
    }
}

package cl.helvoca.ai.realtime;

import cl.helvoca.booking.BookingConfirmationWorkflowService;
import cl.helvoca.booking.BookingRepository;
import cl.helvoca.booking.BookingSource;
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
import cl.helvoca.operations.BusinessOrder;
import cl.helvoca.request.BusinessRequestService;
import cl.helvoca.schedule.BusinessScheduleService;
import cl.helvoca.servicecatalog.ServiceItemRepository;
import cl.helvoca.telephony.twilio.TwilioCertificationCommandStore;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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

    @Test
    void primaryCreateBookingRejectsWeekdayMismatchBeforeCreatingTwoPhaseProposal() {
        Fixture f = new Fixture();
        f.call.setCertification(false);
        UUID customerId = UUID.randomUUID();
        f.call.setCustomerId(customerId);

        Business business = new Business();
        business.setName("Negocio");
        business.setTimezone("America/Santiago");
        business.setLanguage("es");
        when(f.businesses.findById(f.businessId)).thenReturn(Optional.of(business));

        BookingConfirmationWorkflowService workflow = mock(BookingConfirmationWorkflowService.class);
        setField(f.service, "bookingConfirmationWorkflow", workflow);
        JSONObject proposal = new JSONObject()
                .put("success", true)
                .put("data", new JSONObject()
                        .put("operationId", UUID.randomUUID().toString())
                        .put("confirmationToken", UUID.randomUUID().toString())
                        .put("requiresConfirmation", true)
                        .put("bookingCreated", false))
                .put("error", JSONObject.NULL);
        when(workflow.execute(
                eq(f.businessId), eq(customerId), eq(f.callId), eq(f.context.callerNumber()),
                eq(BusinessOrder.Source.VOICE), eq(BookingSource.AI_CALL), any(JSONObject.class)))
                .thenReturn(proposal);

        Instant startAt = ZonedDateTime.of(
                LocalDate.of(2026, 11, 13), LocalTime.of(10, 30), ZoneId.of("America/Santiago")).toInstant();
        JSONObject result = new JSONObject(f.service.execute(
                f.context,
                "create_booking",
                new JSONObject()
                        .put("serviceId", UUID.randomUUID().toString())
                        .put("startAt", startAt.toString())
                        .put("requestedWeekday", "THURSDAY")
                        .toString()));

        assertFalse(result.getBoolean("success"));
        assertEquals("WEEKDAY_DATE_MISMATCH", result.getJSONObject("error").getString("code"));
        verifyNoInteractions(workflow);
        verifyNoInteractions(f.bookings);
        verify(f.trace).recordTool(
                eq(f.businessId), eq(f.callId), eq("create_booking"), any(JSONObject.class), anyLong());
    }

    @Test
    void primaryCreateBookingKeepsTwoPhaseProposalForMatchingWeekday() {
        Fixture f = new Fixture();
        f.call.setCertification(false);
        UUID customerId = UUID.randomUUID();
        f.call.setCustomerId(customerId);

        Business business = new Business();
        business.setName("Negocio");
        business.setTimezone("America/Santiago");
        business.setLanguage("es");
        when(f.businesses.findById(f.businessId)).thenReturn(Optional.of(business));

        BookingConfirmationWorkflowService workflow = mock(BookingConfirmationWorkflowService.class);
        setField(f.service, "bookingConfirmationWorkflow", workflow);

        Instant startAt = ZonedDateTime.of(
                LocalDate.of(2026, 11, 13), LocalTime.of(10, 30), ZoneId.of("America/Santiago")).toInstant();
        UUID operationId = UUID.randomUUID();
        String token = UUID.randomUUID().toString();
        JSONObject proposal = new JSONObject()
                .put("success", true)
                .put("data", new JSONObject()
                        .put("operationId", operationId.toString())
                        .put("confirmationToken", token)
                        .put("requiresConfirmation", true)
                        .put("bookingCreated", false))
                .put("error", JSONObject.NULL);
        when(workflow.execute(
                eq(f.businessId), eq(customerId), eq(f.callId), eq(f.context.callerNumber()),
                eq(BusinessOrder.Source.VOICE), eq(BookingSource.AI_CALL), any(JSONObject.class)))
                .thenReturn(proposal);

        JSONObject result = new JSONObject(f.service.execute(
                f.context,
                "create_booking",
                new JSONObject()
                        .put("serviceId", UUID.randomUUID().toString())
                        .put("startAt", startAt.toString())
                        .put("requestedWeekday", "FRIDAY")
                        .toString()));

        assertTrue(result.getBoolean("success"));
        assertTrue(result.getJSONObject("data").getBoolean("requiresConfirmation"));
        assertFalse(result.getJSONObject("data").getBoolean("bookingCreated"));
        verify(workflow).execute(
                eq(f.businessId), eq(customerId), eq(f.callId), eq(f.context.callerNumber()),
                eq(BusinessOrder.Source.VOICE), eq(BookingSource.AI_CALL), any(JSONObject.class));
    }

    @Test
    void primaryCreateBookingPhaseTwoStillReachesWorkflowWithoutStartAtOrWeekday() {
        Fixture f = new Fixture();
        f.call.setCertification(false);
        UUID customerId = UUID.randomUUID();
        f.call.setCustomerId(customerId);

        BookingConfirmationWorkflowService workflow = mock(BookingConfirmationWorkflowService.class);
        setField(f.service, "bookingConfirmationWorkflow", workflow);
        JSONObject invalidConfirmation = new JSONObject()
                .put("success", false)
                .put("data", JSONObject.NULL)
                .put("error", new JSONObject()
                        .put("code", "INVALID_CONFIRMATION")
                        .put("message", "La confirmación requiere identificadores válidos."));
        when(workflow.execute(
                eq(f.businessId), eq(customerId), eq(f.callId), eq(f.context.callerNumber()),
                eq(BusinessOrder.Source.VOICE), eq(BookingSource.AI_CALL), any(JSONObject.class)))
                .thenReturn(invalidConfirmation);

        JSONObject result = new JSONObject(f.service.execute(
                f.context,
                "create_booking",
                new JSONObject()
                        .put("operationId", UUID.randomUUID().toString())
                        .put("confirmationToken", UUID.randomUUID().toString())
                        .toString()));

        assertFalse(result.getBoolean("success"));
        assertEquals("INVALID_CONFIRMATION", result.getJSONObject("error").getString("code"));
        verify(workflow).execute(
                eq(f.businessId), eq(customerId), eq(f.callId), eq(f.context.callerNumber()),
                eq(BusinessOrder.Source.VOICE), eq(BookingSource.AI_CALL), any(JSONObject.class));
    }

    @Test
    void primaryRescheduleRejectsWeekdayMismatchBeforeBookingLookupOrLock() {
        Fixture f = new Fixture();
        f.call.setCertification(false);

        Business business = new Business();
        business.setName("Negocio");
        business.setTimezone("America/Santiago");
        business.setLanguage("es");
        when(f.businesses.findById(f.businessId)).thenReturn(Optional.of(business));

        Instant newStartAt = ZonedDateTime.of(
                LocalDate.of(2026, 11, 13), LocalTime.of(10, 30), ZoneId.of("America/Santiago")).toInstant();
        JSONObject result = new JSONObject(f.service.execute(
                f.context,
                "reschedule_booking",
                new JSONObject()
                        .put("bookingId", UUID.randomUUID().toString())
                        .put("newStartAt", newStartAt.toString())
                        .put("requestedWeekday", "THURSDAY")
                        .toString()));

        assertFalse(result.getBoolean("success"));
        assertEquals("WEEKDAY_DATE_MISMATCH", result.getJSONObject("error").getString("code"));
        verifyNoInteractions(f.bookings);
        verifyNoInteractions(f.jdbc);
    }

    @Test
    void primaryRescheduleAllowsMatchingWeekdayToReachExistingHandler() {
        Fixture f = new Fixture();
        f.call.setCertification(false);
        UUID customerId = UUID.randomUUID();
        f.call.setCustomerId(customerId);

        Business business = new Business();
        business.setName("Negocio");
        business.setTimezone("America/Santiago");
        business.setLanguage("es");
        when(f.businesses.findById(f.businessId)).thenReturn(Optional.of(business));

        UUID bookingId = UUID.randomUUID();
        Instant newStartAt = ZonedDateTime.of(
                LocalDate.of(2026, 11, 13), LocalTime.of(10, 30), ZoneId.of("America/Santiago")).toInstant();
        JSONObject result = new JSONObject(f.service.execute(
                f.context,
                "reschedule_booking",
                new JSONObject()
                        .put("bookingId", bookingId.toString())
                        .put("newStartAt", newStartAt.toString())
                        .put("requestedWeekday", "FRIDAY")
                        .toString()));

        assertFalse(result.getBoolean("success"));
        assertEquals("BOOKING_NOT_FOUND", result.getJSONObject("error").getString("code"));
        verify(f.bookings).findByIdAndBusinessIdAndCustomerId(bookingId, f.businessId, customerId);
        verifyNoInteractions(f.jdbc);
    }

    @Test
    void primaryRescheduleKeepsDateOnlyRequestsBackwardCompatible() {
        Fixture f = new Fixture();
        f.call.setCertification(false);
        UUID customerId = UUID.randomUUID();
        f.call.setCustomerId(customerId);

        UUID bookingId = UUID.randomUUID();
        Instant newStartAt = ZonedDateTime.of(
                LocalDate.of(2026, 11, 13), LocalTime.of(10, 30), ZoneId.of("America/Santiago")).toInstant();
        JSONObject result = new JSONObject(f.service.execute(
                f.context,
                "reschedule_booking",
                new JSONObject()
                        .put("bookingId", bookingId.toString())
                        .put("newStartAt", newStartAt.toString())
                        .toString()));

        assertFalse(result.getBoolean("success"));
        assertEquals("BOOKING_NOT_FOUND", result.getJSONObject("error").getString("code"));
        verify(f.bookings).findByIdAndBusinessIdAndCustomerId(bookingId, f.businessId, customerId);
        verifyNoInteractions(f.jdbc);
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
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

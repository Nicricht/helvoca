package cl.helvoca.operations;

import cl.helvoca.agent.AiAgent;
import cl.helvoca.agent.AiAgentRepository;
import cl.helvoca.agent.AiCapability;
import cl.helvoca.ai.realtime.CertificationGuardedRealtimeToolService;
import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.booking.Booking;
import cl.helvoca.booking.BookingConfirmationWorkflowService;
import cl.helvoca.booking.BookingRepository;
import cl.helvoca.booking.BookingSource;
import cl.helvoca.booking.BookingStatus;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.call.CallAction;
import cl.helvoca.call.CallActionRepository;
import cl.helvoca.call.CallDirection;
import cl.helvoca.call.CallSession;
import cl.helvoca.call.CallSessionRepository;
import cl.helvoca.call.CallStatus;
import cl.helvoca.call.CallSummary;
import cl.helvoca.call.CallSummaryRepository;
import cl.helvoca.call.CallSummaryService;
import cl.helvoca.call.CallTranscript;
import cl.helvoca.call.CallTranscriptRepository;
import cl.helvoca.call.CallTranscriptService;
import cl.helvoca.customer.Customer;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.quality.ConversationQualityEngine;
import cl.helvoca.schedule.BusinessHour;
import cl.helvoca.schedule.BusinessHourRepository;
import cl.helvoca.servicecatalog.ServiceItem;
import cl.helvoca.servicecatalog.ServiceItemRepository;
import cl.helvoca.telephony.CallLifecycleService;
import cl.helvoca.telephony.twilio.TwilioCallControl;
import cl.helvoca.telephony.twilio.TwilioProperties;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers
@SpringBootTest(properties = {
        "app.seed.enabled=false",
        "app.outbound.delivery-enabled=false",
        "app.outbound.provider=NONE",
        "app.twilio.provisioning-enabled=false"
})
class GoldenJourneyCommercialV1IntegrationTest {
    private static final ZoneId ZONE = ZoneId.of("America/Santiago");
    private static final String CUSTOMER_PHONE = "+56911112222";
    private static final String DESTINATION_PHONE = "+56222223333";
    private static final String ACCOUNT_SID = "AC11111111111111111111111111111111";
    private static final String CALL_SID = "CA22222222222222222222222222222222";
    private static final String GREETING =
            "Hola, soy RecepVoz de Golden Journey Spa. Cuéntame, ¿en qué te puedo ayudar?";

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired BusinessRepository businesses;
    @Autowired ServiceItemRepository services;
    @Autowired BusinessHourRepository hours;
    @Autowired AiAgentRepository agents;
    @Autowired CallSessionRepository calls;
    @Autowired CustomerRepository customers;
    @Autowired BookingRepository bookings;
    @Autowired CallActionRepository actions;
    @Autowired CallTranscriptRepository transcripts;
    @Autowired CallTranscriptService transcriptWriter;
    @Autowired CallSummaryRepository summaries;
    @Autowired ConversationOperationStateRepository conversationStates;
    @Autowired BusinessOperationEventRepository operationEvents;
    @Autowired CertificationGuardedRealtimeToolService tools;
    @Autowired BookingConfirmationWorkflowService bookingWorkflow;
    @Autowired CallLifecycleService lifecycle;

    @Test
    void goldenJourneyBooksReschedulesConfirmsAndClosesWithoutExternalProviders() throws Exception {
        Business business = business("Golden Journey Spa");
        ServiceItem service = service(business.getId(), "Masaje relajante");
        LocalDate bookingDate = LocalDate.now(ZONE).plusDays(2);
        businessHours(business.getId(), bookingDate);
        agent(business.getId(), GREETING);

        CallSession call = call(business.getId(), CUSTOMER_PHONE, DESTINATION_PHONE, CALL_SID, "MZ-golden-journey");
        RealtimeCallContext context = context(call);
        List<ConversationQualityEngine.Turn> dialogue = new ArrayList<>();

        String greeting = tools.agentGreeting(context, "fallback");
        assertEquals(GREETING, greeting);
        say(dialogue, call.getId(), "ASSISTANT", greeting);

        JSONObject callerBefore = execute(context, "find_caller", new JSONObject());
        assertFalse(data(callerBefore).getBoolean("found"));

        say(dialogue, call.getId(), "USER", "Hola. Quiero saber un poco del negocio.");
        JSONObject businessInfo = execute(context, "get_business_information", new JSONObject());
        assertEquals("Golden Journey Spa", data(businessInfo).getString("name"));
        say(dialogue, call.getId(), "ASSISTANT",
                "Somos Golden Journey Spa y atendemos con reserva previa.");

        say(dialogue, call.getId(), "USER", "¿Qué servicio tienen?");
        JSONObject serviceList = execute(context, "list_services", new JSONObject());
        assertTrue(containsId(data(serviceList).getJSONArray("services"), "id", service.getId()));
        assertEquals(1, data(serviceList).getJSONArray("services").length());
        say(dialogue, call.getId(), "ASSISTANT",
                "Tenemos masaje relajante de 30 minutos.");

        say(dialogue, call.getId(), "USER", "¿Qué horas tienen disponibles?");
        JSONObject slotsResult = execute(context, "list_available_slots",
                new JSONObject()
                        .put("serviceId", service.getId().toString())
                        .put("date", bookingDate.toString()));
        JSONArray slots = data(slotsResult).getJSONArray("slots");
        assertTrue(slots.length() >= 2, "Golden Journey needs at least two slots to certify rescheduling");
        Instant firstStart = Instant.parse(slots.getJSONObject(0).getString("startAt"));
        Instant secondStart = Instant.parse(slots.getJSONObject(1).getString("startAt"));
        say(dialogue, call.getId(), "ASSISTANT",
                "Tengo dos horarios seguidos disponibles. ¿Te sirve el primero?");

        say(dialogue, call.getId(), "USER", "Sí, reserva el primero.");
        JSONObject availability = execute(context, "check_booking_availability",
                new JSONObject()
                        .put("serviceId", service.getId().toString())
                        .put("startAt", firstStart.toString()));
        assertTrue(data(availability).getBoolean("available"));
        assertEquals("CUSTOMER_IDENTITY",
                availability.getJSONObject("conversationState").getString("nextRequiredField"));

        say(dialogue, call.getId(), "ASSISTANT", "Perfecto. ¿A nombre de quién hago la reserva?");
        say(dialogue, call.getId(), "USER", "Camila Soto, y este es mi número.");
        JSONObject registered = execute(context, "register_caller",
                new JSONObject().put("name", "Camila Soto"));
        UUID customerId = UUID.fromString(data(registered).getString("customerId"));
        assertTrue(customers.findByIdAndBusinessId(customerId, business.getId()).isPresent());
        assertEquals(customerId, calls.findByIdAndBusinessId(call.getId(), business.getId()).orElseThrow().getCustomerId());

        JSONObject readyState = registered.getJSONObject("conversationState");
        assertEquals("NONE", readyState.getString("nextRequiredField"));
        assertEquals("CREATE_BOOKING_PROPOSAL", readyState.getString("nextAction"));
        assertClosed(readyState, "service", "date", "time", "name", "phone");

        JSONObject proposal = execute(context, "create_booking",
                new JSONObject()
                        .put("serviceId", service.getId().toString())
                        .put("startAt", firstStart.toString()));
        JSONObject proposalData = data(proposal);
        assertTrue(proposalData.getBoolean("requiresConfirmation"));
        assertFalse(proposalData.getBoolean("bookingCreated"));
        assertEquals(0, bookings.findAllByBusinessIdOrderByStartAtDesc(business.getId()).size(),
                "Phase 1 must never create a booking");

        JSONObject proposalState = proposal.getJSONObject("conversationState");
        assertEquals("CONFIRMATION", proposalState.getString("nextRequiredField"));
        assertEquals("ASK_CONFIRMATION_ONCE", proposalState.getString("nextAction"));
        assertClosed(proposalState, "service", "date", "time", "name", "phone");

        say(dialogue, call.getId(), "ASSISTANT",
                "Masaje relajante en ese horario. ¿Confirmo la reserva?");
        say(dialogue, call.getId(), "USER", "Sí.");

        UUID operationId = UUID.fromString(proposalData.getString("operationId"));
        String confirmationToken = proposalData.getString("confirmationToken");
        JSONObject confirmed = execute(context, "create_booking",
                new JSONObject()
                        .put("operationId", operationId.toString())
                        .put("confirmationToken", confirmationToken));
        JSONObject confirmedData = data(confirmed);
        UUID bookingId = UUID.fromString(confirmedData.getString("bookingId"));
        assertFalse(confirmedData.getBoolean("idempotentReplay"));
        assertEquals("BOOKING_COMPLETE",
                confirmed.getJSONObject("conversationState").getString("nextAction"));
        assertClosed(confirmed.getJSONObject("conversationState"),
                "service", "date", "time", "name", "phone", "confirmation");

        Booking created = bookings.findByIdAndBusinessIdAndCustomerId(
                bookingId, business.getId(), customerId).orElseThrow();
        assertEquals(BookingStatus.CONFIRMED, created.getStatus());
        assertEquals(firstStart, created.getStartAt());
        assertEquals(1, bookings.findAllByBusinessIdOrderByStartAtDesc(business.getId()).size());

        JSONObject replay = bookingWorkflow.execute(
                business.getId(),
                customerId,
                call.getId(),
                CUSTOMER_PHONE,
                BusinessOrder.Source.VOICE,
                BookingSource.AI_CALL,
                new JSONObject()
                        .put("operationId", operationId.toString())
                        .put("confirmationToken", confirmationToken));
        assertTrue(replay.getBoolean("success"), replay::toString);
        assertTrue(data(replay).getBoolean("idempotentReplay"));
        assertEquals(bookingId.toString(), data(replay).getString("bookingId"));
        assertEquals(1, bookings.findAllByBusinessIdOrderByStartAtDesc(business.getId()).size(),
                "Idempotent confirmation replay must not create a duplicate booking");

        say(dialogue, call.getId(), "ASSISTANT", "Listo, quedó reservada.");
        say(dialogue, call.getId(), "USER", "Mejor cámbiala al segundo horario.");

        JSONObject rescheduled = execute(context, "reschedule_booking",
                new JSONObject()
                        .put("bookingId", bookingId.toString())
                        .put("newStartAt", secondStart.toString()));
        assertEquals(bookingId.toString(), data(rescheduled).getString("bookingId"));

        Booking moved = bookings.findByIdAndBusinessIdAndCustomerId(
                bookingId, business.getId(), customerId).orElseThrow();
        assertEquals(secondStart, moved.getStartAt());
        assertEquals(BookingStatus.CONFIRMED, moved.getStatus());
        assertEquals(1, bookings.findAllByBusinessIdOrderByStartAtDesc(business.getId()).size(),
                "Rescheduling must update the existing booking instead of creating a second one");
        say(dialogue, call.getId(), "ASSISTANT", "Listo, la reprogramé al segundo horario.");

        say(dialogue, call.getId(), "USER", "¿Entonces quedó confirmada en ese horario?");
        JSONObject listed = execute(context, "list_customer_bookings", new JSONObject());
        JSONArray customerBookings = data(listed).getJSONArray("bookings");
        assertEquals(1, customerBookings.length());
        assertEquals(bookingId.toString(), customerBookings.getJSONObject(0).getString("bookingId"));
        assertEquals(secondStart.toString(), customerBookings.getJSONObject(0).getString("startAt"));
        say(dialogue, call.getId(), "ASSISTANT", "Sí, quedó confirmada en el nuevo horario.");

        ConversationOperationState persistedConversation = conversationStates
                .findByBusinessIdAndChannelAndSourceReferenceId(
                        business.getId(), BusinessOrder.Source.VOICE, call.getId())
                .orElseThrow();
        assertEquals(bookingId.toString(), String.valueOf(persistedConversation.getState().get("bookingId")));
        assertEquals(secondStart.toString(), String.valueOf(persistedConversation.getState().get("startAt")));

        certifyTenantIsolation(bookingId, secondStart, call.getId());

        say(dialogue, call.getId(), "USER", "Perfecto, eso sería todo. Chao.");
        say(dialogue, call.getId(), "ASSISTANT", "Chao, que estés muy bien.");

        JSONObject endResult = new JSONObject(tools.prepareDeferredEndCall(context));
        assertTrue(endResult.getBoolean("success"), endResult::toString);
        assertTrue(data(endResult).getBoolean("pendingPlaybackCompletion"));
        assertFalse(data(endResult).getBoolean("ended"),
                "Certification must not contact the real carrier before playback completes");

        // Simulates the carrier's terminal callback after the single farewell has
        // finished playing. No external telephony request is made by this test.
        lifecycle.updateStatus(call.getId(), "completed", 42);
        lifecycle.markStreamStopped(call.getStreamSid());
        CallSession closed = calls.findByIdAndBusinessId(call.getId(), business.getId()).orElseThrow();
        assertEquals(CallStatus.COMPLETED, closed.getStatus());
        assertNotNull(closed.getEndedAt());
        assertNotNull(closed.getStreamEndedAt());

        List<CallAction> trace = actions.findAllByBusinessIdAndCallIdOrderByCreatedAtAsc(
                business.getId(), call.getId());
        assertActionCount(trace, "BUSINESS_INFORMATION", 1);
        assertActionCount(trace, "SERVICES_LISTED", 1);
        assertActionCount(trace, "CALLER_LOOKUP", 1);
        assertActionCount(trace, "CUSTOMER_REGISTERED", 1);
        assertActionCount(trace, "AVAILABILITY_LISTED", 1);
        assertActionCount(trace, "AVAILABILITY_CHECKED", 1);
        assertActionCount(trace, "BOOKING_PROPOSED", 1);
        assertActionCount(trace, "BOOKING_CREATED", 1);
        assertActionCount(trace, "BOOKING_RESCHEDULED", 1);
        assertActionCount(trace, "BOOKINGS_LISTED", 1);
        assertActionCount(trace, "CALL_ENDED", 1);

        List<ConversationQualityEngine.Action> qualityActions = trace.stream()
                .map(action -> new ConversationQualityEngine.Action(
                        action.getActionType(),
                        action.isSuccess(),
                        action.getEntityType(),
                        action.getEntityId(),
                        action.getDetail()))
                .toList();
        ConversationQualityEngine.Report quality =
                new ConversationQualityEngine().evaluate(dialogue, qualityActions);
        assertTrue(quality.passed(), () -> "Golden conversation quality failed: " + quality.findings());

        List<CallTranscript> persistedTranscript =
                transcripts.findAllByCallIdOrderBySequenceNumberAsc(call.getId());
        assertEquals(dialogue.size(), persistedTranscript.size());
        long farewellTurns = persistedTranscript.stream()
                .filter(item -> "ASSISTANT".equalsIgnoreCase(item.getSpeaker()))
                .filter(item -> item.getContent().toLowerCase().contains("chao"))
                .count();
        assertEquals(1, farewellTurns, "RecepVoz must say farewell exactly once");

        List<BusinessOperationEvent> events =
                operationEvents.findTop100ByBusinessIdAndOperationIdOrderBySequenceNoDesc(
                        business.getId(), operationId);
        assertFalse(events.isEmpty(), "Golden booking must leave durable business-operation events");
        assertTrue(events.stream().allMatch(event -> business.getId().equals(event.getBusinessId())));
        assertTrue(events.stream().anyMatch(event -> event.getStatus() == BusinessOperation.Status.CONFIRMED));

        new CallSummaryService(transcripts, summaries, actions).generate(call.getId());
        CallSummary summary = summaries.findByCallId(call.getId()).orElseThrow();
        assertTrue(summary.getSummary().contains("reserva creada"));
        assertTrue(summary.getSummary().contains("reserva reprogramada"));

        assertEquals(1, bookings.findAllByBusinessIdOrderByStartAtDesc(business.getId()).size());
        assertEquals("BOOKING_RESCHEDULED",
                calls.findByIdAndBusinessId(call.getId(), business.getId()).orElseThrow().getResolution());
    }

    @Test
    void goldenJourneyRecoversWhenSlotIsTakenBeforeConfirmationWithoutDuplicates() {
        Business business = business("Golden Journey Recovery Spa");
        ServiceItem service = service(business.getId(), "Masaje recuperación");
        LocalDate bookingDate = LocalDate.now(ZONE).plusDays(3);
        businessHours(business.getId(), bookingDate);
        agent(business.getId(), "Hola, soy RecepVoz de Golden Journey Recovery Spa.");

        CallSession call = call(
                business.getId(),
                "+56955556666",
                "+56266667777",
                "CA44444444444444444444444444444444",
                "MZ-golden-recovery");
        RealtimeCallContext context = context(call);

        JSONObject slotsResult = execute(context, "list_available_slots",
                new JSONObject()
                        .put("serviceId", service.getId().toString())
                        .put("date", bookingDate.toString()));
        JSONArray slots = data(slotsResult).getJSONArray("slots");
        assertTrue(slots.length() >= 2,
                "Recovery certification needs a fallback slot after the first one is taken");
        Instant firstStart = Instant.parse(slots.getJSONObject(0).getString("startAt"));
        Instant secondStart = Instant.parse(slots.getJSONObject(1).getString("startAt"));

        JSONObject initialAvailability = execute(context, "check_booking_availability",
                new JSONObject()
                        .put("serviceId", service.getId().toString())
                        .put("startAt", firstStart.toString()));
        assertTrue(data(initialAvailability).getBoolean("available"));

        JSONObject registered = execute(context, "register_caller",
                new JSONObject().put("name", "Daniela Recovery"));
        UUID customerId = UUID.fromString(data(registered).getString("customerId"));

        JSONObject firstProposal = execute(context, "create_booking",
                new JSONObject()
                        .put("serviceId", service.getId().toString())
                        .put("startAt", firstStart.toString()));
        JSONObject firstProposalData = data(firstProposal);
        UUID firstOperationId = UUID.fromString(firstProposalData.getString("operationId"));
        String firstConfirmationToken = firstProposalData.getString("confirmationToken");
        assertTrue(firstProposalData.getBoolean("requiresConfirmation"));
        assertEquals(0, data(execute(context, "list_customer_bookings", new JSONObject()))
                        .getJSONArray("bookings").length(),
                "A proposal must not persist a customer booking before confirmation");

        Customer competingCustomer = new Customer();
        competingCustomer.setBusinessId(business.getId());
        competingCustomer.setName("Cliente Concurrente");
        competingCustomer.setPhone("+56977778888");
        competingCustomer = customers.saveAndFlush(competingCustomer);

        Booking competingBooking = new Booking();
        competingBooking.setBusinessId(business.getId());
        competingBooking.setCustomerId(competingCustomer.getId());
        competingBooking.setServiceId(service.getId());
        competingBooking.setStartAt(firstStart);
        competingBooking.setEndAt(firstStart.plusSeconds(service.getDurationMinutes() * 60L));
        competingBooking.setStatus(BookingStatus.CONFIRMED);
        competingBooking.setSource(BookingSource.ADMIN);
        bookings.saveAndFlush(competingBooking);

        JSONObject racedAvailability = execute(context, "check_booking_availability",
                new JSONObject()
                        .put("serviceId", service.getId().toString())
                        .put("startAt", firstStart.toString()));
        assertFalse(data(racedAvailability).getBoolean("available"));
        assertEquals("SLOT_OCCUPIED",
                data(racedAvailability).getString("unavailabilityReasonCode"));

        JSONObject rejectedConfirmation = rawExecute(context, "create_booking",
                new JSONObject()
                        .put("operationId", firstOperationId.toString())
                        .put("confirmationToken", firstConfirmationToken));
        assertFalse(rejectedConfirmation.getBoolean("success"),
                "The stale proposal must not win after another booking occupies the slot");
        assertEquals("BOOKING_SLOT_UNAVAILABLE",
                rejectedConfirmation.getJSONObject("error").getString("code"));
        assertEquals(0, data(execute(context, "list_customer_bookings", new JSONObject()))
                        .getJSONArray("bookings").length(),
                "The failed confirmation must not create a hidden or duplicate customer booking");
        assertEquals(1, bookings.findAllByBusinessIdOrderByStartAtDesc(business.getId()).size(),
                "Only the competing booking should exist after the rejected confirmation");

        JSONObject repeatedRejectedConfirmation = rawExecute(context, "create_booking",
                new JSONObject()
                        .put("operationId", firstOperationId.toString())
                        .put("confirmationToken", firstConfirmationToken));
        assertFalse(repeatedRejectedConfirmation.getBoolean("success"),
                "Repeating an invalidated confirmation must stay rejected");
        assertEquals(1, bookings.findAllByBusinessIdOrderByStartAtDesc(business.getId()).size(),
                "Retrying the rejected confirmation must not create a booking");

        JSONObject fallbackAvailability = execute(context, "check_booking_availability",
                new JSONObject()
                        .put("serviceId", service.getId().toString())
                        .put("startAt", secondStart.toString()));
        assertTrue(data(fallbackAvailability).getBoolean("available"));

        JSONObject fallbackProposal = execute(context, "create_booking",
                new JSONObject()
                        .put("serviceId", service.getId().toString())
                        .put("startAt", secondStart.toString()));
        JSONObject fallbackProposalData = data(fallbackProposal);
        UUID fallbackOperationId = UUID.fromString(fallbackProposalData.getString("operationId"));
        String fallbackConfirmationToken = fallbackProposalData.getString("confirmationToken");
        assertNotEquals(firstOperationId, fallbackOperationId,
                "Recovery must use a fresh durable operation after the original proposal expires");

        JSONObject recovered = execute(context, "create_booking",
                new JSONObject()
                        .put("operationId", fallbackOperationId.toString())
                        .put("confirmationToken", fallbackConfirmationToken));
        UUID recoveredBookingId = UUID.fromString(data(recovered).getString("bookingId"));
        assertFalse(data(recovered).getBoolean("idempotentReplay"));

        Booking recoveredBooking = bookings.findByIdAndBusinessIdAndCustomerId(
                recoveredBookingId, business.getId(), customerId).orElseThrow();
        assertEquals(secondStart, recoveredBooking.getStartAt());
        assertEquals(BookingStatus.CONFIRMED, recoveredBooking.getStatus());
        assertEquals(2, bookings.findAllByBusinessIdOrderByStartAtDesc(business.getId()).size(),
                "Exactly the competitor plus the recovered customer booking should exist");

        JSONObject replay = bookingWorkflow.execute(
                business.getId(),
                customerId,
                call.getId(),
                call.getCallerNumber(),
                BusinessOrder.Source.VOICE,
                BookingSource.AI_CALL,
                new JSONObject()
                        .put("operationId", fallbackOperationId.toString())
                        .put("confirmationToken", fallbackConfirmationToken));
        assertTrue(replay.getBoolean("success"), replay::toString);
        assertTrue(data(replay).getBoolean("idempotentReplay"),
                "A transport retry after recovery must replay the already confirmed booking");
        assertEquals(recoveredBookingId.toString(), data(replay).getString("bookingId"));
        assertEquals(2, bookings.findAllByBusinessIdOrderByStartAtDesc(business.getId()).size(),
                "Idempotent replay after recovery must not create a third booking");

        JSONArray customerBookings = data(execute(context, "list_customer_bookings", new JSONObject()))
                .getJSONArray("bookings");
        assertEquals(1, customerBookings.length());
        assertEquals(recoveredBookingId.toString(), customerBookings.getJSONObject(0).getString("bookingId"));
        assertEquals(secondStart.toString(), customerBookings.getJSONObject(0).getString("startAt"));

        List<CallAction> trace = actions.findAllByBusinessIdAndCallIdOrderByCreatedAtAsc(
                business.getId(), call.getId());
        assertActionCount(trace, "BOOKING_PROPOSED", 2);
        assertActionCount(trace, "BOOKING_CREATED", 1);
    }

    private void certifyTenantIsolation(UUID bookingId, Instant expectedStart, UUID primaryCallId) {
        Business otherBusiness = business("Other Tenant");
        service(otherBusiness.getId(), "Servicio de otro tenant");
        agent(otherBusiness.getId(), "Hola desde otro tenant.");

        Customer otherCustomer = new Customer();
        otherCustomer.setBusinessId(otherBusiness.getId());
        otherCustomer.setName("Cliente Otro");
        otherCustomer.setPhone("+56933334444");
        otherCustomer = customers.saveAndFlush(otherCustomer);

        CallSession otherCall = call(
                otherBusiness.getId(),
                otherCustomer.getPhone(),
                "+56244445555",
                "CA33333333333333333333333333333333",
                "MZ-other-tenant");
        otherCall.setCustomerId(otherCustomer.getId());
        otherCall = calls.saveAndFlush(otherCall);

        RealtimeCallContext otherContext = context(otherCall);
        JSONObject crossTenant = rawExecute(otherContext, "reschedule_booking",
                new JSONObject()
                        .put("bookingId", bookingId.toString())
                        .put("newStartAt", expectedStart.plusSeconds(3600).toString()));

        assertFalse(crossTenant.getBoolean("success"));
        assertEquals("BOOKING_NOT_FOUND", crossTenant.getJSONObject("error").getString("code"));
        assertTrue(bookings.findByIdAndBusinessId(bookingId, otherBusiness.getId()).isEmpty());
        assertEquals(expectedStart, bookings.findById(bookingId).orElseThrow().getStartAt());
        assertTrue(conversationStates.findByBusinessIdAndChannelAndSourceReferenceId(
                otherBusiness.getId(), BusinessOrder.Source.VOICE, primaryCallId).isEmpty());
    }

    private Business business(String name) {
        Business business = new Business();
        business.setName(name);
        business.setTimezone(ZONE.getId());
        business.setLanguage("es");
        return businesses.saveAndFlush(business);
    }

    private ServiceItem service(UUID businessId, String name) {
        ServiceItem service = new ServiceItem();
        service.setBusinessId(businessId);
        service.setName(name);
        service.setDescription("Servicio de certificación Golden Journey");
        service.setDurationMinutes(30);
        service.setPrice(new BigDecimal("25000.00"));
        service.setActive(true);
        return services.saveAndFlush(service);
    }

    private void businessHours(UUID businessId, LocalDate date) {
        BusinessHour hour = new BusinessHour();
        hour.setBusinessId(businessId);
        hour.setDayOfWeek(date.getDayOfWeek().getValue());
        hour.setOpenTime(LocalTime.of(9, 0));
        hour.setCloseTime(LocalTime.of(18, 0));
        hours.saveAndFlush(hour);
    }

    private AiAgent agent(UUID businessId, String greeting) {
        AiAgent agent = new AiAgent();
        agent.setBusinessId(businessId);
        agent.setName("RecepVoz");
        agent.setLanguage("es");
        agent.setGreeting(greeting);
        agent.setActive(true);
        agent.setCapabilities(AiCapability.legacyDefaults());
        return agents.saveAndFlush(agent);
    }

    private CallSession call(UUID businessId,
                             String caller,
                             String destination,
                             String providerCallId,
                             String streamSid) {
        CallSession call = new CallSession();
        call.setBusinessId(businessId);
        call.setTelephonyProvider("twilio");
        call.setAiProvider("certification");
        call.setProviderCallId(providerCallId);
        call.setCallerNumber(caller);
        call.setDestinationNumber(destination);
        call.setDirection(CallDirection.INBOUND);
        call.setStatus(CallStatus.IN_PROGRESS);
        call.setStartedAt(Instant.now());
        call.setAnsweredAt(Instant.now());
        call.setStreamSid(streamSid);
        call.setStreamStartedAt(Instant.now());
        call.setCertification(true);
        return calls.saveAndFlush(call);
    }

    private static RealtimeCallContext context(CallSession call) {
        return new RealtimeCallContext(
                call.getId(),
                call.getBusinessId(),
                call.getCustomerId(),
                call.getCallerNumber(),
                call.getDestinationNumber(),
                call.getStreamSid());
    }

    private JSONObject execute(RealtimeCallContext context, String toolName, JSONObject args) {
        JSONObject result = rawExecute(context, toolName, args);
        if (!result.optBoolean("success", false)) {
            fail(toolName + " failed unexpectedly: " + result);
        }
        return result;
    }

    private JSONObject rawExecute(RealtimeCallContext context, String toolName, JSONObject args) {
        return new JSONObject(tools.execute(context, toolName, args.toString()));
    }

    private void say(List<ConversationQualityEngine.Turn> dialogue,
                     UUID callId,
                     String speaker,
                     String text) {
        dialogue.add(new ConversationQualityEngine.Turn(speaker, text));
        transcriptWriter.append(callId, speaker, text);
    }

    private static JSONObject data(JSONObject root) {
        JSONObject data = root.optJSONObject("data");
        assertNotNull(data, () -> "Missing data payload: " + root);
        return data;
    }

    private static boolean containsId(JSONArray values, String field, UUID id) {
        String expected = id.toString();
        for (int i = 0; i < values.length(); i++) {
            JSONObject value = values.optJSONObject(i);
            if (value != null && expected.equals(value.optString(field))) return true;
        }
        return false;
    }

    private static void assertClosed(JSONObject state, String... fields) {
        Set<Object> closed = state.getJSONArray("closedFields").toList().stream().collect(Collectors.toSet());
        for (String field : fields) {
            assertTrue(closed.contains(field), () -> "Expected closed field " + field + " in " + closed);
        }
    }

    private static void assertActionCount(List<CallAction> trace, String type, long expected) {
        long actual = trace.stream().filter(action -> type.equals(action.getActionType())).count();
        assertEquals(expected, actual, () -> "Unexpected tool/action count for " + type + ": " + trace);
    }

}

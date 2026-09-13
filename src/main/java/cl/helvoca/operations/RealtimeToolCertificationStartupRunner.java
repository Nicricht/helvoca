package cl.helvoca.operations;

import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.ai.realtime.RealtimeToolService;
import cl.helvoca.booking.Booking;
import cl.helvoca.booking.BookingRepository;
import cl.helvoca.booking.BookingStatus;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.call.CallActionRepository;
import cl.helvoca.call.CallDirection;
import cl.helvoca.call.CallSession;
import cl.helvoca.call.CallSessionRepository;
import cl.helvoca.call.CallStatus;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.knowledge.KnowledgeItem;
import cl.helvoca.knowledge.KnowledgeItemRepository;
import cl.helvoca.learning.UnansweredQuestionRepository;
import cl.helvoca.request.BusinessRequestRepository;
import cl.helvoca.schedule.BusinessHour;
import cl.helvoca.schedule.BusinessHourRepository;
import cl.helvoca.servicecatalog.ServiceItem;
import cl.helvoca.servicecatalog.ServiceItemRepository;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Opt-in production smoke test for the real receptionist tools and PostgreSQL.
 *
 * The scenario writes through the real repositories and RealtimeToolService,
 * flushes those writes to PostgreSQL, verifies the persisted state, and then
 * rolls the whole transaction back. No customer/business test data is left
 * behind and no telephony or external AI request is made.
 */
@Component
public class RealtimeToolCertificationStartupRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(RealtimeToolCertificationStartupRunner.class);
    private static final AtomicBoolean FIRED = new AtomicBoolean(false);
    private static final int START_DELAY_SECONDS = 5;
    private static final ZoneId TEST_ZONE = ZoneId.of("America/Santiago");
    private static final String TEST_CALLER = "+56900000001";
    private static final String TEST_DESTINATION = "+56200000001";
    private static final String TEST_TRANSFER = "+56900000002";

    private final boolean enabled;
    private final PlatformTransactionManager transactionManager;
    private final BusinessRepository businesses;
    private final ServiceItemRepository services;
    private final BusinessHourRepository hours;
    private final KnowledgeItemRepository knowledge;
    private final CallSessionRepository calls;
    private final CustomerRepository customers;
    private final BookingRepository bookings;
    private final BusinessRequestRepository requests;
    private final UnansweredQuestionRepository questions;
    private final CallActionRepository actions;
    private final RealtimeToolService tools;

    public RealtimeToolCertificationStartupRunner(
            @Value("${RECEPVOZ_TOOL_CERTIFICATION_ON_STARTUP:false}") boolean enabled,
            PlatformTransactionManager transactionManager,
            BusinessRepository businesses,
            ServiceItemRepository services,
            BusinessHourRepository hours,
            KnowledgeItemRepository knowledge,
            CallSessionRepository calls,
            CustomerRepository customers,
            BookingRepository bookings,
            BusinessRequestRepository requests,
            UnansweredQuestionRepository questions,
            CallActionRepository actions,
            RealtimeToolService tools) {
        this.enabled = enabled;
        this.transactionManager = transactionManager;
        this.businesses = businesses;
        this.services = services;
        this.hours = hours;
        this.knowledge = knowledge;
        this.calls = calls;
        this.customers = customers;
        this.bookings = bookings;
        this.requests = requests;
        this.questions = questions;
        this.actions = actions;
        this.tools = tools;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled || !FIRED.compareAndSet(false, true)) return;

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "recepvoz-tool-certification");
            thread.setDaemon(true);
            return thread;
        });
        log.info("RECEPVOZ_TOOL_CERTIFICATION armed; starting in {} seconds", START_DELAY_SECONDS);
        executor.schedule(() -> {
            try {
                CertificationReport report = certifyAgainstPostgres();
                log.info("RECEPVOZ_TOOL_CERTIFICATION SUCCESS checks={} tools={} actions={} rollback=true",
                        report.checks(), String.join(",", report.tools()), report.actionCount());
            } catch (Exception e) {
                log.error("RECEPVOZ_TOOL_CERTIFICATION FAILED reason={}", rootMessage(e));
            } finally {
                executor.shutdown();
            }
        }, START_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    private CertificationReport certifyAgainstPostgres() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        CertificationReport report = tx.execute(status -> {
            try {
                return runScenario();
            } finally {
                status.setRollbackOnly();
            }
        });
        if (report == null) throw new IllegalStateException("certification transaction returned no report");
        return report;
    }

    private CertificationReport runScenario() {
        List<String> passedTools = new ArrayList<>();
        int checks = 0;

        Business business = new Business();
        business.setName("Helvoca Tool Certification");
        business.setTimezone(TEST_ZONE.getId());
        business.setLanguage("es");
        business.setHumanTransferPhone(TEST_TRANSFER);
        business = businesses.saveAndFlush(business);
        UUID businessId = business.getId();
        require(businessId != null, "business was not persisted");
        checks++;

        ServiceItem service = new ServiceItem();
        service.setBusinessId(businessId);
        service.setName("Consulta certificación");
        service.setDescription("Servicio temporal de certificación transaccional");
        service.setDurationMinutes(30);
        service.setPrice(new BigDecimal("1000.00"));
        service.setActive(true);
        service = services.saveAndFlush(service);
        UUID serviceId = service.getId();
        require(serviceId != null, "service was not persisted");
        checks++;

        LocalDate testDate = LocalDate.now(TEST_ZONE).plusDays(1);
        BusinessHour hour = new BusinessHour();
        hour.setBusinessId(businessId);
        hour.setDayOfWeek(testDate.getDayOfWeek().getValue());
        hour.setOpenTime(LocalTime.of(9, 0));
        hour.setCloseTime(LocalTime.of(18, 0));
        hours.saveAndFlush(hour);
        require(hours.countByBusinessId(businessId) == 1L, "business hours were not persisted");
        checks++;

        KnowledgeItem item = new KnowledgeItem();
        item.setBusinessId(businessId);
        item.setTitle("Estacionamiento certificación");
        item.setCategory("faq");
        item.setContent("La certificación dispone de estacionamiento de prueba.");
        item.setActive(true);
        knowledge.saveAndFlush(item);
        require(item.getId() != null, "knowledge item was not persisted");
        checks++;

        String token = UUID.randomUUID().toString();
        CallSession call = new CallSession();
        call.setBusinessId(businessId);
        call.setTelephonyProvider("certification");
        call.setAiProvider("certification");
        call.setProviderCallId("certification:" + token);
        call.setCallerNumber(TEST_CALLER);
        call.setDestinationNumber(TEST_DESTINATION);
        call.setDirection(CallDirection.INBOUND);
        call.setStatus(CallStatus.IN_PROGRESS);
        call.setStartedAt(Instant.now());
        call.setAnsweredAt(Instant.now());
        call.setStreamSid("certification-stream:" + token);
        call.setStreamStartedAt(Instant.now());
        call = calls.saveAndFlush(call);
        UUID callId = call.getId();
        require(callId != null, "call session was not persisted");
        checks++;

        RealtimeCallContext context = new RealtimeCallContext(
                callId, businessId, null, TEST_CALLER, TEST_DESTINATION, call.getStreamSid());

        JSONObject businessInfo = executeSuccess(context, "get_business_information", new JSONObject(), passedTools);
        require("Helvoca Tool Certification".equals(businessInfo.getString("name")), "business information mismatch");
        checks++;

        JSONObject serviceList = executeSuccess(context, "list_services", new JSONObject(), passedTools);
        require(containsId(serviceList.getJSONArray("services"), "id", serviceId), "service missing from list_services");
        checks++;

        JSONObject knowledgeResult = executeSuccess(context, "search_knowledge",
                new JSONObject().put("query", "estacionamiento"), passedTools);
        require(!knowledgeResult.getJSONArray("results").isEmpty(), "knowledge search returned no result");
        checks++;

        JSONObject callerBefore = executeSuccess(context, "find_caller", new JSONObject(), passedTools);
        require(!callerBefore.getBoolean("found"), "synthetic caller unexpectedly existed before registration");
        checks++;

        JSONObject registered = executeSuccess(context, "register_caller",
                new JSONObject().put("name", "Cliente Certificación").put("email", "certificacion@example.invalid"),
                passedTools);
        UUID customerId = UUID.fromString(registered.getString("customerId"));
        require(customers.findByIdAndBusinessId(customerId, businessId).isPresent(), "registered customer was not persisted");
        require(calls.findByIdAndBusinessId(callId, businessId).orElseThrow().getCustomerId().equals(customerId),
                "call was not linked to registered customer");
        checks += 2;

        JSONObject callerAfter = executeSuccess(context, "find_caller", new JSONObject(), passedTools);
        require(callerAfter.getBoolean("found"), "registered caller was not found");
        require(customerId.toString().equals(callerAfter.getString("id")), "find_caller returned wrong customer");
        checks += 2;

        JSONObject slots = executeSuccess(context, "list_available_slots",
                new JSONObject().put("serviceId", serviceId.toString()).put("date", testDate.toString()),
                passedTools);
        require(slots.getBoolean("scheduleConfigured"), "schedule was not recognized");
        JSONArray available = slots.getJSONArray("slots");
        require(available.length() >= 2, "expected at least two available slots");
        Instant firstStart = Instant.parse(available.getJSONObject(0).getString("startAt"));
        Instant secondStart = Instant.parse(available.getJSONObject(1).getString("startAt"));
        checks += 2;

        JSONObject availability = executeSuccess(context, "check_booking_availability",
                new JSONObject().put("serviceId", serviceId.toString()).put("startAt", firstStart.toString()),
                passedTools);
        require(availability.getBoolean("available"), "first slot was not available");
        require(availability.getBoolean("withinBusinessHours"), "first slot was outside business hours");
        checks += 2;

        JSONObject created = executeSuccess(context, "create_booking",
                new JSONObject().put("serviceId", serviceId.toString())
                        .put("startAt", firstStart.toString())
                        .put("notes", "certification"),
                passedTools);
        UUID bookingId = UUID.fromString(created.getString("bookingId"));
        Booking persistedBooking = bookings.findByIdAndBusinessIdAndCustomerId(bookingId, businessId, customerId)
                .orElseThrow(() -> new IllegalStateException("created booking was not persisted"));
        require(persistedBooking.getStatus() == BookingStatus.CONFIRMED, "created booking was not confirmed");
        require(firstStart.equals(persistedBooking.getStartAt()), "created booking start time mismatch");
        checks += 3;

        JSONObject customerBookings = executeSuccess(context, "list_customer_bookings", new JSONObject(), passedTools);
        require(containsId(customerBookings.getJSONArray("bookings"), "bookingId", bookingId),
                "created booking missing from list_customer_bookings");
        checks++;

        JSONObject rescheduled = executeSuccess(context, "reschedule_booking",
                new JSONObject().put("bookingId", bookingId.toString()).put("newStartAt", secondStart.toString()),
                passedTools);
        require(bookingId.toString().equals(rescheduled.getString("bookingId")), "reschedule returned wrong booking");
        Booking afterReschedule = bookings.findByIdAndBusinessIdAndCustomerId(bookingId, businessId, customerId).orElseThrow();
        require(secondStart.equals(afterReschedule.getStartAt()), "rescheduled booking was not persisted");
        require(afterReschedule.getStatus() == BookingStatus.CONFIRMED, "rescheduled booking lost confirmed status");
        checks += 3;

        JSONObject cancelled = executeSuccess(context, "cancel_booking",
                new JSONObject().put("bookingId", bookingId.toString()), passedTools);
        require("CANCELLED".equals(cancelled.getString("status")), "cancel_booking did not report CANCELLED");
        Booking afterCancel = bookings.findByIdAndBusinessIdAndCustomerId(bookingId, businessId, customerId).orElseThrow();
        require(afterCancel.getStatus() == BookingStatus.CANCELLED, "cancelled booking was not persisted");
        checks += 2;

        JSONObject request = executeSuccess(context, "create_request",
                new JSONObject()
                        .put("requestType", "soporte")
                        .put("title", "Certificación de solicitud")
                        .put("description", "Validación transaccional de PostgreSQL")
                        .put("priority", "NORMAL"),
                passedTools);
        UUID requestId = UUID.fromString(request.getString("requestId"));
        require(requests.findByIdAndBusinessId(requestId, businessId).isPresent(), "business request was not persisted");
        checks++;

        JSONObject unanswered = executeSuccess(context, "record_unanswered_question",
                new JSONObject().put("question", "¿Pregunta de certificación sin respuesta?"), passedTools);
        UUID questionId = UUID.fromString(unanswered.getString("questionId"));
        require(questions.findByIdAndBusinessId(questionId, businessId).isPresent(), "unanswered question was not persisted");
        checks++;

        JSONObject transfer = executeSuccess(context, "transfer_to_human", new JSONObject(), passedTools);
        require(TEST_TRANSFER.equals(transfer.getString("targetPhone")), "transfer target did not come from trusted business config");
        checks++;

        int actionCount = actions.findAllByCallIdOrderByCreatedAtAsc(callId).size();
        require(actionCount >= passedTools.size(), "call trace actions were not persisted for every tool invocation");
        checks++;

        return new CertificationReport(checks, List.copyOf(passedTools), actionCount);
    }

    private JSONObject executeSuccess(RealtimeCallContext context,
                                      String toolName,
                                      JSONObject args,
                                      List<String> passedTools) {
        JSONObject root = new JSONObject(tools.execute(context, toolName, args.toString()));
        if (!root.optBoolean("success", false)) {
            JSONObject error = root.optJSONObject("error");
            String code = error == null ? "unknown" : error.optString("code", "unknown");
            String message = error == null ? "" : error.optString("message", "");
            throw new IllegalStateException(toolName + " failed code=" + code + " message=" + message);
        }
        JSONObject data = root.optJSONObject("data");
        if (data == null) throw new IllegalStateException(toolName + " returned no data object");
        passedTools.add(toolName);
        log.info("RECEPVOZ_TOOL_CERTIFICATION PASS tool={}", toolName);
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

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current != null && current.getCause() != null) current = current.getCause();
        if (current == null) return "unknown";
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private record CertificationReport(int checks, List<String> tools, int actionCount) {}
}

package cl.helvoca.messaging;

import cl.helvoca.booking.BookingConfirmationWorkflowService;
import cl.helvoca.booking.BookingSource;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.customer.Customer;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.operations.BusinessOrder;
import cl.helvoca.schedule.BusinessScheduleService;
import cl.helvoca.servicecatalog.ServiceItem;
import cl.helvoca.servicecatalog.ServiceItemRepository;
import cl.helvoca.security.TenantDatabaseContext;
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

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.regex.Pattern;

/** Temporary rollback-only production probe for the WhatsApp booking proposal path. */
@Component
public class WhatsAppBookingProposalProbeStartupRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(WhatsAppBookingProposalProbeStartupRunner.class);
    private static final Pattern UUID_PATTERN = Pattern.compile("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
    private static final Pattern PHONE_PATTERN = Pattern.compile("\\+?[0-9][0-9 ()-]{7,}[0-9]");

    private final boolean enabled;
    private final String businessId;
    private final TenantDatabaseContext databaseContext;
    private final MessagingConversationRepository conversations;
    private final CustomerRepository customers;
    private final ServiceItemRepository services;
    private final BusinessRepository businesses;
    private final BusinessScheduleService schedule;
    private final BookingConfirmationWorkflowService workflow;
    private final PlatformTransactionManager transactionManager;

    public WhatsAppBookingProposalProbeStartupRunner(
            @Value("${HELVOCA_WHATSAPP_BOOKING_PROPOSAL_PROBE_ON_STARTUP:false}") boolean enabled,
            @Value("${HELVOCA_WHATSAPP_BOOKING_OPERATION_DIAGNOSTIC_BUSINESS_ID:}") String businessId,
            TenantDatabaseContext databaseContext,
            MessagingConversationRepository conversations,
            CustomerRepository customers,
            ServiceItemRepository services,
            BusinessRepository businesses,
            BusinessScheduleService schedule,
            BookingConfirmationWorkflowService workflow,
            PlatformTransactionManager transactionManager) {
        this.enabled = enabled;
        this.businessId = businessId == null ? "" : businessId.trim();
        this.databaseContext = databaseContext;
        this.conversations = conversations;
        this.customers = customers;
        this.services = services;
        this.businesses = businesses;
        this.schedule = schedule;
        this.workflow = workflow;
        this.transactionManager = transactionManager;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) return;
        UUID tenantId;
        try {
            tenantId = UUID.fromString(businessId);
        } catch (Exception e) {
            log.warn("WHATSAPP_BOOKING_PROPOSAL_PROBE result=SKIPPED reason=invalid_business_id");
            return;
        }
        databaseContext.runAsTenant(tenantId, () -> executeProbe(tenantId));
    }

    private void executeProbe(UUID businessId) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
        try {
            JSONObject result = tx.execute(status -> {
                try {
                    return invokeProposal(businessId);
                } finally {
                    status.setRollbackOnly();
                }
            });
            boolean success = result != null && result.optBoolean("success", false);
            String errorCode = "NONE";
            if (result != null && !success && result.optJSONObject("error") != null) {
                errorCode = safe(result.getJSONObject("error").optString("code", "NONE"));
            }
            log.info("WHATSAPP_BOOKING_PROPOSAL_PROBE result=RETURNED success={} errorCode={}", success, errorCode);
        } catch (Throwable failure) {
            Throwable root = rootCause(failure);
            log.info(
                    "WHATSAPP_BOOKING_PROPOSAL_PROBE result=THREW exceptionClass={} rootClass={} sqlState={} rootMessage={}",
                    failure.getClass().getSimpleName(), root.getClass().getSimpleName(), sqlState(failure), safeMessage(root.getMessage()));
        }
    }

    private JSONObject invokeProposal(UUID businessId) {
        MessagingConversation conversation = conversations
                .findAllByBusinessIdAndChannelOrderByLastMessageAtDesc(businessId, "whatsapp")
                .stream().findFirst().orElse(null);
        if (conversation == null) return error("NO_WHATSAPP_CONVERSATION");

        Customer customer = conversation.getCustomerId() == null
                ? customers.findFirstByBusinessIdAndPhone(businessId, conversation.getSender()).orElse(null)
                : customers.findByIdAndBusinessId(conversation.getCustomerId(), businessId).orElse(null);
        if (customer == null) return error("NO_CUSTOMER");

        ServiceItem service = services.findAllByBusinessIdOrderByNameAsc(businessId)
                .stream().filter(ServiceItem::isActive).findFirst().orElse(null);
        if (service == null) return error("NO_ACTIVE_SERVICE");

        Business business = businesses.findById(businessId).orElse(null);
        ZoneId zone;
        try {
            zone = ZoneId.of(business == null || business.getTimezone() == null || business.getTimezone().isBlank()
                    ? "UTC" : business.getTimezone());
        } catch (Exception ignored) {
            zone = ZoneId.of("UTC");
        }

        BusinessScheduleService.AvailableSlot slot = null;
        LocalDate firstDate = LocalDate.now(zone);
        for (int offset = 0; offset < 14 && slot == null; offset++) {
            BusinessScheduleService.DailyAvailability daily = schedule.listAvailableSlots(
                    businessId, service.getId(), service.getDurationMinutes(), firstDate.plusDays(offset), 1);
            if (!daily.slots().isEmpty()) slot = daily.slots().getFirst();
        }
        if (slot == null) return error("NO_AVAILABLE_SLOT");

        return workflow.execute(
                businessId,
                customer.getId(),
                conversation.getId(),
                conversation.getSender(),
                BusinessOrder.Source.WHATSAPP,
                BookingSource.AI_WHATSAPP,
                new JSONObject().put("serviceId", service.getId().toString()).put("startAt", slot.startAt().toString()));
    }

    private static JSONObject error(String code) {
        return new JSONObject().put("success", false).put("error", new JSONObject().put("code", code));
    }

    private static Throwable rootCause(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        return current;
    }

    private static String sqlState(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SQLException sql && sql.getSQLState() != null) return safe(sql.getSQLState());
            current = current.getCause();
        }
        return "NONE";
    }

    private static String safeMessage(String value) {
        if (value == null || value.isBlank()) return "NONE";
        String clean = UUID_PATTERN.matcher(value).replaceAll("<uuid>");
        clean = PHONE_PATTERN.matcher(clean).replaceAll("<phone>");
        clean = clean.replaceAll("[\\r\\n\\t]+", " ").replaceAll("\\s+", " ").trim();
        if (clean.length() > 240) clean = clean.substring(0, 240);
        return clean.replaceAll("[^A-Za-z0-9_ .,:;()\\[\\]{}<>=/'\"-]", "_");
    }

    private static String safe(String value) {
        if (value == null || value.isBlank()) return "NONE";
        return value.trim().replaceAll("[^A-Za-z0-9_.-]", "_");
    }
}

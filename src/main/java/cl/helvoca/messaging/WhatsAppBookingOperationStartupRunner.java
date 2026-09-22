package cl.helvoca.messaging;

import cl.helvoca.booking.BookingRepository;
import cl.helvoca.operations.BusinessOperation;
import cl.helvoca.operations.BusinessOperationRepository;
import cl.helvoca.operations.BusinessOrder;
import cl.helvoca.operations.ConversationOperationState;
import cl.helvoca.operations.ConversationStateService;
import cl.helvoca.security.TenantDatabaseContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class WhatsAppBookingOperationStartupRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(WhatsAppBookingOperationStartupRunner.class);

    private final boolean enabled;
    private final String businessId;
    private final TenantDatabaseContext databaseContext;
    private final MessagingConversationRepository conversations;
    private final ConversationStateService conversationState;
    private final BusinessOperationRepository operations;
    private final BookingRepository bookings;

    public WhatsAppBookingOperationStartupRunner(
            @Value("${HELVOCA_WHATSAPP_BOOKING_OPERATION_DIAGNOSTIC_ON_STARTUP:false}") boolean enabled,
            @Value("${HELVOCA_WHATSAPP_BOOKING_OPERATION_DIAGNOSTIC_BUSINESS_ID:}") String businessId,
            TenantDatabaseContext databaseContext,
            MessagingConversationRepository conversations,
            ConversationStateService conversationState,
            BusinessOperationRepository operations,
            BookingRepository bookings) {
        this.enabled = enabled;
        this.businessId = businessId == null ? "" : businessId.trim();
        this.databaseContext = databaseContext;
        this.conversations = conversations;
        this.conversationState = conversationState;
        this.operations = operations;
        this.bookings = bookings;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) return;

        UUID tenantId;
        try {
            tenantId = UUID.fromString(businessId);
        } catch (Exception e) {
            log.warn("WHATSAPP_BOOKING_OPERATION_DIAGNOSTIC skipped=invalid_business_id");
            return;
        }

        databaseContext.runAsTenant(tenantId, () -> log(evaluate(tenantId)));
    }

    Diagnostic evaluate(UUID businessId) {
        MessagingConversation conversation = conversations
                .findAllByBusinessIdAndChannelOrderByLastMessageAtDesc(businessId, "whatsapp")
                .stream()
                .findFirst()
                .orElse(null);
        if (conversation == null) {
            return new Diagnostic(false, false, false, "NONE", "NONE", false, "NONE");
        }

        ConversationOperationState state = conversationState.find(
                businessId,
                conversation.getId(),
                BusinessOrder.Source.WHATSAPP);
        if (state == null || state.getState() == null) {
            return new Diagnostic(true, false, false, "NONE", "NONE", false, "NONE");
        }

        Map<String, Object> values = state.getState();
        String intent = safeEnum(values.get("intent"));
        UUID operationId = uuid(values.get("operationId"));
        if (operationId == null) {
            return new Diagnostic(true, false, false, "NONE", "NONE", false, intent);
        }

        BusinessOperation operation = operations.findByIdAndBusinessId(operationId, businessId).orElse(null);
        if (operation == null) {
            return new Diagnostic(true, true, false, "NONE", "NONE", false, intent);
        }

        boolean bookingProjectionFound = operation.getType() == BusinessOperation.Type.BOOKING
                && bookings.findByOperationIdAndBusinessId(operationId, businessId).isPresent();
        return new Diagnostic(
                true,
                true,
                true,
                operation.getType() == null ? "NONE" : operation.getType().name(),
                operation.getStatus() == null ? "NONE" : operation.getStatus().name(),
                bookingProjectionFound,
                intent);
    }

    private void log(Diagnostic diagnostic) {
        log.info(
                "WHATSAPP_BOOKING_OPERATION_DIAGNOSTIC conversationFound={} operationReferenceFound={} operationFound={} operationType={} operationStatus={} bookingProjectionFound={} intent={}",
                diagnostic.conversationFound(),
                diagnostic.operationReferenceFound(),
                diagnostic.operationFound(),
                diagnostic.operationType(),
                diagnostic.operationStatus(),
                diagnostic.bookingProjectionFound(),
                diagnostic.intent());
    }

    private static UUID uuid(Object value) {
        if (value == null) return null;
        try {
            return UUID.fromString(String.valueOf(value).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static String safeEnum(Object value) {
        if (value == null) return "NONE";
        String clean = String.valueOf(value).trim();
        if (clean.isBlank()) return "NONE";
        return clean.replaceAll("[^A-Za-z0-9_ -]", "_");
    }

    record Diagnostic(
            boolean conversationFound,
            boolean operationReferenceFound,
            boolean operationFound,
            String operationType,
            String operationStatus,
            boolean bookingProjectionFound,
            String intent
    ) {}
}

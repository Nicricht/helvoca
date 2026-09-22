package cl.helvoca.messaging;

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
public class WhatsAppBookingFlowStartupRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(WhatsAppBookingFlowStartupRunner.class);

    private final boolean enabled;
    private final String businessId;
    private final TenantDatabaseContext databaseContext;
    private final MessagingConversationRepository conversations;
    private final ConversationStateService conversationState;

    public WhatsAppBookingFlowStartupRunner(
            @Value("${HELVOCA_WHATSAPP_BOOKING_FLOW_DIAGNOSTIC_ON_STARTUP:false}") boolean enabled,
            @Value("${HELVOCA_WHATSAPP_BOOKING_FLOW_DIAGNOSTIC_BUSINESS_ID:}") String businessId,
            TenantDatabaseContext databaseContext,
            MessagingConversationRepository conversations,
            ConversationStateService conversationState) {
        this.enabled = enabled;
        this.businessId = businessId == null ? "" : businessId.trim();
        this.databaseContext = databaseContext;
        this.conversations = conversations;
        this.conversationState = conversationState;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) return;

        UUID tenantId;
        try {
            tenantId = UUID.fromString(businessId);
        } catch (Exception e) {
            log.warn("WHATSAPP_BOOKING_FLOW_DIAGNOSTIC skipped=invalid_business_id");
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
            return new Diagnostic(false, false, false, false, false, false, "NONE");
        }

        ConversationOperationState state = conversationState.find(
                businessId,
                conversation.getId(),
                BusinessOrder.Source.WHATSAPP);
        if (state == null || state.getState() == null) {
            return new Diagnostic(true, false, false, false, false, false, "NONE");
        }

        Map<String, Object> values = state.getState();
        boolean confirmationPending = Boolean.TRUE.equals(values.get("confirmationPending"));
        boolean hasOperationId = present(values.get("operationId"));
        boolean hasConfirmationToken = present(values.get("confirmationToken"));
        boolean hasBookingId = present(values.get("bookingId"));
        String operationStatus = cleanStatus(values.get("operationStatus"));

        return new Diagnostic(
                true,
                true,
                confirmationPending,
                hasOperationId,
                hasConfirmationToken,
                hasBookingId,
                operationStatus);
    }

    private void log(Diagnostic diagnostic) {
        log.info(
                "WHATSAPP_BOOKING_FLOW_DIAGNOSTIC conversationFound={} stateFound={} confirmationPending={} hasOperationId={} hasConfirmationToken={} hasBookingId={} operationStatus={}",
                diagnostic.conversationFound(),
                diagnostic.stateFound(),
                diagnostic.confirmationPending(),
                diagnostic.hasOperationId(),
                diagnostic.hasConfirmationToken(),
                diagnostic.hasBookingId(),
                diagnostic.operationStatus());
    }

    private static boolean present(Object value) {
        return value != null && !String.valueOf(value).isBlank();
    }

    private static String cleanStatus(Object value) {
        if (value == null) return "NONE";
        String status = String.valueOf(value).trim();
        if (status.isBlank()) return "NONE";
        return status.replaceAll("[^A-Za-z0-9_ -]", "_");
    }

    record Diagnostic(
            boolean conversationFound,
            boolean stateFound,
            boolean confirmationPending,
            boolean hasOperationId,
            boolean hasConfirmationToken,
            boolean hasBookingId,
            String operationStatus
    ) {}
}

package cl.helvoca.messaging;

import cl.helvoca.security.TenantDatabaseContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class WhatsAppBookingRetryDiagnosticStartupRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(WhatsAppBookingRetryDiagnosticStartupRunner.class);

    private final boolean enabled;
    private final String businessId;
    private final TenantDatabaseContext databaseContext;
    private final MessagingConversationRepository conversations;
    private final JdbcTemplate jdbc;

    public WhatsAppBookingRetryDiagnosticStartupRunner(
            @Value("${HELVOCA_WHATSAPP_BOOKING_RETRY_DIAGNOSTIC_ON_STARTUP:false}") boolean enabled,
            @Value("${HELVOCA_WHATSAPP_BOOKING_OPERATION_DIAGNOSTIC_BUSINESS_ID:}") String businessId,
            TenantDatabaseContext databaseContext,
            MessagingConversationRepository conversations,
            JdbcTemplate jdbc) {
        this.enabled = enabled;
        this.businessId = businessId == null ? "" : businessId.trim();
        this.databaseContext = databaseContext;
        this.conversations = conversations;
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) return;

        UUID tenantId;
        try {
            tenantId = UUID.fromString(businessId);
        } catch (Exception e) {
            log.warn("WHATSAPP_BOOKING_RETRY_DIAGNOSTIC skipped=invalid_business_id");
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
            return new Diagnostic(false, false, "NONE", "NONE", "NONE", "NONE", 0, 0);
        }

        return jdbc.query("""
                        SELECT tool_name, outcome, failure_class, error_code, attempt_no, max_attempts
                        FROM business_operation_retry_attempt
                        WHERE business_id = ? AND source_reference_id = ?
                        ORDER BY sequence_no DESC
                        LIMIT 1
                        """,
                rs -> rs.next()
                        ? new Diagnostic(
                        true,
                        true,
                        safe(rs.getString("tool_name")),
                        safe(rs.getString("outcome")),
                        safe(rs.getString("failure_class")),
                        safe(rs.getString("error_code")),
                        rs.getInt("attempt_no"),
                        rs.getInt("max_attempts"))
                        : new Diagnostic(true, false, "NONE", "NONE", "NONE", "NONE", 0, 0),
                businessId,
                conversation.getId());
    }

    private void log(Diagnostic diagnostic) {
        log.info(
                "WHATSAPP_BOOKING_RETRY_DIAGNOSTIC conversationFound={} retryFound={} toolName={} outcome={} failureClass={} errorCode={} attemptNo={} maxAttempts={}",
                diagnostic.conversationFound(),
                diagnostic.retryFound(),
                diagnostic.toolName(),
                diagnostic.outcome(),
                diagnostic.failureClass(),
                diagnostic.errorCode(),
                diagnostic.attemptNo(),
                diagnostic.maxAttempts());
    }

    private static String safe(String value) {
        if (value == null || value.isBlank()) return "NONE";
        return value.trim().replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    record Diagnostic(
            boolean conversationFound,
            boolean retryFound,
            String toolName,
            String outcome,
            String failureClass,
            String errorCode,
            int attemptNo,
            int maxAttempts
    ) {}
}

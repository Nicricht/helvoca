package cl.helvoca.retention;

import cl.helvoca.security.TenantProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class DataRetentionInventoryService {
    private final JdbcTemplate jdbc;
    private final TenantProvider tenantProvider;
    private final Clock clock;

    @Autowired
    public DataRetentionInventoryService(JdbcTemplate jdbc, TenantProvider tenantProvider) {
        this(jdbc, tenantProvider, Clock.systemUTC());
    }

    DataRetentionInventoryService(JdbcTemplate jdbc, TenantProvider tenantProvider, Clock clock) {
        this.jdbc = jdbc;
        this.tenantProvider = tenantProvider;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Inventory dryRun() {
        UUID businessId = tenantProvider.requireBusinessId();
        Instant now = clock.instant();
        DataRetentionPolicy.Cutoffs cutoffs = DataRetentionPolicy.cutoffs(now);

        long callTranscripts = count("""
                SELECT COUNT(*)
                FROM call_transcript t
                JOIN call_session c ON c.id = t.call_id
                WHERE c.business_id = ?
                  AND c.ended_at IS NOT NULL
                  AND c.ended_at < ?
                """, businessId, cutoffs.callContentBefore());

        long callSummaries = count("""
                SELECT COUNT(*)
                FROM call_summary s
                JOIN call_session c ON c.id = s.call_id
                WHERE c.business_id = ?
                  AND c.ended_at IS NOT NULL
                  AND c.ended_at < ?
                """, businessId, cutoffs.callContentBefore());

        long callActions = count("""
                SELECT COUNT(*)
                FROM call_action a
                JOIN call_session c ON c.id = a.call_id
                WHERE c.business_id = ?
                  AND c.ended_at IS NOT NULL
                  AND c.ended_at < ?
                """, businessId, cutoffs.callContentBefore());

        long callSessions = count("""
                SELECT COUNT(*)
                FROM call_session
                WHERE business_id = ?
                  AND ended_at IS NOT NULL
                  AND ended_at < ?
                """, businessId, cutoffs.callSessionsBefore());

        long messagingMessages = count("""
                SELECT COUNT(*)
                FROM messaging_message m
                JOIN messaging_conversation c ON c.id = m.conversation_id
                WHERE c.business_id = ?
                  AND m.created_at < ?
                """, businessId, cutoffs.messageContentBefore());

        long messagingConversations = count("""
                SELECT COUNT(*)
                FROM messaging_conversation
                WHERE business_id = ?
                  AND last_message_at < ?
                """, businessId, cutoffs.conversationsBefore());

        long customerReviewCandidates = count("""
                SELECT COUNT(*)
                FROM customer
                WHERE business_id = ?
                  AND updated_at < ?
                """, businessId, cutoffs.customerReviewBefore());

        long nonFinancialOperations = count("""
                SELECT COUNT(*)
                FROM business_operation
                WHERE business_id = ?
                  AND type <> 'PAYMENT'
                  AND status IN ('COMPLETED', 'CANCELLED', 'EXPIRED', 'FAILED')
                  AND updated_at < ?
                """, businessId, cutoffs.operationsBefore());

        long financialOperationsHeld = count("""
                SELECT COUNT(*)
                FROM business_operation
                WHERE business_id = ?
                  AND type = 'PAYMENT'
                  AND updated_at < ?
                """, businessId, cutoffs.operationsBefore());

        long auditLogs = count("""
                SELECT COUNT(*)
                FROM audit_log
                WHERE business_id = ?
                  AND created_at < ?
                """, businessId, cutoffs.auditBefore());

        return new Inventory(
                now,
                false,
                cutoffs,
                callTranscripts,
                callSummaries,
                callActions,
                callSessions,
                messagingMessages,
                messagingConversations,
                customerReviewCandidates,
                nonFinancialOperations,
                financialOperationsHeld,
                auditLogs);
    }

    private long count(String sql, UUID businessId, Instant cutoff) {
        Long value = jdbc.queryForObject(sql, Long.class, businessId, cutoff);
        return value == null ? 0L : value;
    }

    public record Inventory(
            Instant generatedAt,
            boolean destructiveActionsEnabled,
            DataRetentionPolicy.Cutoffs cutoffs,
            long callTranscriptsEligible,
            long callSummariesEligible,
            long callActionsEligible,
            long callSessionsEligible,
            long messagingMessagesEligible,
            long messagingConversationsEligible,
            long customerInactivityReviewCandidates,
            long nonFinancialOperationsEligible,
            long financialOperationsHeldFromAutomation,
            long auditLogsEligible
    ) {}
}

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
                FROM call_session c
                WHERE c.business_id = ?
                  AND c.ended_at IS NOT NULL
                  AND c.ended_at < ?
                  AND NOT EXISTS (
                      SELECT 1 FROM business_request r WHERE r.call_id = c.id
                  )
                  AND NOT EXISTS (
                      SELECT 1 FROM unanswered_question q WHERE q.call_id = c.id
                  )
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

        long outboundMessageContent = count("""
                SELECT COUNT(*)
                FROM outbound_message
                WHERE business_id = ?
                  AND status IN ('SENT', 'FAILED', 'CANCELLED', 'BLOCKED')
                  AND updated_at < ?
                  AND (
                      recipient_address <> '[redacted]'
                      OR content_text <> '[redacted]'
                      OR provider_message_id IS NOT NULL
                  )
                """, businessId, cutoffs.messageContentBefore());

        long customerReviewCandidates = count("""
                SELECT COUNT(*)
                FROM customer
                WHERE business_id = ?
                  AND updated_at < ?
                """, businessId, cutoffs.customerReviewBefore());

        long nonFinancialOperations = count("""
                SELECT COUNT(*)
                FROM business_operation o
                WHERE o.business_id = ?
                  AND o.type <> 'PAYMENT'
                  AND o.status IN ('COMPLETED', 'CANCELLED', 'EXPIRED', 'FAILED')
                  AND o.updated_at < ?
                  AND NOT EXISTS (SELECT 1 FROM business_order x WHERE x.operation_id = o.id)
                  AND NOT EXISTS (SELECT 1 FROM business_quote x WHERE x.operation_id = o.id)
                  AND NOT EXISTS (SELECT 1 FROM business_lead x WHERE x.operation_id = o.id)
                  AND NOT EXISTS (SELECT 1 FROM business_request x WHERE x.operation_id = o.id)
                  AND NOT EXISTS (SELECT 1 FROM booking x WHERE x.operation_id = o.id)
                  AND NOT EXISTS (SELECT 1 FROM business_delivery x WHERE x.operation_id = o.id)
                  AND NOT EXISTS (
                      SELECT 1
                      FROM business_payment x
                      WHERE x.operation_id = o.id
                         OR x.target_operation_id = o.id
                  )
                  AND NOT EXISTS (SELECT 1 FROM outbound_message x WHERE x.operation_id = o.id)
                  AND NOT EXISTS (SELECT 1 FROM persistent_job x WHERE x.operation_id = o.id)
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
                outboundMessageContent,
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
            long outboundMessageContentEligible,
            long customerInactivityReviewCandidates,
            long nonFinancialOperationsEligible,
            long financialOperationsHeldFromAutomation,
            long auditLogsEligible
    ) {}
}

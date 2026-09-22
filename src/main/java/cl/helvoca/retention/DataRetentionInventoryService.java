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
                  AND NOT EXISTS (
                      SELECT 1
                      FROM retention_legal_hold h
                      WHERE h.business_id = c.business_id
                        AND h.target_type = 'CALL_SESSION'
                        AND h.target_id = c.id
                        AND h.released_at IS NULL
                  )
                """, businessId, cutoffs.callContentBefore());

        long callSummaries = count("""
                SELECT COUNT(*)
                FROM call_summary s
                JOIN call_session c ON c.id = s.call_id
                WHERE c.business_id = ?
                  AND c.ended_at IS NOT NULL
                  AND c.ended_at < ?
                  AND NOT EXISTS (
                      SELECT 1
                      FROM retention_legal_hold h
                      WHERE h.business_id = c.business_id
                        AND h.target_type = 'CALL_SESSION'
                        AND h.target_id = c.id
                        AND h.released_at IS NULL
                  )
                """, businessId, cutoffs.callContentBefore());

        long callActions = count("""
                SELECT COUNT(*)
                FROM call_action a
                JOIN call_session c ON c.id = a.call_id
                WHERE c.business_id = ?
                  AND c.ended_at IS NOT NULL
                  AND c.ended_at < ?
                  AND NOT EXISTS (
                      SELECT 1
                      FROM retention_legal_hold h
                      WHERE h.business_id = c.business_id
                        AND h.target_type = 'CALL_SESSION'
                        AND h.target_id = c.id
                        AND h.released_at IS NULL
                  )
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
                  AND NOT EXISTS (
                      SELECT 1
                      FROM retention_legal_hold h
                      WHERE h.business_id = c.business_id
                        AND h.target_type = 'CALL_SESSION'
                        AND h.target_id = c.id
                        AND h.released_at IS NULL
                  )
                """, businessId, cutoffs.callSessionsBefore());

        long messagingMessages = count("""
                SELECT COUNT(*)
                FROM messaging_message m
                JOIN messaging_conversation c ON c.id = m.conversation_id
                WHERE c.business_id = ?
                  AND m.created_at < ?
                  AND NOT EXISTS (
                      SELECT 1
                      FROM retention_legal_hold h
                      WHERE h.business_id = c.business_id
                        AND h.target_type = 'MESSAGING_CONVERSATION'
                        AND h.target_id = c.id
                        AND h.released_at IS NULL
                  )
                """, businessId, cutoffs.messageContentBefore());

        long messagingConversations = count("""
                SELECT COUNT(*)
                FROM messaging_conversation c
                WHERE c.business_id = ?
                  AND c.last_message_at < ?
                  AND NOT EXISTS (
                      SELECT 1
                      FROM retention_legal_hold h
                      WHERE h.business_id = c.business_id
                        AND h.target_type = 'MESSAGING_CONVERSATION'
                        AND h.target_id = c.id
                        AND h.released_at IS NULL
                  )
                """, businessId, cutoffs.conversationsBefore());

        long outboundMessageContent = count("""
                SELECT COUNT(*)
                FROM outbound_message om
                WHERE om.business_id = ?
                  AND om.status IN ('SENT', 'FAILED', 'CANCELLED', 'BLOCKED')
                  AND om.updated_at < ?
                  AND (
                      om.recipient_address <> '[redacted]'
                      OR om.content_text <> '[redacted]'
                      OR om.provider_message_id IS NOT NULL
                  )
                  AND NOT EXISTS (
                      SELECT 1
                      FROM retention_legal_hold h
                      WHERE h.business_id = om.business_id
                        AND h.target_type = 'OUTBOUND_MESSAGE'
                        AND h.target_id = om.id
                        AND h.released_at IS NULL
                  )
                """, businessId, cutoffs.messageContentBefore());

        Long customerReviewCandidatesValue = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM customer c
                WHERE c.business_id = ?
                  AND c.updated_at < ?
                  AND NOT EXISTS (
                      SELECT 1
                      FROM retention_legal_hold h
                      WHERE h.business_id = c.business_id
                        AND h.target_type = 'CUSTOMER'
                        AND h.target_id = c.id
                        AND h.released_at IS NULL
                  )
                  AND NOT EXISTS (
                      SELECT 1
                      FROM business_operation o
                      WHERE o.business_id = c.business_id
                        AND o.customer_id = c.id
                        AND o.status IN (
                            'DRAFT',
                            'PROPOSED',
                            'AWAITING_CONFIRMATION',
                            'CONFIRMED',
                            'EXECUTING'
                        )
                  )
                  AND NOT EXISTS (
                      SELECT 1
                      FROM call_session cs
                      WHERE cs.business_id = c.business_id
                        AND cs.customer_id = c.id
                        AND cs.ended_at IS NULL
                  )
                  AND NOT EXISTS (
                      SELECT 1
                      FROM messaging_conversation mc
                      WHERE mc.business_id = c.business_id
                        AND mc.customer_id = c.id
                        AND mc.last_message_at >= ?
                  )
                """,
                Long.class,
                businessId,
                cutoffs.customerReviewBefore(),
                cutoffs.conversationsBefore());
        long customerReviewCandidates =
                customerReviewCandidatesValue == null ? 0L : customerReviewCandidatesValue;

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
                  AND NOT EXISTS (
                      SELECT 1
                      FROM retention_legal_hold h
                      WHERE h.business_id = o.business_id
                        AND h.target_type = 'BUSINESS_OPERATION'
                        AND h.target_id = o.id
                        AND h.released_at IS NULL
                  )
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
                FROM audit_log a
                WHERE a.business_id = ?
                  AND a.created_at < ?
                  AND NOT EXISTS (
                      SELECT 1
                      FROM retention_legal_hold h
                      WHERE h.business_id = a.business_id
                        AND h.target_type = 'AUDIT_LOG'
                        AND h.target_id = a.id
                        AND h.released_at IS NULL
                  )
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

package cl.helvoca.retention;

import cl.helvoca.audit.AuditService;
import cl.helvoca.common.ConflictException;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.security.TenantProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class CustomerProfileAnonymizationService {
    private final JdbcTemplate jdbc;
    private final TenantProvider tenantProvider;
    private final AuditService auditService;

    public CustomerProfileAnonymizationService(
            JdbcTemplate jdbc,
            TenantProvider tenantProvider,
            AuditService auditService) {
        this.jdbc = jdbc;
        this.tenantProvider = tenantProvider;
        this.auditService = auditService;
    }

    @Transactional
    public Result anonymizeCurrentTenantCustomer(UUID customerId) {
        if (customerId == null) throw new IllegalArgumentException("customerId is required");

        UUID businessId = tenantProvider.requireBusinessId();

        Boolean held = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM retention_legal_hold h
                    WHERE h.business_id = ?
                      AND h.target_type = 'CUSTOMER'
                      AND h.target_id = ?
                      AND h.released_at IS NULL
                )
                """,
                Boolean.class,
                businessId,
                customerId);

        if (Boolean.TRUE.equals(held)) {
            throw new ConflictException("Customer is protected by an active legal hold");
        }

        Boolean hasActiveOperation = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM business_operation o
                    WHERE o.business_id = ?
                      AND o.customer_id = ?
                      AND o.status IN (
                          'DRAFT',
                          'PROPOSED',
                          'AWAITING_CONFIRMATION',
                          'CONFIRMED',
                          'EXECUTING'
                      )
                )
                """,
                Boolean.class,
                businessId,
                customerId);

        if (Boolean.TRUE.equals(hasActiveOperation)) {
            throw new ConflictException("Customer has an active business operation");
        }

        Boolean hasActiveCall = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM call_session cs
                    WHERE cs.business_id = ?
                      AND cs.customer_id = ?
                      AND cs.ended_at IS NULL
                )
                """,
                Boolean.class,
                businessId,
                customerId);

        if (Boolean.TRUE.equals(hasActiveCall)) {
            throw new ConflictException("Customer has an active call session");
        }

        Instant conversationCutoff = DataRetentionPolicy.cutoffs(Instant.now()).conversationsBefore();
        Boolean hasRecentConversation = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM messaging_conversation mc
                    WHERE mc.business_id = ?
                      AND mc.customer_id = ?
                      AND mc.last_message_at >= ?
                )
                """,
                Boolean.class,
                businessId,
                customerId,
                conversationCutoff);

        if (Boolean.TRUE.equals(hasRecentConversation)) {
            throw new ConflictException("Customer has a recent messaging conversation");
        }

        int customersUpdated = jdbc.update("""
                UPDATE customer c
                SET name = NULL,
                    phone = NULL,
                    email = NULL,
                    notes = NULL,
                    updated_at = CURRENT_TIMESTAMP
                WHERE c.id = ?
                  AND c.business_id = ?
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
                """, customerId, businessId, conversationCutoff);

        if (customersUpdated != 1) {
            throw new NotFoundException("Customer not found");
        }

        int callSessionsScrubbed = jdbc.update("""
                UPDATE call_session cs
                   SET caller_number = NULL,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE cs.business_id = ?
                   AND cs.customer_id = ?
                   AND cs.ended_at IS NOT NULL
                   AND cs.caller_number IS NOT NULL
                   AND NOT EXISTS (
                       SELECT 1
                       FROM retention_legal_hold h
                       WHERE h.business_id = cs.business_id
                         AND h.target_type = 'CALL_SESSION'
                         AND h.target_id = cs.id
                         AND h.released_at IS NULL
                   )
                """, businessId, customerId);

        int messagingConversationsScrubbed = jdbc.update("""
                UPDATE messaging_conversation mc
                   SET sender = '[redacted]',
                       updated_at = CURRENT_TIMESTAMP
                 WHERE mc.business_id = ?
                   AND mc.customer_id = ?
                   AND mc.last_message_at < ?
                   AND mc.sender <> '[redacted]'
                   AND NOT EXISTS (
                       SELECT 1
                       FROM retention_legal_hold h
                       WHERE h.business_id = mc.business_id
                         AND h.target_type = 'MESSAGING_CONVERSATION'
                         AND h.target_id = mc.id
                         AND h.released_at IS NULL
                   )
                """, businessId, customerId, conversationCutoff);

        int bookingNotesScrubbed = jdbc.update("""
                UPDATE booking b
                   SET notes = NULL,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE b.business_id = ?
                   AND b.customer_id = ?
                   AND b.notes IS NOT NULL
                   AND EXISTS (
                       SELECT 1
                       FROM business_operation o
                       WHERE o.id = b.operation_id
                         AND o.business_id = b.business_id
                         AND o.customer_id = b.customer_id
                         AND o.type = 'BOOKING'
                         AND o.status IN ('COMPLETED', 'CANCELLED', 'EXPIRED', 'FAILED')
                         AND NOT EXISTS (
                             SELECT 1
                             FROM retention_legal_hold h
                             WHERE h.business_id = o.business_id
                               AND h.target_type = 'BUSINESS_OPERATION'
                               AND h.target_id = o.id
                               AND h.released_at IS NULL
                         )
                   )
                """, businessId, customerId);

        int bookingOperationMetadataScrubbed = jdbc.update("""
                UPDATE business_operation o
                   SET metadata_json = o.metadata_json - 'notes'
                 WHERE o.business_id = ?
                   AND o.customer_id = ?
                   AND o.type = 'BOOKING'
                   AND o.status IN ('COMPLETED', 'CANCELLED', 'EXPIRED', 'FAILED')
                   AND jsonb_exists(o.metadata_json, 'notes')
                   AND NOT EXISTS (
                       SELECT 1
                       FROM retention_legal_hold h
                       WHERE h.business_id = o.business_id
                         AND h.target_type = 'BUSINESS_OPERATION'
                         AND h.target_id = o.id
                         AND h.released_at IS NULL
                   )
                """, businessId, customerId);

        int identitiesDeleted = jdbc.update("""
                DELETE FROM customer_identity
                WHERE business_id = ?
                  AND customer_id = ?
                """, businessId, customerId);

        auditService.humanSuccess(
                businessId,
                "CUSTOMER_PROFILE_ANONYMIZE",
                "CUSTOMER",
                customerId);

        return new Result(
                customerId,
                callSessionsScrubbed,
                messagingConversationsScrubbed,
                bookingNotesScrubbed,
                bookingOperationMetadataScrubbed,
                identitiesDeleted);
    }

    public record Result(
            UUID customerId,
            int callSessionsScrubbed,
            int messagingConversationsScrubbed,
            int bookingNotesScrubbed,
            int bookingOperationMetadataScrubbed,
            int identitiesDeleted
    ) {}
}

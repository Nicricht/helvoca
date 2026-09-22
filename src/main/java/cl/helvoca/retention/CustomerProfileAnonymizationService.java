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

        int businessRequestsScrubbed = jdbc.update("""
                UPDATE business_request r
                   SET title = '[redacted]',
                       description = NULL,
                       contact_name = NULL,
                       contact_phone = NULL,
                       details_json = NULL,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE r.business_id = ?
                   AND r.customer_id = ?
                   AND EXISTS (
                       SELECT 1
                       FROM business_operation o
                       WHERE o.id = r.operation_id
                         AND o.business_id = r.business_id
                         AND o.customer_id = r.customer_id
                         AND o.type = 'REQUEST'
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
                   AND (
                       r.title <> '[redacted]'
                       OR r.description IS NOT NULL
                       OR r.contact_name IS NOT NULL
                       OR r.contact_phone IS NOT NULL
                       OR r.details_json IS NOT NULL
                   )
                """, businessId, customerId);

        int requestOperationPiiScrubbed = jdbc.update("""
                UPDATE business_operation o
                   SET contact_name = NULL,
                       contact_phone = NULL,
                       metadata_json = o.metadata_json - 'title' - 'description' - 'details'
                 WHERE o.business_id = ?
                   AND o.customer_id = ?
                   AND o.type = 'REQUEST'
                   AND o.status IN ('COMPLETED', 'CANCELLED', 'EXPIRED', 'FAILED')
                   AND (
                       o.contact_name IS NOT NULL
                       OR o.contact_phone IS NOT NULL
                       OR jsonb_exists(o.metadata_json, 'title')
                       OR jsonb_exists(o.metadata_json, 'description')
                       OR jsonb_exists(o.metadata_json, 'details')
                   )
                   AND NOT EXISTS (
                       SELECT 1
                       FROM retention_legal_hold h
                       WHERE h.business_id = o.business_id
                         AND h.target_type = 'BUSINESS_OPERATION'
                         AND h.target_id = o.id
                         AND h.released_at IS NULL
                   )
                """, businessId, customerId);

        int businessLeadsScrubbed = jdbc.update("""
                UPDATE business_lead l
                   SET name = '[redacted]',
                       phone = NULL,
                       email = NULL,
                       notes = NULL,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE l.business_id = ?
                   AND l.customer_id = ?
                   AND EXISTS (
                       SELECT 1
                       FROM business_operation o
                       WHERE o.id = l.operation_id
                         AND o.business_id = l.business_id
                         AND o.customer_id = l.customer_id
                         AND o.type = 'LEAD'
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
                   AND (
                       l.name <> '[redacted]'
                       OR l.phone IS NOT NULL
                       OR l.email IS NOT NULL
                       OR l.notes IS NOT NULL
                   )
                """, businessId, customerId);

        int leadOperationPiiScrubbed = jdbc.update("""
                UPDATE business_operation o
                   SET contact_name = NULL,
                       contact_phone = NULL,
                       metadata_json = o.metadata_json - 'name' - 'email' - 'notes'
                 WHERE o.business_id = ?
                   AND o.customer_id = ?
                   AND o.type = 'LEAD'
                   AND o.status IN ('COMPLETED', 'CANCELLED', 'EXPIRED', 'FAILED')
                   AND (
                       o.contact_name IS NOT NULL
                       OR o.contact_phone IS NOT NULL
                       OR jsonb_exists(o.metadata_json, 'name')
                       OR jsonb_exists(o.metadata_json, 'email')
                       OR jsonb_exists(o.metadata_json, 'notes')
                   )
                   AND NOT EXISTS (
                       SELECT 1
                       FROM retention_legal_hold h
                       WHERE h.business_id = o.business_id
                         AND h.target_type = 'BUSINESS_OPERATION'
                         AND h.target_id = o.id
                         AND h.released_at IS NULL
                   )
                """, businessId, customerId);

        int businessQuotesScrubbed = jdbc.update("""
                UPDATE business_quote q
                   SET contact_name = NULL,
                       contact_phone = NULL,
                       title = '[redacted]',
                       description = NULL,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE q.business_id = ?
                   AND q.customer_id = ?
                   AND EXISTS (
                       SELECT 1
                       FROM business_operation o
                       WHERE o.id = q.operation_id
                         AND o.business_id = q.business_id
                         AND o.customer_id = q.customer_id
                         AND o.type = 'QUOTE'
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
                   AND (
                       q.contact_name IS NOT NULL
                       OR q.contact_phone IS NOT NULL
                       OR q.title <> '[redacted]'
                       OR q.description IS NOT NULL
                   )
                """, businessId, customerId);

        int quoteOperationPiiScrubbed = jdbc.update("""
                UPDATE business_operation o
                   SET contact_name = NULL,
                       contact_phone = NULL,
                       metadata_json = o.metadata_json - 'title' - 'description'
                 WHERE o.business_id = ?
                   AND o.customer_id = ?
                   AND o.type = 'QUOTE'
                   AND o.status IN ('COMPLETED', 'CANCELLED', 'EXPIRED', 'FAILED')
                   AND (
                       o.contact_name IS NOT NULL
                       OR o.contact_phone IS NOT NULL
                       OR jsonb_exists(o.metadata_json, 'title')
                       OR jsonb_exists(o.metadata_json, 'description')
                   )
                   AND NOT EXISTS (
                       SELECT 1
                       FROM retention_legal_hold h
                       WHERE h.business_id = o.business_id
                         AND h.target_type = 'BUSINESS_OPERATION'
                         AND h.target_id = o.id
                         AND h.released_at IS NULL
                   )
                """, businessId, customerId);

        int businessOrdersScrubbed = jdbc.update("""
                UPDATE business_order bo
                   SET contact_name = NULL,
                       contact_phone = NULL,
                       delivery_address = NULL,
                       notes = NULL,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE bo.business_id = ?
                   AND bo.customer_id = ?
                   AND EXISTS (
                       SELECT 1
                       FROM business_operation o
                       WHERE o.id = bo.operation_id
                         AND o.business_id = bo.business_id
                         AND o.customer_id = bo.customer_id
                         AND o.type = 'ORDER'
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
                   AND (
                       bo.contact_name IS NOT NULL
                       OR bo.contact_phone IS NOT NULL
                       OR bo.delivery_address IS NOT NULL
                       OR bo.notes IS NOT NULL
                   )
                """, businessId, customerId);

        int orderLineNotesScrubbed = jdbc.update("""
                UPDATE business_order_line bol
                   SET notes = NULL
                 WHERE bol.notes IS NOT NULL
                   AND EXISTS (
                       SELECT 1
                       FROM business_order bo
                       JOIN business_operation o
                         ON o.id = bo.operation_id
                        AND o.business_id = bo.business_id
                        AND o.customer_id = bo.customer_id
                       WHERE bo.id = bol.order_id
                         AND bo.business_id = ?
                         AND bo.customer_id = ?
                         AND o.type = 'ORDER'
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

        int orderOperationPiiScrubbed = jdbc.update("""
                UPDATE business_operation o
                   SET contact_name = NULL,
                       contact_phone = NULL,
                       delivery_address = NULL
                 WHERE o.business_id = ?
                   AND o.customer_id = ?
                   AND o.type = 'ORDER'
                   AND o.status IN ('COMPLETED', 'CANCELLED', 'EXPIRED', 'FAILED')
                   AND (
                       o.contact_name IS NOT NULL
                       OR o.contact_phone IS NOT NULL
                       OR o.delivery_address IS NOT NULL
                   )
                   AND NOT EXISTS (
                       SELECT 1
                       FROM retention_legal_hold h
                       WHERE h.business_id = o.business_id
                         AND h.target_type = 'BUSINESS_OPERATION'
                         AND h.target_id = o.id
                         AND h.released_at IS NULL
                   )
                """, businessId, customerId);

        int businessDeliveriesScrubbed = jdbc.update("""
                UPDATE business_delivery d
                   SET contact_name = NULL,
                       contact_phone = NULL,
                       delivery_address = '[redacted]',
                       notes = NULL,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE d.business_id = ?
                   AND d.customer_id = ?
                   AND EXISTS (
                       SELECT 1
                       FROM business_operation o
                       WHERE o.id = d.operation_id
                         AND o.business_id = d.business_id
                         AND o.customer_id = d.customer_id
                         AND o.type = 'DELIVERY'
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
                   AND (
                       d.contact_name IS NOT NULL
                       OR d.contact_phone IS NOT NULL
                       OR d.delivery_address <> '[redacted]'
                       OR d.notes IS NOT NULL
                   )
                """, businessId, customerId);

        int deliveryOperationPiiScrubbed = jdbc.update("""
                UPDATE business_operation o
                   SET contact_name = NULL,
                       contact_phone = NULL,
                       delivery_address = NULL,
                       metadata_json = o.metadata_json - 'notes'
                 WHERE o.business_id = ?
                   AND o.customer_id = ?
                   AND o.type = 'DELIVERY'
                   AND o.status IN ('COMPLETED', 'CANCELLED', 'EXPIRED', 'FAILED')
                   AND (
                       o.contact_name IS NOT NULL
                       OR o.contact_phone IS NOT NULL
                       OR o.delivery_address IS NOT NULL
                       OR jsonb_exists(o.metadata_json, 'notes')
                   )
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
                businessRequestsScrubbed,
                requestOperationPiiScrubbed,
                businessLeadsScrubbed,
                leadOperationPiiScrubbed,
                businessQuotesScrubbed,
                quoteOperationPiiScrubbed,
                businessOrdersScrubbed,
                orderLineNotesScrubbed,
                orderOperationPiiScrubbed,
                businessDeliveriesScrubbed,
                deliveryOperationPiiScrubbed,
                identitiesDeleted);
    }

    public record Result(
            UUID customerId,
            int callSessionsScrubbed,
            int messagingConversationsScrubbed,
            int bookingNotesScrubbed,
            int bookingOperationMetadataScrubbed,
            int businessRequestsScrubbed,
            int requestOperationPiiScrubbed,
            int businessLeadsScrubbed,
            int leadOperationPiiScrubbed,
            int businessQuotesScrubbed,
            int quoteOperationPiiScrubbed,
            int businessOrdersScrubbed,
            int orderLineNotesScrubbed,
            int orderOperationPiiScrubbed,
            int businessDeliveriesScrubbed,
            int deliveryOperationPiiScrubbed,
            int identitiesDeleted
    ) {}
}

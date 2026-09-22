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
public class MessagingRetentionService {
    private final JdbcTemplate jdbc;
    private final TenantProvider tenantProvider;
    private final Clock clock;

    @Autowired
    public MessagingRetentionService(JdbcTemplate jdbc, TenantProvider tenantProvider) {
        this(jdbc, tenantProvider, Clock.systemUTC());
    }

    MessagingRetentionService(JdbcTemplate jdbc, TenantProvider tenantProvider, Clock clock) {
        this.jdbc = jdbc;
        this.tenantProvider = tenantProvider;
        this.clock = clock;
    }

    @Transactional
    public Result purgeExpiredMessagingForCurrentTenant() {
        UUID businessId = tenantProvider.requireBusinessId();
        DataRetentionPolicy.Cutoffs cutoffs = DataRetentionPolicy.cutoffs(clock.instant());

        int messages = jdbc.update("""
                DELETE FROM messaging_message m
                WHERE m.conversation_id IN (
                    SELECT c.id
                    FROM messaging_conversation c
                    WHERE c.business_id = ?
                      AND NOT EXISTS (
                          SELECT 1
                          FROM retention_legal_hold h
                          WHERE h.business_id = c.business_id
                            AND h.target_type = 'MESSAGING_CONVERSATION'
                            AND h.target_id = c.id
                            AND h.released_at IS NULL
                      )
                )
                  AND m.created_at < ?
                """, businessId, cutoffs.messageContentBefore());

        int conversations = jdbc.update("""
                DELETE FROM messaging_conversation c
                WHERE c.business_id = ?
                  AND c.last_message_at < ?
                  AND NOT EXISTS (
                      SELECT 1
                      FROM messaging_message m
                      WHERE m.conversation_id = c.id
                        AND m.created_at >= ?
                  )
                  AND NOT EXISTS (
                      SELECT 1
                      FROM retention_legal_hold h
                      WHERE h.business_id = c.business_id
                        AND h.target_type = 'MESSAGING_CONVERSATION'
                        AND h.target_id = c.id
                        AND h.released_at IS NULL
                  )
                """, businessId, cutoffs.conversationsBefore(), cutoffs.conversationsBefore());

        return new Result(
                cutoffs.messageContentBefore(),
                cutoffs.conversationsBefore(),
                messages,
                conversations);
    }

    public record Result(
            Instant messageCutoff,
            Instant conversationCutoff,
            int messagesDeleted,
            int conversationsDeleted
    ) {}
}

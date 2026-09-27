package cl.helvoca.observability;

import cl.helvoca.common.NotFoundException;
import cl.helvoca.security.TenantProvider;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class JourneyTraceService {
    static final String DUPLICATE_VISIBILITY =
            "REJECTED_DUPLICATE_ATTEMPTS_ARE_NOT_PERSISTED_BY_CURRENT_SOURCES";

    static final String TRACE_SQL = """
            WITH
            seed_calls AS (
                SELECT c.id
                  FROM call_session c
                 WHERE c.business_id = :businessId
                   AND (
                       c.id = CAST(:uuidIdentifier AS uuid)
                       OR c.provider_call_id = :identifier
                       OR c.stream_sid = :identifier
                   )
            ),
            seed_conversations AS (
                SELECT c.id
                  FROM messaging_conversation c
                 WHERE c.business_id = :businessId
                   AND c.id = CAST(:uuidIdentifier AS uuid)
                UNION
                SELECT m.conversation_id
                  FROM messaging_message m
                  JOIN messaging_conversation c ON c.id = m.conversation_id
                 WHERE c.business_id = :businessId
                   AND (
                       m.id = CAST(:uuidIdentifier AS uuid)
                       OR m.external_message_id = :identifier
                       OR m.provider_message_id = :identifier
                   )
            ),
            seed_jobs AS (
                SELECT j.operation_id
                  FROM persistent_job j
                 WHERE j.business_id = :businessId
                   AND (
                       j.id = CAST(:uuidIdentifier AS uuid)
                       OR j.idempotency_key = :identifier
                       OR j.payload ->> 'correlationId' = :identifier
                   )
            ),
            seed_outbound AS (
                SELECT m.operation_id
                  FROM outbound_message m
                 WHERE m.business_id = :businessId
                   AND (
                       m.id = CAST(:uuidIdentifier AS uuid)
                       OR m.idempotency_key = :identifier
                       OR m.provider_message_id = :identifier
                   )
            ),
            seed_webhooks AS (
                SELECT w.provider, w.external_id
                  FROM payment_webhook_event w
                 WHERE w.business_id = :businessId
                   AND (
                       w.id = CAST(:uuidIdentifier AS uuid)
                       OR w.event_id = :identifier
                       OR w.external_id = :identifier
                   )
            ),
            seed_webhook_operations AS (
                SELECT p.operation_id
                  FROM business_payment p
                  JOIN seed_webhooks w
                    ON lower(p.provider) = lower(w.provider)
                   AND p.external_id = w.external_id
                 WHERE p.business_id = :businessId
            ),
            base_ops AS (
                SELECT o.id
                  FROM business_operation o
                 WHERE o.business_id = :businessId
                   AND (
                       o.id = CAST(:uuidIdentifier AS uuid)
                       OR o.source_reference_id = CAST(:uuidIdentifier AS uuid)
                       OR o.source_reference_id IN (SELECT id FROM seed_calls)
                       OR o.source_reference_id IN (SELECT id FROM seed_conversations)
                   )
                UNION SELECT operation_id FROM seed_jobs WHERE operation_id IS NOT NULL
                UNION SELECT operation_id FROM seed_outbound WHERE operation_id IS NOT NULL
                UNION SELECT operation_id FROM seed_webhook_operations WHERE operation_id IS NOT NULL
            ),
            linked_once AS (
                SELECT id FROM base_ops
                UNION
                SELECT p.operation_id
                  FROM business_payment p
                 WHERE p.business_id = :businessId
                   AND p.target_operation_id IN (SELECT id FROM base_ops)
                UNION
                SELECT p.target_operation_id
                  FROM business_payment p
                 WHERE p.business_id = :businessId
                   AND p.operation_id IN (SELECT id FROM base_ops)
                UNION
                SELECT d.operation_id
                  FROM business_delivery d
                  JOIN business_order o
                    ON o.id = d.order_id
                   AND o.business_id = d.business_id
                 WHERE d.business_id = :businessId
                   AND o.operation_id IN (SELECT id FROM base_ops)
                UNION
                SELECT o.operation_id
                  FROM business_delivery d
                  JOIN business_order o
                    ON o.id = d.order_id
                   AND o.business_id = d.business_id
                 WHERE d.business_id = :businessId
                   AND d.operation_id IN (SELECT id FROM base_ops)
            ),
            ops AS (
                SELECT id FROM linked_once
                UNION
                SELECT p.operation_id
                  FROM business_payment p
                 WHERE p.business_id = :businessId
                   AND p.target_operation_id IN (SELECT id FROM linked_once)
                UNION
                SELECT p.target_operation_id
                  FROM business_payment p
                 WHERE p.business_id = :businessId
                   AND p.operation_id IN (SELECT id FROM linked_once)
                UNION
                SELECT d.operation_id
                  FROM business_delivery d
                  JOIN business_order o
                    ON o.id = d.order_id
                   AND o.business_id = d.business_id
                 WHERE d.business_id = :businessId
                   AND o.operation_id IN (SELECT id FROM linked_once)
                UNION
                SELECT o.operation_id
                  FROM business_delivery d
                  JOIN business_order o
                    ON o.id = d.order_id
                   AND o.business_id = d.business_id
                 WHERE d.business_id = :businessId
                   AND d.operation_id IN (SELECT id FROM linked_once)
            ),
            journey_calls AS (
                SELECT id FROM seed_calls
                UNION
                SELECT o.source_reference_id
                  FROM business_operation o
                 WHERE o.business_id = :businessId
                   AND o.id IN (SELECT id FROM ops)
                   AND o.source = 'VOICE'
                   AND o.source_reference_id IS NOT NULL
            ),
            journey_conversations AS (
                SELECT id FROM seed_conversations
                UNION
                SELECT o.source_reference_id
                  FROM business_operation o
                 WHERE o.business_id = :businessId
                   AND o.id IN (SELECT id FROM ops)
                   AND o.source = 'WHATSAPP'
                   AND o.source_reference_id IS NOT NULL
            ),
            timeline AS (
                SELECT c.started_at AS at,
                       'CALL'::text AS stage,
                       'CALL_STARTED'::text AS event,
                       c.status::text AS status,
                       c.id AS call_id,
                       NULL::uuid AS operation_id,
                       c.id AS resource_id,
                       c.id AS source_reference_id,
                       c.telephony_provider::text AS provider,
                       NULL::text AS actor,
                       NULL::text AS correlation_id,
                       NULL::integer AS attempt_no,
                       NULL::integer AS max_attempts,
                       NULL::text AS error_code,
                       NULL::bigint AS duration_ms,
                       NULL::integer AS delay_ms,
                       false AS recovered,
                       NULL::text AS transition
                  FROM call_session c
                  JOIN journey_calls j ON j.id = c.id
                 WHERE c.business_id = :businessId

                UNION ALL

                SELECT c.answered_at,
                       'CALL', 'CALL_ANSWERED', c.status::text,
                       c.id, NULL::uuid, c.id, c.id,
                       c.telephony_provider::text, NULL::text, NULL::text,
                       NULL::integer, NULL::integer, NULL::text,
                       (EXTRACT(EPOCH FROM (c.answered_at - c.started_at)) * 1000)::bigint,
                       NULL::integer, false, NULL::text
                  FROM call_session c
                  JOIN journey_calls j ON j.id = c.id
                 WHERE c.business_id = :businessId
                   AND c.answered_at IS NOT NULL

                UNION ALL

                SELECT c.ai_setup_completed_at,
                       'AI', 'AI_SETUP_COMPLETED', c.status::text,
                       c.id, NULL::uuid, c.id, c.id,
                       c.ai_provider::text, 'AI'::text, NULL::text,
                       NULL::integer, NULL::integer, NULL::text,
                       (EXTRACT(EPOCH FROM (c.ai_setup_completed_at - c.started_at)) * 1000)::bigint,
                       NULL::integer, false,
                       CASE WHEN c.ai_model IS NULL OR btrim(c.ai_model) = '' THEN NULL::text
                            ELSE ('model=' || c.ai_model)::text END
                  FROM call_session c
                  JOIN journey_calls j ON j.id = c.id
                 WHERE c.business_id = :businessId
                   AND c.ai_setup_completed_at IS NOT NULL

                UNION ALL

                SELECT a.created_at,
                       'TOOL', a.action_type::text,
                       CASE WHEN a.success THEN 'SUCCESS' ELSE 'FAILED' END,
                       a.call_id, NULL::uuid, a.id, a.call_id,
                       NULL::text, 'AI'::text, NULL::text,
                       NULL::integer, NULL::integer, a.error_code::text,
                       a.duration_ms, NULL::integer, false, NULL::text
                  FROM call_action a
                  JOIN journey_calls j ON j.id = a.call_id
                 WHERE a.business_id = :businessId

                UNION ALL

                SELECT m.created_at,
                       'WHATSAPP',
                       ('MESSAGE_' || upper(m.direction))::text,
                       COALESCE(m.provider_delivery_status, m.direction)::text,
                       NULL::uuid, NULL::uuid, m.id, m.conversation_id,
                       m.provider::text, m.role::text, NULL::text,
                       NULL::integer, NULL::integer, m.failure_code::text,
                       CASE WHEN m.sent_at IS NULL THEN NULL::bigint
                            ELSE (EXTRACT(EPOCH FROM (m.sent_at - m.created_at)) * 1000)::bigint END,
                       NULL::integer, false, NULL::text
                  FROM messaging_message m
                  JOIN messaging_conversation c ON c.id = m.conversation_id
                  JOIN journey_conversations j ON j.id = m.conversation_id
                 WHERE c.business_id = :businessId

                UNION ALL

                SELECT o.updated_at,
                       'OPERATION', 'OPERATION_STATE', o.status::text,
                       CASE WHEN o.source = 'VOICE' THEN o.source_reference_id ELSE NULL::uuid END,
                       o.id, o.id, o.source_reference_id,
                       NULL::text, NULL::text, NULL::text,
                       NULL::integer, NULL::integer, NULL::text,
                       (EXTRACT(EPOCH FROM (o.updated_at - o.created_at)) * 1000)::bigint,
                       NULL::integer, false, NULL::text
                  FROM business_operation o
                 WHERE o.business_id = :businessId
                   AND o.id IN (SELECT id FROM ops)

                UNION ALL

                SELECT e.created_at,
                       'OPERATION', e.event_type::text, e.status::text,
                       CASE WHEN e.channel = 'VOICE' THEN e.source_reference_id ELSE NULL::uuid END,
                       e.operation_id, e.id, e.source_reference_id,
                       NULL::text, e.actor_type::text, NULL::text,
                       NULL::integer, NULL::integer, NULL::text,
                       NULL::bigint, NULL::integer, false,
                       CASE WHEN e.previous_status IS NULL THEN NULL::text
                            ELSE e.previous_status::text || ' -> ' || e.status::text END
                  FROM business_operation_event e
                 WHERE e.business_id = :businessId
                   AND e.operation_id IN (SELECT id FROM ops)

                UNION ALL

                SELECT r.created_at,
                       'RETRY', r.outcome::text, r.outcome::text,
                       CASE WHEN r.source_reference_id IN (SELECT id FROM journey_calls)
                            THEN r.source_reference_id ELSE NULL::uuid END,
                       r.operation_id, r.id, r.source_reference_id,
                       NULL::text, 'SYSTEM'::text, NULL::text,
                       r.attempt_no, r.max_attempts, r.error_code::text,
                       NULL::bigint, r.delay_ms,
                       (r.outcome = 'SUCCEEDED_AFTER_RETRY'),
                       r.failure_class::text
                  FROM business_operation_retry_attempt r
                 WHERE r.business_id = :businessId
                   AND (
                       r.operation_id IN (SELECT id FROM ops)
                       OR r.source_reference_id IN (SELECT id FROM journey_calls)
                       OR r.source_reference_id IN (SELECT id FROM journey_conversations)
                   )

                UNION ALL

                SELECT j.updated_at,
                       'JOB', j.job_type::text, j.status::text,
                       CASE WHEN o.source = 'VOICE' THEN o.source_reference_id ELSE NULL::uuid END,
                       j.operation_id, j.id, o.source_reference_id,
                       NULL::text, 'SYSTEM'::text,
                       j.payload ->> 'correlationId',
                       j.attempt_count, j.max_attempts, j.last_error_code::text,
                       (EXTRACT(EPOCH FROM (COALESCE(j.completed_at, j.updated_at) - j.created_at)) * 1000)::bigint,
                       NULL::integer,
                       (j.status = 'SUCCEEDED' AND j.attempt_count > 1),
                       NULL::text
                  FROM persistent_job j
                  LEFT JOIN business_operation o
                    ON o.id = j.operation_id
                   AND o.business_id = j.business_id
                 WHERE j.business_id = :businessId
                   AND (
                       j.operation_id IN (SELECT id FROM ops)
                       OR j.id = CAST(:uuidIdentifier AS uuid)
                       OR j.idempotency_key = :identifier
                       OR j.payload ->> 'correlationId' = :identifier
                   )

                UNION ALL

                SELECT p.updated_at,
                       'PAYMENT', 'PAYMENT_STATE', p.status::text,
                       CASE WHEN o.source = 'VOICE' THEN o.source_reference_id ELSE NULL::uuid END,
                       p.operation_id, p.id, p.source_reference_id,
                       p.provider::text, 'PROVIDER'::text, NULL::text,
                       NULL::integer, NULL::integer,
                       CASE WHEN p.status = 'FAILED' THEN 'PAYMENT_FAILED' ELSE NULL::text END,
                       (EXTRACT(EPOCH FROM (p.updated_at - p.created_at)) * 1000)::bigint,
                       NULL::integer, false,
                       ('target=' || p.target_operation_id::text)::text
                  FROM business_payment p
                  LEFT JOIN business_operation o
                    ON o.id = p.operation_id
                   AND o.business_id = p.business_id
                 WHERE p.business_id = :businessId
                   AND p.operation_id IN (SELECT id FROM ops)

                UNION ALL

                SELECT w.received_at,
                       'WEBHOOK', 'PAYMENT_WEBHOOK', w.status::text,
                       CASE WHEN o.source = 'VOICE' THEN o.source_reference_id ELSE NULL::uuid END,
                       p.operation_id, w.id, p.source_reference_id,
                       w.provider::text, 'PROVIDER'::text, NULL::text,
                       NULL::integer, NULL::integer,
                       CASE WHEN w.status = 'FAILED' THEN 'PAYMENT_WEBHOOK_FAILED' ELSE NULL::text END,
                       CASE WHEN w.processed_at IS NULL THEN NULL::bigint
                            ELSE (EXTRACT(EPOCH FROM (w.processed_at - w.received_at)) * 1000)::bigint END,
                       NULL::integer, false, NULL::text
                  FROM payment_webhook_event w
                  JOIN business_payment p
                    ON p.business_id = w.business_id
                   AND lower(p.provider) = lower(w.provider)
                   AND p.external_id = w.external_id
                  LEFT JOIN business_operation o
                    ON o.id = p.operation_id
                   AND o.business_id = p.business_id
                 WHERE w.business_id = :businessId
                   AND p.operation_id IN (SELECT id FROM ops)

                UNION ALL

                SELECT m.updated_at,
                       'OUTBOUND', m.purpose::text, m.status::text,
                       CASE WHEN o.source = 'VOICE' THEN o.source_reference_id ELSE NULL::uuid END,
                       m.operation_id, m.id, o.source_reference_id,
                       m.provider::text, 'SYSTEM'::text, NULL::text,
                       m.retry_count, NULL::integer, m.failure_code::text,
                       (EXTRACT(EPOCH FROM (COALESCE(m.sent_at, m.updated_at) - m.created_at)) * 1000)::bigint,
                       NULL::integer,
                       (m.status = 'SENT' AND m.retry_count > 0),
                       m.provider_delivery_status::text
                  FROM outbound_message m
                  LEFT JOIN business_operation o
                    ON o.id = m.operation_id
                   AND o.business_id = m.business_id
                 WHERE m.business_id = :businessId
                   AND (
                       m.operation_id IN (SELECT id FROM ops)
                       OR m.id = CAST(:uuidIdentifier AS uuid)
                       OR m.idempotency_key = :identifier
                       OR m.provider_message_id = :identifier
                   )

                UNION ALL

                SELECT u.occurred_at,
                       'USAGE', u.meter_key::text, u.unit::text,
                       CASE WHEN u.source_type = 'CALL_SESSION'
                                  AND u.source_id IN (SELECT id::text FROM journey_calls)
                            THEN CAST(u.source_id AS uuid)
                            ELSE NULL::uuid END,
                       NULL::uuid, u.id,
                       CASE WHEN u.source_type = 'CALL_SESSION'
                                  AND u.source_id IN (SELECT id::text FROM journey_calls)
                            THEN CAST(u.source_id AS uuid)
                            ELSE NULL::uuid END,
                       u.provider::text, 'SYSTEM'::text, NULL::text,
                       NULL::integer, NULL::integer, NULL::text,
                       NULL::bigint, NULL::integer, false,
                       (
                           'quantity=' || u.quantity::text || ' ' || u.unit::text
                           || CASE WHEN u.estimated_cost_usd IS NULL THEN ''
                                   ELSE ' estimated_cost_usd=' || u.estimated_cost_usd::text END
                           || CASE WHEN u.actual_cost_usd IS NULL THEN ''
                                   ELSE ' actual_cost_usd=' || u.actual_cost_usd::text END
                       )::text
                  FROM usage_meter_event u
                 WHERE u.business_id = :businessId
                   AND (
                       (u.source_type = 'CALL_SESSION'
                            AND u.source_id IN (SELECT id::text FROM journey_calls))
                       OR u.source_id IN (SELECT id::text FROM ops)
                   )

                UNION ALL

                SELECT c.ended_at,
                       'CALL', 'CALL_ENDED', c.status::text,
                       c.id, NULL::uuid, c.id, c.id,
                       c.telephony_provider::text, NULL::text, NULL::text,
                       NULL::integer, NULL::integer,
                       CASE WHEN c.status = 'FAILED' THEN 'CALL_FAILED' ELSE NULL::text END,
                       (EXTRACT(EPOCH FROM (c.ended_at - c.started_at)) * 1000)::bigint,
                       NULL::integer, false, c.resolution::text
                  FROM call_session c
                  JOIN journey_calls j ON j.id = c.id
                 WHERE c.business_id = :businessId
                   AND c.ended_at IS NOT NULL
            )
            SELECT *
              FROM timeline
             WHERE at IS NOT NULL
             ORDER BY at ASC, stage ASC, event ASC, resource_id ASC
            """;

    private static final RowMapper<EventView> EVENT_MAPPER = JourneyTraceService::mapEvent;

    private final NamedParameterJdbcTemplate jdbc;
    private final TenantProvider tenantProvider;

    public JourneyTraceService(NamedParameterJdbcTemplate jdbc, TenantProvider tenantProvider) {
        this.jdbc = jdbc;
        this.tenantProvider = tenantProvider;
    }

    @Transactional(readOnly = true)
    public TraceView get(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("identifier is required");
        }

        UUID businessId = tenantProvider.requireBusinessId();
        String normalized = identifier.trim();
        UUID uuidIdentifier = parseUuid(normalized);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("businessId", businessId)
                .addValue("identifier", normalized)
                .addValue("uuidIdentifier", uuidIdentifier);

        List<EventView> timeline = jdbc.query(TRACE_SQL, params, EVENT_MAPPER);
        if (timeline.isEmpty()) throw new NotFoundException("Journey trace not found");

        List<UUID> callIds = distinctUuid(timeline.stream().map(EventView::callId).toList());
        List<UUID> operationIds = distinctUuid(timeline.stream().map(EventView::operationId).toList());
        List<UUID> sourceReferenceIds = distinctUuid(timeline.stream().map(EventView::sourceReferenceId).toList());
        List<String> providers = distinctString(timeline.stream().map(EventView::provider).toList());
        List<String> correlationIds = distinctString(timeline.stream().map(EventView::correlationId).toList());

        int observedRetries = 0;
        boolean recoveredAutomatically = false;
        String failureStage = null;
        for (EventView event : timeline) {
            if ("RETRY".equals(event.stage()) && "RETRY_SCHEDULED".equals(event.event())) observedRetries++;
            if ("JOB".equals(event.stage()) && event.attemptNo() != null && event.attemptNo() > 1) {
                observedRetries += event.attemptNo() - 1;
            }
            recoveredAutomatically |= event.recoveredAutomatically();
            if (isFailure(event)) failureStage = event.stage() + ":" + event.event();
        }

        long elapsedMs = timeline.size() < 2
                ? 0L
                : Math.max(0L, Duration.between(timeline.getFirst().at(), timeline.getLast().at()).toMillis());

        return new TraceView(
                normalized,
                callIds,
                operationIds,
                sourceReferenceIds,
                correlationIds,
                providers,
                observedRetries,
                recoveredAutomatically,
                failureStage,
                elapsedMs,
                DUPLICATE_VISIBILITY,
                timeline);
    }

    private static boolean isFailure(EventView event) {
        if (event.errorCode() != null && !event.errorCode().isBlank()) return true;
        if ("FAILED".equals(event.status()) || "DEAD_LETTER".equals(event.status()) || "BLOCKED".equals(event.status())) {
            return true;
        }
        return "RETRIES_EXHAUSTED".equals(event.event()) || "UNRESOLVABLE".equals(event.event());
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static List<UUID> distinctUuid(List<UUID> values) {
        Set<UUID> seen = new LinkedHashSet<>();
        for (UUID value : values) if (value != null) seen.add(value);
        return List.copyOf(seen);
    }

    private static List<String> distinctString(List<String> values) {
        Set<String> seen = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) seen.add(value);
        }
        return List.copyOf(seen);
    }

    private static EventView mapEvent(ResultSet rs, int rowNum) throws SQLException {
        return new EventView(
                instant(rs, "at"),
                rs.getString("stage"),
                rs.getString("event"),
                rs.getString("status"),
                uuid(rs, "call_id"),
                uuid(rs, "operation_id"),
                uuid(rs, "resource_id"),
                uuid(rs, "source_reference_id"),
                rs.getString("provider"),
                rs.getString("actor"),
                rs.getString("correlation_id"),
                integer(rs, "attempt_no"),
                integer(rs, "max_attempts"),
                rs.getString("error_code"),
                longValue(rs, "duration_ms"),
                integer(rs, "delay_ms"),
                rs.getBoolean("recovered"),
                rs.getString("transition"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static UUID uuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }

    private static Integer integer(ResultSet rs, String column) throws SQLException {
        Number value = (Number) rs.getObject(column);
        return value == null ? null : value.intValue();
    }

    private static Long longValue(ResultSet rs, String column) throws SQLException {
        Number value = (Number) rs.getObject(column);
        return value == null ? null : value.longValue();
    }

    public record TraceView(
            String identifier,
            List<UUID> callIds,
            List<UUID> operationIds,
            List<UUID> sourceReferenceIds,
            List<String> correlationIds,
            List<String> providers,
            int observedRetries,
            boolean recoveredAutomatically,
            String failureStage,
            long elapsedMs,
            String duplicateObservation,
            List<EventView> timeline) {
    }

    public record EventView(
            Instant at,
            String stage,
            String event,
            String status,
            UUID callId,
            UUID operationId,
            UUID resourceId,
            UUID sourceReferenceId,
            String provider,
            String actor,
            String correlationId,
            Integer attemptNo,
            Integer maxAttempts,
            String errorCode,
            Long durationMs,
            Integer delayMs,
            boolean recoveredAutomatically,
            String transition) {
    }
}

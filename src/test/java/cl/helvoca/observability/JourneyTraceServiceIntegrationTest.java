package cl.helvoca.observability;

import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers
class JourneyTraceServiceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private JdbcTemplate jdbc;
    private TenantProvider tenantProvider;
    private JourneyTraceService service;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP SCHEMA public CASCADE");
        jdbc.execute("CREATE SCHEMA public");
        createSchema();

        tenantProvider = mock(TenantProvider.class);
        service = new JourneyTraceService(new NamedParameterJdbcTemplate(dataSource), tenantProvider);
    }

    @Test
    void reconstructsCrossSubsystemJourneyAndNeverLeaksAnotherTenant() {
        UUID businessId = UUID.randomUUID();
        UUID otherBusinessId = UUID.randomUUID();
        UUID callId = UUID.randomUUID();
        UUID otherCallId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        UUID paymentOperationId = UUID.randomUUID();
        String sharedProviderCallId = "provider-shared";
        Instant base = Instant.parse("2026-09-27T05:00:00Z");

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);

        insertCall(businessId, callId, sharedProviderCallId, base, "twilio", "openai");
        insertCall(otherBusinessId, otherCallId, sharedProviderCallId, base.plusSeconds(30), "other-provider", "other-ai");

        jdbc.update("""
                INSERT INTO call_action
                    (id, business_id, call_id, action_type, success, error_code, duration_ms, created_at)
                VALUES (?, ?, ?, 'ORDER_CREATED', true, NULL, 125, ?)
                """, UUID.randomUUID(), businessId, callId, ts(base.plusSeconds(3)));

        jdbc.update("""
                INSERT INTO call_action
                    (id, business_id, call_id, action_type, success, error_code, created_at)
                VALUES (?, ?, ?, 'SHOULD_NOT_LEAK', true, NULL, ?)
                """, UUID.randomUUID(), otherBusinessId, otherCallId, ts(base.plusSeconds(31)));

        insertOperation(businessId, operationId, callId, "ORDER", base.plusSeconds(4), base.plusSeconds(6));
        insertOperation(businessId, paymentOperationId, callId, "PAYMENT", base.plusSeconds(5), base.plusSeconds(9));

        jdbc.update("""
                INSERT INTO business_operation_event
                    (id, business_id, operation_id, event_type, status, channel,
                     source_reference_id, actor_type, created_at, previous_status)
                VALUES (?, ?, ?, 'ORDER_CONFIRMED', 'CONFIRMED', 'VOICE',
                        ?, 'AI', ?, 'AWAITING_CONFIRMATION')
                """, UUID.randomUUID(), businessId, operationId, callId, ts(base.plusSeconds(5)));

        jdbc.update("""
                INSERT INTO business_operation_retry_attempt
                    (id, business_id, source_reference_id, operation_id, outcome,
                     attempt_no, max_attempts, error_code, created_at, delay_ms, failure_class)
                VALUES (?, ?, ?, ?, 'RETRY_SCHEDULED', 1, 3,
                        'TEMPORARY_BACKEND_FAILURE', ?, 100, 'TRANSIENT')
                """, UUID.randomUUID(), businessId, callId, operationId, ts(base.plusMillis(4500)));

        jdbc.update("""
                INSERT INTO business_operation_retry_attempt
                    (id, business_id, source_reference_id, operation_id, outcome,
                     attempt_no, max_attempts, error_code, created_at, delay_ms, failure_class)
                VALUES (?, ?, ?, ?, 'SUCCEEDED_AFTER_RETRY', 2, 3,
                        NULL, ?, 0, NULL)
                """, UUID.randomUUID(), businessId, callId, operationId, ts(base.plusMillis(4800)));

        jdbc.update("""
                INSERT INTO persistent_job
                    (id, business_id, operation_id, idempotency_key, payload, updated_at,
                     job_type, status, attempt_count, max_attempts, last_error_code,
                     completed_at, created_at)
                VALUES (?, ?, ?, 'dispatch:test', CAST(? AS jsonb), ?,
                        'OUTBOUND_MESSAGE_DISPATCH', 'SUCCEEDED', 2, 5, NULL, ?, ?)
                """, UUID.randomUUID(), businessId, operationId,
                "{\"correlationId\":\"corr-abc\"}",
                ts(base.plusSeconds(7)), ts(base.plusSeconds(7)), ts(base.plusSeconds(6)));

        jdbc.update("""
                INSERT INTO business_payment
                    (id, business_id, operation_id, target_operation_id, source_reference_id,
                     provider, external_id, status, updated_at, created_at)
                VALUES (?, ?, ?, ?, ?, 'mercadopago', 'pay-external', 'SUCCEEDED', ?, ?)
                """, UUID.randomUUID(), businessId, paymentOperationId, operationId, callId,
                ts(base.plusSeconds(9)), ts(base.plusSeconds(5)));

        jdbc.update("""
                INSERT INTO payment_webhook_event
                    (id, business_id, provider, event_id, external_id, status, received_at, processed_at)
                VALUES (?, ?, 'mercadopago', 'evt-payment', 'pay-external',
                        'PROCESSED', ?, ?)
                """, UUID.randomUUID(), businessId, ts(base.plusSeconds(8)), ts(base.plusSeconds(9)));

        jdbc.update("""
                INSERT INTO outbound_message
                    (id, business_id, operation_id, idempotency_key, provider_message_id,
                     updated_at, purpose, status, provider, retry_count, failure_code,
                     sent_at, created_at, provider_delivery_status)
                VALUES (?, ?, ?, 'outbound:test', 'wamid.test', ?,
                        'ORDER_STATUS', 'SENT', 'meta', 1, NULL, ?, ?, 'delivered')
                """, UUID.randomUUID(), businessId, operationId, ts(base.plusSeconds(8)),
                ts(base.plusSeconds(8)), ts(base.plusSeconds(6)));

        jdbc.update("""
                INSERT INTO usage_meter_event
                    (id, business_id, meter_key, quantity, unit, estimated_cost_usd,
                     actual_cost_usd, source_type, source_id, provider, occurred_at, sequence_no)
                VALUES (?, ?, 'VOICE_SECONDS', 10, 'SECONDS', 0.01500000,
                        NULL, 'CALL_SESSION', ?, 'twilio', ?, 1)
                """, UUID.randomUUID(), businessId, callId.toString(), ts(base.plusSeconds(10)));

        JourneyTraceService.TraceView trace = service.get(sharedProviderCallId);

        assertEquals(Set.of(callId), Set.copyOf(trace.callIds()));
        assertFalse(trace.callIds().contains(otherCallId));
        assertEquals(Set.of(operationId, paymentOperationId), Set.copyOf(trace.operationIds()));
        assertEquals(Set.of("corr-abc"), Set.copyOf(trace.correlationIds()));
        assertTrue(trace.providers().containsAll(Set.of("twilio", "openai", "mercadopago", "meta")));
        assertEquals(2, trace.observedRetries());
        assertTrue(trace.recoveredAutomatically());
        assertEquals("RETRY:RETRY_SCHEDULED", trace.failureStage());
        assertEquals(10_000L, trace.elapsedMs());

        Set<String> stages = trace.timeline().stream()
                .map(JourneyTraceService.EventView::stage)
                .collect(Collectors.toSet());
        assertTrue(stages.containsAll(Set.of(
                "CALL", "AI", "TOOL", "OPERATION", "RETRY",
                "JOB", "PAYMENT", "WEBHOOK", "OUTBOUND", "USAGE")));
        assertTrue(trace.timeline().stream()
                .anyMatch(event -> "AI_SETUP_COMPLETED".equals(event.event())
                        && "model=gpt-realtime-2.1".equals(event.transition())));
        assertTrue(trace.timeline().stream()
                .anyMatch(event -> "ORDER_CREATED".equals(event.event())
                        && Long.valueOf(125L).equals(event.durationMs())));
        assertTrue(trace.timeline().stream()
                .anyMatch(event -> "VOICE_SECONDS".equals(event.event())
                        && event.transition() != null
                        && event.transition().contains("quantity=10")
                        && event.transition().contains("estimated_cost_usd=0.01500000")));
        assertTrue(trace.timeline().stream().noneMatch(event -> "SHOULD_NOT_LEAK".equals(event.event())));
        verify(tenantProvider).requireBusinessId();
    }

    private void createSchema() {
        String[] statements = {
                """
                CREATE TABLE call_session (
                    id uuid PRIMARY KEY, business_id uuid NOT NULL, provider_call_id text,
                    stream_sid text, started_at timestamptz NOT NULL, answered_at timestamptz,
                    ended_at timestamptz, ai_setup_completed_at timestamptz, status text,
                    telephony_provider text, ai_provider text, ai_model text, resolution text
                )
                """,
                """
                CREATE TABLE call_action (
                    id uuid PRIMARY KEY, business_id uuid NOT NULL, call_id uuid NOT NULL,
                    action_type text, success boolean, error_code text, duration_ms bigint,
                    created_at timestamptz
                )
                """,
                """
                CREATE TABLE usage_meter_event (
                    id uuid PRIMARY KEY, sequence_no bigint NOT NULL, business_id uuid NOT NULL,
                    meter_key text NOT NULL, quantity numeric NOT NULL, unit text NOT NULL,
                    estimated_cost_usd numeric, actual_cost_usd numeric, source_type text NOT NULL,
                    source_id text NOT NULL, provider text, occurred_at timestamptz NOT NULL
                )
                """,
                """
                CREATE TABLE messaging_conversation (
                    id uuid PRIMARY KEY, business_id uuid NOT NULL
                )
                """,
                """
                CREATE TABLE messaging_message (
                    id uuid PRIMARY KEY, conversation_id uuid NOT NULL, external_message_id text,
                    provider_message_id text, direction text, provider_delivery_status text,
                    created_at timestamptz, sent_at timestamptz, provider text, role text,
                    failure_code text
                )
                """,
                """
                CREATE TABLE business_operation (
                    id uuid PRIMARY KEY, business_id uuid NOT NULL, source_reference_id uuid,
                    source text, status text, created_at timestamptz, updated_at timestamptz
                )
                """,
                """
                CREATE TABLE business_operation_event (
                    id uuid PRIMARY KEY, business_id uuid NOT NULL, operation_id uuid NOT NULL,
                    event_type text, status text, channel text, source_reference_id uuid,
                    actor_type text, created_at timestamptz, previous_status text
                )
                """,
                """
                CREATE TABLE business_operation_retry_attempt (
                    id uuid PRIMARY KEY, business_id uuid NOT NULL, source_reference_id uuid,
                    operation_id uuid, outcome text, attempt_no integer, max_attempts integer,
                    error_code text, created_at timestamptz, delay_ms integer, failure_class text
                )
                """,
                """
                CREATE TABLE persistent_job (
                    id uuid PRIMARY KEY, business_id uuid NOT NULL, operation_id uuid,
                    idempotency_key text, payload jsonb, updated_at timestamptz, job_type text,
                    status text, attempt_count integer, max_attempts integer, last_error_code text,
                    completed_at timestamptz, created_at timestamptz
                )
                """,
                """
                CREATE TABLE business_payment (
                    id uuid PRIMARY KEY, business_id uuid NOT NULL, operation_id uuid NOT NULL,
                    target_operation_id uuid NOT NULL, source_reference_id uuid, provider text,
                    external_id text, status text, updated_at timestamptz, created_at timestamptz
                )
                """,
                """
                CREATE TABLE payment_webhook_event (
                    id uuid PRIMARY KEY, business_id uuid NOT NULL, provider text, event_id text,
                    external_id text, status text, received_at timestamptz, processed_at timestamptz
                )
                """,
                """
                CREATE TABLE outbound_message (
                    id uuid PRIMARY KEY, business_id uuid NOT NULL, operation_id uuid,
                    idempotency_key text, provider_message_id text, updated_at timestamptz,
                    purpose text, status text, provider text, retry_count integer,
                    failure_code text, sent_at timestamptz, created_at timestamptz,
                    provider_delivery_status text
                )
                """,
                """
                CREATE TABLE business_delivery (
                    id uuid PRIMARY KEY, business_id uuid NOT NULL, operation_id uuid NOT NULL,
                    order_id uuid
                )
                """,
                """
                CREATE TABLE business_order (
                    id uuid PRIMARY KEY, business_id uuid NOT NULL, operation_id uuid NOT NULL
                )
                """
        };
        for (String statement : statements) jdbc.execute(statement);
    }

    private void insertCall(UUID businessId,
                            UUID callId,
                            String providerCallId,
                            Instant start,
                            String telephonyProvider,
                            String aiProvider) {
        jdbc.update("""
                INSERT INTO call_session
                    (id, business_id, provider_call_id, stream_sid, started_at, answered_at,
                     ended_at, ai_setup_completed_at, status, telephony_provider, ai_provider, ai_model, resolution)
                VALUES (?, ?, ?, NULL, ?, ?, ?, ?, 'COMPLETED', ?, ?, ?, 'ORDER_CREATED')
                """, callId, businessId, providerCallId, ts(start), ts(start.plusSeconds(1)),
                ts(start.plusSeconds(10)), ts(start.plusSeconds(2)), telephonyProvider, aiProvider,
                "openai".equals(aiProvider) ? "gpt-realtime-2.1" : "other-model");
    }

    private void insertOperation(UUID businessId,
                                 UUID operationId,
                                 UUID sourceReferenceId,
                                 String type,
                                 Instant createdAt,
                                 Instant updatedAt) {
        jdbc.update("""
                INSERT INTO business_operation
                    (id, business_id, source_reference_id, source, status, created_at, updated_at)
                VALUES (?, ?, ?, 'VOICE', ?, ?, ?)
                """, operationId, businessId, sourceReferenceId,
                "PAYMENT".equals(type) ? "COMPLETED" : "CONFIRMED",
                ts(createdAt), ts(updatedAt));
    }

    private static Timestamp ts(Instant instant) {
        return Timestamp.from(instant);
    }
}

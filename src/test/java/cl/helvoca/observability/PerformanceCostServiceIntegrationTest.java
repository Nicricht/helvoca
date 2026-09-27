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

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers
class PerformanceCostServiceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private JdbcTemplate jdbc;
    private TenantProvider tenants;
    private PerformanceCostService service;
    private UUID businessId;
    private UUID otherBusinessId;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP SCHEMA public CASCADE");
        jdbc.execute("CREATE SCHEMA public");
        createSchema();

        tenants = mock(TenantProvider.class);
        PerformanceCostProperties properties = new PerformanceCostProperties();
        properties.setRegressionPercent(new BigDecimal("25"));
        service = new PerformanceCostService(
                new NamedParameterJdbcTemplate(dataSource), tenants, properties);

        businessId = UUID.randomUUID();
        otherBusinessId = UUID.randomUUID();
        when(tenants.requireBusinessId()).thenReturn(businessId);
    }

    @Test
    void aggregatesPersistedPerformanceCostSignalsAndKeepsTenantIsolation() {
        Instant from = Instant.parse("2026-09-27T00:00:00Z");
        Instant to = Instant.parse("2026-09-27T01:00:00Z");
        Instant previous = from.minusSeconds(3600);

        UUID previousCall = insertCall(
                businessId, previous.plusSeconds(60), 60, 500, 1000,
                "0.010000", "0.020000");
        insertTool(businessId, previousCall, previous.plusSeconds(70), true);

        UUID callId = insertCall(
                businessId, from.plusSeconds(60), 90, 700, 2000,
                "0.030000", "0.060000");
        insertTool(businessId, callId, from.plusSeconds(70), true);
        insertTool(businessId, callId, from.plusSeconds(71), true);
        insertTool(businessId, callId, from.plusSeconds(72), true);
        insertTool(businessId, callId, from.plusSeconds(73), false);

        jdbc.update("""
                INSERT INTO business_operation_retry_attempt
                    (id, business_id, outcome, created_at)
                VALUES (?, ?, 'RETRY_SCHEDULED', ?)
                """, UUID.randomUUID(), businessId, ts(from.plusSeconds(80)));

        jdbc.update("""
                INSERT INTO persistent_job
                    (id, business_id, created_at, updated_at, completed_at,
                     status, attempt_count)
                VALUES (?, ?, ?, ?, ?, 'SUCCEEDED', 2)
                """, UUID.randomUUID(), businessId,
                ts(from.plusSeconds(100)), ts(from.plusSeconds(103)), ts(from.plusSeconds(103)));

        UUID conversationId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO messaging_conversation(id, business_id, opened_at)
                VALUES (?, ?, ?)
                """, conversationId, businessId, ts(from.plusSeconds(120)));
        jdbc.update("""
                INSERT INTO messaging_message
                    (id, conversation_id, provider, provider_message_id, failure_code, created_at)
                VALUES (?, ?, 'META_WHATSAPP_CLOUD', 'wamid.1', NULL, ?)
                """, UUID.randomUUID(), conversationId, ts(from.plusSeconds(125)));

        jdbc.update("""
                INSERT INTO outbound_message
                    (id, business_id, provider, sent_at, failure_code, retry_count, created_at)
                VALUES (?, ?, 'meta', ?, NULL, 1, ?)
                """, UUID.randomUUID(), businessId,
                ts(from.plusSeconds(131)), ts(from.plusSeconds(130)));

        jdbc.update("""
                INSERT INTO payment_webhook_event
                    (id, business_id, provider, received_at, processed_at)
                VALUES (?, ?, 'mercadopago', ?, ?)
                """, UUID.randomUUID(), businessId,
                ts(from.plusSeconds(140)), ts(from.plusMillis(140500)));

        insertUsage(businessId, "AI_INPUT_TOKENS", "120", null, null, from.plusSeconds(150));
        insertUsage(businessId, "AI_OUTPUT_TOKENS", "30", null, null, from.plusSeconds(151));
        insertUsage(businessId, "PROVIDER_COST", "1", "0.01000000", "0.00800000", from.plusSeconds(152));

        UUID leakedCall = insertCall(
                otherBusinessId, from.plusSeconds(30), 999, 9000, 12000,
                "9.000000", "9.000000");
        insertTool(otherBusinessId, leakedCall, from.plusSeconds(31), false);

        PerformanceCostService.Snapshot snapshot = service.snapshot(from, to);
        PerformanceCostService.PeriodMetrics current = snapshot.current();

        assertEquals(1, current.calls());
        assertEquals(4, current.toolCalls());
        assertEquals(1, current.failedToolCalls());
        assertEquals(1, current.retryEvents());
        assertEquals(1, current.jobRetries());
        assertEquals(4, current.providerInteractionsObserved());
        assertEquals(2, current.observedJourneys());
        assertEquals(0, current.aiInputTokens().compareTo(new BigDecimal("120")));
        assertEquals(0, current.aiOutputTokens().compareTo(new BigDecimal("30")));
        assertEquals(0, current.aiTotalTokens().compareTo(new BigDecimal("150")));
        assertEquals(0, current.estimatedTelephonyCostUsd().compareTo(new BigDecimal("0.030000")));
        assertEquals(0, current.estimatedAiCostUsd().compareTo(new BigDecimal("0.060000")));
        assertEquals(0, current.estimatedOtherCostUsd().compareTo(new BigDecimal("0.01000000")));
        assertEquals(0, current.estimatedCostUsdPerJourney().compareTo(new BigDecimal("0.05000000")));
        assertEquals(0, current.actualCostUsd().compareTo(new BigDecimal("0.00800000")));
        assertTrue(snapshot.coverage().aiTokens());
        assertTrue(snapshot.coverage().actualProviderCost());
        assertFalse(snapshot.coverage().criticalQueryLatency());

        Map<String, PerformanceCostService.StageLatency> stages = snapshot.stageLatencies().stream()
                .collect(Collectors.toMap(PerformanceCostService.StageLatency::stage, value -> value));
        assertEquals(700, stages.get("CALL_ANSWER").p95Ms());
        assertEquals(2000, stages.get("AI_SETUP").p95Ms());
        assertEquals(3000, stages.get("JOB_COMPLETION").p95Ms());
        assertEquals(1000, stages.get("OUTBOUND_ACCEPTANCE").p95Ms());
        assertEquals(500, stages.get("PAYMENT_WEBHOOK").p95Ms());

        assertEquals(1, snapshot.previous().calls());
        assertTrue(snapshot.budgets().stream().anyMatch(check ->
                "P95_AI_SETUP_MS".equals(check.metric())
                        && "REGRESSION".equals(check.status())
                        && "Relative previous-period budget".equals(check.reason())));

        verify(tenants).requireBusinessId();
    }

    @Test
    void certificationAndSimulatorCallsDoNotPolluteProductionBudget() {
        Instant from = Instant.parse("2026-09-27T00:00:00Z");
        Instant to = Instant.parse("2026-09-27T01:00:00Z");

        insertExcludedCall(businessId, from.plusSeconds(10), true, "twilio");
        insertExcludedCall(businessId, from.plusSeconds(20), false, "simulator");

        PerformanceCostService.Snapshot snapshot = service.snapshot(from, to);

        assertEquals(0, snapshot.current().calls());
        assertEquals(0, snapshot.current().observedJourneys());
        assertEquals(0, snapshot.current().toolCallsPerJourney().compareTo(BigDecimal.ZERO));
    }

    private UUID insertCall(UUID tenant,
                            Instant startedAt,
                            int durationSeconds,
                            long answerLatencyMs,
                            long aiSetupLatencyMs,
                            String telephonyCost,
                            String aiCost) {
        UUID callId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO call_session
                    (id, business_id, started_at, answered_at, ai_setup_completed_at,
                     ended_at, duration_seconds, certification, telephony_provider,
                     estimated_telephony_cost_usd, estimated_ai_cost_usd, estimated_total_cost_usd)
                VALUES (?, ?, ?, ?, ?, ?, ?, false, 'twilio', ?, ?, ?)
                """,
                callId, tenant, ts(startedAt),
                ts(startedAt.plusMillis(answerLatencyMs)),
                ts(startedAt.plusMillis(aiSetupLatencyMs)),
                ts(startedAt.plusSeconds(durationSeconds)),
                durationSeconds,
                new BigDecimal(telephonyCost),
                new BigDecimal(aiCost),
                new BigDecimal(telephonyCost).add(new BigDecimal(aiCost)));
        return callId;
    }

    private void insertExcludedCall(UUID tenant, Instant startedAt, boolean certification, String provider) {
        jdbc.update("""
                INSERT INTO call_session
                    (id, business_id, started_at, duration_seconds, certification,
                     telephony_provider, estimated_telephony_cost_usd,
                     estimated_ai_cost_usd, estimated_total_cost_usd)
                VALUES (?, ?, ?, 10, ?, ?, 1, 1, 2)
                """, UUID.randomUUID(), tenant, ts(startedAt), certification, provider);
    }

    private void insertTool(UUID tenant, UUID callId, Instant at, boolean success) {
        jdbc.update("""
                INSERT INTO call_action(id, business_id, call_id, success, created_at)
                VALUES (?, ?, ?, ?, ?)
                """, UUID.randomUUID(), tenant, callId, success, ts(at));
    }

    private void insertUsage(UUID tenant,
                             String meterKey,
                             String quantity,
                             String estimatedCost,
                             String actualCost,
                             Instant at) {
        jdbc.update("""
                INSERT INTO usage_meter_event
                    (id, business_id, meter_key, quantity, estimated_cost_usd,
                     actual_cost_usd, source_type, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, 'V5_TEST', ?)
                """, UUID.randomUUID(), tenant, meterKey, new BigDecimal(quantity),
                estimatedCost == null ? null : new BigDecimal(estimatedCost),
                actualCost == null ? null : new BigDecimal(actualCost), ts(at));
    }

    private void createSchema() {
        String[] statements = {
                """
                CREATE TABLE call_session (
                    id uuid PRIMARY KEY, business_id uuid NOT NULL, started_at timestamptz NOT NULL,
                    answered_at timestamptz, ai_setup_completed_at timestamptz, ended_at timestamptz,
                    duration_seconds integer, certification boolean NOT NULL,
                    telephony_provider text NOT NULL, estimated_telephony_cost_usd numeric,
                    estimated_ai_cost_usd numeric, estimated_total_cost_usd numeric
                )
                """,
                """
                CREATE TABLE call_action (
                    id uuid PRIMARY KEY, business_id uuid NOT NULL, call_id uuid NOT NULL,
                    success boolean NOT NULL, created_at timestamptz NOT NULL
                )
                """,
                """
                CREATE TABLE business_operation_retry_attempt (
                    id uuid PRIMARY KEY, business_id uuid NOT NULL, outcome text NOT NULL,
                    created_at timestamptz NOT NULL
                )
                """,
                """
                CREATE TABLE persistent_job (
                    id uuid PRIMARY KEY, business_id uuid NOT NULL, created_at timestamptz NOT NULL,
                    updated_at timestamptz NOT NULL, completed_at timestamptz,
                    status text NOT NULL, attempt_count integer NOT NULL
                )
                """,
                """
                CREATE TABLE outbound_message (
                    id uuid PRIMARY KEY, business_id uuid NOT NULL, provider text,
                    sent_at timestamptz, failure_code text, retry_count integer NOT NULL,
                    created_at timestamptz NOT NULL
                )
                """,
                """
                CREATE TABLE messaging_conversation (
                    id uuid PRIMARY KEY, business_id uuid NOT NULL, opened_at timestamptz NOT NULL
                )
                """,
                """
                CREATE TABLE messaging_message (
                    id uuid PRIMARY KEY, conversation_id uuid NOT NULL, provider text,
                    provider_message_id text, failure_code text, created_at timestamptz NOT NULL
                )
                """,
                """
                CREATE TABLE payment_webhook_event (
                    id uuid PRIMARY KEY, business_id uuid NOT NULL, provider text,
                    received_at timestamptz NOT NULL, processed_at timestamptz
                )
                """,
                """
                CREATE TABLE usage_meter_event (
                    id uuid PRIMARY KEY, business_id uuid NOT NULL, meter_key text NOT NULL,
                    quantity numeric NOT NULL, estimated_cost_usd numeric, actual_cost_usd numeric,
                    source_type text NOT NULL, occurred_at timestamptz NOT NULL
                )
                """
        };
        for (String statement : statements) jdbc.execute(statement);
    }

    private static Timestamp ts(Instant value) {
        return Timestamp.from(value);
    }
}

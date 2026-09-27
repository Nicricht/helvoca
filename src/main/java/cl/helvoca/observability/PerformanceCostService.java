package cl.helvoca.observability;

import cl.helvoca.security.TenantProvider;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PerformanceCostService {
    static final String METRICS_SQL = """
            WITH calls AS (
                SELECT *
                  FROM call_session
                 WHERE business_id = :businessId
                   AND started_at >= :from
                   AND started_at < :to
                   AND certification = false
                   AND telephony_provider <> 'simulator'
            ),
            call_metrics AS (
                SELECT COUNT(*) AS call_count,
                       AVG(duration_seconds) FILTER (WHERE duration_seconds IS NOT NULL) AS avg_call_duration_seconds,
                       percentile_cont(0.95) WITHIN GROUP (ORDER BY duration_seconds)
                           FILTER (WHERE duration_seconds IS NOT NULL) AS p95_call_duration_seconds,
                       COALESCE(SUM(estimated_telephony_cost_usd), 0) AS telephony_cost,
                       COALESCE(SUM(estimated_ai_cost_usd), 0) AS ai_cost,
                       COALESCE(SUM(estimated_total_cost_usd), 0) AS call_total_cost
                  FROM calls
            ),
            tool_metrics AS (
                SELECT COUNT(*) AS tool_calls,
                       COUNT(*) FILTER (WHERE a.success = false) AS failed_tool_calls
                  FROM call_action a
                  JOIN calls c ON c.id = a.call_id
                 WHERE a.business_id = :businessId
                   AND a.created_at >= :from
                   AND a.created_at < :to
            ),
            retry_metrics AS (
                SELECT COUNT(*) FILTER (WHERE outcome = 'RETRY_SCHEDULED') AS retry_events
                  FROM business_operation_retry_attempt
                 WHERE business_id = :businessId
                   AND created_at >= :from
                   AND created_at < :to
            ),
            job_metrics AS (
                SELECT COALESCE(SUM(GREATEST(attempt_count - 1, 0)), 0) AS job_retries,
                       COUNT(*) AS jobs
                  FROM persistent_job
                 WHERE business_id = :businessId
                   AND created_at >= :from
                   AND created_at < :to
            ),
            outbound_provider AS (
                SELECT COUNT(*) AS interactions
                  FROM outbound_message
                 WHERE business_id = :businessId
                   AND created_at >= :from
                   AND created_at < :to
                   AND provider IS NOT NULL
                   AND (
                       sent_at IS NOT NULL
                       OR failure_code IS NOT NULL
                       OR retry_count > 0
                   )
            ),
            messaging_provider AS (
                SELECT COUNT(*) AS interactions
                  FROM messaging_message m
                  JOIN messaging_conversation c ON c.id = m.conversation_id
                 WHERE c.business_id = :businessId
                   AND m.created_at >= :from
                   AND m.created_at < :to
                   AND m.provider IS NOT NULL
                   AND (
                       m.provider_message_id IS NOT NULL
                       OR m.failure_code IS NOT NULL
                   )
            ),
            webhook_provider AS (
                SELECT COUNT(*) AS interactions
                  FROM payment_webhook_event
                 WHERE business_id = :businessId
                   AND received_at >= :from
                   AND received_at < :to
            ),
            conversation_metrics AS (
                SELECT COUNT(*) AS messaging_journeys
                  FROM messaging_conversation
                 WHERE business_id = :businessId
                   AND opened_at >= :from
                   AND opened_at < :to
            ),
            usage_metrics AS (
                SELECT COALESCE(SUM(quantity) FILTER (
                           WHERE meter_key = 'AI_INPUT_TOKENS'), 0) AS ai_input_tokens,
                       COALESCE(SUM(quantity) FILTER (
                           WHERE meter_key = 'AI_OUTPUT_TOKENS'), 0) AS ai_output_tokens,
                       COALESCE(SUM(quantity) FILTER (
                           WHERE meter_key = 'AI_TOKENS'), 0) AS ai_total_tokens_direct,
                       COUNT(*) FILTER (
                           WHERE meter_key IN ('AI_INPUT_TOKENS','AI_OUTPUT_TOKENS','AI_TOKENS')) AS token_samples,
                       COALESCE(SUM(estimated_cost_usd) FILTER (
                           WHERE source_type <> 'CALL_SESSION'), 0) AS other_estimated_cost,
                       COALESCE(SUM(actual_cost_usd), 0) AS actual_cost,
                       COUNT(*) FILTER (WHERE actual_cost_usd IS NOT NULL) AS actual_cost_samples
                  FROM usage_meter_event
                 WHERE business_id = :businessId
                   AND occurred_at >= :from
                   AND occurred_at < :to
            )
            SELECT cm.call_count,
                   cm.avg_call_duration_seconds,
                   cm.p95_call_duration_seconds,
                   cm.telephony_cost,
                   cm.ai_cost,
                   cm.call_total_cost,
                   tm.tool_calls,
                   tm.failed_tool_calls,
                   rm.retry_events,
                   jm.job_retries,
                   jm.jobs,
                   (cm.call_count
                       + op.interactions
                       + mp.interactions
                       + wp.interactions) AS provider_interactions,
                   conv.messaging_journeys,
                   um.ai_input_tokens,
                   um.ai_output_tokens,
                   um.ai_total_tokens_direct,
                   um.token_samples,
                   um.other_estimated_cost,
                   um.actual_cost,
                   um.actual_cost_samples
              FROM call_metrics cm
              CROSS JOIN tool_metrics tm
              CROSS JOIN retry_metrics rm
              CROSS JOIN job_metrics jm
              CROSS JOIN outbound_provider op
              CROSS JOIN messaging_provider mp
              CROSS JOIN webhook_provider wp
              CROSS JOIN conversation_metrics conv
              CROSS JOIN usage_metrics um
            """;

    static final String STAGE_LATENCY_SQL = """
            WITH samples(stage, latency_ms) AS (
                SELECT 'CALL_ANSWER',
                       EXTRACT(EPOCH FROM (answered_at - started_at)) * 1000
                  FROM call_session
                 WHERE business_id = :businessId
                   AND started_at >= :from
                   AND started_at < :to
                   AND certification = false
                   AND telephony_provider <> 'simulator'
                   AND answered_at IS NOT NULL

                UNION ALL

                SELECT 'AI_SETUP',
                       EXTRACT(EPOCH FROM (ai_setup_completed_at - started_at)) * 1000
                  FROM call_session
                 WHERE business_id = :businessId
                   AND started_at >= :from
                   AND started_at < :to
                   AND certification = false
                   AND telephony_provider <> 'simulator'
                   AND ai_setup_completed_at IS NOT NULL

                UNION ALL

                SELECT 'JOB_COMPLETION',
                       EXTRACT(EPOCH FROM (COALESCE(completed_at, updated_at) - created_at)) * 1000
                  FROM persistent_job
                 WHERE business_id = :businessId
                   AND created_at >= :from
                   AND created_at < :to
                   AND status IN ('SUCCEEDED','DEAD_LETTER','CANCELLED')

                UNION ALL

                SELECT 'OUTBOUND_ACCEPTANCE',
                       EXTRACT(EPOCH FROM (sent_at - created_at)) * 1000
                  FROM outbound_message
                 WHERE business_id = :businessId
                   AND created_at >= :from
                   AND created_at < :to
                   AND sent_at IS NOT NULL

                UNION ALL

                SELECT 'PAYMENT_WEBHOOK',
                       EXTRACT(EPOCH FROM (processed_at - received_at)) * 1000
                  FROM payment_webhook_event
                 WHERE business_id = :businessId
                   AND received_at >= :from
                   AND received_at < :to
                   AND processed_at IS NOT NULL
            )
            SELECT stage,
                   COUNT(*) AS samples,
                   AVG(latency_ms) AS avg_ms,
                   percentile_cont(0.95) WITHIN GROUP (ORDER BY latency_ms) AS p95_ms,
                   MAX(latency_ms) AS max_ms
              FROM samples
             WHERE latency_ms >= 0
             GROUP BY stage
             ORDER BY stage
            """;

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final NamedParameterJdbcTemplate jdbc;
    private final TenantProvider tenants;
    private final PerformanceCostProperties properties;

    public PerformanceCostService(NamedParameterJdbcTemplate jdbc,
                                  TenantProvider tenants,
                                  PerformanceCostProperties properties) {
        this.jdbc = jdbc;
        this.tenants = tenants;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public Snapshot snapshot(Instant from, Instant to) {
        validateWindow(from, to);
        UUID businessId = tenants.requireBusinessId();
        Duration window = Duration.between(from, to);
        Instant previousFrom = from.minus(window);
        Instant previousTo = from;

        PeriodMetrics current = loadMetrics(businessId, from, to);
        PeriodMetrics previous = loadMetrics(businessId, previousFrom, previousTo);
        List<StageLatency> currentStages = loadStages(businessId, from, to);
        List<StageLatency> previousStages = loadStages(businessId, previousFrom, previousTo);

        return new Snapshot(
                Instant.now(),
                from,
                to,
                previousFrom,
                previousTo,
                current,
                previous,
                currentStages,
                previousStages,
                evaluateBudgets(current, previous, currentStages, previousStages, properties),
                coverage(current, currentStages));
    }

    private void validateWindow(Instant from, Instant to) {
        if (from == null || to == null || !from.isBefore(to)) {
            throw new IllegalArgumentException("Performance interval must satisfy from < to");
        }
        Duration duration = Duration.between(from, to);
        if (duration.compareTo(Duration.ofDays(properties.getMaxWindowDays())) > 0) {
            throw new IllegalArgumentException("Performance interval exceeds configured maximum");
        }
    }

    private PeriodMetrics loadMetrics(UUID businessId, Instant from, Instant to) {
        MapSqlParameterSource params = params(businessId, from, to);
        return jdbc.queryForObject(METRICS_SQL, params, (rs, rowNum) -> mapMetrics(rs));
    }

    private List<StageLatency> loadStages(UUID businessId, Instant from, Instant to) {
        return jdbc.query(STAGE_LATENCY_SQL, params(businessId, from, to),
                (rs, rowNum) -> new StageLatency(
                        rs.getString("stage"),
                        rs.getLong("samples"),
                        roundedLong(rs, "avg_ms"),
                        roundedLong(rs, "p95_ms"),
                        roundedLong(rs, "max_ms")));
    }

    private static MapSqlParameterSource params(UUID businessId, Instant from, Instant to) {
        return new MapSqlParameterSource()
                .addValue("businessId", businessId)
                .addValue("from", Timestamp.from(from))
                .addValue("to", Timestamp.from(to));
    }

    private static PeriodMetrics mapMetrics(ResultSet rs) throws SQLException {
        long calls = rs.getLong("call_count");
        long messagingJourneys = rs.getLong("messaging_journeys");
        long journeys = calls + messagingJourneys;
        long retryEvents = rs.getLong("retry_events");
        long jobRetries = rs.getLong("job_retries");
        long retries = retryEvents + jobRetries;
        long toolCalls = rs.getLong("tool_calls");
        long providerInteractions = rs.getLong("provider_interactions");

        BigDecimal telephonyCost = decimal(rs, "telephony_cost");
        BigDecimal aiCost = decimal(rs, "ai_cost");
        BigDecimal otherCost = decimal(rs, "other_estimated_cost");
        BigDecimal estimatedTotalCost = telephonyCost.add(aiCost).add(otherCost);

        BigDecimal inputTokens = decimal(rs, "ai_input_tokens");
        BigDecimal outputTokens = decimal(rs, "ai_output_tokens");
        BigDecimal directTokens = decimal(rs, "ai_total_tokens_direct");
        BigDecimal totalTokens = directTokens.signum() > 0
                ? directTokens
                : inputTokens.add(outputTokens);

        return new PeriodMetrics(
                calls,
                nullableDecimal(rs, "avg_call_duration_seconds"),
                nullableDecimal(rs, "p95_call_duration_seconds"),
                toolCalls,
                rs.getLong("failed_tool_calls"),
                retryEvents,
                jobRetries,
                rs.getLong("jobs"),
                providerInteractions,
                journeys,
                inputTokens,
                outputTokens,
                totalTokens,
                rs.getLong("token_samples"),
                telephonyCost,
                aiCost,
                otherCost,
                estimatedTotalCost,
                decimal(rs, "actual_cost"),
                rs.getLong("actual_cost_samples"),
                perJourney(toolCalls, journeys),
                perJourney(providerInteractions, journeys),
                perJourney(retries, journeys),
                perJourney(estimatedTotalCost, journeys));
    }

    static List<BudgetCheck> evaluateBudgets(PeriodMetrics current,
                                             PeriodMetrics previous,
                                             List<StageLatency> currentStages,
                                             List<StageLatency> previousStages,
                                             PerformanceCostProperties properties) {
        List<BudgetCheck> checks = new ArrayList<>();
        BigDecimal regressionPercent = properties.getRegressionPercent();

        relative(checks, "TOOL_CALLS_PER_JOURNEY",
                current.toolCallsPerJourney(), previous.toolCallsPerJourney(), regressionPercent);
        relative(checks, "PROVIDER_INTERACTIONS_PER_JOURNEY",
                current.providerInteractionsPerJourney(), previous.providerInteractionsPerJourney(), regressionPercent);
        relative(checks, "RETRIES_PER_JOURNEY",
                current.retriesPerJourney(), previous.retriesPerJourney(), regressionPercent);
        relative(checks, "ESTIMATED_COST_USD_PER_JOURNEY",
                current.estimatedCostUsdPerJourney(), previous.estimatedCostUsdPerJourney(), regressionPercent);

        Map<String, StageLatency> previousByStage = new LinkedHashMap<>();
        for (StageLatency stage : previousStages) previousByStage.put(stage.stage(), stage);
        for (StageLatency stage : currentStages) {
            StageLatency baseline = previousByStage.get(stage.stage());
            relative(checks, "P95_" + stage.stage() + "_MS",
                    BigDecimal.valueOf(stage.p95Ms()),
                    baseline == null ? null : BigDecimal.valueOf(baseline.p95Ms()),
                    regressionPercent);
        }

        absolute(checks, "P95_AI_SETUP_MS",
                stageP95(currentStages, "AI_SETUP"),
                BigDecimal.valueOf(properties.getMaxP95AiSetupMs()));
        absolute(checks, "P95_CALL_ANSWER_MS",
                stageP95(currentStages, "CALL_ANSWER"),
                BigDecimal.valueOf(properties.getMaxP95CallAnswerMs()));
        absolute(checks, "RETRIES_PER_JOURNEY",
                current.retriesPerJourney(), properties.getMaxRetriesPerJourney());
        absolute(checks, "TOOL_CALLS_PER_JOURNEY",
                current.toolCallsPerJourney(), properties.getMaxToolCallsPerJourney());
        absolute(checks, "PROVIDER_INTERACTIONS_PER_JOURNEY",
                current.providerInteractionsPerJourney(), properties.getMaxProviderInteractionsPerJourney());
        absolute(checks, "ESTIMATED_COST_USD_PER_JOURNEY",
                current.estimatedCostUsdPerJourney(), properties.getMaxEstimatedCostUsdPerJourney());

        return List.copyOf(checks);
    }

    private static Coverage coverage(PeriodMetrics metrics, List<StageLatency> stages) {
        List<String> missing = new ArrayList<>();
        boolean tokens = metrics.tokenSamples() > 0;
        boolean actualCost = metrics.actualCostSamples() > 0;
        boolean stageLatency = !stages.isEmpty();

        if (!tokens) {
            missing.add("AI token usage has no persisted usage_meter_event samples in this window");
        }
        if (!actualCost) {
            missing.add("Actual provider cost has no persisted samples; estimated cost remains available");
        }
        missing.add("Critical SQL query latency is not persisted by current main; V5 does not fabricate it");

        return new Coverage(stageLatency, tokens, actualCost, false, List.copyOf(missing));
    }

    private static void relative(List<BudgetCheck> checks,
                                 String metric,
                                 BigDecimal current,
                                 BigDecimal previous,
                                 BigDecimal regressionPercent) {
        if (current == null || previous == null || previous.signum() <= 0) {
            checks.add(new BudgetCheck(metric, "NO_BASELINE", current, null, previous,
                    regressionPercent, "Previous comparable period has no positive baseline"));
            return;
        }
        BigDecimal multiplier = BigDecimal.ONE.add(regressionPercent.divide(HUNDRED, 8, RoundingMode.HALF_UP));
        BigDecimal limit = previous.multiply(multiplier);
        String status = current.compareTo(limit) > 0 ? "REGRESSION" : "PASS";
        checks.add(new BudgetCheck(metric, status, current, limit, previous,
                regressionPercent, "Relative previous-period budget"));
    }

    private static void absolute(List<BudgetCheck> checks,
                                 String metric,
                                 BigDecimal current,
                                 BigDecimal configuredLimit) {
        if (configuredLimit == null || configuredLimit.signum() <= 0) return;
        if (current == null) {
            checks.add(new BudgetCheck(metric, "NO_DATA", null, configuredLimit, null,
                    null, "Configured absolute budget has no current sample"));
            return;
        }
        checks.add(new BudgetCheck(metric,
                current.compareTo(configuredLimit) > 0 ? "REGRESSION" : "PASS",
                current, configuredLimit, null, null, "Configured absolute budget"));
    }

    private static BigDecimal stageP95(List<StageLatency> stages, String name) {
        for (StageLatency stage : stages) {
            if (name.equals(stage.stage())) return BigDecimal.valueOf(stage.p95Ms());
        }
        return null;
    }

    private static BigDecimal perJourney(long value, long journeys) {
        if (journeys <= 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(value)
                .divide(BigDecimal.valueOf(journeys), 6, RoundingMode.HALF_UP);
    }

    private static BigDecimal perJourney(BigDecimal value, long journeys) {
        if (value == null || journeys <= 0) return BigDecimal.ZERO;
        return value.divide(BigDecimal.valueOf(journeys), 8, RoundingMode.HALF_UP);
    }

    private static BigDecimal decimal(ResultSet rs, String column) throws SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal nullableDecimal(ResultSet rs, String column) throws SQLException {
        return rs.getBigDecimal(column);
    }

    private static long roundedLong(ResultSet rs, String column) throws SQLException {
        Number value = (Number) rs.getObject(column);
        return value == null ? 0L : Math.round(value.doubleValue());
    }

    public record Snapshot(
            Instant generatedAt,
            Instant from,
            Instant to,
            Instant previousFrom,
            Instant previousTo,
            PeriodMetrics current,
            PeriodMetrics previous,
            List<StageLatency> stageLatencies,
            List<StageLatency> previousStageLatencies,
            List<BudgetCheck> budgets,
            Coverage coverage) {
    }

    public record PeriodMetrics(
            long calls,
            BigDecimal averageCallDurationSeconds,
            BigDecimal p95CallDurationSeconds,
            long toolCalls,
            long failedToolCalls,
            long retryEvents,
            long jobRetries,
            long jobs,
            long providerInteractionsObserved,
            long observedJourneys,
            BigDecimal aiInputTokens,
            BigDecimal aiOutputTokens,
            BigDecimal aiTotalTokens,
            long tokenSamples,
            BigDecimal estimatedTelephonyCostUsd,
            BigDecimal estimatedAiCostUsd,
            BigDecimal estimatedOtherCostUsd,
            BigDecimal estimatedTotalCostUsd,
            BigDecimal actualCostUsd,
            long actualCostSamples,
            BigDecimal toolCallsPerJourney,
            BigDecimal providerInteractionsPerJourney,
            BigDecimal retriesPerJourney,
            BigDecimal estimatedCostUsdPerJourney) {
    }

    public record StageLatency(
            String stage,
            long samples,
            long averageMs,
            long p95Ms,
            long maxMs) {
    }

    public record BudgetCheck(
            String metric,
            String status,
            BigDecimal current,
            BigDecimal limit,
            BigDecimal baseline,
            BigDecimal regressionPercent,
            String reason) {
    }

    public record Coverage(
            boolean stageLatency,
            boolean aiTokens,
            boolean actualProviderCost,
            boolean criticalQueryLatency,
            List<String> missingSignals) {
    }
}

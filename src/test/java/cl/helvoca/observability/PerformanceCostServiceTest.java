package cl.helvoca.observability;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PerformanceCostServiceTest {

    @Test
    void flagsRelativeRegressionAgainstPreviousComparablePeriod() {
        PerformanceCostProperties properties = new PerformanceCostProperties();
        properties.setRegressionPercent(new BigDecimal("25"));

        var previous = metrics("1.000000", "2.000000", "0.200000", "0.03000000");
        var current = metrics("1.500000", "2.100000", "0.250000", "0.05000000");

        var checks = PerformanceCostService.evaluateBudgets(
                current,
                previous,
                List.of(new PerformanceCostService.StageLatency("AI_SETUP", 10, 800, 1500, 2000)),
                List.of(new PerformanceCostService.StageLatency("AI_SETUP", 10, 700, 1000, 1500)),
                properties);

        assertEquals("REGRESSION", status(checks, "TOOL_CALLS_PER_JOURNEY", "Relative previous-period budget"));
        assertEquals("PASS", status(checks, "PROVIDER_INTERACTIONS_PER_JOURNEY", "Relative previous-period budget"));
        assertEquals("PASS", status(checks, "RETRIES_PER_JOURNEY", "Relative previous-period budget"));
        assertEquals("REGRESSION", status(checks, "ESTIMATED_COST_USD_PER_JOURNEY", "Relative previous-period budget"));
        assertEquals("REGRESSION", status(checks, "P95_AI_SETUP_MS", "Relative previous-period budget"));
    }

    @Test
    void optionalAbsoluteBudgetsAreEnforcedWhenConfigured() {
        PerformanceCostProperties properties = new PerformanceCostProperties();
        properties.setMaxP95AiSetupMs(1200);
        properties.setMaxRetriesPerJourney(new BigDecimal("0.50"));
        properties.setMaxEstimatedCostUsdPerJourney(new BigDecimal("0.04"));

        var current = metrics("1.000000", "1.000000", "0.750000", "0.05000000");

        var checks = PerformanceCostService.evaluateBudgets(
                current,
                current,
                List.of(new PerformanceCostService.StageLatency("AI_SETUP", 4, 900, 1500, 1700)),
                List.of(new PerformanceCostService.StageLatency("AI_SETUP", 4, 900, 1500, 1700)),
                properties);

        assertEquals("REGRESSION", status(checks, "P95_AI_SETUP_MS", "Configured absolute budget"));
        assertEquals("REGRESSION", status(checks, "RETRIES_PER_JOURNEY", "Configured absolute budget"));
        assertEquals("REGRESSION", status(checks, "ESTIMATED_COST_USD_PER_JOURNEY", "Configured absolute budget"));
    }

    @Test
    void missingPreviousSamplesAreReportedInsteadOfPretendingRegression() {
        PerformanceCostProperties properties = new PerformanceCostProperties();

        var current = metrics("2.000000", "3.000000", "1.000000", "0.10000000");
        var empty = metrics("0", "0", "0", "0");

        var checks = PerformanceCostService.evaluateBudgets(
                current, empty,
                List.of(new PerformanceCostService.StageLatency("AI_SETUP", 2, 1000, 1500, 1700)),
                List.of(),
                properties);

        assertEquals("NO_BASELINE", status(checks, "TOOL_CALLS_PER_JOURNEY", "Previous comparable period has no positive baseline"));
        assertEquals("NO_BASELINE", status(checks, "P95_AI_SETUP_MS", "Previous comparable period has no positive baseline"));
    }

    private static PerformanceCostService.PeriodMetrics metrics(
            String toolCallsPerJourney,
            String providerCallsPerJourney,
            String retriesPerJourney,
            String costPerJourney) {
        return new PerformanceCostService.PeriodMetrics(
                1,
                BigDecimal.valueOf(60),
                BigDecimal.valueOf(60),
                1,
                0,
                0,
                0,
                0,
                1,
                1,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0,
                new BigDecimal(toolCallsPerJourney),
                new BigDecimal(providerCallsPerJourney),
                new BigDecimal(retriesPerJourney),
                new BigDecimal(costPerJourney));
    }

    private static String status(List<PerformanceCostService.BudgetCheck> checks,
                                 String metric,
                                 String reason) {
        return checks.stream()
                .filter(check -> metric.equals(check.metric()) && reason.equals(check.reason()))
                .findFirst()
                .orElseThrow()
                .status();
    }
}

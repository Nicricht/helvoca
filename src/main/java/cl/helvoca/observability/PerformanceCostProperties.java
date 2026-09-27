package cl.helvoca.observability;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@ConfigurationProperties(prefix = "app.performance-cost")
public class PerformanceCostProperties {
    private int maxWindowDays = 31;
    private BigDecimal regressionPercent = new BigDecimal("25");
    private long maxP95AiSetupMs = 0;
    private long maxP95CallAnswerMs = 0;
    private BigDecimal maxRetriesPerJourney = BigDecimal.ZERO;
    private BigDecimal maxToolCallsPerJourney = BigDecimal.ZERO;
    private BigDecimal maxProviderInteractionsPerJourney = BigDecimal.ZERO;
    private BigDecimal maxEstimatedCostUsdPerJourney = BigDecimal.ZERO;

    public int getMaxWindowDays() { return maxWindowDays; }
    public void setMaxWindowDays(int value) { maxWindowDays = Math.max(1, Math.min(value, 366)); }

    public BigDecimal getRegressionPercent() { return regressionPercent; }
    public void setRegressionPercent(BigDecimal value) {
        regressionPercent = nonNegative(value, new BigDecimal("25"));
    }

    public long getMaxP95AiSetupMs() { return maxP95AiSetupMs; }
    public void setMaxP95AiSetupMs(long value) { maxP95AiSetupMs = Math.max(0, value); }

    public long getMaxP95CallAnswerMs() { return maxP95CallAnswerMs; }
    public void setMaxP95CallAnswerMs(long value) { maxP95CallAnswerMs = Math.max(0, value); }

    public BigDecimal getMaxRetriesPerJourney() { return maxRetriesPerJourney; }
    public void setMaxRetriesPerJourney(BigDecimal value) {
        maxRetriesPerJourney = nonNegative(value, BigDecimal.ZERO);
    }

    public BigDecimal getMaxToolCallsPerJourney() { return maxToolCallsPerJourney; }
    public void setMaxToolCallsPerJourney(BigDecimal value) {
        maxToolCallsPerJourney = nonNegative(value, BigDecimal.ZERO);
    }

    public BigDecimal getMaxProviderInteractionsPerJourney() { return maxProviderInteractionsPerJourney; }
    public void setMaxProviderInteractionsPerJourney(BigDecimal value) {
        maxProviderInteractionsPerJourney = nonNegative(value, BigDecimal.ZERO);
    }

    public BigDecimal getMaxEstimatedCostUsdPerJourney() { return maxEstimatedCostUsdPerJourney; }
    public void setMaxEstimatedCostUsdPerJourney(BigDecimal value) {
        maxEstimatedCostUsdPerJourney = nonNegative(value, BigDecimal.ZERO);
    }

    private static BigDecimal nonNegative(BigDecimal value, BigDecimal fallback) {
        return value == null || value.signum() < 0 ? fallback : value;
    }
}

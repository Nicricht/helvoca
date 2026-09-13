package cl.helvoca.telephony;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@ConfigurationProperties(prefix = "app.commercial.calls")
public class CallCommercialProperties {
    private int maxConcurrentPerBusiness = 10;
    private BigDecimal telephonyCostPerMinuteUsd = BigDecimal.ZERO;
    private BigDecimal aiCostPerMinuteUsd = BigDecimal.ZERO;

    public int getMaxConcurrentPerBusiness() {
        return maxConcurrentPerBusiness;
    }

    public void setMaxConcurrentPerBusiness(int maxConcurrentPerBusiness) {
        this.maxConcurrentPerBusiness = Math.max(0, maxConcurrentPerBusiness);
    }

    public BigDecimal getTelephonyCostPerMinuteUsd() {
        return telephonyCostPerMinuteUsd;
    }

    public void setTelephonyCostPerMinuteUsd(BigDecimal telephonyCostPerMinuteUsd) {
        this.telephonyCostPerMinuteUsd = nonNegative(telephonyCostPerMinuteUsd);
    }

    public BigDecimal getAiCostPerMinuteUsd() {
        return aiCostPerMinuteUsd;
    }

    public void setAiCostPerMinuteUsd(BigDecimal aiCostPerMinuteUsd) {
        this.aiCostPerMinuteUsd = nonNegative(aiCostPerMinuteUsd);
    }

    private static BigDecimal nonNegative(BigDecimal value) {
        if (value == null || value.signum() < 0) return BigDecimal.ZERO;
        return value;
    }
}

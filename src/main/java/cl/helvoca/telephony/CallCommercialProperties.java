package cl.helvoca.telephony;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "app.commercial.calls")
public class CallCommercialProperties {
    private int maxConcurrentPerBusiness = 10;
    private BigDecimal telephonyCostPerMinuteUsd = BigDecimal.ZERO;
    private BigDecimal aiCostPerMinuteUsd = BigDecimal.ZERO;
    private BigDecimal usdToClpRate = BigDecimal.ZERO;
    private Map<String, BigDecimal> telephonyProviderCostPerMinuteUsd = new LinkedHashMap<>();
    private Map<String, BigDecimal> aiProviderCostPerMinuteUsd = new LinkedHashMap<>();
    private Map<String, BigDecimal> aiModelCostPerMinuteUsd = new LinkedHashMap<>();

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

    public BigDecimal getUsdToClpRate() {
        return usdToClpRate;
    }

    public void setUsdToClpRate(BigDecimal usdToClpRate) {
        this.usdToClpRate = nonNegative(usdToClpRate);
    }

    public Map<String, BigDecimal> getTelephonyProviderCostPerMinuteUsd() {
        return Map.copyOf(telephonyProviderCostPerMinuteUsd);
    }

    public void setTelephonyProviderCostPerMinuteUsd(Map<String, BigDecimal> values) {
        this.telephonyProviderCostPerMinuteUsd = normalizeRates(values);
    }

    public Map<String, BigDecimal> getAiProviderCostPerMinuteUsd() {
        return Map.copyOf(aiProviderCostPerMinuteUsd);
    }

    public void setAiProviderCostPerMinuteUsd(Map<String, BigDecimal> values) {
        this.aiProviderCostPerMinuteUsd = normalizeRates(values);
    }

    public Map<String, BigDecimal> getAiModelCostPerMinuteUsd() {
        return Map.copyOf(aiModelCostPerMinuteUsd);
    }

    public void setAiModelCostPerMinuteUsd(Map<String, BigDecimal> values) {
        this.aiModelCostPerMinuteUsd = normalizeRates(values);
    }

    public BigDecimal resolveTelephonyCostPerMinuteUsd(String provider) {
        return resolve(telephonyProviderCostPerMinuteUsd, provider, telephonyCostPerMinuteUsd);
    }

    public BigDecimal resolveAiCostPerMinuteUsd(String provider, String model) {
        BigDecimal modelRate = lookup(aiModelCostPerMinuteUsd, model);
        if (modelRate != null) return modelRate;
        return resolve(aiProviderCostPerMinuteUsd, provider, aiCostPerMinuteUsd);
    }

    private static BigDecimal resolve(Map<String, BigDecimal> rates,
                                      String key,
                                      BigDecimal fallback) {
        BigDecimal configured = lookup(rates, key);
        return configured == null ? nonNegative(fallback) : configured;
    }

    private static BigDecimal lookup(Map<String, BigDecimal> rates, String key) {
        if (rates == null || rates.isEmpty() || key == null || key.isBlank()) return null;
        return rates.get(normalizeKey(key));
    }

    private static Map<String, BigDecimal> normalizeRates(Map<String, BigDecimal> values) {
        Map<String, BigDecimal> normalized = new LinkedHashMap<>();
        if (values == null) return normalized;
        values.forEach((key, value) -> {
            if (key == null || key.isBlank()) return;
            normalized.put(normalizeKey(key), nonNegative(value));
        });
        return normalized;
    }

    private static String normalizeKey(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static BigDecimal nonNegative(BigDecimal value) {
        if (value == null || value.signum() < 0) return BigDecimal.ZERO;
        return value;
    }
}

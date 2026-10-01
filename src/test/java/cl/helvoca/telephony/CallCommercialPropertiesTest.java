package cl.helvoca.telephony;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CallCommercialPropertiesTest {

    @Test
    void resolvesModelThenProviderThenLegacyRate() {
        CallCommercialProperties properties = new CallCommercialProperties();
        properties.setAiCostPerMinuteUsd(new BigDecimal("0.030"));
        properties.setAiProviderCostPerMinuteUsd(Map.of(
                "Gemini", new BigDecimal("0.025")));
        properties.setAiModelCostPerMinuteUsd(Map.of(
                "Gemini-3.8-Live", new BigDecimal("0.020")));

        assertEquals(new BigDecimal("0.020"),
                properties.resolveAiCostPerMinuteUsd("gemini", "gemini-3.8-live"));
        assertEquals(new BigDecimal("0.025"),
                properties.resolveAiCostPerMinuteUsd(" GEMINI ", "other-model"));
        assertEquals(new BigDecimal("0.030"),
                properties.resolveAiCostPerMinuteUsd("other", "other-model"));
    }

    @Test
    void resolvesTelephonyProviderAndSanitizesInvalidRates() {
        CallCommercialProperties properties = new CallCommercialProperties();
        properties.setTelephonyCostPerMinuteUsd(new BigDecimal("0.010"));
        properties.setTelephonyProviderCostPerMinuteUsd(Map.of(
                "TWILIO", new BigDecimal("0.008")));

        assertEquals(new BigDecimal("0.008"),
                properties.resolveTelephonyCostPerMinuteUsd("twilio"));
        assertEquals(new BigDecimal("0.010"),
                properties.resolveTelephonyCostPerMinuteUsd("other"));

        properties.setUsdToClpRate(new BigDecimal("-1"));
        properties.setTwilioNumberMonthlyCostUsd(new BigDecimal("-7"));
        properties.setAiProviderCostPerMinuteUsd(Map.of("gemini", new BigDecimal("-2")));
        assertEquals(BigDecimal.ZERO, properties.getUsdToClpRate());
        assertEquals(BigDecimal.ZERO, properties.getTwilioNumberMonthlyCostUsd());
        assertEquals(BigDecimal.ZERO, properties.resolveAiCostPerMinuteUsd("gemini", null));
    }

    @Test
    void coversBlankAndNullRateKeysAndLegacyNulls() {
        CallCommercialProperties properties = new CallCommercialProperties();
        properties.setMaxConcurrentPerBusiness(-10);
        properties.setAiCostPerMinuteUsd(null);
        properties.setTelephonyCostPerMinuteUsd(null);

        java.util.Map<String, BigDecimal> rates = new java.util.LinkedHashMap<>();
        rates.put(null, BigDecimal.ONE);
        rates.put("   ", BigDecimal.ONE);
        rates.put("Gemini", new BigDecimal("0.020"));
        properties.setAiProviderCostPerMinuteUsd(rates);

        assertEquals(0, properties.getMaxConcurrentPerBusiness());
        assertEquals(BigDecimal.ZERO, properties.getAiCostPerMinuteUsd());
        assertEquals(BigDecimal.ZERO, properties.getTelephonyCostPerMinuteUsd());
        assertEquals(new BigDecimal("0.020"), properties.resolveAiCostPerMinuteUsd("gemini", " "));
        assertEquals(BigDecimal.ZERO, properties.resolveAiCostPerMinuteUsd(null, null));
        assertEquals(BigDecimal.ZERO, properties.resolveTelephonyCostPerMinuteUsd(""));
    }

    @Test
    void nullRateMapsRemainSafeAndEmpty() {
        CallCommercialProperties properties = new CallCommercialProperties();
        properties.setAiProviderCostPerMinuteUsd(null);
        properties.setAiModelCostPerMinuteUsd(null);
        properties.setTelephonyProviderCostPerMinuteUsd(null);

        assertEquals(Map.of(), properties.getAiProviderCostPerMinuteUsd());
        assertEquals(Map.of(), properties.getAiModelCostPerMinuteUsd());
        assertEquals(Map.of(), properties.getTelephonyProviderCostPerMinuteUsd());
    }
}

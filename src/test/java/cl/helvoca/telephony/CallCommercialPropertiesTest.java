package cl.helvoca.telephony;

import cl.helvoca.call.CallDirection;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void estimatedBudgetDefaultsOffAndRejectsInvalidNegativeAmounts() {
        CallCommercialProperties props = new CallCommercialProperties();
        assertEquals(BigDecimal.ZERO, props.getMaxEstimatedCostPerBusinessPerUtcDayUsd());
        assertEquals(BigDecimal.ZERO, props.getEstimatedNewCallReservationUsd());
        props.setMaxEstimatedCostPerBusinessPerUtcDayUsd(new BigDecimal("-1"));
        props.setEstimatedNewCallReservationUsd(null);
        assertEquals(BigDecimal.ZERO, props.getMaxEstimatedCostPerBusinessPerUtcDayUsd());
        assertEquals(BigDecimal.ZERO, props.getEstimatedNewCallReservationUsd());
        props.setMaxEstimatedCostPerBusinessPerUtcDayUsd(new BigDecimal("0.900000"));
        props.setEstimatedNewCallReservationUsd(new BigDecimal("0.200000"));
        assertEquals(new BigDecimal("0.900000"), props.getMaxEstimatedCostPerBusinessPerUtcDayUsd());
        assertEquals(new BigDecimal("0.200000"), props.getEstimatedNewCallReservationUsd());
    }

    @Test
    void budgetPricingRequiresPositiveAiFallbackAndEveryExplicitOverride() {
        CallCommercialProperties properties = new CallCommercialProperties();
        assertFalse(properties.hasCompleteAiPricingForEstimatedBudget());

        properties.setAiCostPerMinuteUsd(new BigDecimal("0.02"));
        assertTrue(properties.hasCompleteAiPricingForEstimatedBudget());

        properties.setAiProviderCostPerMinuteUsd(Map.of("gemini", BigDecimal.ZERO));
        assertFalse(properties.hasCompleteAiPricingForEstimatedBudget());

        properties.setAiProviderCostPerMinuteUsd(Map.of("gemini", new BigDecimal("0.02")));
        assertTrue(properties.hasCompleteAiPricingForEstimatedBudget());

        properties.setAiModelCostPerMinuteUsd(Map.of("gemini-3.8-live", BigDecimal.ZERO));
        assertFalse(properties.hasCompleteAiPricingForEstimatedBudget());

        properties.setAiModelCostPerMinuteUsd(Map.of("gemini-3.8-live", new BigDecimal("0.02")));
        assertTrue(properties.hasCompleteAiPricingForEstimatedBudget());
    }

    @Test
    void directionSpecificTelephonyRatesOverrideOnlyTheirOwnDirection() {
        CallCommercialProperties properties = new CallCommercialProperties();
        properties.setTelephonyCostPerMinuteUsd(new BigDecimal("0.010"));
        properties.setTelephonyProviderCostPerMinuteUsd(Map.of("TWILIO", new BigDecimal("0.080")));
        properties.setInboundTelephonyProviderCostPerMinuteUsd(
                Map.of(" TWILIO ", new BigDecimal("0.0151")));
        properties.setOutboundTelephonyProviderCostPerMinuteUsd(
                Map.of("Twilio", new BigDecimal("0.0746")));

        assertEquals(new BigDecimal("0.0151"),
                properties.resolveTelephonyCostPerMinuteUsd("twilio", CallDirection.INBOUND));
        assertEquals(new BigDecimal("0.0746"),
                properties.resolveTelephonyCostPerMinuteUsd(" TWILIO ", CallDirection.OUTBOUND));
        assertEquals(new BigDecimal("0.080"),
                properties.resolveTelephonyCostPerMinuteUsd("twilio", null));
        assertEquals(new BigDecimal("0.010"),
                properties.resolveTelephonyCostPerMinuteUsd("other", CallDirection.INBOUND));
        assertEquals(new BigDecimal("0.010"),
                properties.resolveTelephonyCostPerMinuteUsd("other", CallDirection.OUTBOUND));
        assertEquals(new BigDecimal("0.0151"),
                properties.getInboundTelephonyProviderCostPerMinuteUsd().get("twilio"));
        assertEquals(new BigDecimal("0.0746"),
                properties.getOutboundTelephonyProviderCostPerMinuteUsd().get("twilio"));
    }

    @Test
    void invalidOrAbsentDirectionalRatesDoNotInventPositiveCharges() {
        CallCommercialProperties properties = new CallCommercialProperties();
        properties.setInboundTelephonyProviderCostPerMinuteUsd(
                Map.of("twilio", new BigDecimal("-0.5")));
        properties.setOutboundTelephonyProviderCostPerMinuteUsd(null);
        assertEquals(BigDecimal.ZERO,
                properties.resolveTelephonyCostPerMinuteUsd("twilio", CallDirection.INBOUND));
        assertEquals(BigDecimal.ZERO,
                properties.resolveTelephonyCostPerMinuteUsd("twilio", CallDirection.OUTBOUND));
        assertEquals(Map.of(), properties.getOutboundTelephonyProviderCostPerMinuteUsd());
        properties.setInboundTelephonyProviderCostPerMinuteUsd(null);
        assertEquals(Map.of(), properties.getInboundTelephonyProviderCostPerMinuteUsd());
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

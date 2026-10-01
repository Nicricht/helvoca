package cl.helvoca.platform;

import cl.helvoca.telephony.CallCommercialProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlatformCommercialEconomicsServiceTest {

    @Test
    void calculatesPlanValueOverageCostAndEstimatedGrossMargin() {
        PlatformCommercialEconomicsRepository repository = mock(PlatformCommercialEconomicsRepository.class);
        CallCommercialProperties properties = new CallCommercialProperties();
        properties.setUsdToClpRate(new BigDecimal("900"));

        UUID businessId = UUID.randomUUID();
        when(repository.currentBusinesses()).thenReturn(List.of(
                new PlatformCommercialEconomicsRepository.BusinessEconomicsSource(
                        businessId, "Barbería Norte", "BASIC", "Emprende", "ACTIVE",
                        24_990, false, new BigDecimal("6000"), new BigDecimal("6300"),
                        new BigDecimal("60"), 149, 0, new BigDecimal("5.000000"))
        ));
        when(repository.providerBreakdown()).thenReturn(List.of(
                new PlatformCommercialEconomicsRepository.ProviderCostSource(
                        "gemini", "gemini-3.8-live", 4L, new BigDecimal("6300"),
                        new BigDecimal("1.100000"), new BigDecimal("3.900000"),
                        new BigDecimal("5.000000"))
        ));

        var report = new PlatformCommercialEconomicsService(repository, properties).portfolio();

        assertEquals(1, report.businessCount());
        assertEquals(24_990L, report.basePlanValueClp());
        assertEquals(745L, report.estimatedOverageValueClp());
        assertEquals(25_735L, report.estimatedCommercialValueClp());
        assertEquals(new BigDecimal("4500"), report.estimatedPlatformCostClp());
        assertEquals(new BigDecimal("21235"), report.estimatedGrossMarginClp());
        assertEquals(new BigDecimal("82.5"), report.estimatedGrossMarginPercent());

        var business = report.businesses().getFirst();
        assertEquals("OVERAGE", business.usageAlertLevel());
        assertEquals(105L, business.usedMinutes());
        assertEquals(100L, business.includedMinutes());
        assertEquals(745L, business.estimatedOverageValueClp());

        var provider = report.providers().getFirst();
        assertEquals("gemini", provider.aiProvider());
        assertEquals("gemini-3.8-live", provider.aiModel());
        assertEquals(105L, provider.minutes());
        assertEquals(new BigDecimal("4500"), provider.estimatedTotalCostClp());
    }

    @Test
    void includesConfiguredMonthlyPhoneCost() {
        PlatformCommercialEconomicsRepository repository = mock(PlatformCommercialEconomicsRepository.class);
        CallCommercialProperties properties = new CallCommercialProperties();
        properties.setUsdToClpRate(new BigDecimal("900"));
        properties.setTwilioNumberMonthlyCostUsd(new BigDecimal("7"));

        when(repository.currentBusinesses()).thenReturn(List.of(
                new PlatformCommercialEconomicsRepository.BusinessEconomicsSource(
                        UUID.randomUUID(), "Negocio", "BASIC", "Emprende", "ACTIVE",
                        24_990, false, new BigDecimal("6000"), BigDecimal.ZERO,
                        new BigDecimal("60"), 149, 1, new BigDecimal("3.000000"))
        ));
        when(repository.providerBreakdown()).thenReturn(List.of());

        var report = new PlatformCommercialEconomicsService(repository, properties).portfolio();

        assertEquals(new BigDecimal("10.000000"), report.estimatedPlatformCostUsd());
        assertEquals(new BigDecimal("9000"), report.estimatedPlatformCostClp());
        assertEquals(1, report.businesses().getFirst().activeVoiceNumberCount());
        assertEquals(new BigDecimal("7"), report.businesses().getFirst().estimatedFixedPhoneCostUsd());
    }

    @Test
    void customPricingReferenceIsNotCountedAsCommercialValue() {
        PlatformCommercialEconomicsRepository repository = mock(PlatformCommercialEconomicsRepository.class);
        CallCommercialProperties properties = new CallCommercialProperties();
        properties.setUsdToClpRate(new BigDecimal("900"));

        when(repository.currentBusinesses()).thenReturn(List.of(
                new PlatformCommercialEconomicsRepository.BusinessEconomicsSource(
                        UUID.randomUUID(), "Enterprise", "ENTERPRISE", "Enterprise", "ACTIVE",
                        119_990, true, new BigDecimal("60000"), BigDecimal.ZERO,
                        null, null, 0, new BigDecimal("1.000000"))
        ));
        when(repository.providerBreakdown()).thenReturn(List.of());

        var report = new PlatformCommercialEconomicsService(repository, properties).portfolio();

        assertEquals(1, report.businessesWithUnknownCommercialValue());
        assertEquals(0L, report.basePlanValueClp());
        assertNull(report.businesses().getFirst().estimatedCommercialValueClp());
        assertTrue(report.businesses().getFirst().commercialValueUnknown());
    }

    @Test
    void nonBillableAndUnknownPriceStatesDoNotInventCommercialValue() {
        PlatformCommercialEconomicsRepository repository = mock(PlatformCommercialEconomicsRepository.class);
        CallCommercialProperties properties = new CallCommercialProperties();
        properties.setUsdToClpRate(new BigDecimal("900"));

        when(repository.currentBusinesses()).thenReturn(List.of(
                new PlatformCommercialEconomicsRepository.BusinessEconomicsSource(
                        UUID.randomUUID(), "Trial", "BASIC", "Emprende", "TRIALING",
                        24_990, false, null, new BigDecimal("-10"),
                        null, null, 0, null),
                new PlatformCommercialEconomicsRepository.BusinessEconomicsSource(
                        UUID.randomUUID(), "Sin precio", "ENTERPRISE", "Enterprise", "ACTIVE",
                        null, false, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, -1, -2, BigDecimal.ZERO),
                new PlatformCommercialEconomicsRepository.BusinessEconomicsSource(
                        UUID.randomUUID(), "Sin estado", "BASIC", "Emprende", null,
                        24_990, false, BigDecimal.ZERO, BigDecimal.ZERO,
                        new BigDecimal("60"), 149, 0, BigDecimal.ZERO)
        ));
        when(repository.providerBreakdown()).thenReturn(List.of(
                new PlatformCommercialEconomicsRepository.ProviderCostSource(
                        "gemini", "gemini-3.8-live", 1L, null,
                        null, new BigDecimal("-1"), null)
        ));

        var report = new PlatformCommercialEconomicsService(repository, properties).portfolio();

        assertEquals(3, report.businessCount());
        assertEquals(1, report.businessesWithUnknownCommercialValue());
        assertEquals(0L, report.basePlanValueClp());
        assertNull(report.estimatedGrossMarginPercent());
        assertEquals(0L, report.providers().getFirst().minutes());
        assertEquals(BigDecimal.ZERO, report.providers().getFirst().estimatedTotalCostUsd());
        assertEquals(BigDecimal.ZERO, report.businesses().getFirst().estimatedPlatformCostUsd());
        assertNull(report.businesses().get(1).estimatedCommercialValueClp());
    }

    @Test
    void pastDueStillCarriesEstimatedContractValueButNotPaymentTruth() {
        PlatformCommercialEconomicsRepository repository = mock(PlatformCommercialEconomicsRepository.class);
        CallCommercialProperties properties = new CallCommercialProperties();
        properties.setUsdToClpRate(new BigDecimal("900"));

        when(repository.currentBusinesses()).thenReturn(List.of(
                new PlatformCommercialEconomicsRepository.BusinessEconomicsSource(
                        UUID.randomUUID(), "Atrasado", "BASIC", "Emprende", "past_due",
                        24_990, false, new BigDecimal("6000"), BigDecimal.ZERO,
                        new BigDecimal("60"), 149, 0, BigDecimal.ZERO)
        ));
        when(repository.providerBreakdown()).thenReturn(List.of());

        var report = new PlatformCommercialEconomicsService(repository, properties).portfolio();

        assertEquals(24_990L, report.basePlanValueClp());
        assertEquals(new BigDecimal("24990"), report.estimatedGrossMarginClp());
        assertEquals(new BigDecimal("100.0"), report.estimatedGrossMarginPercent());
    }

    @Test
    void doesNotInventClpMarginWhenExchangeRateIsUnavailable() {
        PlatformCommercialEconomicsRepository repository = mock(PlatformCommercialEconomicsRepository.class);
        CallCommercialProperties properties = new CallCommercialProperties();
        properties.setUsdToClpRate(BigDecimal.ZERO);

        when(repository.currentBusinesses()).thenReturn(List.of(
                new PlatformCommercialEconomicsRepository.BusinessEconomicsSource(
                        UUID.randomUUID(), "Negocio", "PRO", "Negocio", "ACTIVE",
                        39_990, false, new BigDecimal("15000"), BigDecimal.ZERO,
                        new BigDecimal("60"), 129, 0, new BigDecimal("2.500000"))
        ));
        when(repository.providerBreakdown()).thenReturn(List.of());

        var report = new PlatformCommercialEconomicsService(repository, properties).portfolio();

        assertNull(report.estimatedPlatformCostClp());
        assertNull(report.estimatedGrossMarginClp());
        assertNull(report.estimatedGrossMarginPercent());
        assertEquals(new BigDecimal("2.500000"), report.estimatedPlatformCostUsd());
    }
}

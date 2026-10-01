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
                        24_990, new BigDecimal("6000"), new BigDecimal("6300"),
                        new BigDecimal("60"), 149, new BigDecimal("5.000000"))
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
    void doesNotInventClpMarginWhenExchangeRateIsUnavailable() {
        PlatformCommercialEconomicsRepository repository = mock(PlatformCommercialEconomicsRepository.class);
        CallCommercialProperties properties = new CallCommercialProperties();
        properties.setUsdToClpRate(BigDecimal.ZERO);

        when(repository.currentBusinesses()).thenReturn(List.of(
                new PlatformCommercialEconomicsRepository.BusinessEconomicsSource(
                        UUID.randomUUID(), "Negocio", "PRO", "Negocio", "ACTIVE",
                        39_990, new BigDecimal("15000"), BigDecimal.ZERO,
                        new BigDecimal("60"), 129, new BigDecimal("2.500000"))
        ));
        when(repository.providerBreakdown()).thenReturn(List.of());

        var report = new PlatformCommercialEconomicsService(repository, properties).portfolio();

        assertNull(report.estimatedPlatformCostClp());
        assertNull(report.estimatedGrossMarginClp());
        assertNull(report.estimatedGrossMarginPercent());
        assertEquals(new BigDecimal("2.500000"), report.estimatedPlatformCostUsd());
    }
}

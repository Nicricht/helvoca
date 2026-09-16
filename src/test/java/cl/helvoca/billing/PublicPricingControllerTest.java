package cl.helvoca.billing;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PublicPricingControllerTest {

    @Test
    void exposesExpectedCommercialCatalogFromDatabasePlans() {
        CommercialPlanCatalogService catalog = mock(CommercialPlanCatalogService.class);
        when(catalog.activePlans()).thenReturn(List.of(
                plan("BASIC", "EMPRENDE", "Emprende", 24_990, 6_000, 1, 149, false, false, 1),
                plan("PRO", "NEGOCIO", "Negocio", 39_990, 15_000, 3, 129, false, true, 2),
                plan("BUSINESS", "PRO", "Pro", 69_990, 30_000, 10, 109, false, false, 3),
                plan("ENTERPRISE", "ENTERPRISE", "Enterprise", 119_990, 60_000, 10, null, true, false, 4)));

        var plans = new PublicPricingController(catalog).plans();

        assertEquals(4, plans.size());
        assertEquals("EMPRENDE", plans.get(0).code());
        assertEquals("Emprende", plans.get(0).name());
        assertEquals(24_990, plans.get(0).monthlyPriceClp());
        assertEquals(100, plans.get(0).includedMinutes());
        assertEquals(1, plans.get(0).maxConcurrentCalls());
        assertEquals(149, plans.get(0).overagePerMinuteClp());

        assertEquals("NEGOCIO", plans.get(1).code());
        assertEquals(39_990, plans.get(1).monthlyPriceClp());
        assertEquals(250, plans.get(1).includedMinutes());
        assertEquals(3, plans.get(1).maxConcurrentCalls());
        assertEquals(129, plans.get(1).overagePerMinuteClp());
        assertTrue(plans.get(1).recommended());

        assertEquals("PRO", plans.get(2).code());
        assertEquals(69_990, plans.get(2).monthlyPriceClp());
        assertEquals(500, plans.get(2).includedMinutes());
        assertEquals(10, plans.get(2).maxConcurrentCalls());
        assertEquals(109, plans.get(2).overagePerMinuteClp());

        assertEquals("ENTERPRISE", plans.get(3).code());
        assertEquals(119_990, plans.get(3).monthlyPriceClp());
        assertEquals(1_000, plans.get(3).includedMinutes());
        assertTrue(plans.get(3).customPricing());
        assertNull(plans.get(3).overagePerMinuteClp());
        verify(catalog).activePlans();
    }

    private static CommercialPlanCatalogService.Plan plan(
            String technicalCode, String publicCode, String name, Integer price,
            int voiceSeconds, int concurrentCalls, Integer overage,
            boolean customPricing, boolean recommended, int sortOrder) {
        return new CommercialPlanCatalogService.Plan(
                technicalCode, publicCode, name, price, "CLP", customPricing, recommended, true, sortOrder,
                List.of(
                        new CommercialPlanCatalogService.EntitlementRule(
                                "VOICE_SECONDS", "USAGE", "VOICE_SECONDS", BigDecimal.valueOf(voiceSeconds),
                                "SECONDS", false, BigDecimal.valueOf(60), overage),
                        new CommercialPlanCatalogService.EntitlementRule(
                                "CONCURRENT_CALLS", "CAPACITY", null, BigDecimal.valueOf(concurrentCalls),
                                "COUNT", true, null, null)));
    }
}

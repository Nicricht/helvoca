package cl.helvoca.billing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PublicPricingControllerTest {

    @Test
    void exposesExpectedCommercialCatalog() {
        var plans = new PublicPricingController().plans();

        assertEquals(4, plans.size());
        assertEquals("EMPRENDE", plans.get(0).code());
        assertEquals("Emprende", plans.get(0).name());
        assertEquals(24_990, plans.get(0).monthlyPriceClp());
        assertEquals(100, plans.get(0).includedMinutes());
        assertEquals(1, plans.get(0).maxConcurrentCalls());
        assertEquals(249, plans.get(0).overagePerMinuteClp());

        assertEquals("NEGOCIO", plans.get(1).code());
        assertEquals(49_990, plans.get(1).monthlyPriceClp());
        assertEquals(300, plans.get(1).includedMinutes());
        assertEquals(3, plans.get(1).maxConcurrentCalls());
        assertEquals(199, plans.get(1).overagePerMinuteClp());
        assertTrue(plans.get(1).recommended());

        assertEquals("PRO", plans.get(2).code());
        assertEquals(99_990, plans.get(2).monthlyPriceClp());
        assertEquals(600, plans.get(2).includedMinutes());
        assertEquals(10, plans.get(2).maxConcurrentCalls());
        assertEquals(169, plans.get(2).overagePerMinuteClp());

        assertEquals("ENTERPRISE", plans.get(3).code());
        assertEquals(199_990, plans.get(3).monthlyPriceClp());
        assertTrue(plans.get(3).customPricing());
        assertNull(plans.get(3).overagePerMinuteClp());
    }
}

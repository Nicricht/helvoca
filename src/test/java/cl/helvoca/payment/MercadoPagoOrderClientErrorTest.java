package cl.helvoca.payment;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MercadoPagoOrderClientErrorTest {

    @Test
    void normalizesScaledClpAmountToWholeNumberString() {
        assertEquals("1000", MercadoPagoOrderClient.normalizeIntegerAmount(
                new java.math.BigDecimal("1000.00")));
        assertEquals("1500", MercadoPagoOrderClient.normalizeIntegerAmount(
                new java.math.BigDecimal("1500")));
        assertThrows(IllegalArgumentException.class, () ->
                MercadoPagoOrderClient.normalizeIntegerAmount(
                        new java.math.BigDecimal("1000.50")));
    }

    @Test
    void extractsNestedDetailsWithoutDumpingWholeProviderBody() {
        JSONObject body = new JSONObject()
                .put("details", new JSONArray().put(new JSONObject()
                        .put("code", "required_properties")
                        .put("message", "required property 'email' is missing")));

        assertEquals("required_properties", MercadoPagoOrderClient.providerErrorCode(body));
        assertEquals(
                "required property 'email' is missing",
                MercadoPagoOrderClient.providerErrorDetail(body));
    }

    @Test
    void prefersTopLevelProviderCodeWhenPresent() {
        JSONObject body = new JSONObject()
                .put("code", "invalid_total_amount")
                .put("message", "total amount mismatch");

        assertEquals("invalid_total_amount", MercadoPagoOrderClient.providerErrorCode(body));
        assertEquals("total amount mismatch", MercadoPagoOrderClient.providerErrorDetail(body));
    }

    @Test
    void sanitizesAndBoundsProviderDetail() {
        String longMessage = "bad\nvalue\t" + "x".repeat(400);
        JSONObject body = new JSONObject().put("message", longMessage);

        String detail = MercadoPagoOrderClient.providerErrorDetail(body);

        assertNotNull(detail);
        assertFalse(detail.contains("\n"));
        assertFalse(detail.contains("\t"));
        assertTrue(detail.length() <= 240);
    }
}

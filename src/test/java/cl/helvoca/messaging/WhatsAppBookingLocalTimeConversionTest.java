package cl.helvoca.messaging;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WhatsAppBookingLocalTimeConversionTest {

    @Test
    void convertsBusinessLocalTimeToInstantUsingBusinessTimezone() {
        JSONObject args = new JSONObject()
                .put("localDate", "2026-09-24")
                .put("localTime", "11:30");

        Instant startAt = WhatsAppToolService.resolveBookingStart(
                args,
                "America/Santiago",
                "startAt",
                "localDate",
                "localTime");

        assertEquals(Instant.parse("2026-09-24T14:30:00Z"), startAt);
    }

    @Test
    void preservesCanonicalInstantWhenLocalFieldsAreAbsent() {
        JSONObject args = new JSONObject()
                .put("startAt", "2026-09-24T14:30:00Z");

        Instant startAt = WhatsAppToolService.resolveBookingStart(
                args,
                "America/Santiago",
                "startAt",
                "localDate",
                "localTime");

        assertEquals(Instant.parse("2026-09-24T14:30:00Z"), startAt);
    }

    @Test
    void rejectsIncompleteLocalDateTime() {
        JSONObject args = new JSONObject()
                .put("localDate", "2026-09-24");

        assertThrows(
                IllegalArgumentException.class,
                () -> WhatsAppToolService.resolveBookingStart(
                        args,
                        "America/Santiago",
                        "startAt",
                        "localDate",
                        "localTime"));
    }
}

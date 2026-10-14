package cl.helvoca.billing;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class MercadoPagoWebhookTimestampGuardTest {
    private static final Instant NOW = Instant.parse("2026-10-14T12:00:00Z");
    private static final Duration WINDOW = Duration.ofMinutes(5);

    @Test
    void permitsFreshMillisTimestamp() {
        assertDoesNotThrow(() -> MercadoPagoWebhookTimestampGuard.validate(
                "ts=" + NOW.toEpochMilli() + ",v1=fake", WINDOW, NOW));
    }

    @Test
    void permitsFreshSecondsTimestampForLegacyProviderFormats() {
        assertDoesNotThrow(() -> MercadoPagoWebhookTimestampGuard.validate(
                "ts=" + NOW.getEpochSecond() + ",v1=fake", WINDOW, NOW));
    }

    @Test
    void rejectsOldSecondsTimestamp() {
        assertThrows(IllegalArgumentException.class, () -> MercadoPagoWebhookTimestampGuard.validate(
                "ts=" + NOW.minusSeconds(301).getEpochSecond() + ",v1=fake", WINDOW, NOW));
    }

    @Test
    void rejectsFutureMillisTimestamp() {
        assertThrows(IllegalArgumentException.class, () -> MercadoPagoWebhookTimestampGuard.validate(
                "ts=" + NOW.plusSeconds(301).toEpochMilli() + ",v1=fake", WINDOW, NOW));
    }

    @Test
    void acceptsBoundaryTimestamp() {
        assertDoesNotThrow(() -> MercadoPagoWebhookTimestampGuard.validate(
                "ts=" + NOW.minusSeconds(300).toEpochMilli() + ",v1=fake", WINDOW, NOW));
    }

    @Test
    void rejectsDuplicateTimestampEvenWhenValid() {
        String ts = Long.toString(NOW.toEpochMilli());
        assertThrows(IllegalArgumentException.class, () -> MercadoPagoWebhookTimestampGuard.validate(
                "ts=" + ts + ",ts=" + ts + ",v1=fake", WINDOW, NOW));
    }

    @Test
    void rejectsNonnumericAndMissingTimestamp() {
        for (String sig : new String[]{"v1=fake", "ts=garbage,v1=fake", "ts=0,v1=fake"}) {
            assertThrows(IllegalArgumentException.class, () ->
                    MercadoPagoWebhookTimestampGuard.validate(sig, WINDOW, NOW));
        }
    }

    @Test
    void refusesDisabledReplayWindow() {
        assertThrows(IllegalArgumentException.class, () -> MercadoPagoWebhookTimestampGuard.validate(
                "ts=" + NOW.toEpochMilli() + ",v1=fake", Duration.ZERO, NOW));
    }

    @Test
    void refusesMissingInputs() {
        assertThrows(IllegalArgumentException.class, () ->
                MercadoPagoWebhookTimestampGuard.validate(null, WINDOW, NOW));
        assertThrows(IllegalArgumentException.class, () ->
                MercadoPagoWebhookTimestampGuard.validate("ts=1", null, NOW));
        assertThrows(IllegalArgumentException.class, () ->
                MercadoPagoWebhookTimestampGuard.validate("ts=1", WINDOW, null));
    }

    @Test
    void rejectsHugeFutureEvenWithThirteenDigitTimestamp() {
        assertThrows(IllegalArgumentException.class, () -> MercadoPagoWebhookTimestampGuard.validate(
                "ts=9999999999999,v1=fake", WINDOW, NOW));
    }
}

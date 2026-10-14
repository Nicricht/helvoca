package cl.helvoca.billing;

import java.time.Duration;
import java.time.Instant;

/**
 * Applies a strict replay window after Mercado Pago SDK verified the HMAC.
 *
 * SDK-Java 3.7.0's tolerance overload interprets ts as seconds. Official
 * webhook examples also contain 13-digit millisecond timestamps. We preserve
 * the SDK's HMAC verification and independently accept both documented units,
 * never disabling replay-window checks.
 */
final class MercadoPagoWebhookTimestampGuard {

    private MercadoPagoWebhookTimestampGuard() { }

    static void validate(String header, Duration window, Instant now) {
        if (header == null || window == null || now == null || window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("Invalid webhook timestamp controls");
        }
        String ts = null;
        for (String fragment : header.split(",", -1)) {
            String[] kv = fragment.trim().split("=", 2);
            if (kv.length == 2 && "ts".equalsIgnoreCase(kv[0].trim())) {
                if (ts != null) {
                    throw new IllegalArgumentException("Repeated webhook timestamp");
                }
                ts = kv[1].trim();
            }
        }
        if (ts == null || !(ts.matches("[0-9]{10}") || ts.matches("[0-9]{13}"))) {
            throw new IllegalArgumentException("Invalid webhook timestamp");
        }
        try {
            long raw = Long.parseLong(ts);
            long millis = ts.length() == 10 ? Math.multiplyExact(raw, 1000L) : raw;
            long drift = Math.abs(Math.subtractExact(now.toEpochMilli(), millis));
            if (drift > window.toMillis()) {
                throw new IllegalArgumentException("Webhook timestamp outside permitted window");
            }
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Webhook timestamp arithmetic overflow", e);
        }
    }
}

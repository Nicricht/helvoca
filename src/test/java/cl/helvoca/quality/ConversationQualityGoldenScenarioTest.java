package cl.helvoca.quality;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationQualityGoldenScenarioTest {
    private final ConversationQualityEngine engine = new ConversationQualityEngine();

    @Test
    void regressionRepeatedReservationQuestionFails() {
        var report = engine.evaluate(List.of(
                turn("USER", "Quiero mañana a las cuatro."),
                turn("ASSISTANT", "¿A nombre de quién sería la reserva?"),
                turn("USER", "Nicolás."),
                turn("ASSISTANT", "¿A nombre de quién sería la reserva?")
        ), List.of());
        assertFalse(report.passed());
        assertTrue(has(report, "REPEATED_QUESTION"));
    }

    @Test
    void regressionDoubleBookingFails() {
        UUID id = UUID.randomUUID();
        var report = engine.evaluate(List.of(), List.of(
                action("BOOKING_CREATED", id),
                action("BOOKING_CREATED", id)
        ));
        assertFalse(report.passed());
        assertTrue(has(report, "DUPLICATE_SUCCESSFUL_EFFECT"));
    }

    @Test
    void regressionDoublePaymentFails() {
        UUID id = UUID.randomUUID();
        var report = engine.evaluate(List.of(), List.of(
                action("PAYMENT_CONFIRMED", id),
                action("PAYMENT_CONFIRMED", id)
        ));
        assertFalse(report.passed());
        assertTrue(has(report, "DUPLICATE_SUCCESSFUL_EFFECT"));
    }

    @Test
    void regressionDoubleInventoryConsumptionFails() {
        UUID id = UUID.randomUUID();
        var report = engine.evaluate(List.of(), List.of(
                action("INVENTORY_CONSUMED", id),
                action("INVENTORY_CONSUMED", id)
        ));
        assertFalse(report.passed());
        assertTrue(has(report, "DUPLICATE_SUCCESSFUL_EFFECT"));
    }

    @Test
    void regressionSpeakingAfterFarewellFails() {
        var report = engine.evaluate(List.of(
                turn("USER", "No necesito nada más, chao."),
                turn("ASSISTANT", "Chao, que estés súper."),
                turn("ASSISTANT", "¿Quieres que revise otra cosa?")
        ), List.of());
        assertFalse(report.passed());
        assertTrue(has(report, "POST_FAREWELL_CONTINUATION"));
    }

    @Test
    void healthySingleConfirmationSingleEffectPasses() {
        var report = engine.evaluate(List.of(
                turn("USER", "Quiero reservar mañana a las cuatro."),
                turn("ASSISTANT", "Perfecto. ¿A nombre de quién sería?"),
                turn("USER", "Nicolás."),
                turn("ASSISTANT", "Mañana a las cuatro para Nicolás. ¿Confirmo?"),
                turn("USER", "Sí."),
                turn("ASSISTANT", "Listo, quedó reservado.")
        ), List.of(action("BOOKING_CREATED", UUID.randomUUID())));
        assertTrue(report.passed());
    }

    private static ConversationQualityEngine.Turn turn(String speaker, String text) {
        return new ConversationQualityEngine.Turn(speaker, text);
    }

    private static ConversationQualityEngine.Action action(String type, UUID entityId) {
        return new ConversationQualityEngine.Action(type, true, "ENTITY", entityId, type);
    }

    private static boolean has(ConversationQualityEngine.Report report, String code) {
        return report.findings().stream().anyMatch(f -> code.equals(f.code()));
    }
}

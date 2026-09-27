package cl.helvoca.quality;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ConversationQualityEngineTest {
    private final ConversationQualityEngine engine = new ConversationQualityEngine();

    @Test
    void cleanBookingConversationPasses() {
        var report = engine.evaluate(
                List.of(
                        new ConversationQualityEngine.Turn("USER", "Quiero reservar mañana."),
                        new ConversationQualityEngine.Turn("ASSISTANT", "Claro. ¿A qué hora te acomoda?"),
                        new ConversationQualityEngine.Turn("USER", "A las cuatro."),
                        new ConversationQualityEngine.Turn("ASSISTANT", "Perfecto. ¿A nombre de quién sería?"),
                        new ConversationQualityEngine.Turn("USER", "Nicolás."),
                        new ConversationQualityEngine.Turn("ASSISTANT", "Tengo mañana a las cuatro para Nicolás. ¿Confirmo?"),
                        new ConversationQualityEngine.Turn("USER", "Sí."),
                        new ConversationQualityEngine.Turn("ASSISTANT", "Listo, quedó reservada. Que estés muy bien.")
                ),
                List.of(new ConversationQualityEngine.Action(
                        "BOOKING_CREATED", true, "BOOKING", UUID.randomUUID(), "created")));

        assertTrue(report.passed());
        assertEquals(0, report.errors());
    }

    @Test
    void catchesRepeatedBookingQuestion() {
        var report = engine.evaluate(
                List.of(
                        new ConversationQualityEngine.Turn("USER", "Mañana a las cuatro."),
                        new ConversationQualityEngine.Turn("ASSISTANT", "¿A nombre de quién sería la reserva?"),
                        new ConversationQualityEngine.Turn("USER", "Nicolás."),
                        new ConversationQualityEngine.Turn("ASSISTANT", "Perfecto. ¿A nombre de quién sería la reserva?")
                ),
                List.of());

        assertFalse(report.passed());
        assertTrue(report.findings().stream().anyMatch(f -> "REPEATED_QUESTION".equals(f.code())));
    }

    @Test
    void catchesDuplicateSuccessfulSideEffect() {
        UUID bookingId = UUID.randomUUID();
        var report = engine.evaluate(
                List.of(),
                List.of(
                        new ConversationQualityEngine.Action("BOOKING_CREATED", true, "BOOKING", bookingId, "first"),
                        new ConversationQualityEngine.Action("BOOKING_CREATED", true, "BOOKING", bookingId, "retry")
                ));

        assertFalse(report.passed());
        assertTrue(report.findings().stream().anyMatch(f -> "DUPLICATE_SUCCESSFUL_EFFECT".equals(f.code())));
    }

    @Test
    void catchesConversationContinuingAfterFarewell() {
        var report = engine.evaluate(
                List.of(
                        new ConversationQualityEngine.Turn("USER", "Eso sería todo, chao."),
                        new ConversationQualityEngine.Turn("ASSISTANT", "Chao, que estés muy bien."),
                        new ConversationQualityEngine.Turn("ASSISTANT", "¿Necesitas alguna otra cosa?")
                ),
                List.of());

        assertFalse(report.passed());
        assertTrue(report.findings().stream().anyMatch(f -> "POST_FAREWELL_CONTINUATION".equals(f.code())));
    }

    @Test
    void flagsLongAssistantTurnWithoutFailingTheConversation() {
        String longReply = "uno dos tres cuatro cinco seis siete ocho nueve diez once doce trece catorce quince "
                + "dieciseis diecisiete dieciocho diecinueve veinte veintiuno veintidos veintitres veinticuatro "
                + "veinticinco veintiseis veintisiete veintiocho veintinueve treinta treintauno treintados "
                + "treintatres treintacuatro treintacinco treintaseis";

        var report = engine.evaluate(
                List.of(new ConversationQualityEngine.Turn("ASSISTANT", longReply)),
                List.of());

        assertTrue(report.passed());
        assertEquals(1, report.warnings());
        assertEquals("ASSISTANT_TURN_TOO_LONG", report.findings().get(0).code());
    }
}

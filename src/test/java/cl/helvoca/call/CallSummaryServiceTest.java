package cl.helvoca.call;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CallSummaryServiceTest {

    @Test
    void localSummaryUsesTranscriptAndVerifiedActionsWithoutExternalAi() {
        CallTranscript first = transcript("USER", "Quiero reservar una consulta mañana a las 10");
        CallTranscript assistant = transcript("ASSISTANT", "Claro, voy a comprobar disponibilidad.");
        CallTranscript last = transcript("USER", "Perfecto, muchas gracias");

        CallAction created = action("BOOKING_CREATED", true, "Consulta · 2026-09-14T10:00-03:00", null);
        CallAction rescheduled = action("BOOKING_RESCHEDULED", true, "Consulta · 2026-09-14T11:00-03:00", null);
        CallAction cancelled = action("BOOKING_CANCELLED", true, "Consulta · 2026-09-14T11:00-03:00", null);
        CallAction failed = action("HUMAN_TRANSFER", false, null, "HUMAN_TRANSFER_FAILED");

        String summary = CallSummaryService.buildLocalSummary(
                List.of(first, assistant, last),
                List.of(created, rescheduled, cancelled, failed));

        assertTrue(summary.contains("3 intervenciones"));
        assertTrue(summary.contains("Motivo inicial: Quiero reservar una consulta mañana a las 10"));
        assertTrue(summary.contains("reserva creada"));
        assertTrue(summary.contains("reserva reprogramada"));
        assertTrue(summary.contains("reserva cancelada"));
        assertTrue(summary.contains("HUMAN_TRANSFER_FAILED"));
        assertTrue(summary.contains("Último mensaje del cliente: Perfecto, muchas gracias"));
        assertFalse(summary.toLowerCase().contains("openai"));
        assertFalse(summary.toLowerCase().contains("proveedor de ia"));
    }

    @Test
    void localSummaryMarksUnansweredQuestionAsPending() {
        CallAction unanswered = action(
                "UNANSWERED_QUESTION_RECORDED",
                true,
                "¿Tienen estacionamiento?",
                null);

        String summary = CallSummaryService.buildLocalSummary(List.of(), List.of(unanswered));

        assertTrue(summary.contains("pregunta pendiente registrada"));
        assertTrue(summary.contains("Quedó al menos una pregunta pendiente"));
    }

    @Test
    void certificationPrefixIsRemovedFromCustomerText() {
        CallTranscript item = transcript("USER", "[SIMULATED_CERTIFICATION] Necesito una reserva para mañana");

        String summary = CallSummaryService.buildLocalSummary(List.of(item), List.of());

        assertTrue(summary.contains("Motivo inicial: Necesito una reserva para mañana"));
        assertFalse(summary.contains("SIMULATED_CERTIFICATION"));
    }

    private static CallTranscript transcript(String speaker, String content) {
        CallTranscript item = new CallTranscript();
        item.setSpeaker(speaker);
        item.setContent(content);
        return item;
    }

    private static CallAction action(String type, boolean success, String detail, String errorCode) {
        CallAction action = new CallAction();
        action.setActionType(type);
        action.setSuccess(success);
        action.setDetail(detail);
        action.setErrorCode(errorCode);
        return action;
    }
}

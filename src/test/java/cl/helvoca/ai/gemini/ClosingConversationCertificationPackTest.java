package cl.helvoca.ai.gemini;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ClosingConversationCertificationPackTest {

    record ExplicitScenario(String phrase, boolean shouldClose) {}
    record ContextScenario(String user, String lastAssistant, String previousAssistant, boolean shouldClose) {}

    @TestFactory
    Stream<DynamicTest> explicitClosingIntentMatrix() {
        List<ExplicitScenario> scenarios = List.of(
                new ExplicitScenario("No, gracias.", true),
                new ExplicitScenario("Eso sería todo.", true),
                new ExplicitScenario("Chao.", true),
                new ExplicitScenario("Hasta luego.", true),
                new ExplicitScenario("Puedes cortar la llamada.", true),
                new ExplicitScenario("Cuelga, por favor.", true),
                new ExplicitScenario("Terminemos la llamada.", true),
                new ExplicitScenario("No necesito nada más.", true),
                new ExplicitScenario("Sí.", false),
                new ExplicitScenario("Perfecto.", false),
                new ExplicitScenario("Dale.", false),
                new ExplicitScenario("No.", false),
                new ExplicitScenario("No, esa hora no.", false),
                new ExplicitScenario("No, prefiero mañana.", false),
                new ExplicitScenario("Quiero otra hora.", false)
        );

        return scenarios.stream().map(s -> DynamicTest.dynamicTest(
                "explicit: " + s.phrase(),
                () -> assertEquals(s.shouldClose(),
                        GeminiLiveVoiceSession.hasExplicitClosingIntent(s.phrase()))));
    }

    @TestFactory
    Stream<DynamicTest> contextualClosingIntentMatrix() {
        List<ContextScenario> scenarios = List.of(
                new ContextScenario("No.", "¿Necesitas algo más?", "", true),
                new ContextScenario("No.", "¿Te ayudo con algo más?", "", true),
                new ContextScenario("No.", "¿Puedo ayudarte con algo más?", "", true),
                new ContextScenario("No.", "¿Alguna otra cosa?", "", true),
                new ContextScenario("No.", "¿Quieres otro horario?", "", false),
                new ContextScenario("No.", "¿Confirmas la reserva?", "", false),
                new ContextScenario("No.", "¿Te sirve mañana?", "", false),
                new ContextScenario("Sí.", "¿Necesitas algo más?", "", false),
                new ContextScenario("Perfecto.", "¿Necesitas algo más?", "", false),
                new ContextScenario("No.", "Muchas gracias por llamar.", "¿Necesitas algo más?", true)
        );

        return scenarios.stream().map(s -> DynamicTest.dynamicTest(
                "context: " + s.user() + " / " + s.lastAssistant(),
                () -> assertEquals(s.shouldClose(),
                        GeminiLiveVoiceSession.hasClosingIntent(
                                s.user(), s.lastAssistant(), s.previousAssistant()))));
    }
}

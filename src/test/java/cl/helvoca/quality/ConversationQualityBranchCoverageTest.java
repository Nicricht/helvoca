package cl.helvoca.quality;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ConversationQualityBranchCoverageTest {

    @Test
    void qualityEngineCoversNullInputsQuestionFormsAndActionKinds() {
        ConversationQualityEngine engine = new ConversationQualityEngine();

        assertTrue(engine.evaluate(null, null, 0).passed());
        assertEquals("", ConversationQualityEngine.normalize(null));

        List<ConversationQualityEngine.Turn> turns = List.of(
                new ConversationQualityEngine.Turn("ASSISTANT", "que hora"),
                new ConversationQualityEngine.Turn("ASSISTANT", "cual servicio"),
                new ConversationQualityEngine.Turn("ASSISTANT", "cuando vienes"),
                new ConversationQualityEngine.Turn("ASSISTANT", "como prefieres"),
                new ConversationQualityEngine.Turn("ASSISTANT", "a nombre de quien"),
                new ConversationQualityEngine.Turn("ASSISTANT", "te acomoda mañana"),
                new ConversationQualityEngine.Turn("ASSISTANT", "te sirve tarde"),
                new ConversationQualityEngine.Turn("ASSISTANT", "te tinca cuatro"),
                new ConversationQualityEngine.Turn("ASSISTANT", "esto no es pregunta"),
                new ConversationQualityEngine.Turn("ASSISTANT", "??"),
                new ConversationQualityEngine.Turn("ASSISTANT", "a?")
        );
        engine.evaluate(turns, List.of());

        List<ConversationQualityEngine.Action> actions = new ArrayList<>();
        actions.add(null);
        actions.add(new ConversationQualityEngine.Action("PAYMENT", false, "PAYMENT", UUID.randomUUID(), "failed"));
        actions.add(new ConversationQualityEngine.Action(null, true, null, null, null));
        actions.add(action("CREATE_CUSTOMER"));
        actions.add(action("BOOKING_CONFIRMED"));
        actions.add(action("CANCEL_BOOKING"));
        actions.add(action("RESCHEDULE_BOOKING"));
        actions.add(action("TRANSFER_TO_HUMAN"));
        actions.add(action("ORDER_CREATED"));
        actions.add(action("STOCK_RESERVED"));
        actions.add(action("RESERVATION_CREATED"));
        actions.add(action("READ_ONLY_LOOKUP"));

        assertTrue(engine.evaluate(List.of(), actions).passed());

        var duplicateWithoutEntity = engine.evaluate(List.of(), List.of(
                new ConversationQualityEngine.Action("PAYMENT_CONFIRMED", true, "PAYMENT", null, "same detail"),
                new ConversationQualityEngine.Action("PAYMENT_CONFIRMED", true, "PAYMENT", null, "same detail")
        ));
        assertFalse(duplicateWithoutEntity.passed());
        assertTrue(duplicateWithoutEntity.findings().stream()
                .anyMatch(f -> "DUPLICATE_SUCCESSFUL_EFFECT".equals(f.code())));
    }

    @Test
    void farewellRecognizerCoversSupportedClosingFormsAndNegatives() {
        for (String text : List.of(
                "chao", "chau", "adiós", "hasta luego", "hasta pronto",
                "nos vemos", "eso sería todo", "no necesito nada más",
                "puedes cortar", "corta la llamada")) {
            assertTrue(ConversationQualityEngine.farewell(text), text);
        }
        assertFalse(ConversationQualityEngine.farewell(""));
        assertFalse(ConversationQualityEngine.farewell("sigamos conversando"));
    }

    @Test
    void replayFixtureNormalizesNullableCollectionsAndRejectsInvalidSchema() {
        assertThrows(IllegalArgumentException.class,
                () -> new ConversationReplayFixture(0, "bad", "x", List.of(), List.of(), null));

        ConversationReplayFixture fixture =
                new ConversationReplayFixture(1, "empty", "fingerprint", null, null, null);
        assertTrue(fixture.turns().isEmpty());
        assertTrue(fixture.actions().isEmpty());
        assertTrue(fixture.expected().passed());

        ConversationReplayFixture.Expected expected =
                new ConversationReplayFixture.Expected(false, null);
        assertTrue(expected.findingCodes().isEmpty());
    }

    @Test
    void anonymizerCoversNullBlankAndMalformedKnownValues() {
        ConversationReplayAnonymizer anonymizer = new ConversationReplayAnonymizer();

        assertNull(anonymizer.redact(null, null));
        assertEquals("   ", anonymizer.redact("   ", null));
        assertEquals("hola", anonymizer.redact("hola", null));

        Map<String, String> known = new LinkedHashMap<>();
        known.put(null, "SecretName");
        known.put("***", "OtherSecret");
        known.put("BLANK_VALUE", " ");
        known.put("NULL_VALUE", null);

        String result = anonymizer.redact("SecretName OtherSecret", known);
        assertEquals("[REDACTED] [REDACTED]", result);
    }

    @Test
    void replayRunnerCoversNullFixtureAnonymousActionsAndSignatureMismatches() {
        ConversationReplayRunner runner = new ConversationReplayRunner();
        assertThrows(IllegalArgumentException.class, () -> runner.run(null));

        ConversationReplayFixture anonymousActions = new ConversationReplayFixture(
                1, "anonymous-actions", "x",
                List.of(),
                List.of(
                        new ConversationReplayFixture.ReplayAction(
                                "LOOKUP", true, "NONE", null, "one"),
                        new ConversationReplayFixture.ReplayAction(
                                "LOOKUP", true, "NONE", " ", "two")),
                new ConversationReplayFixture.Expected(true, List.of()));
        assertTrue(runner.run(anonymousActions).matchesExpected());

        ConversationReplayFixture missingExpected = new ConversationReplayFixture(
                1, "missing", "x", List.of(), List.of(),
                new ConversationReplayFixture.Expected(true, List.of("REPEATED_QUESTION")));
        var missing = runner.run(missingExpected);
        assertFalse(missing.matchesExpected());
        assertEquals(List.of("REPEATED_QUESTION"), missing.missingFindingCodes());

        ConversationReplayFixture unexpected = new ConversationReplayFixture(
                1, "unexpected", "x",
                List.of(
                        new ConversationReplayFixture.ReplayTurn("ASSISTANT", "¿A nombre de quién sería?"),
                        new ConversationReplayFixture.ReplayTurn("USER", "X"),
                        new ConversationReplayFixture.ReplayTurn("ASSISTANT", "Perfecto. ¿A nombre de quién sería?")),
                List.of(),
                new ConversationReplayFixture.Expected(false, List.of()));
        var unexpectedResult = runner.run(unexpected);
        assertFalse(unexpectedResult.matchesExpected());
        assertTrue(unexpectedResult.unexpectedFindingCodes().contains("REPEATED_QUESTION"));
    }

    private static ConversationQualityEngine.Action action(String type) {
        return new ConversationQualityEngine.Action(type, true, "ENTITY", UUID.randomUUID(), type);
    }
}

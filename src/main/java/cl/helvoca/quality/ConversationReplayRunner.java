package cl.helvoca.quality;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class ConversationReplayRunner {
    private final ConversationQualityEngine engine;

    public ConversationReplayRunner() {
        this(new ConversationQualityEngine());
    }

    ConversationReplayRunner(ConversationQualityEngine engine) {
        this.engine = engine;
    }

    public ReplayResult run(ConversationReplayFixture fixture) {
        if (fixture == null) throw new IllegalArgumentException("fixture is required");

        List<ConversationQualityEngine.Turn> turns = fixture.turns().stream()
                .map(turn -> new ConversationQualityEngine.Turn(turn.speaker(), turn.text()))
                .toList();

        List<ConversationQualityEngine.Action> actions = fixture.actions().stream()
                .map(action -> new ConversationQualityEngine.Action(
                        action.type(),
                        action.success(),
                        action.entityType(),
                        entityUuid(action.entityRef()),
                        action.detail()))
                .toList();

        ConversationQualityEngine.Report report = engine.evaluate(turns, actions);
        Set<String> actualCodes = new LinkedHashSet<>();
        for (ConversationQualityEngine.Finding finding : report.findings()) {
            actualCodes.add(finding.code());
        }

        Set<String> expectedCodes = new LinkedHashSet<>(fixture.expected().findingCodes());
        List<String> missing = new ArrayList<>(expectedCodes);
        missing.removeAll(actualCodes);
        List<String> unexpected = new ArrayList<>(actualCodes);
        unexpected.removeAll(expectedCodes);

        boolean matches = report.passed() == fixture.expected().passed()
                && missing.isEmpty()
                && unexpected.isEmpty();

        return new ReplayResult(matches, report, List.copyOf(missing), List.copyOf(unexpected));
    }

    private static UUID entityUuid(String entityRef) {
        if (entityRef == null || entityRef.isBlank()) return null;
        return UUID.nameUUIDFromBytes(entityRef.getBytes(StandardCharsets.UTF_8));
    }

    public record ReplayResult(
            boolean matchesExpected,
            ConversationQualityEngine.Report report,
            List<String> missingFindingCodes,
            List<String> unexpectedFindingCodes) {}
}

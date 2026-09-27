package cl.helvoca.quality;

import java.util.List;

public record ConversationReplayFixture(
        int schemaVersion,
        String name,
        String sourceFingerprint,
        List<ReplayTurn> turns,
        List<ReplayAction> actions,
        Expected expected) {

    public ConversationReplayFixture {
        if (schemaVersion <= 0) throw new IllegalArgumentException("schemaVersion must be positive");
        turns = turns == null ? List.of() : List.copyOf(turns);
        actions = actions == null ? List.of() : List.copyOf(actions);
        expected = expected == null ? new Expected(true, List.of()) : expected;
    }

    public record ReplayTurn(String speaker, String text) {}

    public record ReplayAction(
            String type,
            boolean success,
            String entityType,
            String entityRef,
            String detail) {}

    public record Expected(boolean passed, List<String> findingCodes) {
        public Expected {
            findingCodes = findingCodes == null ? List.of() : List.copyOf(findingCodes);
        }
    }
}

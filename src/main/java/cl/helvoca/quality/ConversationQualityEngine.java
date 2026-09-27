package cl.helvoca.quality;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ConversationQualityEngine {
    private static final int DEFAULT_MAX_ASSISTANT_WORDS = 35;

    public Report evaluate(List<Turn> turns, List<Action> actions) {
        return evaluate(turns, actions, DEFAULT_MAX_ASSISTANT_WORDS);
    }

    public Report evaluate(List<Turn> turns, List<Action> actions, int maxAssistantWords) {
        List<Turn> safeTurns = turns == null ? List.of() : turns;
        List<Action> safeActions = actions == null ? List.of() : actions;
        List<Finding> findings = new ArrayList<>();

        detectRepeatedAssistantQuestions(safeTurns, findings);
        detectRepeatedAssistantStatements(safeTurns, findings);
        detectPostFarewellContinuation(safeTurns, findings);
        detectLongAssistantTurns(safeTurns, Math.max(1, maxAssistantWords), findings);
        detectDuplicateSuccessfulEffects(safeActions, findings);

        long errors = findings.stream().filter(f -> f.severity() == Severity.ERROR).count();
        long warnings = findings.stream().filter(f -> f.severity() == Severity.WARNING).count();
        return new Report(errors == 0, (int) errors, (int) warnings, List.copyOf(findings));
    }

    private static void detectRepeatedAssistantQuestions(List<Turn> turns, List<Finding> findings) {
        List<IndexedTurn> assistantQuestions = new ArrayList<>();
        for (int i = 0; i < turns.size(); i++) {
            Turn turn = turns.get(i);
            if (!assistant(turn) || !question(turn.text())) continue;
            String normalized = normalize(turn.text());
            if (normalized.isBlank()) continue;

            for (IndexedTurn previous : assistantQuestions) {
                if (i - previous.index() > 6) continue;
                double similarity = jaccard(tokens(normalized), tokens(previous.normalized()));
                if (similarity >= 0.78d) {
                    findings.add(new Finding(
                            "REPEATED_QUESTION",
                            Severity.ERROR,
                            i,
                            "La asistente repitió una pregunta ya formulada.",
                            Map.of("similarity", round(similarity), "previousTurn", previous.index())));
                    break;
                }
            }
            assistantQuestions.add(new IndexedTurn(i, normalized));
        }
    }

    private static void detectRepeatedAssistantStatements(List<Turn> turns, List<Finding> findings) {
        String previousAssistant = null;
        int previousIndex = -1;
        for (int i = 0; i < turns.size(); i++) {
            Turn turn = turns.get(i);
            if (!assistant(turn)) continue;
            String normalized = normalize(turn.text());
            if (normalized.isBlank()) continue;
            if (previousAssistant != null && normalized.equals(previousAssistant)) {
                findings.add(new Finding(
                        "REPEATED_ASSISTANT_TURN",
                        Severity.ERROR,
                        i,
                        "La asistente repitió exactamente su intervención anterior.",
                        Map.of("previousTurn", previousIndex)));
            }
            previousAssistant = normalized;
            previousIndex = i;
        }
    }

    private static void detectPostFarewellContinuation(List<Turn> turns, List<Finding> findings) {
        for (int i = 0; i < turns.size(); i++) {
            Turn turn = turns.get(i);
            if (!user(turn) || !farewell(turn.text())) continue;

            int assistantAfterFarewell = 0;
            for (int j = i + 1; j < turns.size(); j++) {
                if (assistant(turns.get(j))) {
                    assistantAfterFarewell++;
                    if (assistantAfterFarewell > 1) {
                        findings.add(new Finding(
                                "POST_FAREWELL_CONTINUATION",
                                Severity.ERROR,
                                j,
                                "La asistente siguió conversando después de una despedida explícita.",
                                Map.of("farewellTurn", i)));
                        return;
                    }
                }
            }
            return;
        }
    }

    private static void detectLongAssistantTurns(
            List<Turn> turns,
            int maxAssistantWords,
            List<Finding> findings) {
        for (int i = 0; i < turns.size(); i++) {
            Turn turn = turns.get(i);
            if (!assistant(turn)) continue;
            int words = wordCount(turn.text());
            if (words > maxAssistantWords) {
                findings.add(new Finding(
                        "ASSISTANT_TURN_TOO_LONG",
                        Severity.WARNING,
                        i,
                        "La respuesta de la asistente es demasiado larga para una conversación ágil.",
                        Map.of("words", words, "limit", maxAssistantWords)));
            }
        }
    }

    private static void detectDuplicateSuccessfulEffects(List<Action> actions, List<Finding> findings) {
        Map<String, Integer> firstByEffect = new LinkedHashMap<>();
        for (int i = 0; i < actions.size(); i++) {
            Action action = actions.get(i);
            if (action == null || !action.success()) continue;
            String type = normalizeAction(action.type());
            if (!consequential(type)) continue;

            String identity = effectIdentity(action, type);
            Integer previous = firstByEffect.putIfAbsent(identity, i);
            if (previous != null) {
                findings.add(new Finding(
                        "DUPLICATE_SUCCESSFUL_EFFECT",
                        Severity.ERROR,
                        i,
                        "La misma acción con efecto real terminó con éxito más de una vez.",
                        Map.of("previousAction", previous, "actionType", type)));
            }
        }
    }

    private static String effectIdentity(Action action, String type) {
        if (action.entityId() != null) return type + "|" + action.entityId();
        String detail = normalize(action.detail());
        return type + "|" + detail;
    }

    private static boolean consequential(String type) {
        return type.contains("CREATE")
                || type.contains("CREATED")
                || type.contains("CONFIRM")
                || type.contains("CONFIRMED")
                || type.contains("PAYMENT")
                || type.contains("CANCEL")
                || type.contains("RESCHEDULE")
                || type.contains("TRANSFER")
                || type.contains("ORDER");
    }

    private static boolean assistant(Turn turn) {
        return turn != null && "ASSISTANT".equalsIgnoreCase(turn.speaker());
    }

    private static boolean user(Turn turn) {
        return turn != null && "USER".equalsIgnoreCase(turn.speaker());
    }

    private static boolean question(String text) {
        if (text == null) return false;
        String normalized = normalize(text);
        return text.contains("?")
                || normalized.startsWith("que ")
                || normalized.startsWith("cual ")
                || normalized.startsWith("cuando ")
                || normalized.startsWith("como ")
                || normalized.startsWith("a nombre de")
                || normalized.startsWith("te acomoda")
                || normalized.startsWith("te sirve")
                || normalized.startsWith("te tinca");
    }

    static boolean farewell(String text) {
        String value = normalize(text);
        if (value.isBlank()) return false;
        return value.contains("chao")
                || value.contains("chau")
                || value.contains("adios")
                || value.contains("hasta luego")
                || value.contains("hasta pronto")
                || value.contains("nos vemos")
                || value.contains("eso seria todo")
                || value.contains("no necesito nada mas")
                || value.contains("puedes cortar")
                || value.contains("corta la llamada");
    }

    static String normalize(String text) {
        if (text == null) return "";
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
        return decomposed
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9+ ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String normalizeAction(String type) {
        return type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
    }

    private static Set<String> tokens(String normalized) {
        Set<String> result = new HashSet<>();
        for (String token : normalized.split(" ")) {
            if (token.length() > 2) result.add(token);
        }
        return result;
    }

    private static double jaccard(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) return 0d;
        Set<String> intersection = new HashSet<>(left);
        intersection.retainAll(right);
        Set<String> union = new HashSet<>(left);
        union.addAll(right);
        return union.isEmpty() ? 0d : (double) intersection.size() / union.size();
    }

    private static int wordCount(String text) {
        String normalized = normalize(text);
        return normalized.isBlank() ? 0 : normalized.split(" ").length;
    }

    private static double round(double value) {
        return Math.round(value * 100d) / 100d;
    }

    private record IndexedTurn(int index, String normalized) {}

    public enum Severity { WARNING, ERROR }

    public record Turn(String speaker, String text) {}

    public record Action(
            String type,
            boolean success,
            String entityType,
            UUID entityId,
            String detail) {}

    public record Finding(
            String code,
            Severity severity,
            int index,
            String message,
            Map<String, Object> evidence) {}

    public record Report(
            boolean passed,
            int errors,
            int warnings,
            List<Finding> findings) {}
}

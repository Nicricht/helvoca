package cl.helvoca.agent;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Provider-neutral voice profiles exposed to Helvoca tenants.
 *
 * <p>New selections are stored using an OpenAI-safe canonical alias because
 * OpenAI Live already consumes the tenant voice directly. Gemini translates
 * that alias to its provider-specific equivalent at session creation. Known
 * legacy provider values remain readable at runtime.</p>
 */
public enum AgentVoiceProfile {
    NATURAL("natural", "Natural", "Equilibrada y conversacional", "marin", "Aoede"),
    PROFESSIONAL("professional", "Profesional", "Clara y orientada a atención", "cedar", "Charon"),
    FRIENDLY("friendly", "Amigable", "Cercana y cordial", "coral", "Achird"),
    KNOWLEDGEABLE("knowledgeable", "Experta", "Segura y enfocada en información", "sage", "Sadaltager"),
    BRIGHT("bright", "Brillante", "Ágil y luminosa", "shimmer", "Zephyr"),
    CLEAR("clear", "Clara", "Directa y fácil de entender", "verse", "Iapetus"),
    SOFT("soft", "Suave", "Calmada y delicada", "ballad", "Achernar"),
    SMOOTH("smooth", "Serena", "Fluida y estable", "ash", "Algieba"),
    MATURE("mature", "Madura", "Sobria y con presencia", "echo", "Gacrux"),
    NEUTRAL("neutral", "Neutra", "Balanceada para uso general", "alloy", "Schedar");

    private static final Set<String> OPENAI_REALTIME_VOICES = Set.of(
            "alloy", "ash", "ballad", "coral", "echo", "sage", "shimmer", "verse", "marin", "cedar");

    private static final Set<String> GEMINI_VOICES = Set.of(
            "Zephyr", "Puck", "Charon", "Kore", "Fenrir", "Leda", "Orus", "Aoede",
            "Callirrhoe", "Autonoe", "Enceladus", "Iapetus", "Umbriel", "Algieba", "Despina",
            "Erinome", "Algenib", "Rasalgethi", "Laomedeia", "Achernar", "Alnilam", "Schedar",
            "Gacrux", "Pulcherrima", "Achird", "Zubenelgenubi", "Vindemiatrix", "Sadachbia",
            "Sadaltager", "Sulafat");

    private final String code;
    private final String displayName;
    private final String description;
    private final String openAiVoice;
    private final String geminiVoice;

    AgentVoiceProfile(String code, String displayName, String description, String openAiVoice, String geminiVoice) {
        this.code = code;
        this.displayName = displayName;
        this.description = description;
        this.openAiVoice = openAiVoice;
        this.geminiVoice = geminiVoice;
    }

    public String code() { return code; }
    public String displayName() { return displayName; }
    public String description() { return description; }
    public String openAiVoice() { return openAiVoice; }
    public String geminiVoice() { return geminiVoice; }

    public static List<AgentVoiceProfile> catalog() {
        return List.of(values());
    }

    public static Optional<AgentVoiceProfile> fromSelection(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        String normalized = value.trim();
        return Arrays.stream(values())
                .filter(profile -> profile.code.equalsIgnoreCase(normalized)
                        || profile.openAiVoice.equalsIgnoreCase(normalized)
                        || profile.geminiVoice.equalsIgnoreCase(normalized))
                .findFirst();
    }

    public static String normalizeForStorage(String value) {
        if (value == null || value.isBlank()) return null;
        return fromSelection(value)
                .map(AgentVoiceProfile::openAiVoice)
                .orElseThrow(() -> new IllegalArgumentException("Unsupported agent voice"));
    }

    public static String resolveOpenAi(String storedSelection, String fallback) {
        if (storedSelection == null || storedSelection.isBlank()) return fallback;
        Optional<AgentVoiceProfile> profile = fromSelection(storedSelection);
        if (profile.isPresent()) return profile.get().openAiVoice;
        return isOpenAiRealtimeVoice(storedSelection) ? storedSelection.trim().toLowerCase(Locale.ROOT) : fallback;
    }

    public static String resolveGemini(String storedSelection, String fallback) {
        if (storedSelection == null || storedSelection.isBlank()) return fallback;
        Optional<AgentVoiceProfile> profile = fromSelection(storedSelection);
        if (profile.isPresent()) return profile.get().geminiVoice;
        String rawGemini = canonicalGeminiVoice(storedSelection);
        return rawGemini == null ? fallback : rawGemini;
    }

    private static boolean isOpenAiRealtimeVoice(String value) {
        return value != null && OPENAI_REALTIME_VOICES.contains(value.trim().toLowerCase(Locale.ROOT));
    }

    private static String canonicalGeminiVoice(String value) {
        if (value == null) return null;
        return GEMINI_VOICES.stream()
                .filter(candidate -> candidate.equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElse(null);
    }
}

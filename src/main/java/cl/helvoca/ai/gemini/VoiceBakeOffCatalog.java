package cl.helvoca.ai.gemini;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Curated Gemini prebuilt voices used only by the explicit voice bake-off harness.
 * Traits mirror Google's public prebuilt-voice labels so comparisons stay
 * reproducible and provider-native.
 */
public final class VoiceBakeOffCatalog {
    private static final List<Candidate> CANDIDATES = List.of(
            new Candidate("Leda", "Youthful"),
            new Candidate("Sadachbia", "Lively"),
            new Candidate("Laomedeia", "Upbeat"),
            new Candidate("Achird", "Friendly"),
            new Candidate("Aoede", "Breezy"),
            new Candidate("Sulafat", "Warm")
    );

    private static final Map<String, Candidate> BY_KEY = CANDIDATES.stream()
            .collect(java.util.stream.Collectors.toUnmodifiableMap(
                    candidate -> candidate.voiceName().toLowerCase(Locale.ROOT),
                    candidate -> candidate));

    private VoiceBakeOffCatalog() {}

    public static List<Candidate> candidates() {
        return CANDIDATES;
    }

    public static Optional<Candidate> find(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        return Optional.ofNullable(BY_KEY.get(value.trim().toLowerCase(Locale.ROOT)));
    }

    public static String normalize(String value) {
        return find(value).map(Candidate::voiceName).orElse(null);
    }

    public static boolean allowed(String value) {
        return find(value).isPresent();
    }

    public record Candidate(String voiceName, String providerTrait) {}
}

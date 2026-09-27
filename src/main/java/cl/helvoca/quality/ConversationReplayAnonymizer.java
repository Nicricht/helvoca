package cl.helvoca.quality;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ConversationReplayAnonymizer {
    private static final Pattern EMAIL =
            Pattern.compile("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
    private static final Pattern PHONE =
            Pattern.compile("(?<![A-Za-z0-9])(?:\\+?\\d[\\d\\s().-]{7,}\\d)(?![A-Za-z0-9])");
    private static final Pattern CHILEAN_RUT =
            Pattern.compile("(?i)\\b\\d{1,2}\\.?\\d{3}\\.?\\d{3}-[0-9K]\\b");
    private static final Pattern URL =
            Pattern.compile("(?i)\\bhttps?://[^\\s]+");

    public String redact(String text, Map<String, String> knownSensitiveValues) {
        if (text == null || text.isBlank()) return text;

        String result = text;
        Map<String, String> values = knownSensitiveValues == null
                ? Map.of()
                : new LinkedHashMap<>(knownSensitiveValues);

        for (Map.Entry<String, String> entry : values.entrySet()) {
            String value = entry.getValue();
            if (value == null || value.isBlank()) continue;
            String placeholder = placeholder(entry.getKey());
            result = result.replaceAll("(?i)" + Pattern.quote(value.trim()),
                    Matcher.quoteReplacement(placeholder));
        }

        result = EMAIL.matcher(result).replaceAll("[EMAIL]");
        result = CHILEAN_RUT.matcher(result).replaceAll("[ID]");
        result = PHONE.matcher(result).replaceAll("[PHONE]");
        result = URL.matcher(result).replaceAll("[URL]");
        return result;
    }

    private static String placeholder(String key) {
        if (key == null || key.isBlank()) return "[REDACTED]";
        String clean = key.toUpperCase()
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return clean.isBlank() ? "[REDACTED]" : "[" + clean + "]";
    }
}

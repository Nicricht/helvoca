package cl.helvoca.ai.deepgram;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Fixed, privacy-preserving Deepgram wire settings for Twilio PCMU. */
public final class DeepgramVoiceProtocol {
    private DeepgramVoiceProtocol() { }

    public static URI sttUri(DeepgramVoiceProperties properties) {
        return withQuery(properties.getSttEndpoint(),
                "model", properties.getSttModel(),
                "language", properties.getSttLanguage(),
                "language_hint", properties.getSttLanguageHint(),
                "encoding", "mulaw",
                "sample_rate", "8000",
                "interim_results", "true",
                "endpointing", "300",
                "utterance_end_ms", "1000",
                "vad_events", "true",
                "smart_format", "true",
                "punctuate", "true",
                "numerals", "true",
                "mip_opt_out", "true");
    }

    public static URI ttsUri(DeepgramVoiceProperties properties) {
        return withQuery(properties.getTtsEndpoint(),
                "model", properties.getTtsModel(),
                "encoding", "mulaw",
                "sample_rate", "8000",
                "container", "none",
                "mip_opt_out", "true");
    }

    private static URI withQuery(String base, String... pairs) {
        if (pairs.length % 2 != 0) throw new IllegalArgumentException("Query pairs must be complete");
        StringBuilder url = new StringBuilder(base);
        url.append(base.contains("?") ? '&' : '?');
        for (int i = 0; i < pairs.length; i += 2) {
            if (i > 0) url.append('&');
            url.append(encode(pairs[i])).append('=').append(encode(pairs[i + 1]));
        }
        return URI.create(url.toString());
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}

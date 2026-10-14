package cl.helvoca.ai.gemini;

/**
 * Offline allowlist for the API-key based Gemini Live adapter.
 * A model name being listed here does not prove that a particular Google
 * project is entitled to it, nor does it authorize a paid provider call.
 */
final class GeminiLiveModelPolicy {
    static final String DEFAULT_MODEL = "gemini-3.8-live";
    static final String LEGACY_MODEL = "gemini-3.1-flash-live-preview";
    static final String OFFICIAL_WEBSOCKET_URL =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent";

    private GeminiLiveModelPolicy() { }

    static String tier(String model) {
        if (DEFAULT_MODEL.equals(model)) return "STABLE";
        if (LEGACY_MODEL.equals(model)) return "LEGACY_PREVIEW";
        return "UNSUPPORTED";
    }

    static boolean supported(String model) {
        return !"UNSUPPORTED".equals(tier(model));
    }

    static boolean approvedEndpoint(String url) {
        return OFFICIAL_WEBSOCKET_URL.equals(url == null ? null : url.trim());
    }
}

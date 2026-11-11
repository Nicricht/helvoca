package cl.helvoca.voice;

import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.ai.realtime.RealtimeToolService;
import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;

/**
 * Answers only an exact service-price lookup from the authenticated tenant catalog.
 * Hours and address are deliberately delegated to Gemini until their official,
 * tenant-specific read APIs are verified. No mutations or speculative answers.
 */
public final class DeepgramEconomyPublicResponder {
    private static final Locale CHILE = Locale.forLanguageTag("es-CL");
    private final RealtimeToolService tools;

    public DeepgramEconomyPublicResponder(RealtimeToolService tools) {
        this.tools = tools;
    }

    public Optional<String> answer(RealtimeCallContext context, String utterance) {
        if (context == null || context.businessId() == null) return Optional.empty();
        VoiceEconomyIntentRouter.Decision decision = VoiceEconomyIntentRouter.classify(utterance);
        if (decision.path() != VoiceEconomyIntentRouter.Path.RULES_CANDIDATE
                || decision.reason() != VoiceEconomyIntentRouter.Reason.SERVICE_PRICE) {
            return Optional.empty();
        }
        String name = requestedService(utterance);
        if (name == null) return Optional.empty();
        try {
            JSONObject result = new JSONObject(tools.execute(context, "list_services", "{}"));
            if (!result.optBoolean("success")) return Optional.empty();
            JSONObject data = result.optJSONObject("data");
            JSONArray services = data == null ? null : data.optJSONArray("services");
            if (services == null) return Optional.empty();
            for (int i = 0; i < services.length(); i++) {
                JSONObject service = services.optJSONObject(i);
                if (service == null || !name.equals(normalize(service.optString("name", "")))
                        || service.isNull("price")) continue;
                BigDecimal price = new BigDecimal(service.get("price").toString());
                if (price.signum() <= 0 || price.scale() > 2) return Optional.empty();
                String formatted = NumberFormat.getIntegerInstance(CHILE).format(price);
                return Optional.of("Según el catálogo, " + service.getString("name")
                        + " cuesta " + formatted + " pesos.");
            }
        } catch (RuntimeException ignored) {
            // Unavailable or unauthorized official data is never replaced with invented prices.
        }
        return Optional.empty();
    }

    private static String requestedService(String utterance) {
        String value = normalize(utterance);
        if (value.endsWith(" por favor")) value = value.substring(0, value.length() - 10);
        for (String name : new String[]{"medicina general", "psicologia", "dermatologia", "nutricion"}) {
            if (value.endsWith(name)) return name;
        }
        return null;
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replaceAll("[¿?¡!.,;:]", " ")
                .replaceAll("\\s+", " ").trim();
    }
}

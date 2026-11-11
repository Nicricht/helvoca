package cl.helvoca.voice;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Fail-closed, offline eligibility assessment for a future lower-cost speech
 * executor. It NEVER switches a live call, generates speech, or reads tenant data.
 * No caller utterance is logged. A cheap route requires an independently ready
 * speech provider, verified per-minute estimates and a separate backend response
 * contract before it can ever be executed.
 */
public final class EconomicVoiceRouteAdvisor {
    private EconomicVoiceRouteAdvisor() { }

    public enum Intent { PUBLIC_HOURS, PUBLIC_LOCATION, COMPLEX_OR_PRIVATE }
    public enum Route { KEEP_GEMINI_LIVE, ECONOMY_CANDIDATE }
    public enum Reason {
        SHADOW_DISABLED, NOT_PUBLIC_SIMPLE, NO_READY_ECONOMY_EXECUTOR,
        UNVERIFIED_PRICES, NOT_CHEAPER, ELIGIBLE
    }

    public record Advice(Intent intent, Route route, Reason reason) { }

    private static final Pattern HOURS = Pattern.compile(
            "^(?:(?:hola|buenas)(?: tardes| dias| noches)?[ ,]*)?"
            + "(?:cual(?:es)? (?:es|son) (?:el |los )?horarios?(?: de atencion)?"
            + "|que horario(?:s)? tienen"
            + "|en que horario atienden"
            + "|a que hora (?:abren|cierran|atienden))"
            + "(?: (?:hoy|manana|el sabado|los sabados|el domingo|los domingos))?$");

    private static final Pattern LOCATION = Pattern.compile(
            "^(?:(?:hola|buenas)(?: tardes| dias| noches)?[ ,]*)?"
            + "(?:cual es (?:la |su )?direccion"
            + "|donde (?:quedan|estan ubicados|se encuentran|estan))$");

    /**
     * Full-utterance whitelist: ambiguous, medical, personal and booking intents
     * always stay on Gemini, even if they happen to contain "horario".
     */
    public static Intent classify(String utterance) {
        if (utterance == null || utterance.length() > 220) return Intent.COMPLEX_OR_PRIVATE;
        String text = Normalizer.normalize(utterance, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[¿?¡!.,;:]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (HOURS.matcher(text).matches()) return Intent.PUBLIC_HOURS;
        if (LOCATION.matcher(text).matches()) return Intent.PUBLIC_LOCATION;
        return Intent.COMPLEX_OR_PRIVATE;
    }

    /**
     * Advisory decision only. No provider handoff exists in this class.
     * Prices must be verified, strictly positive and in the same currency/unit.
     */
    public static Advice advise(String utterance, boolean shadowEnabled,
                                boolean economySpeechExecutorReady,
                                BigDecimal economyEstimatedUsdPerMinute,
                                BigDecimal liveEstimatedUsdPerMinute) {
        Intent intent = classify(utterance);
        if (!shadowEnabled) {
            return new Advice(intent, Route.KEEP_GEMINI_LIVE, Reason.SHADOW_DISABLED);
        }
        if (intent == Intent.COMPLEX_OR_PRIVATE) {
            return new Advice(intent, Route.KEEP_GEMINI_LIVE, Reason.NOT_PUBLIC_SIMPLE);
        }
        if (!economySpeechExecutorReady) {
            return new Advice(intent, Route.KEEP_GEMINI_LIVE, Reason.NO_READY_ECONOMY_EXECUTOR);
        }
        if (economyEstimatedUsdPerMinute == null || liveEstimatedUsdPerMinute == null
                || economyEstimatedUsdPerMinute.signum() <= 0
                || liveEstimatedUsdPerMinute.signum() <= 0) {
            return new Advice(intent, Route.KEEP_GEMINI_LIVE, Reason.UNVERIFIED_PRICES);
        }
        if (economyEstimatedUsdPerMinute.compareTo(liveEstimatedUsdPerMinute) >= 0) {
            return new Advice(intent, Route.KEEP_GEMINI_LIVE, Reason.NOT_CHEAPER);
        }
        return new Advice(intent, Route.ECONOMY_CANDIDATE, Reason.ELIGIBLE);
    }
}

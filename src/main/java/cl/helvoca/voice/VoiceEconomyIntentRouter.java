package cl.helvoca.voice;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Conservative, zero-network intent adviser for a future low-cost speech path.
 * Classification is NOT authorization and NEVER produces a spoken answer.
 * Tenant-scoped official data, STT/TTS quality and cost gates must be checked
 * before a candidate can be served outside Gemini Live.
 */
public final class VoiceEconomyIntentRouter {
    public enum Path { RULES_CANDIDATE, GEMINI_LIVE }
    public enum Reason { BUSINESS_HOURS, BUSINESS_ADDRESS, SERVICE_PRICE,
        MULTI_INTENT_OR_PERSONAL, AMBIGUOUS }

    public record Decision(Path path, Reason reason) {
        public boolean cheaperPathCandidate() {
            return path == Path.RULES_CANDIDATE;
        }
    }

    private static final Pattern HOURS = Pattern.compile(
            "^(cual es el horario( de atencion)?|cuales son los horarios( de atencion)?"
            + "|que horario(s)? tienen|a que hora (abren|cierran))( por favor)?$");
    private static final Pattern ADDRESS = Pattern.compile(
            "^(donde (estan|quedan|se ubican)|cual es (la )?direccion"
            + "|en que direccion (estan|quedan))( por favor)?$");
    // A price candidate still needs a real service-name match against the
    // authenticated tenant catalog before a future rules engine may answer.
    private static final Pattern PRICE = Pattern.compile(
            "^(cuanto (cuesta|vale|sale)|cual es (el )?precio de)"
            + " (la |el |una |un )?(consulta|servicio|atencion)?( de | para )?"
            + "(medicina general|psicologia|dermatologia|nutricion)( por favor)?$");
    private static final Pattern SENSITIVE_OR_ACTION = Pattern.compile(
            "\\b(mi|mis|mio|mia|hijo|hija|mama|papa|rut|dni|nombre|apellido|"
            + "paciente|reserva|reservar|agendar|cancelar|anular|reprogramar|"
            + "disponibilidad|disponible|hora para|turno|cita|manana|hoy|"
            + "proximo|proxima|lunes|martes|miercoles|jueves|viernes|sabado|domingo|"
            + "urgente|urgencia|emergencia|dolor|sangrado|suicidio|"
            + "humano|persona|recepcionista|operador|transferir|"
            + "whatsapp|pago|tarjeta|telefono|correo|email)\\b");

    private VoiceEconomyIntentRouter() { }

    /**
     * Fail closed for incomplete, multi-intent, health-related or contextual
     * speech. Matching the text only recommends a shadow route, not a switch.
     */
    public static Decision classify(String utterance) {
        if (utterance == null || utterance.isBlank() || utterance.length() > 160) {
            return new Decision(Path.GEMINI_LIVE, Reason.AMBIGUOUS);
        }
        String normalized = Normalizer.normalize(utterance.toLowerCase(Locale.ROOT),
                        Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("[¿?¡!.,;:]", " ")
                .trim().replaceAll("\\s+", " ");
        if (normalized.isBlank() || normalized.length() > 140) {
            return new Decision(Path.GEMINI_LIVE, Reason.AMBIGUOUS);
        }
        if (normalized.contains(" y ") || normalized.contains(" pero ")
                || normalized.contains(" tambien ")
                || SENSITIVE_OR_ACTION.matcher(normalized).find()) {
            return new Decision(Path.GEMINI_LIVE, Reason.MULTI_INTENT_OR_PERSONAL);
        }
        if (HOURS.matcher(normalized).matches()) {
            return new Decision(Path.RULES_CANDIDATE, Reason.BUSINESS_HOURS);
        }
        if (ADDRESS.matcher(normalized).matches()) {
            return new Decision(Path.RULES_CANDIDATE, Reason.BUSINESS_ADDRESS);
        }
        if (PRICE.matcher(normalized).matches()) {
            return new Decision(Path.RULES_CANDIDATE, Reason.SERVICE_PRICE);
        }
        return new Decision(Path.GEMINI_LIVE, Reason.AMBIGUOUS);
    }
}

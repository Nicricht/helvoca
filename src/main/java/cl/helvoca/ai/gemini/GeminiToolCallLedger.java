package cl.helvoca.ai.gemini;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Per-call idempotency ledger for Gemini Live function-call IDs.
 *
 * <p>A repeated provider delivery must receive the original function response
 * without executing a booking, cancellation, payment or other side effect a
 * second time. A reused ID with different arguments fails closed. The ledger
 * is capped so an upstream provider cannot grow per-call memory indefinitely;
 * after the cap, new calls are refused rather than evicting replay evidence.</p>
 */
final class GeminiToolCallLedger {
    static final int MAX_CALLS = 512;

    private final Map<String, Entry> completed = new HashMap<>();

    synchronized Resolution resolve(String id,
                                    String name,
                                    String canonicalArgs,
                                    Supplier<JSONObject> operation) {
        Entry previous = completed.get(id);
        if (previous != null) {
            if (!previous.name().equals(name) || !previous.canonicalArgs().equals(canonicalArgs)) {
                return new Resolution(failure("TOOL_CALL_ID_CONFLICT",
                        "La herramienta reutilizó un identificador con otros argumentos. La acción no se repetirá."), false);
            }
            return new Resolution(new JSONObject(previous.resultJson()), false);
        }

        if (completed.size() >= MAX_CALLS) {
            return new Resolution(failure("TOOL_CALL_LIMIT_REACHED",
                    "Esta llamada alcanzó el límite seguro de operaciones. No se ejecutarán acciones nuevas."), false);
        }

        JSONObject result = operation.get();
        String resultJson = result.toString();
        completed.put(id, new Entry(name, canonicalArgs, resultJson));
        return new Resolution(new JSONObject(resultJson), true);
    }

    private static JSONObject failure(String code, String message) {
        return new JSONObject()
                .put("success", false)
                .put("data", JSONObject.NULL)
                .put("error", new JSONObject().put("code", code).put("message", message));
    }

    record Resolution(JSONObject result, boolean executed) {}
    private record Entry(String name, String canonicalArgs, String resultJson) {}
}

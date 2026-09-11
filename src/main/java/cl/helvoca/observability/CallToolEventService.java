package cl.helvoca.observability;

import cl.helvoca.ai.realtime.RealtimeCallContext;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CallToolEventService {
    private final CallToolEventRepository repository;

    public CallToolEventService(CallToolEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(RealtimeCallContext context, String toolName, JSONObject result, long durationMs) {
        CallToolEvent event = new CallToolEvent();
        event.setBusinessId(context.businessId());
        event.setCallId(context.callId());
        event.setToolName(toolName == null ? "unknown" : truncate(toolName, 80));
        event.setSuccess(result != null && result.optBoolean("success", false));
        String code = result == null ? null : result.optString("code", null);
        event.setResultCode(code == null || code.isBlank() ? null : truncate(code, 80));
        event.setDurationMs(Math.max(0, durationMs));
        repository.save(event);
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}

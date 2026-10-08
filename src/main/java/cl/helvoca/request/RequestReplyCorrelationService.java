package cl.helvoca.request;

import org.json.JSONObject;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.UUID;

/**
 * Captures only server-produced create_request IDs and the exact persisted
 * WhatsApp reply message. This is a correlation ledger, NOT a resolution
 * decision or proof of provider delivery.
 */
@Service
public class RequestReplyCorrelationService {
    public record CreatedRequest(UUID requestId, UUID operationId) {}

    private final JdbcTemplate jdbc;

    public RequestReplyCorrelationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Parse the DIRECT result returned by the Java tool invocation, never a
     * model-written summary, browser parameter or subsequent chat message.
     */
    public static CreatedRequest verifiedToolResult(String toolName, String toolResult) {
        if (!"create_request".equals(toolName) || toolResult == null || toolResult.isBlank()) {
            return null;
        }
        try {
            JSONObject json = new JSONObject(toolResult);
            if (!json.optBoolean("success", false)) return null;
            JSONObject data = json.optJSONObject("data");
            if (data == null || !"OPEN".equals(data.optString("status"))) return null;
            UUID requestId = UUID.fromString(data.getString("requestId"));
            UUID operationId = UUID.fromString(data.getString("operationId"));
            return new CreatedRequest(requestId, operationId);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /**
     * Shares the inbound WhatsApp transaction: either request, message and
     * linkage commit together, or none of this turn's new data is committed.
     * Duplicate tool callbacks are de-duplicated without any provider call.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void capture(UUID businessId, UUID conversationId, UUID inboundMessageId,
                        Collection<CreatedRequest> createdRequests) {
        if (businessId == null || conversationId == null || inboundMessageId == null
                || createdRequests == null || createdRequests.isEmpty()) {
            return;
        }
        for (CreatedRequest request : new LinkedHashSet<>(createdRequests)) {
            if (request == null || request.requestId() == null || request.operationId() == null) continue;
            // The SELECT and the database trigger independently verify tenant,
            // immutable REQUEST_CREATED evidence and the exact inbound reply.
            // A missing/mismatched record cannot be silently promoted into proof.
            jdbc.update("""
                    INSERT INTO public.business_request_reply_correlation(
                        business_id, request_id, operation_id, conversation_id, inbound_message_id)
                    SELECT r.business_id, r.id, r.operation_id, c.id, m.id
                      FROM public.business_request r
                      JOIN public.business_operation op
                        ON op.id = r.operation_id AND op.business_id = r.business_id
                      JOIN public.messaging_conversation c
                        ON c.id = op.source_reference_id AND c.business_id = r.business_id
                      JOIN public.messaging_message m ON m.conversation_id = c.id
                     WHERE r.business_id = ?
                       AND r.id = ?
                       AND r.operation_id = ?
                       AND r.source = 'AI_WHATSAPP'
                       AND op.type = 'REQUEST'
                       AND op.source = 'WHATSAPP'
                       AND c.id = ?
                       AND lower(c.channel) = 'whatsapp'
                       AND m.id = ?
                       AND m.direction = 'INBOUND'
                       AND m.role = 'USER'
                       AND nullif(btrim(m.reply_text), '') IS NOT NULL
                       AND EXISTS (
                         SELECT 1 FROM public.business_operation_event ev
                          WHERE ev.business_id = r.business_id
                            AND ev.operation_id = r.operation_id
                            AND ev.source_reference_id = c.id
                            AND ev.operation_type = 'REQUEST'
                            AND ev.channel = 'WHATSAPP'
                            AND ev.event_type = 'REQUEST_CREATED'
                            AND ev.actor_type = 'AI'
                       )
                    ON CONFLICT (business_id, request_id, inbound_message_id) DO NOTHING
                    """, businessId, request.requestId(), request.operationId(), conversationId, inboundMessageId);
        }
    }
}

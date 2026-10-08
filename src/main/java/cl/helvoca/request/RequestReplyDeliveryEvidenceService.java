package cl.helvoca.request;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * An append-only observation of an authenticated Meta provider delivery event.
 * The webhook is authenticated and tenant-resolved BEFORE reaching this service.
 * The INSERT itself verifies persisted provider status and the exact V96 link.
 *
 * Never resolves a request, sends anything or makes an additional model call.
 */
@Service
public class RequestReplyDeliveryEvidenceService {
    private final JdbcTemplate jdbc;

    public RequestReplyDeliveryEvidenceService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordMetaReceipt(UUID businessId, UUID inboundMessageId,
                                  String providerMessageId, String receiptStatus) {
        if (businessId == null || inboundMessageId == null
                || providerMessageId == null || providerMessageId.isBlank()
                || (!"DELIVERED".equals(receiptStatus) && !"READ".equals(receiptStatus))) {
            return;
        }

        // The provider callback already updated messaging_message in this same
        // transaction. Any other status, missing correlation, stale provider ID,
        // forged tenant or unpersisted delivery produces zero inserted events.
        jdbc.update("""
                INSERT INTO public.business_request_reply_delivery_event(
                    business_id, correlation_id, inbound_message_id, provider,
                    provider_message_id, receipt_status, provider_recorded_at)
                SELECT corr.business_id, corr.id, m.id, 'META_WHATSAPP_CLOUD',
                       m.provider_message_id, ?, CASE
                         WHEN ? = 'READ' THEN m.read_at ELSE m.delivered_at END
                  FROM public.business_request_reply_correlation corr
                  JOIN public.messaging_conversation c
                    ON c.id = corr.conversation_id AND c.business_id = corr.business_id
                  JOIN public.messaging_message m
                    ON m.id = corr.inbound_message_id AND m.conversation_id = c.id
                 WHERE corr.business_id = ?
                   AND corr.inbound_message_id = ?
                   AND m.direction = 'INBOUND'
                   AND m.role = 'USER'
                   AND m.provider = 'META_WHATSAPP_CLOUD'
                   AND m.provider_message_id = ?
                   AND m.provider_delivery_status = ?
                   AND m.delivered_at IS NOT NULL
                   AND (? <> 'READ' OR m.read_at IS NOT NULL)
                ON CONFLICT (business_id, correlation_id, receipt_status) DO NOTHING
                """, receiptStatus, receiptStatus, businessId, inboundMessageId,
                providerMessageId, receiptStatus, receiptStatus);
    }
}

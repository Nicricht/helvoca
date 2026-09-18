package cl.helvoca.booking;

import cl.helvoca.call.CallDetailResponse;
import cl.helvoca.messaging.MessagingConversationQueryService;
import cl.helvoca.operations.BusinessOperationEventService;

import java.util.List;
import java.util.UUID;

public record BookingContextResponse(
        String channel,
        UUID sourceReferenceId,
        CallDetailResponse call,
        MessagingConversationQueryService.ConversationDetail whatsapp,
        List<BusinessOperationEventService.EventView> events
) {
}

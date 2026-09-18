package cl.helvoca.booking;

import cl.helvoca.customer.Customer;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.messaging.outbound.OutboundMessage;
import cl.helvoca.messaging.outbound.OutboundMessageRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BookingIncidentDeliveryStatusServiceTest {

    @Test
    void deliveredOutboundMessageBecomesHumanDeliveryState() {
        UUID businessId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        BookingIncidentRecipientRepository recipients = mock(BookingIncidentRecipientRepository.class);
        OutboundMessageRepository messages = mock(OutboundMessageRepository.class);
        CustomerRepository customers = mock(CustomerRepository.class);
        BookingIncidentRecipient recipient = recipient(recipientId, customerId, messageId);
        OutboundMessage message = mock(OutboundMessage.class);
        Customer customer = mock(Customer.class);
        Instant deliveredAt = Instant.parse("2026-09-18T20:00:00Z");

        when(recipients.findAllByCampaignIdAndBusinessIdOrderByCreatedAtAsc(campaignId, businessId))
                .thenReturn(List.of(recipient));
        when(messages.findByIdAndBusinessId(messageId, businessId)).thenReturn(Optional.of(message));
        when(customers.findByIdAndBusinessId(customerId, businessId)).thenReturn(Optional.of(customer));
        when(customer.getName()).thenReturn("Nicolás Vega");
        when(message.getChannel()).thenReturn(OutboundMessage.Channel.WHATSAPP);
        when(message.getProviderDeliveryStatus()).thenReturn("DELIVERED");
        when(message.getStatus()).thenReturn(OutboundMessage.Status.SENT);
        when(message.getDeliveredAt()).thenReturn(deliveredAt);

        var result = new BookingIncidentDeliveryStatusService(recipients, messages, customers)
                .forCampaign(businessId, campaignId);

        assertEquals(1, result.size());
        var item = result.getFirst();
        assertEquals("Nicolás Vega", item.customerName());
        assertEquals("WHATSAPP", item.channel());
        assertEquals("DELIVERED", item.status());
        assertEquals(deliveredAt, item.statusAt());
        assertFalse(item.retryable());
    }

    @Test
    void undeliveredOutboundMessageIsRetryableWithinLimit() {
        UUID businessId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        BookingIncidentRecipientRepository recipients = mock(BookingIncidentRecipientRepository.class);
        OutboundMessageRepository messages = mock(OutboundMessageRepository.class);
        CustomerRepository customers = mock(CustomerRepository.class);
        BookingIncidentRecipient recipient = recipient(UUID.randomUUID(), UUID.randomUUID(), messageId);
        OutboundMessage message = mock(OutboundMessage.class);

        when(recipients.findAllByCampaignIdAndBusinessIdOrderByCreatedAtAsc(campaignId, businessId))
                .thenReturn(List.of(recipient));
        when(messages.findByIdAndBusinessId(messageId, businessId)).thenReturn(Optional.of(message));
        when(customers.findByIdAndBusinessId(recipient.getCustomerId(), businessId)).thenReturn(Optional.empty());
        when(message.getChannel()).thenReturn(OutboundMessage.Channel.WHATSAPP);
        when(message.getProviderDeliveryStatus()).thenReturn("UNDELIVERED");
        when(message.getStatus()).thenReturn(OutboundMessage.Status.SENT);
        when(message.getRetryCount()).thenReturn(1);
        when(message.getDeliveryUpdatedAt()).thenReturn(Instant.parse("2026-09-18T20:05:00Z"));

        var item = new BookingIncidentDeliveryStatusService(recipients, messages, customers)
                .forCampaign(businessId, campaignId).getFirst();

        assertEquals("FAILED", item.status());
        assertTrue(item.retryable());
        assertEquals(1, item.retryCount());
    }

    private static BookingIncidentRecipient recipient(UUID id, UUID customerId, UUID messageId) {
        BookingIncidentRecipient recipient = mock(BookingIncidentRecipient.class);
        when(recipient.getId()).thenReturn(id);
        when(recipient.getCustomerId()).thenReturn(customerId);
        when(recipient.getOutboundMessageId()).thenReturn(messageId);
        when(recipient.getStatus()).thenReturn(BookingIncidentRecipient.Status.QUEUED);
        when(recipient.getChannelPreference()).thenReturn(BookingIncidentRecipient.ChannelPreference.WHATSAPP);
        when(recipient.getUpdatedAt()).thenReturn(Instant.parse("2026-09-18T19:00:00Z"));
        return recipient;
    }
}

package cl.helvoca.operations;

import cl.helvoca.messaging.outbound.MessagingProvider;
import cl.helvoca.messaging.outbound.MessagingProviderRegistry;
import cl.helvoca.messaging.outbound.OutboundDispatchOutboxService;
import cl.helvoca.messaging.outbound.OutboundMessage;
import cl.helvoca.messaging.outbound.OutboundMessagingProperties;
import cl.helvoca.messaging.outbound.OutboundMessagingService;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CrossChannelMessagingToolServiceTest {
    @Mock OutboundMessagingService outbound;
    @Mock MessagingProviderRegistry providers;
    @Mock OutboundDispatchOutboxService outbox;
    @Mock MessagingProvider provider;
    @Mock OutboundMessage message;

    private OutboundMessagingProperties properties;
    private CrossChannelMessagingToolService service;

    @BeforeEach
    void setUp() {
        properties = new OutboundMessagingProperties();
        service = new CrossChannelMessagingToolService(outbound, properties, providers, outbox);
    }

    @Test
    void preparesButNeverClaimsSentWhenRealDeliveryIsDisabled() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        when(message.getId()).thenReturn(messageId);
        when(message.getChannel()).thenReturn(OutboundMessage.Channel.WHATSAPP);
        when(message.getPurpose()).thenReturn(OutboundMessage.Purpose.PAYMENT_LINK);
        when(message.getStatus()).thenReturn(OutboundMessage.Status.PREPARED);
        when(outbound.prepare(eq(businessId), eq(customerId), eq(OutboundMessage.Channel.WHATSAPP),
                eq(OutboundMessage.Purpose.PAYMENT_LINK), eq(operationId), isNull())).thenReturn(message);

        JSONObject result = service.execute(businessId, customerId, new JSONObject()
                .put("operationId", operationId.toString())
                .put("purpose", "PAYMENT_LINK")
                .toString());

        assertFalse(result.getBoolean("success"));
        assertEquals("WHATSAPP_DELIVERY_DISABLED", result.getJSONObject("error").getString("code"));
        assertTrue(result.getJSONObject("data").getBoolean("prepared"));
        assertFalse(result.getJSONObject("data").getBoolean("sent"));
        verifyNoInteractions(providers, outbox);
    }

    @Test
    void queuesBackendPreparedMessageOnlyWhenProviderIsConfigured() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        properties.setDeliveryEnabled(true);
        properties.setProvider("TWILIO_WHATSAPP");
        when(message.getId()).thenReturn(messageId);
        when(message.getChannel()).thenReturn(OutboundMessage.Channel.WHATSAPP);
        when(message.getPurpose()).thenReturn(OutboundMessage.Purpose.PAYMENT_LINK);
        when(message.getStatus()).thenReturn(OutboundMessage.Status.PREPARED);
        when(outbound.prepare(eq(businessId), eq(customerId), eq(OutboundMessage.Channel.WHATSAPP),
                eq(OutboundMessage.Purpose.PAYMENT_LINK), eq(operationId), isNull())).thenReturn(message);
        when(providers.require("TWILIO_WHATSAPP", OutboundMessage.Channel.WHATSAPP)).thenReturn(provider);

        JSONObject result = service.execute(businessId, customerId, new JSONObject()
                .put("operationId", operationId.toString())
                .put("purpose", "PAYMENT_LINK")
                .toString());

        assertTrue(result.getBoolean("success"));
        assertEquals("QUEUED", result.getJSONObject("data").getString("status"));
        assertFalse(result.getJSONObject("data").getBoolean("sent"));
        verify(outbox).queue(businessId, messageId);
    }

    @Test
    void usesSmsChannelWhenTwilioSmsProviderIsConfigured() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        properties.setDeliveryEnabled(true);
        properties.setProvider("TWILIO_SMS");
        when(message.getId()).thenReturn(messageId);
        when(message.getChannel()).thenReturn(OutboundMessage.Channel.SMS);
        when(message.getPurpose()).thenReturn(OutboundMessage.Purpose.BOOKING_CONFIRMATION);
        when(message.getStatus()).thenReturn(OutboundMessage.Status.PREPARED);
        when(outbound.prepare(eq(businessId), eq(customerId), eq(OutboundMessage.Channel.SMS),
                eq(OutboundMessage.Purpose.BOOKING_CONFIRMATION), eq(operationId), isNull())).thenReturn(message);
        when(providers.require("TWILIO_SMS", OutboundMessage.Channel.SMS)).thenReturn(provider);

        JSONObject result = service.execute(businessId, customerId, new JSONObject()
                .put("operationId", operationId.toString())
                .put("purpose", "BOOKING_CONFIRMATION")
                .toString());

        assertTrue(result.getBoolean("success"));
        assertEquals("SMS", result.getJSONObject("data").getString("channel"));
        assertEquals("QUEUED", result.getJSONObject("data").getString("status"));
        verify(outbox).queue(businessId, messageId);
    }

    @Test
    void rejectsMissingVerifiedCustomerContextBeforePreparingAnything() {
        JSONObject result = service.execute(UUID.randomUUID(), null, new JSONObject()
                .put("operationId", UUID.randomUUID().toString())
                .put("purpose", "PAYMENT_LINK")
                .toString());

        assertFalse(result.getBoolean("success"));
        assertEquals("CUSTOMER_CONTEXT_REQUIRED", result.getJSONObject("error").getString("code"));
        verifyNoInteractions(outbound, providers, outbox);
    }
}

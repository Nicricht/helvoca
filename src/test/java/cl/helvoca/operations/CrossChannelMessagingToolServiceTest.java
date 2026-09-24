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

import java.util.List;
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
    @Mock CatalogShowcaseMessagingService showcase;

    private OutboundMessagingProperties properties;
    private CrossChannelMessagingToolService service;

    @BeforeEach
    void setUp() {
        properties = new OutboundMessagingProperties();
        service = new CrossChannelMessagingToolService(outbound, properties, providers, outbox, showcase);
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
    void productShowcaseQueuesAllPreparedMediaOnTheSameOperation() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        UUID productA = UUID.randomUUID();
        UUID productB = UUID.randomUUID();
        UUID mediaA = UUID.randomUUID();
        UUID mediaB = UUID.randomUUID();
        UUID messageAId = UUID.randomUUID();
        UUID messageBId = UUID.randomUUID();

        OutboundMessage messageA = mock(OutboundMessage.class);
        OutboundMessage messageB = mock(OutboundMessage.class);
        when(messageA.getId()).thenReturn(messageAId);
        when(messageA.getStatus()).thenReturn(OutboundMessage.Status.PREPARED);
        when(messageB.getId()).thenReturn(messageBId);
        when(messageB.getStatus()).thenReturn(OutboundMessage.Status.PREPARED);

        when(showcase.prepare(
                businessId,
                customerId,
                operationId,
                null,
                List.of(productA, productB)))
                .thenReturn(List.of(
                        new CatalogShowcaseMessagingService.PreparedShowcaseMessage(
                                messageA, productA, "Producto A", mediaA, cl.helvoca.catalog.CatalogMedia.Type.IMAGE),
                        new CatalogShowcaseMessagingService.PreparedShowcaseMessage(
                                messageB, productB, "Producto B", mediaB, cl.helvoca.catalog.CatalogMedia.Type.VIDEO)));

        properties.setDeliveryEnabled(true);
        properties.setProvider("META_WHATSAPP_CLOUD");
        when(providers.require("META_WHATSAPP_CLOUD", OutboundMessage.Channel.WHATSAPP)).thenReturn(provider);

        JSONObject result = service.execute(businessId, customerId, new JSONObject()
                .put("operationId", operationId.toString())
                .put("purpose", "PRODUCT_SHOWCASE")
                .put("catalogItemIds", new org.json.JSONArray()
                        .put(productA.toString())
                        .put(productB.toString()))
                .toString());

        assertTrue(result.getBoolean("success"));
        JSONObject data = result.getJSONObject("data");
        assertEquals(operationId.toString(), data.getString("operationId"));
        assertEquals("PRODUCT_SHOWCASE", data.getString("purpose"));
        assertEquals(2, data.getInt("messageCount"));
        assertEquals("QUEUED", data.getString("status"));
        assertTrue(data.getBoolean("queued"));
        assertFalse(data.getBoolean("sent"));
        verify(outbox).queue(businessId, messageAId);
        verify(outbox).queue(businessId, messageBId);
    }

    @Test
    void productShowcaseRejectsMoreThanThreeProductsBeforePreparation() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();

        JSONObject result = service.execute(businessId, customerId, new JSONObject()
                .put("operationId", operationId.toString())
                .put("purpose", "PRODUCT_SHOWCASE")
                .put("catalogItemIds", new org.json.JSONArray()
                        .put(UUID.randomUUID().toString())
                        .put(UUID.randomUUID().toString())
                        .put(UUID.randomUUID().toString())
                        .put(UUID.randomUUID().toString()))
                .toString());

        assertFalse(result.getBoolean("success"));
        assertEquals("INVALID_ARGUMENT", result.getJSONObject("error").getString("code"));
        verifyNoInteractions(showcase, providers, outbox);
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

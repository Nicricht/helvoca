package cl.helvoca.operations;

import cl.helvoca.catalog.CatalogItem;
import cl.helvoca.catalog.CatalogItemRepository;
import cl.helvoca.catalog.CatalogMedia;
import cl.helvoca.catalog.CatalogMediaRepository;
import cl.helvoca.delivery.DeliveryCoverageService;
import cl.helvoca.delivery.DeliveryWorkflowService;
import cl.helvoca.delivery.DeliveryZone;
import cl.helvoca.delivery.DeliveryZoneRepository;
import cl.helvoca.payment.PaymentWorkflowService;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommercialOperationToolServiceTest {
    @Mock CatalogItemRepository catalog;
    @Mock CatalogMediaRepository catalogMedia;
    @Mock DeliveryZoneRepository deliveryZones;
    @Mock BusinessOrderRepository orders;
    @Mock BusinessOrderLineRepository orderLines;
    @Mock BusinessOperationRepository operations;
    @Mock BusinessOperationCapabilityService capabilities;
    @Mock OrderWorkflowService orderWorkflow;
    @Mock DeliveryWorkflowService deliveryWorkflow;
    @Mock UniversalOperationWorkflowService universalOperations;
    @Mock PaymentWorkflowService paymentWorkflow;
    @Mock ConversationStateService conversationState;

    private DeliveryCoverageService deliveryCoverage;
    private CommercialOperationToolService service;

    @BeforeEach
    void setUp() {
        deliveryCoverage = new DeliveryCoverageService(deliveryZones);
        service = new CommercialOperationToolService(
                catalog, catalogMedia, deliveryZones, deliveryCoverage, orders, orderLines,
                operations, capabilities, orderWorkflow, deliveryWorkflow,
                universalOperations, paymentWorkflow, conversationState);
    }

    @Test
    void listCatalogIncludesBackendOwnedMediaIdsWithoutExposingUrls() {
        UUID businessId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();

        CatalogItem item = new CatalogItem();
        item.setId(itemId);
        item.setBusinessId(businessId);
        item.setKind(CatalogItem.Kind.PRODUCT);
        item.setName("Shampoo");
        item.setPrice(new BigDecimal("12990"));
        item.setCurrency("CLP");
        item.setActive(true);

        CatalogMedia media = new CatalogMedia();
        media.setId(mediaId);
        media.setBusinessId(businessId);
        media.setCatalogItemId(itemId);
        media.setMediaType(CatalogMedia.Type.IMAGE);
        media.setMediaUrl("https://cdn.example.test/shampoo.jpg");
        media.setMimeType("image/jpeg");
        media.setCaption("Shampoo hidratante");
        media.setActive(true);

        when(capabilities.isToolAllowed(businessId, "list_catalog")).thenReturn(true);
        when(catalog.findAllByBusinessIdAndActiveTrueOrderByNameAsc(businessId)).thenReturn(List.of(item));
        when(catalogMedia.findAllByBusinessIdAndCatalogItemIdAndActiveTrueOrderBySortOrderAscCreatedAtAsc(
                businessId, itemId)).thenReturn(List.of(media));

        JSONObject result = new JSONObject(service.execute(
                businessId, null, null, null, BusinessOrder.Source.VOICE, "list_catalog", "{}"));

        assertTrue(result.getBoolean("success"));
        JSONObject product = result.getJSONObject("data").getJSONArray("items").getJSONObject(0);
        assertTrue(product.getBoolean("hasMedia"));
        assertEquals(mediaId.toString(), product.getJSONArray("media").getJSONObject(0).getString("mediaId"));
        assertEquals("IMAGE", product.getJSONArray("media").getJSONObject(0).getString("type"));
        assertFalse(product.toString().contains("https://cdn.example.test"));
    }

    @Test
    void voiceAndWhatsAppAdapterUsesTheSameOrderWorkflowDomainAndRecordsStructuredState() {
        UUID businessId = UUID.randomUUID();
        UUID sourceReferenceId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        JSONObject domainResult = new JSONObject()
                .put("success", true)
                .put("data", new JSONObject()
                        .put("operationId", operationId.toString())
                        .put("revision", 1)
                        .put("status", "AWAITING_CONFIRMATION")
                        .put("confirmationToken", UUID.randomUUID().toString())
                        .put("total", 9900)
                        .put("currency", "CLP"))
                .put("error", JSONObject.NULL);
        when(capabilities.isToolAllowed(businessId, "quote_order")).thenReturn(true);
        when(orderWorkflow.quote(eq(businessId), isNull(), eq(sourceReferenceId),
                eq("+56911111111"), eq(BusinessOrder.Source.VOICE), any(JSONObject.class)))
                .thenReturn(domainResult);

        JSONObject result = new JSONObject(service.execute(
                businessId, null, sourceReferenceId, "+56911111111", BusinessOrder.Source.VOICE,
                "quote_order", new JSONObject()
                        .put("items", new org.json.JSONArray())
                        .put("fulfillmentType", "PICKUP")
                        .toString()));

        assertTrue(result.getBoolean("success"));
        verify(orderWorkflow).quote(eq(businessId), isNull(), eq(sourceReferenceId),
                eq("+56911111111"), eq(BusinessOrder.Source.VOICE), any(JSONObject.class));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> patch = ArgumentCaptor.forClass(Map.class);
        verify(conversationState).apply(eq(businessId), eq(sourceReferenceId),
                eq(BusinessOrder.Source.VOICE), eq(operationId), patch.capture());
        assertEquals("ORDER", patch.getValue().get("intent"));
        assertEquals("quote_order", patch.getValue().get("lastTool"));
        assertEquals(true, patch.getValue().get("confirmationPending"));
        assertEquals(9900, patch.getValue().get("total"));
    }

    @Test
    void standaloneDeliveryUsesTheSameSharedDomainFromAnyChannel() {
        UUID businessId = UUID.randomUUID();
        UUID sourceReferenceId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        JSONObject domainResult = new JSONObject()
                .put("success", true)
                .put("data", new JSONObject()
                        .put("operationId", operationId.toString())
                        .put("revision", 1)
                        .put("status", "AWAITING_CONFIRMATION")
                        .put("confirmationToken", UUID.randomUUID().toString())
                        .put("fee", 1500)
                        .put("currency", "CLP"))
                .put("error", JSONObject.NULL);
        when(capabilities.isToolAllowed(businessId, "quote_delivery")).thenReturn(true);
        when(deliveryWorkflow.quote(eq(businessId), isNull(), eq(sourceReferenceId),
                eq("+56911111111"), eq(BusinessOrder.Source.WHATSAPP), any(JSONObject.class)))
                .thenReturn(domainResult);

        JSONObject result = new JSONObject(service.execute(
                businessId, null, sourceReferenceId, "+56911111111", BusinessOrder.Source.WHATSAPP,
                "quote_delivery", new JSONObject().put("address", "Quilicura 123").toString()));

        assertTrue(result.getBoolean("success"));
        verify(deliveryWorkflow).quote(eq(businessId), isNull(), eq(sourceReferenceId),
                eq("+56911111111"), eq(BusinessOrder.Source.WHATSAPP), any(JSONObject.class));
        verifyNoInteractions(conversationState);
    }

    @Test
    void paymentUsesTheSameSharedDomainFromVoiceAndWhatsApp() {
        UUID businessId = UUID.randomUUID();
        UUID sourceReferenceId = UUID.randomUUID();
        UUID targetOperationId = UUID.randomUUID();
        JSONObject domainResult = new JSONObject()
                .put("success", true)
                .put("data", new JSONObject()
                        .put("operationId", UUID.randomUUID().toString())
                        .put("targetOperationId", targetOperationId.toString())
                        .put("amount", 24990)
                        .put("currency", "CLP"))
                .put("error", JSONObject.NULL);
        when(capabilities.isToolAllowed(businessId, "quote_payment")).thenReturn(true);
        when(paymentWorkflow.quote(eq(businessId), isNull(), eq(sourceReferenceId),
                eq("+56911111111"), eq(BusinessOrder.Source.WHATSAPP), any(JSONObject.class)))
                .thenReturn(domainResult);

        JSONObject result = new JSONObject(service.execute(
                businessId, null, sourceReferenceId, "+56911111111", BusinessOrder.Source.WHATSAPP,
                "quote_payment", new JSONObject()
                        .put("targetOperationId", targetOperationId.toString())
                        .toString()));

        assertTrue(result.getBoolean("success"));
        verify(paymentWorkflow).quote(eq(businessId), isNull(), eq(sourceReferenceId),
                eq("+56911111111"), eq(BusinessOrder.Source.WHATSAPP), any(JSONObject.class));
        verifyNoInteractions(conversationState);
    }

    @Test
    void updateOrderDeliveryAndPaymentAreFirstClassSupportedTools() {
        assertTrue(service.supports("update_order"));
        assertTrue(service.supports("quote_order"));
        assertTrue(service.supports("create_order"));
        assertTrue(service.supports("quote_delivery"));
        assertTrue(service.supports("update_delivery"));
        assertTrue(service.supports("create_delivery"));
        assertTrue(service.supports("get_delivery_status"));
        assertTrue(service.supports("cancel_delivery"));
        assertTrue(service.supports("quote_payment"));
        assertTrue(service.supports("update_payment"));
        assertTrue(service.supports("create_payment"));
        assertTrue(service.supports("get_payment_status"));
        assertTrue(service.supports("cancel_payment"));
    }

    @Test
    void createQuoteDelegatesToUniversalOperationEngine() {
        UUID businessId = UUID.randomUUID();
        UUID sourceReferenceId = UUID.randomUUID();
        JSONObject domainResult = new JSONObject()
                .put("success", true)
                .put("data", new JSONObject()
                        .put("operationId", UUID.randomUUID().toString())
                        .put("quoteId", UUID.randomUUID().toString()))
                .put("error", JSONObject.NULL);
        when(capabilities.isToolAllowed(businessId, "create_quote")).thenReturn(true);
        when(universalOperations.createQuote(eq(businessId), isNull(), eq(sourceReferenceId),
                eq("+56911111111"), eq(BusinessOrder.Source.WHATSAPP), any(JSONObject.class)))
                .thenReturn(domainResult);

        JSONObject result = new JSONObject(service.execute(
                businessId, null, sourceReferenceId, "+56911111111", BusinessOrder.Source.WHATSAPP,
                "create_quote", new JSONObject().put("title", "Cotizar instalación").toString()));

        assertTrue(result.getBoolean("success"));
        verify(universalOperations).createQuote(eq(businessId), isNull(), eq(sourceReferenceId),
                eq("+56911111111"), eq(BusinessOrder.Source.WHATSAPP), any(JSONObject.class));
    }

    @Test
    void orderTotalChangedStillRefreshesConversationState() {
        UUID businessId = UUID.randomUUID();
        UUID sourceReferenceId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        String newToken = UUID.randomUUID().toString();
        JSONObject domainResult = new JSONObject()
                .put("success", false)
                .put("data", new JSONObject()
                        .put("operationId", operationId.toString())
                        .put("revision", 4)
                        .put("confirmationToken", newToken)
                        .put("status", "AWAITING_CONFIRMATION")
                        .put("total", 14500)
                        .put("currency", "CLP"))
                .put("error", new JSONObject()
                        .put("code", "ORDER_TOTAL_CHANGED")
                        .put("message", "changed"));
        when(capabilities.isToolAllowed(businessId, "create_order")).thenReturn(true);
        when(orderWorkflow.confirm(eq(businessId), isNull(), eq(sourceReferenceId),
                eq("+56911111111"), eq(BusinessOrder.Source.VOICE), any(JSONObject.class)))
                .thenReturn(domainResult);

        JSONObject result = new JSONObject(service.execute(
                businessId, null, sourceReferenceId, "+56911111111", BusinessOrder.Source.VOICE,
                "create_order", new JSONObject()
                        .put("operationId", operationId.toString())
                        .put("confirmationToken", UUID.randomUUID().toString())
                        .toString()));

        assertFalse(result.getBoolean("success"));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> patch = ArgumentCaptor.forClass(Map.class);
        verify(conversationState).apply(eq(businessId), eq(sourceReferenceId),
                eq(BusinessOrder.Source.VOICE), eq(operationId), patch.capture());
        assertEquals(4, patch.getValue().get("operationRevision"));
        assertEquals(newToken, patch.getValue().get("confirmationToken"));
        assertEquals(true, patch.getValue().get("confirmationPending"));
    }

    @Test
    void commercialToolDisabledForTenantFailsClosedBeforeDomainExecution() {
        UUID businessId = UUID.randomUUID();
        when(capabilities.isToolAllowed(businessId, "quote_order")).thenReturn(false);

        JSONObject result = new JSONObject(service.execute(
                businessId, null, UUID.randomUUID(), "+56911111111", BusinessOrder.Source.WHATSAPP,
                "quote_order", new JSONObject().toString()));

        assertFalse(result.getBoolean("success"));
        assertEquals("TOOL_DISABLED", result.getJSONObject("error").getString("code"));
        verifyNoInteractions(orderWorkflow, deliveryWorkflow, universalOperations, paymentWorkflow, conversationState);
    }

    @Test
    void validateDeliveryAddressReturnsBackendResolvedZone() {
        UUID businessId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        DeliveryZone zone = new DeliveryZone();
        zone.setId(zoneId);
        zone.setBusinessId(businessId);
        zone.setName("Santiago Norte");
        zone.setCoverageTerms("Huechuraba; Quilicura");
        zone.setFee(new BigDecimal("1200"));
        zone.setMinimumOrder(new BigDecimal("6000"));
        zone.setActive(true);

        when(capabilities.isToolAllowed(businessId, "validate_delivery_address")).thenReturn(true);
        when(deliveryZones.findAllByBusinessIdAndActiveTrueOrderByNameAsc(businessId)).thenReturn(List.of(zone));

        JSONObject result = new JSONObject(service.execute(
                businessId, null, null, "+56911111111", BusinessOrder.Source.VOICE,
                "validate_delivery_address",
                new JSONObject().put("address", "Los Libertadores 6500, Quilicura").toString()));

        assertTrue(result.getBoolean("success"));
        JSONObject data = result.getJSONObject("data");
        assertTrue(data.getBoolean("covered"));
        assertEquals(zoneId.toString(), data.getString("deliveryZoneId"));
        assertEquals(new BigDecimal("1200"), new BigDecimal(String.valueOf(data.get("fee"))));
    }

    @Test
    void disabledDeliveryValidationFailsClosedBeforeCoverageLookup() {
        UUID businessId = UUID.randomUUID();
        when(capabilities.isToolAllowed(businessId, "validate_delivery_address")).thenReturn(false);

        JSONObject result = new JSONObject(service.execute(
                businessId, null, null, "+56911111111", BusinessOrder.Source.WHATSAPP,
                "validate_delivery_address",
                new JSONObject().put("address", "Providencia 123").toString()));

        assertFalse(result.getBoolean("success"));
        assertEquals("TOOL_DISABLED", result.getJSONObject("error").getString("code"));
        verifyNoInteractions(deliveryZones, deliveryWorkflow);
    }
}

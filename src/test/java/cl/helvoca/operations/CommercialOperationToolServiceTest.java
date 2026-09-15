package cl.helvoca.operations;

import cl.helvoca.catalog.CatalogItemRepository;
import cl.helvoca.delivery.DeliveryZone;
import cl.helvoca.delivery.DeliveryZoneRepository;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommercialOperationToolServiceTest {
    @Mock CatalogItemRepository catalog;
    @Mock DeliveryZoneRepository deliveryZones;
    @Mock BusinessOrderRepository orders;
    @Mock BusinessOrderLineRepository orderLines;
    @Mock BusinessQuoteRepository quotes;
    @Mock BusinessLeadRepository leads;
    @Mock BusinessOperationRepository operations;
    @Mock BusinessOperationCapabilityService capabilities;
    @Mock OrderWorkflowService orderWorkflow;

    private CommercialOperationToolService service;

    @BeforeEach
    void setUp() {
        service = new CommercialOperationToolService(
                catalog, deliveryZones, orders, orderLines, quotes, leads,
                operations, capabilities, orderWorkflow);
    }

    @Test
    void voiceAndWhatsAppAdapterUsesTheSameOrderWorkflowDomain() {
        UUID businessId = UUID.randomUUID();
        UUID sourceReferenceId = UUID.randomUUID();
        JSONObject domainResult = new JSONObject()
                .put("success", true)
                .put("data", new JSONObject().put("operationId", UUID.randomUUID().toString()))
                .put("error", JSONObject.NULL);
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
    }

    @Test
    void updateOrderIsAFirstClassSupportedTool() {
        assertTrue(service.supports("update_order"));
        assertTrue(service.supports("quote_order"));
        assertTrue(service.supports("create_order"));
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

        when(capabilities.isEnabled(businessId, BusinessOperationCapability.DELIVERY)).thenReturn(true);
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
    void disabledDeliveryFailsClosed() {
        UUID businessId = UUID.randomUUID();
        when(capabilities.isEnabled(businessId, BusinessOperationCapability.DELIVERY)).thenReturn(false);

        JSONObject result = new JSONObject(service.execute(
                businessId, null, null, "+56911111111", BusinessOrder.Source.WHATSAPP,
                "validate_delivery_address",
                new JSONObject().put("address", "Providencia 123").toString()));

        assertFalse(result.getBoolean("success"));
        assertEquals("DELIVERY_DISABLED", result.getJSONObject("error").getString("code"));
        verifyNoInteractions(deliveryZones);
    }
}

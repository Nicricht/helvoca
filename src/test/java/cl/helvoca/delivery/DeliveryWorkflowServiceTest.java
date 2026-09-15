package cl.helvoca.delivery;

import cl.helvoca.operations.BusinessOperation;
import cl.helvoca.operations.BusinessOperationRepository;
import cl.helvoca.operations.BusinessOrder;
import cl.helvoca.operations.BusinessOrderRepository;
import cl.helvoca.operations.ConversationStateService;
import cl.helvoca.operations.OperationPolicyService;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryWorkflowServiceTest {
    @Mock DeliveryCoverageService coverage;
    @Mock BusinessOperationRepository operations;
    @Mock BusinessDeliveryRepository deliveries;
    @Mock BusinessOrderRepository orders;
    @Mock ConversationStateService conversationState;

    private DeliveryWorkflowService service;

    @BeforeEach
    void setUp() {
        service = new DeliveryWorkflowService(
                coverage,
                operations,
                deliveries,
                orders,
                new OperationPolicyService(),
                conversationState);
    }

    @Test
    void quoteCreatesVersionedUniversalDraftWithoutMaterializingDelivery() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID sourceReferenceId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        DeliveryZone zone = zone(businessId, "Quilicura", "1500");

        when(coverage.resolve(businessId, "Los Libertadores 6500, Quilicura")).thenReturn(zone);
        when(operations.saveAndFlush(any(BusinessOperation.class))).thenAnswer(invocation -> {
            BusinessOperation operation = invocation.getArgument(0);
            operation.setId(operationId);
            return operation;
        });

        JSONObject result = service.quote(
                businessId,
                customerId,
                sourceReferenceId,
                "+56911111111",
                BusinessOrder.Source.VOICE,
                new JSONObject()
                        .put("address", "Los Libertadores 6500, Quilicura")
                        .put("contactName", "Nico"));

        assertTrue(result.getBoolean("success"));
        JSONObject data = result.getJSONObject("data");
        assertEquals(operationId.toString(), data.getString("operationId"));
        assertEquals(1, data.getInt("revision"));
        assertEquals("AWAITING_CONFIRMATION", data.getString("status"));
        assertEquals(new BigDecimal("1500"), decimal(data, "fee"));
        assertFalse(data.getString("confirmationToken").isBlank());
        assertTrue(data.getBoolean("confirmationRequired"));
        verify(deliveries, never()).saveAndFlush(any(BusinessDelivery.class));

        ArgumentCaptor<BusinessOperation> persisted = ArgumentCaptor.forClass(BusinessOperation.class);
        verify(operations).saveAndFlush(persisted.capture());
        assertEquals(BusinessOperation.Type.DELIVERY, persisted.getValue().getType());
        assertEquals(BusinessOrder.FulfillmentType.DELIVERY, persisted.getValue().getFulfillmentType());
        assertEquals(zone.getId(), persisted.getValue().getDeliveryZoneId());
        assertNull(persisted.getValue().getTotal());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> patch = ArgumentCaptor.forClass(Map.class);
        verify(conversationState).apply(
                eq(businessId),
                eq(sourceReferenceId),
                eq(BusinessOrder.Source.VOICE),
                eq(operationId),
                patch.capture());
        assertEquals("DELIVERY", patch.getValue().get("intent"));
        assertEquals(true, patch.getValue().get("confirmationPending"));
    }

    @Test
    void updateReplacesDeliveryStateAndInvalidatesOldConfirmation() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        UUID oldToken = UUID.randomUUID();
        DeliveryZone zone = zone(businessId, "Providencia", "2200");
        BusinessOperation operation = operation(
                operationId, businessId, customerId, oldToken, zone.getId(), "Dirección antigua", "1200");

        when(operations.findByIdAndBusinessId(operationId, businessId)).thenReturn(Optional.of(operation));
        when(coverage.resolve(businessId, "Nueva Providencia 1234")).thenReturn(zone);
        when(operations.saveAndFlush(any(BusinessOperation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JSONObject result = service.update(
                businessId,
                customerId,
                null,
                null,
                BusinessOrder.Source.WHATSAPP,
                new JSONObject()
                        .put("operationId", operationId.toString())
                        .put("address", "Nueva Providencia 1234"));

        assertTrue(result.getBoolean("success"));
        JSONObject data = result.getJSONObject("data");
        assertEquals(2, data.getInt("revision"));
        assertEquals(new BigDecimal("2200"), decimal(data, "fee"));
        assertNotEquals(oldToken.toString(), data.getString("confirmationToken"));
        assertEquals("Nueva Providencia 1234", operation.getDeliveryAddress());
        assertEquals(BusinessOperation.Status.AWAITING_CONFIRMATION, operation.getStatus());
    }

    @Test
    void staleConfirmationIsRejectedWithoutCreatingDelivery() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        UUID currentToken = UUID.randomUUID();
        DeliveryZone zone = zone(businessId, "Centro", "1000");
        BusinessOperation operation = operation(
                operationId, businessId, customerId, currentToken, zone.getId(), "Estado 100", "1000");

        when(deliveries.findByOperationIdAndBusinessId(operationId, businessId)).thenReturn(Optional.empty());
        when(operations.findByIdAndBusinessId(operationId, businessId)).thenReturn(Optional.of(operation));

        JSONObject result = service.confirm(
                businessId,
                customerId,
                null,
                null,
                BusinessOrder.Source.VOICE,
                new JSONObject()
                        .put("operationId", operationId.toString())
                        .put("confirmationToken", UUID.randomUUID().toString()));

        assertFalse(result.getBoolean("success"));
        assertEquals("STALE_DELIVERY_CONFIRMATION", result.getJSONObject("error").getString("code"));
        verify(deliveries, never()).saveAndFlush(any(BusinessDelivery.class));
        verifyNoInteractions(coverage);
    }

    @Test
    void confirmMaterializesProjectionOnceAndRetryIsIdempotent() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID sourceReferenceId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        UUID token = UUID.randomUUID();
        UUID deliveryId = UUID.randomUUID();
        DeliveryZone zone = zone(businessId, "Las Condes", "2500");
        BusinessOperation operation = operation(
                operationId, businessId, customerId, token, zone.getId(), "Apoquindo 3000", "2500");
        operation.setSourceReferenceId(sourceReferenceId);
        operation.setContactPhone("+56911111111");
        operation.setMetadata(Map.of("intent", "DELIVERY", "confirmationPending", true));

        AtomicReference<BusinessDelivery> stored = new AtomicReference<>();
        when(deliveries.findByOperationIdAndBusinessId(operationId, businessId))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(operations.findByIdAndBusinessId(operationId, businessId)).thenReturn(Optional.of(operation));
        when(coverage.resolve(businessId, "Apoquindo 3000")).thenReturn(zone);
        when(deliveries.saveAndFlush(any(BusinessDelivery.class))).thenAnswer(invocation -> {
            BusinessDelivery delivery = invocation.getArgument(0);
            delivery.setId(deliveryId);
            stored.set(delivery);
            return delivery;
        });
        when(operations.saveAndFlush(any(BusinessOperation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JSONObject args = new JSONObject()
                .put("operationId", operationId.toString())
                .put("confirmationToken", token.toString());

        JSONObject first = service.confirm(
                businessId, customerId, sourceReferenceId, "+56911111111",
                BusinessOrder.Source.VOICE, args);
        JSONObject second = service.confirm(
                businessId, customerId, sourceReferenceId, "+56911111111",
                BusinessOrder.Source.VOICE, args);

        assertTrue(first.getBoolean("success"));
        assertFalse(first.getJSONObject("data").getBoolean("idempotentReplay"));
        assertEquals(deliveryId.toString(), first.getJSONObject("data").getString("deliveryId"));
        assertTrue(second.getBoolean("success"));
        assertTrue(second.getJSONObject("data").getBoolean("idempotentReplay"));
        assertEquals(deliveryId.toString(), second.getJSONObject("data").getString("deliveryId"));
        verify(deliveries, times(1)).saveAndFlush(any(BusinessDelivery.class));
        assertEquals(BusinessOperation.Status.CONFIRMED, operation.getStatus());
        assertNull(operation.getConfirmationToken());
    }

    @Test
    void changedDeliveryFeeForcesASecondConfirmation() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        UUID token = UUID.randomUUID();
        DeliveryZone zone = zone(businessId, "Ñuñoa", "1800");
        BusinessOperation operation = operation(
                operationId, businessId, customerId, token, zone.getId(), "Irarrázaval 1000", "1200");
        operation.setMetadata(Map.of("intent", "DELIVERY", "confirmationPending", true));

        when(deliveries.findByOperationIdAndBusinessId(operationId, businessId)).thenReturn(Optional.empty());
        when(operations.findByIdAndBusinessId(operationId, businessId)).thenReturn(Optional.of(operation));
        when(coverage.resolve(businessId, "Irarrázaval 1000")).thenReturn(zone);
        when(operations.saveAndFlush(any(BusinessOperation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JSONObject result = service.confirm(
                businessId,
                customerId,
                null,
                null,
                BusinessOrder.Source.WHATSAPP,
                new JSONObject()
                        .put("operationId", operationId.toString())
                        .put("confirmationToken", token.toString()));

        assertFalse(result.getBoolean("success"));
        assertEquals("DELIVERY_TERMS_CHANGED", result.getJSONObject("error").getString("code"));
        assertEquals(2, result.getJSONObject("data").getInt("revision"));
        assertEquals(new BigDecimal("1800"), decimal(result.getJSONObject("data"), "fee"));
        assertNotEquals(token.toString(), result.getJSONObject("data").getString("confirmationToken"));
        verify(deliveries, never()).saveAndFlush(any(BusinessDelivery.class));
    }

    @Test
    void foreignTenantOrderCannotBeLinkedToDelivery() {
        UUID businessId = UUID.randomUUID();
        UUID foreignOrderId = UUID.randomUUID();
        DeliveryZone zone = zone(businessId, "Centro", "1000");
        when(coverage.resolve(businessId, "Huérfanos 100")).thenReturn(zone);
        when(orders.findByIdAndBusinessId(foreignOrderId, businessId)).thenReturn(Optional.empty());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.quote(
                businessId,
                null,
                UUID.randomUUID(),
                "+56911111111",
                BusinessOrder.Source.VOICE,
                new JSONObject()
                        .put("address", "Huérfanos 100")
                        .put("orderId", foreignOrderId.toString())));

        assertTrue(error.getMessage().contains("pedido vinculado"));
        verify(operations, never()).saveAndFlush(any(BusinessOperation.class));
    }

    private static DeliveryZone zone(UUID businessId, String name, String fee) {
        DeliveryZone zone = new DeliveryZone();
        zone.setId(UUID.randomUUID());
        zone.setBusinessId(businessId);
        zone.setName(name);
        zone.setCoverageTerms(name);
        zone.setFee(new BigDecimal(fee));
        zone.setActive(true);
        return zone;
    }

    private static BusinessOperation operation(UUID id,
                                               UUID businessId,
                                               UUID customerId,
                                               UUID token,
                                               UUID zoneId,
                                               String address,
                                               String fee) {
        BusinessOperation operation = new BusinessOperation();
        operation.setId(id);
        operation.setBusinessId(businessId);
        operation.setCustomerId(customerId);
        operation.setType(BusinessOperation.Type.DELIVERY);
        operation.setStatus(BusinessOperation.Status.AWAITING_CONFIRMATION);
        operation.setSource(BusinessOrder.Source.VOICE);
        operation.setRevision(1);
        operation.setConfirmationToken(token);
        operation.setFulfillmentType(BusinessOrder.FulfillmentType.DELIVERY);
        operation.setDeliveryZoneId(zoneId);
        operation.setDeliveryAddress(address);
        operation.setDeliveryFee(new BigDecimal(fee));
        operation.setCurrency("CLP");
        return operation;
    }

    private static BigDecimal decimal(JSONObject object, String key) {
        return new BigDecimal(String.valueOf(object.get(key)));
    }
}

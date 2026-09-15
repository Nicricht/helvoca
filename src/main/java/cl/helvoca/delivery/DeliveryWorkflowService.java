package cl.helvoca.delivery;

import cl.helvoca.operations.BusinessOperation;
import cl.helvoca.operations.BusinessOperationRepository;
import cl.helvoca.operations.BusinessOrder;
import cl.helvoca.operations.BusinessOrderRepository;
import cl.helvoca.operations.ConversationStateService;
import cl.helvoca.operations.OperationPolicyService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class DeliveryWorkflowService {
    private final DeliveryCoverageService coverage;
    private final BusinessOperationRepository operations;
    private final BusinessDeliveryRepository deliveries;
    private final BusinessOrderRepository orders;
    private final OperationPolicyService policies;
    private final ConversationStateService conversationState;

    public DeliveryWorkflowService(DeliveryCoverageService coverage,
                                   BusinessOperationRepository operations,
                                   BusinessDeliveryRepository deliveries,
                                   BusinessOrderRepository orders,
                                   OperationPolicyService policies,
                                   ConversationStateService conversationState) {
        this.coverage = coverage;
        this.operations = operations;
        this.deliveries = deliveries;
        this.orders = orders;
        this.policies = policies;
        this.conversationState = conversationState;
    }

    @Transactional
    public JSONObject quote(UUID businessId,
                            UUID customerId,
                            UUID sourceReferenceId,
                            String trustedPhone,
                            BusinessOrder.Source source,
                            JSONObject args) {
        Calculation calculation = calculate(
                businessId, customerId, sourceReferenceId, trustedPhone, args);
        BusinessOrder.Source safeSource = source == null ? BusinessOrder.Source.API : source;
        OperationPolicyService.Policy policy = policies.resolve(BusinessOperation.Type.DELIVERY);

        BusinessOperation operation = new BusinessOperation();
        operation.setBusinessId(businessId);
        operation.setCustomerId(customerId);
        operation.setSourceReferenceId(sourceReferenceId);
        operation.setType(BusinessOperation.Type.DELIVERY);
        operation.setSource(safeSource);
        operation.setRevision(1);
        operation.setContactName(optional(args, "contactName"));
        operation.setContactPhone(blank(trustedPhone) ? null : trustedPhone.trim());
        if (policy.requiresExplicitConfirmation()) {
            operation.setStatus(BusinessOperation.Status.AWAITING_CONFIRMATION);
            operation.setConfirmationToken(UUID.randomUUID());
        } else {
            operation.setStatus(BusinessOperation.Status.CONFIRMED);
            operation.setConfirmationToken(null);
        }
        applyCalculation(operation, calculation);
        operation.setMetadata(stateMetadata(calculation, policy.requiresExplicitConfirmation()));
        operation = operations.saveAndFlush(operation);

        recordConversation(
                businessId, sourceReferenceId, safeSource, operation, "quote_delivery", null);
        return success(quoteData(operation, calculation, policy.requiresExplicitConfirmation()));
    }

    @Transactional
    public JSONObject update(UUID businessId,
                             UUID customerId,
                             UUID sourceReferenceId,
                             String trustedPhone,
                             BusinessOrder.Source source,
                             JSONObject args) {
        UUID operationId = uuid(required(args, "operationId"));
        BusinessOperation operation = requireEditableOperation(
                businessId, operationId, customerId, sourceReferenceId, trustedPhone);

        Calculation calculation = calculate(
                businessId, customerId, sourceReferenceId, trustedPhone, args);
        applyCalculation(operation, calculation);
        String contactName = optional(args, "contactName");
        if (!blank(contactName)) operation.setContactName(contactName);
        if (operation.getCustomerId() == null && customerId != null) operation.setCustomerId(customerId);
        if (operation.getSourceReferenceId() == null && sourceReferenceId != null) {
            operation.setSourceReferenceId(sourceReferenceId);
        }
        if (blank(operation.getContactPhone()) && !blank(trustedPhone)) {
            operation.setContactPhone(trustedPhone.trim());
        }
        if (source != null) operation.setSource(source);
        operation.setRevision(operation.getRevision() == null ? 1 : operation.getRevision() + 1);
        operation.setConfirmationToken(UUID.randomUUID());
        operation.setStatus(BusinessOperation.Status.AWAITING_CONFIRMATION);
        operation.setMetadata(stateMetadata(calculation, true));
        operation = operations.saveAndFlush(operation);

        recordConversation(
                businessId,
                sourceReferenceId != null ? sourceReferenceId : operation.getSourceReferenceId(),
                source == null ? operation.getSource() : source,
                operation,
                "update_delivery",
                null);
        return success(quoteData(operation, calculation, true));
    }

    @Transactional
    public JSONObject confirm(UUID businessId,
                              UUID customerId,
                              UUID sourceReferenceId,
                              String trustedPhone,
                              BusinessOrder.Source source,
                              JSONObject args) {
        if (customerId == null && blank(trustedPhone) && sourceReferenceId == null) {
            return error("CUSTOMER_CONTEXT_REQUIRED", "No puedo verificar al cliente que solicita el despacho.");
        }

        UUID operationId = uuid(required(args, "operationId"));
        BusinessDelivery existing = deliveries
                .findByOperationIdAndBusinessId(operationId, businessId)
                .orElse(null);
        if (existing != null) {
            if (!deliveryOwnedBy(existing, customerId, sourceReferenceId, trustedPhone)) {
                return error("DELIVERY_NOT_FOUND", "No encuentro ese despacho entre los despachos del cliente actual.");
            }
            JSONObject data = deliveryData(existing);
            data.put("idempotentReplay", true);
            recordConversation(
                    businessId,
                    sourceReferenceId != null ? sourceReferenceId : existing.getSourceReferenceId(),
                    source == null ? existing.getSource() : source,
                    operationFor(existing, businessId),
                    "create_delivery",
                    existing);
            return success(data);
        }

        BusinessOperation operation = operations.findByIdAndBusinessId(operationId, businessId).orElse(null);
        if (operation == null || operation.getType() != BusinessOperation.Type.DELIVERY
                || !operationOwnedBy(operation, customerId, sourceReferenceId, trustedPhone)) {
            return error("DELIVERY_OPERATION_NOT_FOUND", "No encuentro ese borrador de despacho para el cliente actual.");
        }
        if (operation.getStatus() != BusinessOperation.Status.AWAITING_CONFIRMATION) {
            return error("DELIVERY_NOT_AWAITING_CONFIRMATION", "El despacho ya no está esperando confirmación.");
        }

        UUID providedToken = uuid(required(args, "confirmationToken"));
        if (operation.getConfirmationToken() == null || !operation.getConfirmationToken().equals(providedToken)) {
            return error("STALE_DELIVERY_CONFIRMATION",
                    "La confirmación ya no corresponde a la versión más reciente del despacho. Vuelve a presentar las condiciones actuales.");
        }

        Calculation recalculated = calculate(
                businessId,
                customerId,
                sourceReferenceId,
                trustedPhone,
                argsFromOperation(operation));

        if (deliveryTermsChanged(operation, recalculated)) {
            applyCalculation(operation, recalculated);
            operation.setRevision(operation.getRevision() == null ? 1 : operation.getRevision() + 1);
            operation.setConfirmationToken(UUID.randomUUID());
            operation.setStatus(BusinessOperation.Status.AWAITING_CONFIRMATION);
            operation.setMetadata(stateMetadata(recalculated, true));
            operation = operations.saveAndFlush(operation);
            recordConversation(
                    businessId,
                    sourceReferenceId != null ? sourceReferenceId : operation.getSourceReferenceId(),
                    source == null ? operation.getSource() : source,
                    operation,
                    "create_delivery",
                    null);
            return errorWithData(
                    "DELIVERY_TERMS_CHANGED",
                    "La zona o el costo del despacho cambió. Presenta las condiciones nuevas y solicita una nueva confirmación.",
                    quoteData(operation, recalculated, true));
        }

        BusinessDelivery delivery = new BusinessDelivery();
        delivery.setOperationId(operation.getId());
        delivery.setBusinessId(businessId);
        delivery.setCustomerId(customerId != null ? customerId : operation.getCustomerId());
        delivery.setSourceReferenceId(
                sourceReferenceId != null ? sourceReferenceId : operation.getSourceReferenceId());
        delivery.setOrderId(recalculated.linkedOrder() == null ? null : recalculated.linkedOrder().getId());
        delivery.setContactName(operation.getContactName());
        delivery.setContactPhone(!blank(trustedPhone) ? trustedPhone.trim() : operation.getContactPhone());
        delivery.setDeliveryZoneId(recalculated.zone().getId());
        delivery.setDeliveryAddress(recalculated.address());
        delivery.setStatus(BusinessDelivery.Status.CONFIRMED);
        delivery.setFee(recalculated.fee());
        delivery.setCurrency(recalculated.currency());
        delivery.setSource(source == null ? operation.getSource() : source);
        delivery.setNotes(recalculated.notes());
        delivery = deliveries.saveAndFlush(delivery);

        operation.setStatus(BusinessOperation.Status.CONFIRMED);
        operation.setConfirmationToken(null);
        operation.setMetadata(stateMetadata(recalculated, false));
        operation = operations.saveAndFlush(operation);

        recordConversation(
                businessId,
                sourceReferenceId != null ? sourceReferenceId : operation.getSourceReferenceId(),
                source == null ? operation.getSource() : source,
                operation,
                "create_delivery",
                delivery);

        JSONObject data = deliveryData(delivery);
        data.put("operationId", operationId.toString());
        data.put("operationRevision", operation.getRevision());
        data.put("idempotentReplay", false);
        return success(data);
    }

    @Transactional(readOnly = true)
    public JSONObject status(UUID businessId,
                             UUID customerId,
                             UUID sourceReferenceId,
                             String trustedPhone,
                             JSONObject args) {
        String deliveryIdRaw = optional(args, "deliveryId");
        if (!blank(deliveryIdRaw)) {
            UUID deliveryId = uuid(deliveryIdRaw);
            BusinessDelivery delivery = deliveries.findByIdAndBusinessId(deliveryId, businessId).orElse(null);
            if (delivery == null || !deliveryOwnedBy(delivery, customerId, sourceReferenceId, trustedPhone)) {
                return error("DELIVERY_NOT_FOUND", "No encuentro ese despacho entre los despachos del cliente actual.");
            }
            return success(deliveryData(delivery));
        }

        List<BusinessDelivery> recent;
        if (customerId != null) {
            recent = deliveries.findTop5ByBusinessIdAndCustomerIdOrderByCreatedAtDesc(businessId, customerId);
        } else if (!blank(trustedPhone)) {
            recent = deliveries.findTop5ByBusinessIdAndContactPhoneOrderByCreatedAtDesc(
                    businessId, trustedPhone.trim());
        } else if (sourceReferenceId != null) {
            recent = deliveries.findTop5ByBusinessIdAndSourceReferenceIdOrderByCreatedAtDesc(
                    businessId, sourceReferenceId);
        } else {
            return error("CUSTOMER_CONTEXT_REQUIRED", "No puedo verificar qué despachos pertenecen al cliente actual.");
        }

        JSONArray out = new JSONArray();
        for (BusinessDelivery delivery : recent) out.put(deliveryData(delivery));
        return success(new JSONObject().put("deliveries", out));
    }

    @Transactional
    public JSONObject cancel(UUID businessId,
                             UUID customerId,
                             UUID sourceReferenceId,
                             String trustedPhone,
                             BusinessOrder.Source source,
                             JSONObject args) {
        UUID deliveryId = uuid(required(args, "deliveryId"));
        BusinessDelivery delivery = deliveries.findByIdAndBusinessId(deliveryId, businessId).orElse(null);
        if (delivery == null || !deliveryOwnedBy(delivery, customerId, sourceReferenceId, trustedPhone)) {
            return error("DELIVERY_NOT_FOUND", "No encuentro ese despacho entre los despachos del cliente actual.");
        }
        if (delivery.getStatus() == BusinessDelivery.Status.CANCELLED) {
            return success(deliveryData(delivery));
        }
        if (delivery.getStatus() != BusinessDelivery.Status.CONFIRMED) {
            return error("DELIVERY_CANNOT_BE_CANCELLED",
                    "Ese despacho ya avanzó y requiere revisión humana para cancelarlo.");
        }

        delivery.setStatus(BusinessDelivery.Status.CANCELLED);
        delivery = deliveries.saveAndFlush(delivery);

        BusinessOperation operation = operations
                .findByIdAndBusinessId(delivery.getOperationId(), businessId)
                .orElse(null);
        if (operation != null && operation.getType() == BusinessOperation.Type.DELIVERY) {
            operation.setStatus(BusinessOperation.Status.CANCELLED);
            operation.setConfirmationToken(null);
            operation.setRevision(operation.getRevision() == null ? 1 : operation.getRevision() + 1);
            Map<String, Object> metadata = new LinkedHashMap<>();
            if (operation.getMetadata() != null) metadata.putAll(operation.getMetadata());
            metadata.put("confirmationPending", false);
            metadata.put("projectionStatus", BusinessDelivery.Status.CANCELLED.name());
            operation.setMetadata(metadata);
            operation = operations.saveAndFlush(operation);
        }

        if (operation != null) {
            recordConversation(
                    businessId,
                    sourceReferenceId != null ? sourceReferenceId : delivery.getSourceReferenceId(),
                    source == null ? delivery.getSource() : source,
                    operation,
                    "cancel_delivery",
                    delivery);
        }
        return success(deliveryData(delivery));
    }

    private BusinessOperation requireEditableOperation(UUID businessId,
                                                       UUID operationId,
                                                       UUID customerId,
                                                       UUID sourceReferenceId,
                                                       String trustedPhone) {
        BusinessOperation operation = operations.findByIdAndBusinessId(operationId, businessId).orElse(null);
        if (operation == null || operation.getType() != BusinessOperation.Type.DELIVERY
                || !operationOwnedBy(operation, customerId, sourceReferenceId, trustedPhone)) {
            throw new IllegalArgumentException("No encuentro ese borrador de despacho para el cliente actual.");
        }
        if (operation.getStatus() != BusinessOperation.Status.AWAITING_CONFIRMATION
                && operation.getStatus() != BusinessOperation.Status.DRAFT) {
            throw new IllegalArgumentException("El despacho ya fue confirmado o cerrado y no se puede modificar como borrador.");
        }
        return operation;
    }

    private Calculation calculate(UUID businessId,
                                  UUID customerId,
                                  UUID sourceReferenceId,
                                  String trustedPhone,
                                  JSONObject args) {
        String address = required(args, "address").trim();
        DeliveryZone zone = coverage.resolve(businessId, address);

        String requestedZoneId = optional(args, "deliveryZoneId");
        if (!blank(requestedZoneId) && !zone.getId().equals(uuid(requestedZoneId))) {
            throw new IllegalArgumentException(
                    "La zona indicada no corresponde a la cobertura validada para esa dirección.");
        }

        BusinessOrder linkedOrder = null;
        String orderIdRaw = optional(args, "orderId");
        if (!blank(orderIdRaw)) {
            UUID orderId = uuid(orderIdRaw);
            linkedOrder = orders.findByIdAndBusinessId(orderId, businessId).orElse(null);
            if (linkedOrder == null || !orderOwnedBy(linkedOrder, customerId, sourceReferenceId, trustedPhone)) {
                throw new IllegalArgumentException("No encuentro el pedido vinculado entre los pedidos del cliente actual.");
            }
            if (linkedOrder.getStatus() == BusinessOrder.Status.CANCELLED
                    || linkedOrder.getStatus() == BusinessOrder.Status.DISPATCHED
                    || linkedOrder.getStatus() == BusinessOrder.Status.COMPLETED) {
                throw new IllegalArgumentException("El pedido vinculado ya no admite iniciar un nuevo despacho.");
            }
            if (zone.getMinimumOrder() != null
                    && linkedOrder.getSubtotal() != null
                    && linkedOrder.getSubtotal().compareTo(zone.getMinimumOrder()) < 0) {
                throw new IllegalArgumentException(
                        "El pedido vinculado no alcanza la compra mínima de la zona de despacho.");
            }
        }

        String currency = linkedOrder == null || blank(linkedOrder.getCurrency())
                ? "CLP"
                : linkedOrder.getCurrency();
        return new Calculation(
                zone,
                address,
                zone.getFee() == null ? BigDecimal.ZERO : zone.getFee(),
                currency,
                linkedOrder,
                optional(args, "notes"));
    }

    private static void applyCalculation(BusinessOperation operation, Calculation calculation) {
        operation.setFulfillmentType(BusinessOrder.FulfillmentType.DELIVERY);
        operation.setDeliveryZoneId(calculation.zone().getId());
        operation.setDeliveryAddress(calculation.address());
        operation.setSubtotal(null);
        operation.setDeliveryFee(calculation.fee());
        operation.setTotal(null);
        operation.setCurrency(calculation.currency());
    }

    private static Map<String, Object> stateMetadata(Calculation calculation, boolean confirmationPending) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("intent", "DELIVERY");
        metadata.put("confirmationPending", confirmationPending);
        metadata.put("paymentPending", false);
        metadata.put("deliveryZone", calculation.zone().getName());
        metadata.put("minimumOrderVerified",
                calculation.zone().getMinimumOrder() == null || calculation.linkedOrder() != null);
        if (calculation.linkedOrder() != null) {
            metadata.put("orderId", calculation.linkedOrder().getId().toString());
        }
        if (!blank(calculation.notes())) metadata.put("notes", calculation.notes());
        return metadata;
    }

    private static JSONObject argsFromOperation(BusinessOperation operation) {
        JSONObject args = new JSONObject()
                .put("address", operation.getDeliveryAddress())
                .put("deliveryZoneId", operation.getDeliveryZoneId().toString());
        if (!blank(operation.getContactName())) args.put("contactName", operation.getContactName());
        Map<String, Object> metadata = operation.getMetadata();
        if (metadata != null) {
            Object orderId = metadata.get("orderId");
            if (orderId != null) args.put("orderId", String.valueOf(orderId));
            Object notes = metadata.get("notes");
            if (notes != null) args.put("notes", String.valueOf(notes));
        }
        return args;
    }

    private static boolean deliveryTermsChanged(BusinessOperation operation, Calculation calculation) {
        return !Objects.equals(operation.getDeliveryZoneId(), calculation.zone().getId())
                || operation.getDeliveryFee() == null
                || operation.getDeliveryFee().compareTo(calculation.fee()) != 0
                || !Objects.equals(operation.getCurrency(), calculation.currency());
    }

    private void recordConversation(UUID businessId,
                                    UUID sourceReferenceId,
                                    BusinessOrder.Source source,
                                    BusinessOperation operation,
                                    String toolName,
                                    BusinessDelivery delivery) {
        if (sourceReferenceId == null || operation == null) return;
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("intent", "DELIVERY");
        patch.put("lastTool", toolName);
        patch.put("operationId", operation.getId().toString());
        patch.put("operationType", "DELIVERY");
        patch.put("operationStatus", operation.getStatus().name());
        patch.put("operationRevision", operation.getRevision());
        patch.put("deliveryAddress", operation.getDeliveryAddress());
        patch.put("deliveryZoneId",
                operation.getDeliveryZoneId() == null ? null : operation.getDeliveryZoneId().toString());
        patch.put("deliveryFee", operation.getDeliveryFee());
        patch.put("currency", operation.getCurrency());
        patch.put("confirmationPending", operation.getStatus() == BusinessOperation.Status.AWAITING_CONFIRMATION);
        patch.put("confirmationToken",
                operation.getConfirmationToken() == null ? null : operation.getConfirmationToken().toString());
        if (operation.getMetadata() != null && operation.getMetadata().get("orderId") != null) {
            patch.put("orderId", String.valueOf(operation.getMetadata().get("orderId")));
        } else {
            patch.put("orderId", null);
        }
        if (delivery != null) {
            patch.put("deliveryId", delivery.getId().toString());
            patch.put("deliveryStatus", delivery.getStatus().name());
        }
        conversationState.apply(
                businessId,
                sourceReferenceId,
                source == null ? BusinessOrder.Source.API : source,
                operation.getId(),
                patch);
    }

    private BusinessOperation operationFor(BusinessDelivery delivery, UUID businessId) {
        return operations.findByIdAndBusinessId(delivery.getOperationId(), businessId).orElse(null);
    }

    private static JSONObject quoteData(BusinessOperation operation,
                                        Calculation calculation,
                                        boolean confirmationRequired) {
        return new JSONObject()
                .put("operationId", operation.getId().toString())
                .put("revision", operation.getRevision())
                .put("confirmationToken", nullable(
                        operation.getConfirmationToken() == null ? null : operation.getConfirmationToken().toString()))
                .put("status", operation.getStatus().name())
                .put("address", calculation.address())
                .put("deliveryZoneId", calculation.zone().getId().toString())
                .put("deliveryZone", calculation.zone().getName())
                .put("fee", calculation.fee())
                .put("currency", calculation.currency())
                .put("minimumOrder", nullable(calculation.zone().getMinimumOrder()))
                .put("minimumOrderVerified",
                        calculation.zone().getMinimumOrder() == null || calculation.linkedOrder() != null)
                .put("orderId", nullable(
                        calculation.linkedOrder() == null ? null : calculation.linkedOrder().getId().toString()))
                .put("confirmationRequired", confirmationRequired);
    }

    private static JSONObject deliveryData(BusinessDelivery delivery) {
        return new JSONObject()
                .put("deliveryId", delivery.getId().toString())
                .put("operationId", delivery.getOperationId().toString())
                .put("status", delivery.getStatus().name())
                .put("address", delivery.getDeliveryAddress())
                .put("deliveryZoneId", delivery.getDeliveryZoneId().toString())
                .put("fee", delivery.getFee())
                .put("currency", delivery.getCurrency())
                .put("orderId", nullable(delivery.getOrderId() == null ? null : delivery.getOrderId().toString()))
                .put("notes", nullable(delivery.getNotes()));
    }

    private static boolean operationOwnedBy(BusinessOperation operation,
                                            UUID customerId,
                                            UUID sourceReferenceId,
                                            String trustedPhone) {
        if (operation.getCustomerId() != null) return operation.getCustomerId().equals(customerId);
        if (!blank(operation.getContactPhone())) {
            return !blank(trustedPhone) && operation.getContactPhone().equals(trustedPhone.trim());
        }
        return operation.getSourceReferenceId() != null
                && operation.getSourceReferenceId().equals(sourceReferenceId);
    }

    private static boolean deliveryOwnedBy(BusinessDelivery delivery,
                                           UUID customerId,
                                           UUID sourceReferenceId,
                                           String trustedPhone) {
        if (delivery.getCustomerId() != null) return delivery.getCustomerId().equals(customerId);
        if (!blank(delivery.getContactPhone())) {
            return !blank(trustedPhone) && delivery.getContactPhone().equals(trustedPhone.trim());
        }
        return delivery.getSourceReferenceId() != null
                && delivery.getSourceReferenceId().equals(sourceReferenceId);
    }

    private static boolean orderOwnedBy(BusinessOrder order,
                                        UUID customerId,
                                        UUID sourceReferenceId,
                                        String trustedPhone) {
        if (order.getCustomerId() != null) return order.getCustomerId().equals(customerId);
        if (!blank(order.getContactPhone())) {
            return !blank(trustedPhone) && order.getContactPhone().equals(trustedPhone.trim());
        }
        return order.getSourceReferenceId() != null
                && order.getSourceReferenceId().equals(sourceReferenceId);
    }

    private static String required(JSONObject args, String key) {
        if (args == null || !args.has(key) || args.opt(key) == JSONObject.NULL) {
            throw new IllegalArgumentException("Falta el dato requerido: " + key + ".");
        }
        String value = String.valueOf(args.get(key));
        if (value.isBlank()) throw new IllegalArgumentException("Falta el dato requerido: " + key + ".");
        return value;
    }

    private static String optional(JSONObject args, String key) {
        if (args == null || !args.has(key) || args.opt(key) == JSONObject.NULL) return null;
        String value = String.valueOf(args.get(key)).trim();
        return value.isBlank() ? null : value;
    }

    private static UUID uuid(String value) {
        try { return UUID.fromString(value.trim()); }
        catch (Exception e) { throw new IllegalArgumentException("Se recibió un identificador inválido."); }
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static Object nullable(Object value) { return value == null ? JSONObject.NULL : value; }

    private static JSONObject success(JSONObject data) {
        return new JSONObject().put("success", true).put("data", data).put("error", JSONObject.NULL);
    }

    private static JSONObject error(String code, String message) {
        return new JSONObject().put("success", false).put("data", JSONObject.NULL)
                .put("error", new JSONObject().put("code", code).put("message", message));
    }

    private static JSONObject errorWithData(String code, String message, JSONObject data) {
        return new JSONObject().put("success", false).put("data", data)
                .put("error", new JSONObject().put("code", code).put("message", message));
    }

    private record Calculation(DeliveryZone zone,
                               String address,
                               BigDecimal fee,
                               String currency,
                               BusinessOrder linkedOrder,
                               String notes) {}
}

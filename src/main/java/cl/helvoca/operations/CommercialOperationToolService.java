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
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class CommercialOperationToolService {
    public static final String SHOWCASE_SELECTION_TOOL = "select_showcase_product";

    private static final Set<String> SUPPORTED = Set.of(
            "list_catalog",
            SHOWCASE_SELECTION_TOOL,
            "list_delivery_zones",
            "validate_delivery_address",
            "quote_delivery",
            "update_delivery",
            "create_delivery",
            "get_delivery_status",
            "cancel_delivery",
            "quote_order",
            "update_order",
            "create_order",
            "get_order_status",
            "cancel_order",
            "create_quote",
            "create_lead",
            "quote_payment",
            "update_payment",
            "create_payment",
            "get_payment_status",
            "cancel_payment");

    private static final Set<String> ORDER_STATE_TOOLS = Set.of(
            "quote_order",
            "update_order",
            "create_order",
            "cancel_order");

    private final CatalogItemRepository catalog;
    private final CatalogMediaRepository catalogMedia;
    private final DeliveryZoneRepository deliveryZones;
    private final DeliveryCoverageService deliveryCoverage;
    private final BusinessOrderRepository orders;
    private final BusinessOrderLineRepository orderLines;
    private final BusinessOperationRepository operations;
    private final BusinessOperationCapabilityService capabilities;
    private final OrderWorkflowService orderWorkflow;
    private final DeliveryWorkflowService deliveryWorkflow;
    private final UniversalOperationWorkflowService universalOperations;
    private final PaymentWorkflowService paymentWorkflow;
    private final ConversationStateService conversationState;

    public CommercialOperationToolService(CatalogItemRepository catalog,
                                          CatalogMediaRepository catalogMedia,
                                          DeliveryZoneRepository deliveryZones,
                                          DeliveryCoverageService deliveryCoverage,
                                          BusinessOrderRepository orders,
                                          BusinessOrderLineRepository orderLines,
                                          BusinessOperationRepository operations,
                                          BusinessOperationCapabilityService capabilities,
                                          OrderWorkflowService orderWorkflow,
                                          DeliveryWorkflowService deliveryWorkflow,
                                          UniversalOperationWorkflowService universalOperations,
                                          PaymentWorkflowService paymentWorkflow,
                                          ConversationStateService conversationState) {
        this.catalog = catalog;
        this.catalogMedia = catalogMedia;
        this.deliveryZones = deliveryZones;
        this.deliveryCoverage = deliveryCoverage;
        this.orders = orders;
        this.orderLines = orderLines;
        this.operations = operations;
        this.capabilities = capabilities;
        this.orderWorkflow = orderWorkflow;
        this.deliveryWorkflow = deliveryWorkflow;
        this.universalOperations = universalOperations;
        this.paymentWorkflow = paymentWorkflow;
        this.conversationState = conversationState;
    }

    public boolean supports(String toolName) { return SUPPORTED.contains(toolName); }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public String execute(UUID businessId,
                          UUID customerId,
                          UUID sourceReferenceId,
                          String trustedPhone,
                          BusinessOrder.Source source,
                          String toolName,
                          String rawArguments) {
        JSONObject result;
        try {
            if (!SUPPORTED.contains(toolName)) {
                return error("UNKNOWN_COMMERCIAL_TOOL", "La operación comercial solicitada no existe.").toString();
            }
            if (!capabilities.isToolAllowed(businessId, toolName)) {
                return error("TOOL_DISABLED", "La operación comercial no está habilitada para este negocio.").toString();
            }

            JSONObject args = rawArguments == null || rawArguments.isBlank()
                    ? new JSONObject()
                    : new JSONObject(rawArguments);
            result = switch (toolName) {
                case "list_catalog" -> listCatalog(businessId);
                case SHOWCASE_SELECTION_TOOL -> selectShowcaseProduct(businessId, customerId, args);
                case "list_delivery_zones" -> listDeliveryZones(businessId);
                case "validate_delivery_address" -> validateDeliveryAddress(businessId, args);
                case "quote_delivery" -> deliveryWorkflow.quote(
                        businessId, customerId, sourceReferenceId, trustedPhone, source, args);
                case "update_delivery" -> deliveryWorkflow.update(
                        businessId, customerId, sourceReferenceId, trustedPhone, source, args);
                case "create_delivery" -> deliveryWorkflow.confirm(
                        businessId, customerId, sourceReferenceId, trustedPhone, source, args);
                case "get_delivery_status" -> deliveryWorkflow.status(
                        businessId, customerId, sourceReferenceId, trustedPhone, args);
                case "cancel_delivery" -> deliveryWorkflow.cancel(
                        businessId, customerId, sourceReferenceId, trustedPhone, source, args);
                case "quote_order" -> orderWorkflow.quote(
                        businessId, customerId, sourceReferenceId, trustedPhone, source, args);
                case "update_order" -> orderWorkflow.update(
                        businessId, customerId, sourceReferenceId, trustedPhone, args);
                case "create_order" -> orderWorkflow.confirm(
                        businessId, customerId, sourceReferenceId, trustedPhone, source, args);
                case "get_order_status" -> getOrderStatus(businessId, customerId, trustedPhone, args);
                case "cancel_order" -> cancelOrder(businessId, customerId, trustedPhone, args);
                case "create_quote" -> universalOperations.createQuote(
                        businessId, customerId, sourceReferenceId, trustedPhone, source, args);
                case "create_lead" -> universalOperations.createLead(
                        businessId, customerId, sourceReferenceId, trustedPhone, source, args);
                case "quote_payment" -> paymentWorkflow.quote(
                        businessId, customerId, sourceReferenceId, trustedPhone, source, args);
                case "update_payment" -> paymentWorkflow.update(
                        businessId, customerId, sourceReferenceId, trustedPhone, source, args);
                case "create_payment" -> paymentWorkflow.confirm(
                        businessId, customerId, sourceReferenceId, trustedPhone, source, args);
                case "get_payment_status" -> paymentWorkflow.status(
                        businessId, customerId, sourceReferenceId, trustedPhone, source, args);
                case "cancel_payment" -> paymentWorkflow.cancel(
                        businessId, customerId, sourceReferenceId, trustedPhone, source, args);
                default -> error("UNKNOWN_COMMERCIAL_TOOL", "La operación comercial solicitada no existe.");
            };

            recordConversationResult(
                    businessId,
                    sourceReferenceId,
                    source == null ? BusinessOrder.Source.API : source,
                    toolName,
                    result);
        } catch (IllegalArgumentException e) {
            result = error("INVALID_ARGUMENT", e.getMessage());
        } catch (Exception e) {
            result = error("COMMERCIAL_OPERATION_FAILED", "La operación comercial no pudo completarse.");
        }
        return result.toString();
    }

    private JSONObject listCatalog(UUID businessId) {
        JSONArray items = new JSONArray();
        for (CatalogItem item : catalog.findAllByBusinessIdAndActiveTrueOrderByNameAsc(businessId)) {
            items.put(catalogData(
                    item,
                    catalogMedia.findAllByBusinessIdAndCatalogItemIdAndActiveTrueOrderBySortOrderAscCreatedAtAsc(
                            businessId, item.getId())));
        }
        return success(new JSONObject().put("items", items));
    }

    private JSONObject selectShowcaseProduct(UUID businessId,
                                             UUID customerId,
                                             JSONObject args) {
        if (businessId == null) {
            return error("TENANT_CONTEXT_REQUIRED", "No pude verificar el negocio actual.");
        }
        if (customerId == null) {
            return error("CUSTOMER_CONTEXT_REQUIRED",
                    "Primero necesito identificar al cliente de forma segura.");
        }

        UUID operationId = uuid(required(args, "operationId"));
        BusinessOperation operation = operations.findByIdAndBusinessId(operationId, businessId).orElse(null);
        if (operation == null) {
            return error("OPERATION_NOT_FOUND",
                    "No encuentro esa operación dentro del negocio actual.");
        }
        if (operation.getCustomerId() == null || !customerId.equals(operation.getCustomerId())) {
            return error("OPERATION_NOT_OWNED",
                    "La operación no pertenece al cliente actual.");
        }

        Map<String, Object> currentMetadata = operation.getMetadata();
        List<UUID> showcaseIds = showcaseIds(
                currentMetadata == null ? null : currentMetadata.get("showcaseCatalogItemIds"));
        if (showcaseIds.isEmpty()) {
            return error("SHOWCASE_NOT_FOUND",
                    "La operación no tiene un escaparate válido contra el cual resolver la selección.");
        }

        Integer selectionIndex = optionalInteger(args, "selectionIndex");
        String catalogItemIdRaw = optional(args, "catalogItemId");
        UUID selectedByIndex = null;
        UUID selectedById = null;

        if (selectionIndex != null) {
            if (selectionIndex < 1 || selectionIndex > showcaseIds.size()) {
                return error("SHOWCASE_SELECTION_OUT_OF_RANGE",
                        "La posición solicitada no existe en el último escaparate.");
            }
            selectedByIndex = showcaseIds.get(selectionIndex - 1);
        }

        if (!blank(catalogItemIdRaw)) {
            selectedById = uuid(catalogItemIdRaw);
            if (!showcaseIds.contains(selectedById)) {
                return error("CATALOG_ITEM_NOT_IN_SHOWCASE",
                        "Ese producto no formó parte del último escaparate de esta operación.");
            }
        }

        if (selectedByIndex == null && selectedById == null) {
            return error("SHOWCASE_SELECTION_REQUIRED",
                    "Debes indicar selectionIndex o catalogItemId.");
        }
        if (selectedByIndex != null && selectedById != null && !selectedByIndex.equals(selectedById)) {
            return error("SHOWCASE_SELECTION_MISMATCH",
                    "La posición y el producto indicado no corresponden al mismo elemento del escaparate.");
        }

        UUID selectedId = selectedByIndex != null ? selectedByIndex : selectedById;
        int resolvedIndex = showcaseIds.indexOf(selectedId) + 1;

        CatalogItem item = catalog.findByIdAndBusinessId(selectedId, businessId)
                .filter(CatalogItem::isActive)
                .orElse(null);
        if (item == null) {
            return error("CATALOG_ITEM_UNAVAILABLE",
                    "El producto mostrado ya no está disponible en el catálogo activo.");
        }

        Object existingSelection = currentMetadata == null
                ? null
                : currentMetadata.get("selectedCatalogItemId");
        if (existingSelection != null
                && selectedId.toString().equals(String.valueOf(existingSelection))) {
            String stage = currentMetadata.get("commercialStage") == null
                    ? "PRODUCT_SELECTED"
                    : String.valueOf(currentMetadata.get("commercialStage"));
            return success(showcaseSelectionData(
                    operation, item, resolvedIndex, stage, true));
        }

        Map<String, Object> metadata = currentMetadata == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(currentMetadata);
        metadata.put("selectedCatalogItemId", selectedId.toString());
        metadata.put("commercialStage", "PRODUCT_SELECTED");
        metadata.put("lastAction", "PRODUCT_SELECTED");
        operation.setMetadata(metadata);
        operations.saveAndFlush(operation);

        return success(showcaseSelectionData(
                operation, item, resolvedIndex, "PRODUCT_SELECTED", false));
    }

    private JSONObject showcaseSelectionData(BusinessOperation operation,
                                             CatalogItem item,
                                             int selectionIndex,
                                             String commercialStage,
                                             boolean idempotent) {
        List<CatalogMedia> media = catalogMedia
                .findAllByBusinessIdAndCatalogItemIdAndActiveTrueOrderBySortOrderAscCreatedAtAsc(
                        operation.getBusinessId(), item.getId());
        return new JSONObject()
                .put("operationId", operation.getId().toString())
                .put("selectionIndex", selectionIndex)
                .put("selectedCatalogItemId", item.getId().toString())
                .put("commercialStage", commercialStage)
                .put("idempotent", idempotent)
                .put("product", catalogData(item, media));
    }

    private static List<UUID> showcaseIds(Object raw) {
        if (!(raw instanceof Collection<?> collection)
                || collection.isEmpty()
                || collection.size() > 3) {
            return List.of();
        }

        List<UUID> ids = new ArrayList<>();
        for (Object value : collection) {
            if (value == null) return List.of();
            UUID id;
            try {
                id = UUID.fromString(String.valueOf(value).trim());
            } catch (Exception e) {
                return List.of();
            }
            if (ids.contains(id)) return List.of();
            ids.add(id);
        }
        return List.copyOf(ids);
    }

    private static Integer optionalInteger(JSONObject args, String key) {
        if (args == null || !args.has(key) || args.opt(key) == JSONObject.NULL) return null;
        Object raw = args.get(key);
        try {
            if (raw instanceof Number number) {
                double value = number.doubleValue();
                int integer = number.intValue();
                if (value != integer) throw new NumberFormatException();
                return integer;
            }
            String value = String.valueOf(raw).trim();
            return value.isBlank() ? null : Integer.valueOf(value);
        } catch (Exception e) {
            throw new IllegalArgumentException(key + " debe ser un número entero.");
        }
    }

    private JSONObject listDeliveryZones(UUID businessId) {
        JSONArray zones = new JSONArray();
        for (DeliveryZone zone : deliveryZones.findAllByBusinessIdAndActiveTrueOrderByNameAsc(businessId)) {
            zones.put(new JSONObject()
                    .put("id", zone.getId().toString())
                    .put("name", zone.getName())
                    .put("fee", zone.getFee())
                    .put("minimumOrder", nullable(zone.getMinimumOrder())));
        }
        return success(new JSONObject().put("zones", zones).put("addressValidationRequired", true));
    }

    private JSONObject validateDeliveryAddress(UUID businessId, JSONObject args) {
        String address = required(args, "address").trim();
        DeliveryZone zone = deliveryCoverage.resolve(businessId, address);
        return success(new JSONObject()
                .put("covered", true)
                .put("address", address)
                .put("deliveryZoneId", zone.getId().toString())
                .put("deliveryZone", zone.getName())
                .put("fee", zone.getFee())
                .put("minimumOrder", nullable(zone.getMinimumOrder())));
    }

    private JSONObject getOrderStatus(UUID businessId,
                                      UUID customerId,
                                      String trustedPhone,
                                      JSONObject args) {
        String orderIdRaw = optional(args, "orderId");
        if (!blank(orderIdRaw)) {
            UUID orderId = uuid(orderIdRaw);
            BusinessOrder order = orders.findByIdAndBusinessId(orderId, businessId).orElse(null);
            if (order == null || !ownedBy(order, customerId, trustedPhone)) {
                return error("ORDER_NOT_FOUND", "No encuentro ese pedido entre los pedidos del cliente actual.");
            }
            return success(orderData(order, orderLines.findAllByOrderIdOrderByCreatedAtAsc(order.getId())));
        }

        List<BusinessOrder> recent;
        if (customerId != null) {
            recent = orders.findTop5ByBusinessIdAndCustomerIdOrderByCreatedAtDesc(businessId, customerId);
        } else if (!blank(trustedPhone)) {
            recent = orders.findTop5ByBusinessIdAndContactPhoneOrderByCreatedAtDesc(businessId, trustedPhone.trim());
        } else {
            return error("CUSTOMER_CONTEXT_REQUIRED", "No puedo verificar qué pedidos pertenecen al cliente actual.");
        }

        JSONArray out = new JSONArray();
        for (BusinessOrder order : recent) {
            out.put(orderData(order, orderLines.findAllByOrderIdOrderByCreatedAtAsc(order.getId())));
        }
        return success(new JSONObject().put("orders", out));
    }

    private JSONObject cancelOrder(UUID businessId,
                                   UUID customerId,
                                   String trustedPhone,
                                   JSONObject args) {
        UUID orderId = uuid(required(args, "orderId"));
        BusinessOrder order = orders.findByIdAndBusinessId(orderId, businessId).orElse(null);
        if (order == null || !ownedBy(order, customerId, trustedPhone)) {
            return error("ORDER_NOT_FOUND", "No encuentro ese pedido entre los pedidos del cliente actual.");
        }
        if (order.getStatus() == BusinessOrder.Status.CANCELLED) {
            return success(orderData(order, orderLines.findAllByOrderIdOrderByCreatedAtAsc(order.getId())));
        }
        if (order.getStatus() != BusinessOrder.Status.CONFIRMED) {
            return error("ORDER_CANNOT_BE_CANCELLED",
                    "Ese pedido ya avanzó en su preparación y requiere revisión humana para cancelarlo.");
        }
        order.setStatus(BusinessOrder.Status.CANCELLED);
        order = orders.saveAndFlush(order);
        operations.findByIdAndBusinessId(order.getOperationId(), businessId).ifPresent(operation -> {
            operation.setStatus(BusinessOperation.Status.CANCELLED);
            operation.setConfirmationToken(null);
            operation.setRevision(operation.getRevision() == null ? 1 : operation.getRevision() + 1);
            operations.saveAndFlush(operation);
        });
        return success(orderData(order, orderLines.findAllByOrderIdOrderByCreatedAtAsc(order.getId())));
    }

    private void recordConversationResult(UUID businessId,
                                          UUID sourceReferenceId,
                                          BusinessOrder.Source source,
                                          String toolName,
                                          JSONObject result) {
        if (sourceReferenceId == null || !ORDER_STATE_TOOLS.contains(toolName) || result == null) return;

        boolean success = result.optBoolean("success", false);
        JSONObject error = result.optJSONObject("error");
        String errorCode = error == null ? null : error.optString("code", null);
        if (!success && !"ORDER_TOTAL_CHANGED".equals(errorCode)) return;

        JSONObject data = result.optJSONObject("data");
        if (data == null) return;

        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("lastTool", toolName);
        patch.put("intent", "ORDER");

        copyJsonValue(data, patch, "revision", "operationRevision");
        copyJsonValue(data, patch, "status", "operationStatus");
        copyJsonValue(data, patch, "total", "total");
        copyJsonValue(data, patch, "currency", "currency");
        copyJsonValue(data, patch, "fulfillmentType", "fulfillmentType");
        copyJsonValue(data, patch, "address", "deliveryAddress");
        copyJsonValue(data, patch, "deliveryAddress", "deliveryAddress");
        copyJsonValue(data, patch, "deliveryZoneId", "deliveryZoneId");
        copyJsonValue(data, patch, "orderId", "orderId");
        copyJsonValue(data, patch, "confirmationToken", "confirmationToken");

        if (data.has("confirmationToken") && data.opt("confirmationToken") != JSONObject.NULL) {
            patch.put("confirmationPending", true);
        } else if ("create_order".equals(toolName) && success) {
            patch.put("confirmationPending", false);
            patch.put("operationStatus", "CONFIRMED");
            patch.put("confirmationToken", null);
        } else if ("cancel_order".equals(toolName) && success) {
            patch.put("confirmationPending", false);
            patch.put("operationStatus", "CANCELLED");
            patch.put("confirmationToken", null);
        }

        UUID operationId = null;
        String operationIdRaw = data.optString("operationId", null);
        if (!blank(operationIdRaw)) {
            operationId = uuid(operationIdRaw);
            patch.put("operationId", operationIdRaw);
            patch.put("operationType", "ORDER");
        }

        conversationState.apply(
                businessId,
                sourceReferenceId,
                source == null ? BusinessOrder.Source.API : source,
                operationId,
                patch);
    }

    private static void copyJsonValue(JSONObject source,
                                      Map<String, Object> target,
                                      String sourceKey,
                                      String targetKey) {
        if (!source.has(sourceKey)) return;
        Object value = source.opt(sourceKey);
        if (value == null || value == JSONObject.NULL) return;
        if (value instanceof JSONObject object) target.put(targetKey, object.toMap());
        else if (value instanceof JSONArray array) target.put(targetKey, array.toList());
        else target.put(targetKey, value);
    }

    private static JSONObject orderData(BusinessOrder order, List<BusinessOrderLine> lines) {
        JSONArray items = new JSONArray();
        for (BusinessOrderLine line : lines) {
            JSONObject item = new JSONObject()
                    .put("catalogItemId", line.getCatalogItemId().toString())
                    .put("name", line.getItemName())
                    .put("quantity", line.getQuantity())
                    .put("unitPrice", line.getUnitPrice())
                    .put("lineTotal", line.getLineTotal())
                    .put("notes", nullable(line.getNotes()));
            item.put("modifiers", line.getModifiers() == null ? JSONObject.NULL : new JSONObject(line.getModifiers()));
            items.put(item);
        }
        return new JSONObject()
                .put("orderId", order.getId().toString())
                .put("operationId", order.getOperationId().toString())
                .put("status", order.getStatus().name())
                .put("fulfillmentType", order.getFulfillmentType().name())
                .put("items", items)
                .put("subtotal", order.getSubtotal())
                .put("deliveryFee", order.getDeliveryFee())
                .put("total", order.getTotal())
                .put("currency", order.getCurrency())
                .put("deliveryAddress", nullable(order.getDeliveryAddress()));
    }

    private static JSONObject catalogData(CatalogItem item, List<CatalogMedia> media) {
        JSONArray mediaItems = new JSONArray();
        for (CatalogMedia value : media == null ? List.<CatalogMedia>of() : media) {
            mediaItems.put(new JSONObject()
                    .put("mediaId", value.getId().toString())
                    .put("type", value.getMediaType().name())
                    .put("mimeType", nullable(value.getMimeType()))
                    .put("caption", nullable(value.getCaption())));
        }
        return new JSONObject()
                .put("id", item.getId().toString())
                .put("kind", item.getKind().name())
                .put("name", item.getName())
                .put("description", nullable(item.getDescription()))
                .put("price", nullable(item.getPrice()))
                .put("currency", item.getCurrency())
                .put("durationMinutes", nullable(item.getDurationMinutes()))
                .put("media", mediaItems)
                .put("hasMedia", !mediaItems.isEmpty());
    }

    private static boolean ownedBy(BusinessOrder order, UUID customerId, String trustedPhone) {
        if (customerId != null && customerId.equals(order.getCustomerId())) return true;
        return !blank(trustedPhone)
                && order.getContactPhone() != null
                && trustedPhone.trim().equals(order.getContactPhone());
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
}

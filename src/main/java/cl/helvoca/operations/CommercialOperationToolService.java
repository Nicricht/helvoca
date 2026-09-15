package cl.helvoca.operations;

import cl.helvoca.catalog.CatalogItem;
import cl.helvoca.catalog.CatalogItemRepository;
import cl.helvoca.delivery.DeliveryZone;
import cl.helvoca.delivery.DeliveryZoneRepository;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class CommercialOperationToolService {
    private static final Set<String> SUPPORTED = Set.of(
            "list_catalog",
            "list_delivery_zones",
            "validate_delivery_address",
            "quote_order",
            "update_order",
            "create_order",
            "get_order_status",
            "cancel_order",
            "create_quote",
            "create_lead");

    private static final Set<String> ORDER_STATE_TOOLS = Set.of(
            "validate_delivery_address",
            "quote_order",
            "update_order",
            "create_order",
            "cancel_order");

    private final CatalogItemRepository catalog;
    private final DeliveryZoneRepository deliveryZones;
    private final BusinessOrderRepository orders;
    private final BusinessOrderLineRepository orderLines;
    private final BusinessOperationRepository operations;
    private final BusinessOperationCapabilityService capabilities;
    private final OrderWorkflowService orderWorkflow;
    private final UniversalOperationWorkflowService universalOperations;
    private final ConversationStateService conversationState;

    public CommercialOperationToolService(CatalogItemRepository catalog,
                                          DeliveryZoneRepository deliveryZones,
                                          BusinessOrderRepository orders,
                                          BusinessOrderLineRepository orderLines,
                                          BusinessOperationRepository operations,
                                          BusinessOperationCapabilityService capabilities,
                                          OrderWorkflowService orderWorkflow,
                                          UniversalOperationWorkflowService universalOperations,
                                          ConversationStateService conversationState) {
        this.catalog = catalog;
        this.deliveryZones = deliveryZones;
        this.orders = orders;
        this.orderLines = orderLines;
        this.operations = operations;
        this.capabilities = capabilities;
        this.orderWorkflow = orderWorkflow;
        this.universalOperations = universalOperations;
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
                case "list_delivery_zones" -> listDeliveryZones(businessId);
                case "validate_delivery_address" -> validateDeliveryAddress(businessId, args);
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
                default -> error("UNKNOWN_COMMERCIAL_TOOL", "La operación comercial solicitada no existe.");
            };

            recordConversationResult(businessId, sourceReferenceId,
                    source == null ? BusinessOrder.Source.API : source, toolName, result);
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
            items.put(catalogData(item));
        }
        return success(new JSONObject().put("items", items));
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
        if (!capabilities.isEnabled(businessId, BusinessOperationCapability.DELIVERY)) {
            return error("DELIVERY_DISABLED", "El despacho no está habilitado para este negocio.");
        }
        String address = required(args, "address").trim();
        DeliveryZone zone = resolveDeliveryZone(businessId, address);
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

    private DeliveryZone resolveDeliveryZone(UUID businessId, String address) {
        String normalizedAddress = normalizeCoverage(address);
        if (normalizedAddress.isBlank()) throw new IllegalArgumentException("La dirección de despacho es inválida.");
        List<ZoneMatch> matches = new ArrayList<>();
        for (DeliveryZone zone : deliveryZones.findAllByBusinessIdAndActiveTrueOrderByNameAsc(businessId)) {
            int score = coverageScore(zone, normalizedAddress);
            if (score > 0) matches.add(new ZoneMatch(zone, score));
        }
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("La dirección no coincide con ninguna zona de despacho configurada.");
        }
        matches.sort(Comparator.comparingInt(ZoneMatch::score).reversed());
        if (matches.size() > 1 && matches.get(0).score() == matches.get(1).score()) {
            throw new IllegalArgumentException("La dirección coincide con más de una zona de despacho; la cobertura debe revisarse antes de confirmar.");
        }
        return matches.get(0).zone();
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

    private static JSONObject catalogData(CatalogItem item) {
        return new JSONObject()
                .put("id", item.getId().toString())
                .put("kind", item.getKind().name())
                .put("name", item.getName())
                .put("description", nullable(item.getDescription()))
                .put("price", nullable(item.getPrice()))
                .put("currency", item.getCurrency())
                .put("durationMinutes", nullable(item.getDurationMinutes()));
    }

    private static boolean ownedBy(BusinessOrder order, UUID customerId, String trustedPhone) {
        if (customerId != null && customerId.equals(order.getCustomerId())) return true;
        return !blank(trustedPhone) && order.getContactPhone() != null && trustedPhone.trim().equals(order.getContactPhone());
    }

    private static int coverageScore(DeliveryZone zone, String normalizedAddress) {
        int best = 0;
        String terms = zone.getCoverageTerms();
        if (terms == null || terms.isBlank()) return 0;
        for (String raw : terms.split("[,;|\\n\\r]+")) {
            String term = normalizeCoverage(raw);
            if (term.length() >= 3 && normalizedAddress.contains(term)) best = Math.max(best, term.length());
        }
        return best;
    }

    private static String normalizeCoverage(String value) {
        if (value == null) return "";
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return decomposed.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
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

    private record ZoneMatch(DeliveryZone zone, int score) {}
}

package cl.helvoca.operations;

import cl.helvoca.catalog.CatalogItem;
import cl.helvoca.catalog.CatalogItemRepository;
import cl.helvoca.delivery.DeliveryZone;
import cl.helvoca.delivery.DeliveryZoneRepository;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class OrderWorkflowService {
    private final CatalogItemRepository catalog;
    private final DeliveryZoneRepository deliveryZones;
    private final BusinessOperationRepository operations;
    private final BusinessOperationItemRepository operationItems;
    private final BusinessOrderRepository orders;
    private final BusinessOrderLineRepository orderLines;
    private final BusinessOperationCapabilityService capabilities;

    public OrderWorkflowService(CatalogItemRepository catalog,
                                DeliveryZoneRepository deliveryZones,
                                BusinessOperationRepository operations,
                                BusinessOperationItemRepository operationItems,
                                BusinessOrderRepository orders,
                                BusinessOrderLineRepository orderLines,
                                BusinessOperationCapabilityService capabilities) {
        this.catalog = catalog;
        this.deliveryZones = deliveryZones;
        this.operations = operations;
        this.operationItems = operationItems;
        this.orders = orders;
        this.orderLines = orderLines;
        this.capabilities = capabilities;
    }

    public JSONObject quote(UUID businessId,
                            UUID customerId,
                            UUID sourceReferenceId,
                            String trustedPhone,
                            BusinessOrder.Source source,
                            JSONObject args) {
        Calculation calculation = calculate(businessId, args);
        BusinessOperation operation = new BusinessOperation();
        operation.setBusinessId(businessId);
        operation.setCustomerId(customerId);
        operation.setSourceReferenceId(sourceReferenceId);
        operation.setType(BusinessOperation.Type.ORDER);
        operation.setStatus(BusinessOperation.Status.AWAITING_CONFIRMATION);
        operation.setSource(source == null ? BusinessOrder.Source.API : source);
        operation.setRevision(1);
        operation.setConfirmationToken(UUID.randomUUID());
        operation.setContactName(optional(args, "contactName"));
        operation.setContactPhone(blank(trustedPhone) ? null : trustedPhone.trim());
        applyCalculation(operation, calculation);
        operation.setMetadata(stateMetadata(true));
        operation = operations.saveAndFlush(operation);
        replaceItems(operation.getId(), calculation.lines());
        return success(quoteData(operation, calculation));
    }

    public JSONObject update(UUID businessId,
                             UUID customerId,
                             UUID sourceReferenceId,
                             String trustedPhone,
                             JSONObject args) {
        UUID operationId = uuid(required(args, "operationId"));
        BusinessOperation operation = requireEditableOperation(
                businessId, operationId, customerId, sourceReferenceId, trustedPhone);

        Calculation calculation = calculate(businessId, args);
        applyCalculation(operation, calculation);
        String contactName = optional(args, "contactName");
        if (!blank(contactName)) operation.setContactName(contactName);
        if (operation.getCustomerId() == null && customerId != null) operation.setCustomerId(customerId);
        if (operation.getSourceReferenceId() == null && sourceReferenceId != null) operation.setSourceReferenceId(sourceReferenceId);
        if (blank(operation.getContactPhone()) && !blank(trustedPhone)) operation.setContactPhone(trustedPhone.trim());
        operation.setRevision(operation.getRevision() == null ? 1 : operation.getRevision() + 1);
        operation.setConfirmationToken(UUID.randomUUID());
        operation.setStatus(BusinessOperation.Status.AWAITING_CONFIRMATION);
        operation.setMetadata(stateMetadata(true));
        operation = operations.saveAndFlush(operation);
        replaceItems(operation.getId(), calculation.lines());
        return success(quoteData(operation, calculation));
    }

    public JSONObject confirm(UUID businessId,
                              UUID customerId,
                              UUID sourceReferenceId,
                              String trustedPhone,
                              BusinessOrder.Source source,
                              JSONObject args) {
        if (customerId == null && blank(trustedPhone) && sourceReferenceId == null) {
            return error("CUSTOMER_CONTEXT_REQUIRED", "No puedo verificar al cliente que realiza el pedido.");
        }

        UUID operationId = uuid(required(args, "operationId"));
        BusinessOrder existing = orders.findByOperationIdAndBusinessId(operationId, businessId).orElse(null);
        if (existing != null) {
            if (!orderOwnedBy(existing, customerId, sourceReferenceId, trustedPhone)) {
                return error("ORDER_NOT_FOUND", "No encuentro ese pedido entre los pedidos del cliente actual.");
            }
            JSONObject data = orderData(existing, orderLines.findAllByOrderIdOrderByCreatedAtAsc(existing.getId()));
            data.put("idempotentReplay", true);
            return success(data);
        }

        BusinessOperation operation = operations.findByIdAndBusinessId(operationId, businessId).orElse(null);
        if (operation == null || operation.getType() != BusinessOperation.Type.ORDER
                || !operationOwnedBy(operation, customerId, sourceReferenceId, trustedPhone)) {
            return error("ORDER_OPERATION_NOT_FOUND", "No encuentro ese borrador de pedido para el cliente actual.");
        }
        if (operation.getStatus() != BusinessOperation.Status.AWAITING_CONFIRMATION) {
            return error("ORDER_NOT_AWAITING_CONFIRMATION", "El pedido ya no está esperando confirmación.");
        }

        UUID providedToken = uuid(required(args, "confirmationToken"));
        if (operation.getConfirmationToken() == null || !operation.getConfirmationToken().equals(providedToken)) {
            return error("STALE_ORDER_CONFIRMATION",
                    "La confirmación ya no corresponde a la versión más reciente del pedido. Vuelve a presentar el total actual.");
        }

        List<BusinessOperationItem> storedItems = operationItems.findAllByOperationIdOrderByCreatedAtAsc(operationId);
        JSONObject currentArgs = argsFromOperation(operation, storedItems);
        Calculation recalculated = calculate(businessId, currentArgs);

        if (operation.getTotal() == null
                || operation.getTotal().compareTo(recalculated.total()) != 0
                || !Objects.equals(operation.getCurrency(), recalculated.currency())) {
            applyCalculation(operation, recalculated);
            operation.setRevision(operation.getRevision() == null ? 1 : operation.getRevision() + 1);
            operation.setConfirmationToken(UUID.randomUUID());
            operation.setStatus(BusinessOperation.Status.AWAITING_CONFIRMATION);
            operation.setMetadata(stateMetadata(true));
            operation = operations.saveAndFlush(operation);
            replaceItems(operation.getId(), recalculated.lines());
            return errorWithData("ORDER_TOTAL_CHANGED",
                    "El precio o despacho cambió. Presenta el nuevo total y solicita una nueva confirmación.",
                    quoteData(operation, recalculated));
        }

        BusinessOrder order = new BusinessOrder();
        order.setOperationId(operation.getId());
        order.setBusinessId(businessId);
        order.setCustomerId(customerId != null ? customerId : operation.getCustomerId());
        order.setSourceReferenceId(sourceReferenceId != null ? sourceReferenceId : operation.getSourceReferenceId());
        order.setContactName(operation.getContactName());
        order.setContactPhone(!blank(trustedPhone) ? trustedPhone.trim() : operation.getContactPhone());
        order.setFulfillmentType(recalculated.fulfillmentType());
        order.setDeliveryZoneId(recalculated.deliveryZone() == null ? null : recalculated.deliveryZone().getId());
        order.setDeliveryAddress(recalculated.address());
        order.setStatus(BusinessOrder.Status.CONFIRMED);
        order.setSubtotal(recalculated.subtotal());
        order.setDeliveryFee(recalculated.deliveryFee());
        order.setTotal(recalculated.total());
        order.setCurrency(recalculated.currency());
        order.setSource(source == null ? operation.getSource() : source);
        order.setNotes(optional(args, "notes"));
        order = orders.saveAndFlush(order);

        List<BusinessOrderLine> persisted = new ArrayList<>();
        for (Line line : recalculated.lines()) {
            BusinessOrderLine entity = new BusinessOrderLine();
            entity.setOrderId(order.getId());
            entity.setCatalogItemId(line.item().getId());
            entity.setItemName(line.item().getName());
            entity.setQuantity(line.quantity());
            entity.setUnitPrice(line.item().getPrice());
            entity.setLineTotal(line.total());
            entity.setModifiers(line.modifiers());
            entity.setNotes(line.notes());
            persisted.add(orderLines.save(entity));
        }
        orderLines.flush();

        operation.setStatus(BusinessOperation.Status.CONFIRMED);
        operation.setConfirmationToken(null);
        operation.setMetadata(stateMetadata(false));
        operations.saveAndFlush(operation);

        JSONObject data = orderData(order, persisted);
        data.put("operationId", operationId.toString());
        data.put("idempotentReplay", false);
        return success(data);
    }

    private BusinessOperation requireEditableOperation(UUID businessId,
                                                       UUID operationId,
                                                       UUID customerId,
                                                       UUID sourceReferenceId,
                                                       String trustedPhone) {
        BusinessOperation operation = operations.findByIdAndBusinessId(operationId, businessId).orElse(null);
        if (operation == null || operation.getType() != BusinessOperation.Type.ORDER
                || !operationOwnedBy(operation, customerId, sourceReferenceId, trustedPhone)) {
            throw new IllegalArgumentException("No encuentro ese borrador de pedido para el cliente actual.");
        }
        if (operation.getStatus() != BusinessOperation.Status.AWAITING_CONFIRMATION
                && operation.getStatus() != BusinessOperation.Status.DRAFT) {
            throw new IllegalArgumentException("El pedido ya fue confirmado o cerrado y no se puede modificar como borrador.");
        }
        return operation;
    }

    private Calculation calculate(UUID businessId, JSONObject args) {
        JSONArray items = args.optJSONArray("items");
        if (items == null || items.isEmpty()) throw new IllegalArgumentException("El pedido debe incluir al menos un ítem.");

        List<Line> lines = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        String currency = null;
        for (int i = 0; i < items.length(); i++) {
            JSONObject requested = items.optJSONObject(i);
            if (requested == null) throw new IllegalArgumentException("Cada ítem debe ser un objeto válido.");
            UUID itemId = uuid(required(requested, "catalogItemId"));
            int quantity = requested.optInt("quantity", 0);
            if (quantity < 1 || quantity > 100) {
                throw new IllegalArgumentException("La cantidad de cada ítem debe estar entre 1 y 100.");
            }
            CatalogItem item = catalog.findByIdAndBusinessId(itemId, businessId)
                    .filter(CatalogItem::isActive)
                    .orElseThrow(() -> new IllegalArgumentException("Un ítem del catálogo no existe o no está activo."));
            if (item.getPrice() == null) {
                throw new IllegalArgumentException("Un ítem del pedido no tiene precio configurado y requiere cotización.");
            }
            if (currency == null) currency = item.getCurrency();
            if (!Objects.equals(currency, item.getCurrency())) {
                throw new IllegalArgumentException("No se pueden mezclar monedas distintas en una misma operación.");
            }
            Map<String, Object> modifiers = structuredModifiers(requested);
            BigDecimal lineTotal = item.getPrice().multiply(BigDecimal.valueOf(quantity));
            lines.add(new Line(item, quantity, modifiers, optional(requested, "notes"), lineTotal));
            subtotal = subtotal.add(lineTotal);
        }

        BusinessOrder.FulfillmentType fulfillment;
        try {
            fulfillment = BusinessOrder.FulfillmentType.valueOf(required(args, "fulfillmentType")
                    .trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new IllegalArgumentException("fulfillmentType debe ser PICKUP o DELIVERY.");
        }

        DeliveryZone zone = null;
        BigDecimal deliveryFee = BigDecimal.ZERO;
        String address = null;
        if (fulfillment == BusinessOrder.FulfillmentType.DELIVERY) {
            if (!capabilities.isEnabled(businessId, BusinessOperationCapability.DELIVERY)) {
                throw new IllegalArgumentException("El despacho no está habilitado para este negocio.");
            }
            address = required(args, "address").trim();
            if (address.isBlank()) throw new IllegalArgumentException("La dirección de despacho es obligatoria.");
            zone = resolveDeliveryZone(businessId, address);
            String requestedZoneId = optional(args, "deliveryZoneId");
            if (!blank(requestedZoneId) && !zone.getId().equals(uuid(requestedZoneId))) {
                throw new IllegalArgumentException("La zona indicada no corresponde a la cobertura validada para esa dirección.");
            }
            if (zone.getMinimumOrder() != null && subtotal.compareTo(zone.getMinimumOrder()) < 0) {
                throw new IllegalArgumentException("El subtotal no alcanza la compra mínima de la zona de despacho.");
            }
            deliveryFee = zone.getFee();
        }

        return new Calculation(lines, subtotal, deliveryFee, subtotal.add(deliveryFee),
                currency == null ? "CLP" : currency, fulfillment, zone, address);
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

    private void applyCalculation(BusinessOperation operation, Calculation calculation) {
        operation.setFulfillmentType(calculation.fulfillmentType());
        operation.setDeliveryZoneId(calculation.deliveryZone() == null ? null : calculation.deliveryZone().getId());
        operation.setDeliveryAddress(calculation.address());
        operation.setSubtotal(calculation.subtotal());
        operation.setDeliveryFee(calculation.deliveryFee());
        operation.setTotal(calculation.total());
        operation.setCurrency(calculation.currency());
    }

    private void replaceItems(UUID operationId, List<Line> lines) {
        operationItems.deleteAllByOperationId(operationId);
        List<BusinessOperationItem> replacements = new ArrayList<>();
        for (Line line : lines) {
            BusinessOperationItem item = new BusinessOperationItem();
            item.setOperationId(operationId);
            item.setCatalogItemId(line.item().getId());
            item.setItemName(line.item().getName());
            item.setQuantity(line.quantity());
            item.setUnitPrice(line.item().getPrice());
            item.setLineTotal(line.total());
            item.setModifiers(line.modifiers());
            item.setNotes(line.notes());
            replacements.add(item);
        }
        operationItems.saveAll(replacements);
        operationItems.flush();
    }

    private static JSONObject argsFromOperation(BusinessOperation operation, List<BusinessOperationItem> items) {
        JSONArray requested = new JSONArray();
        for (BusinessOperationItem item : items) {
            JSONObject line = new JSONObject()
                    .put("catalogItemId", item.getCatalogItemId().toString())
                    .put("quantity", item.getQuantity());
            if (!blank(item.getNotes())) line.put("notes", item.getNotes());
            if (item.getModifiers() != null && !item.getModifiers().isEmpty()) {
                line.put("modifiers", new JSONObject(item.getModifiers()));
            }
            requested.put(line);
        }
        JSONObject args = new JSONObject()
                .put("items", requested)
                .put("fulfillmentType", operation.getFulfillmentType().name());
        if (operation.getDeliveryZoneId() != null) args.put("deliveryZoneId", operation.getDeliveryZoneId().toString());
        if (!blank(operation.getDeliveryAddress())) args.put("address", operation.getDeliveryAddress());
        return args;
    }

    private static JSONObject quoteData(BusinessOperation operation, Calculation calculation) {
        JSONArray lines = new JSONArray();
        for (Line line : calculation.lines()) {
            JSONObject item = new JSONObject()
                    .put("catalogItemId", line.item().getId().toString())
                    .put("name", line.item().getName())
                    .put("quantity", line.quantity())
                    .put("unitPrice", line.item().getPrice())
                    .put("lineTotal", line.total())
                    .put("notes", nullable(line.notes()));
            item.put("modifiers", line.modifiers() == null ? JSONObject.NULL : new JSONObject(line.modifiers()));
            lines.put(item);
        }
        return new JSONObject()
                .put("operationId", operation.getId().toString())
                .put("revision", operation.getRevision())
                .put("confirmationToken", operation.getConfirmationToken().toString())
                .put("status", operation.getStatus().name())
                .put("items", lines)
                .put("fulfillmentType", calculation.fulfillmentType().name())
                .put("deliveryZoneId", calculation.deliveryZone() == null ? JSONObject.NULL : calculation.deliveryZone().getId().toString())
                .put("deliveryZone", calculation.deliveryZone() == null ? JSONObject.NULL : calculation.deliveryZone().getName())
                .put("address", nullable(calculation.address()))
                .put("subtotal", calculation.subtotal())
                .put("deliveryFee", calculation.deliveryFee())
                .put("total", calculation.total())
                .put("currency", calculation.currency());
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
                .put("status", order.getStatus().name())
                .put("fulfillmentType", order.getFulfillmentType().name())
                .put("items", items)
                .put("subtotal", order.getSubtotal())
                .put("deliveryFee", order.getDeliveryFee())
                .put("total", order.getTotal())
                .put("currency", order.getCurrency())
                .put("deliveryAddress", nullable(order.getDeliveryAddress()));
    }

    private static boolean operationOwnedBy(BusinessOperation operation,
                                            UUID customerId,
                                            UUID sourceReferenceId,
                                            String trustedPhone) {
        if (operation.getCustomerId() != null) return operation.getCustomerId().equals(customerId);
        if (!blank(operation.getContactPhone())) return !blank(trustedPhone) && operation.getContactPhone().equals(trustedPhone.trim());
        return operation.getSourceReferenceId() != null && operation.getSourceReferenceId().equals(sourceReferenceId);
    }

    private static boolean orderOwnedBy(BusinessOrder order,
                                        UUID customerId,
                                        UUID sourceReferenceId,
                                        String trustedPhone) {
        if (order.getCustomerId() != null) return order.getCustomerId().equals(customerId);
        if (!blank(order.getContactPhone())) return !blank(trustedPhone) && order.getContactPhone().equals(trustedPhone.trim());
        return order.getSourceReferenceId() != null && order.getSourceReferenceId().equals(sourceReferenceId);
    }

    private static Map<String, Object> structuredModifiers(JSONObject requested) {
        if (!requested.has("modifiers") || requested.opt("modifiers") == JSONObject.NULL) return null;
        Object value = requested.get("modifiers");
        if (value instanceof JSONObject object) return object.toMap();
        throw new IllegalArgumentException("modifiers debe ser un objeto JSON estructurado.");
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

    private static Map<String, Object> stateMetadata(boolean confirmationPending) {
        return Map.of(
                "intent", "ORDER",
                "confirmationPending", confirmationPending,
                "paymentPending", false);
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

    private record Line(CatalogItem item, int quantity, Map<String, Object> modifiers, String notes, BigDecimal total) {}
    private record Calculation(List<Line> lines,
                               BigDecimal subtotal,
                               BigDecimal deliveryFee,
                               BigDecimal total,
                               String currency,
                               BusinessOrder.FulfillmentType fulfillmentType,
                               DeliveryZone deliveryZone,
                               String address) {}
    private record ZoneMatch(DeliveryZone zone, int score) {}
}

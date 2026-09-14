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
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class CommercialOperationToolService {
    private static final Set<String> SUPPORTED = Set.of(
            "list_catalog",
            "list_delivery_zones",
            "validate_delivery_address",
            "quote_order",
            "create_order",
            "get_order_status",
            "cancel_order",
            "create_quote",
            "create_lead");

    private final CatalogItemRepository catalog;
    private final DeliveryZoneRepository deliveryZones;
    private final BusinessOrderRepository orders;
    private final BusinessOrderLineRepository orderLines;
    private final BusinessQuoteRepository quotes;
    private final BusinessLeadRepository leads;
    private final BusinessOperationCapabilityService capabilities;

    public CommercialOperationToolService(CatalogItemRepository catalog,
                                          DeliveryZoneRepository deliveryZones,
                                          BusinessOrderRepository orders,
                                          BusinessOrderLineRepository orderLines,
                                          BusinessQuoteRepository quotes,
                                          BusinessLeadRepository leads,
                                          BusinessOperationCapabilityService capabilities) {
        this.catalog = catalog;
        this.deliveryZones = deliveryZones;
        this.orders = orders;
        this.orderLines = orderLines;
        this.quotes = quotes;
        this.leads = leads;
        this.capabilities = capabilities;
    }

    public boolean supports(String toolName) {
        return SUPPORTED.contains(toolName);
    }

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
            JSONObject args = rawArguments == null || rawArguments.isBlank()
                    ? new JSONObject()
                    : new JSONObject(rawArguments);
            result = switch (toolName) {
                case "list_catalog" -> listCatalog(businessId);
                case "list_delivery_zones" -> listDeliveryZones(businessId);
                case "validate_delivery_address" -> validateDeliveryAddress(businessId, args);
                case "quote_order" -> quoteOrder(businessId, args);
                case "create_order" -> createOrder(
                        businessId, customerId, sourceReferenceId, trustedPhone, source, args);
                case "get_order_status" -> getOrderStatus(businessId, customerId, trustedPhone, args);
                case "cancel_order" -> cancelOrder(businessId, customerId, trustedPhone, args);
                case "create_quote" -> createQuote(
                        businessId, customerId, sourceReferenceId, trustedPhone, source, args);
                case "create_lead" -> createLead(
                        businessId, customerId, sourceReferenceId, trustedPhone, source, args);
                default -> error("UNKNOWN_COMMERCIAL_TOOL", "La operación comercial solicitada no existe.");
            };
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
        return success(new JSONObject()
                .put("zones", zones)
                .put("addressValidationRequired", true));
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

    private JSONObject quoteOrder(UUID businessId, JSONObject args) {
        OrderCalculation calculation = calculateOrder(businessId, args);
        return success(calculationData(calculation));
    }

    private JSONObject createOrder(UUID businessId,
                                   UUID customerId,
                                   UUID sourceReferenceId,
                                   String trustedPhone,
                                   BusinessOrder.Source source,
                                   JSONObject args) {
        if (customerId == null && blank(trustedPhone)) {
            return error("CUSTOMER_CONTEXT_REQUIRED", "No puedo verificar al cliente que realiza el pedido.");
        }

        OrderCalculation calculation = calculateOrder(businessId, args);
        BigDecimal expectedTotal = money(required(args, "expectedTotal"));
        if (calculation.total().compareTo(expectedTotal) != 0) {
            return error("ORDER_TOTAL_CHANGED",
                    "El total cambió. Vuelve a cotizar el pedido y confirma el nuevo total con el cliente.");
        }

        BusinessOrder order = new BusinessOrder();
        order.setBusinessId(businessId);
        order.setCustomerId(customerId);
        order.setSourceReferenceId(sourceReferenceId);
        order.setContactName(optional(args, "contactName"));
        order.setContactPhone(blank(trustedPhone) ? null : trustedPhone.trim());
        order.setFulfillmentType(calculation.fulfillmentType());
        order.setDeliveryZoneId(calculation.deliveryZone() == null ? null : calculation.deliveryZone().getId());
        order.setDeliveryAddress(calculation.address());
        order.setStatus(BusinessOrder.Status.CONFIRMED);
        order.setSubtotal(calculation.subtotal());
        order.setDeliveryFee(calculation.deliveryFee());
        order.setTotal(calculation.total());
        order.setCurrency(calculation.currency());
        order.setSource(source == null ? BusinessOrder.Source.API : source);
        order.setNotes(optional(args, "notes"));
        order = orders.saveAndFlush(order);

        List<BusinessOrderLine> persistedLines = new ArrayList<>();
        for (LineCalculation line : calculation.lines()) {
            BusinessOrderLine entity = new BusinessOrderLine();
            entity.setOrderId(order.getId());
            entity.setCatalogItemId(line.item().getId());
            entity.setItemName(line.item().getName());
            entity.setQuantity(line.quantity());
            entity.setUnitPrice(line.item().getPrice());
            entity.setLineTotal(line.total());
            entity.setNotes(line.notes());
            persistedLines.add(orderLines.save(entity));
        }
        orderLines.flush();
        return success(orderData(order, persistedLines));
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
        return success(orderData(order, orderLines.findAllByOrderIdOrderByCreatedAtAsc(order.getId())));
    }

    private JSONObject createQuote(UUID businessId,
                                   UUID customerId,
                                   UUID sourceReferenceId,
                                   String trustedPhone,
                                   BusinessOrder.Source source,
                                   JSONObject args) {
        String title = required(args, "title").trim();
        if (title.isBlank()) throw new IllegalArgumentException("La cotización necesita un título.");

        BigDecimal amount = null;
        String currency = "CLP";
        JSONArray items = args.optJSONArray("items");
        if (items != null && !items.isEmpty()) {
            ItemCalculation calculation = calculateItems(businessId, items);
            amount = calculation.subtotal();
            currency = calculation.currency();
        }

        BusinessQuote quote = new BusinessQuote();
        quote.setBusinessId(businessId);
        quote.setCustomerId(customerId);
        quote.setSourceReferenceId(sourceReferenceId);
        quote.setContactName(optional(args, "contactName"));
        quote.setContactPhone(blank(trustedPhone) ? null : trustedPhone.trim());
        quote.setTitle(title);
        quote.setDescription(optional(args, "description"));
        quote.setAmount(amount);
        quote.setCurrency(currency);
        quote.setStatus(amount == null ? BusinessQuote.Status.REQUESTED : BusinessQuote.Status.READY);
        quote.setSource(source == null ? BusinessOrder.Source.API : source);
        quote = quotes.saveAndFlush(quote);

        return success(new JSONObject()
                .put("quoteId", quote.getId().toString())
                .put("title", quote.getTitle())
                .put("status", quote.getStatus().name())
                .put("amount", nullable(quote.getAmount()))
                .put("currency", quote.getCurrency()));
    }

    private JSONObject createLead(UUID businessId,
                                  UUID customerId,
                                  UUID sourceReferenceId,
                                  String trustedPhone,
                                  BusinessOrder.Source source,
                                  JSONObject args) {
        String name = required(args, "name").trim();
        String interest = required(args, "interest").trim();
        if (name.isBlank() || interest.isBlank()) {
            throw new IllegalArgumentException("El lead necesita nombre e interés.");
        }

        BigDecimal budget = null;
        if (args.has("budget") && args.opt("budget") != JSONObject.NULL) {
            budget = money(args.get("budget"));
            if (budget.signum() < 0) throw new IllegalArgumentException("El presupuesto no puede ser negativo.");
        }

        BusinessLead lead = new BusinessLead();
        lead.setBusinessId(businessId);
        lead.setCustomerId(customerId);
        lead.setSourceReferenceId(sourceReferenceId);
        lead.setName(name);
        lead.setPhone(blank(trustedPhone) ? null : trustedPhone.trim());
        lead.setEmail(optional(args, "email"));
        lead.setInterest(interest);
        lead.setBudget(budget);
        lead.setNotes(optional(args, "notes"));
        lead.setStatus(BusinessLead.Status.NEW);
        lead.setSource(source == null ? BusinessOrder.Source.API : source);
        lead = leads.saveAndFlush(lead);

        return success(new JSONObject()
                .put("leadId", lead.getId().toString())
                .put("name", lead.getName())
                .put("interest", lead.getInterest())
                .put("status", lead.getStatus().name()));
    }

    private OrderCalculation calculateOrder(UUID businessId, JSONObject args) {
        JSONArray items = args.optJSONArray("items");
        if (items == null || items.isEmpty()) throw new IllegalArgumentException("El pedido debe incluir al menos un ítem.");
        ItemCalculation itemCalculation = calculateItems(businessId, items);

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
            if (zone.getMinimumOrder() != null
                    && itemCalculation.subtotal().compareTo(zone.getMinimumOrder()) < 0) {
                throw new IllegalArgumentException("El subtotal no alcanza la compra mínima de la zona de despacho.");
            }
            deliveryFee = zone.getFee();
        }

        return new OrderCalculation(
                itemCalculation.lines(),
                itemCalculation.subtotal(),
                deliveryFee,
                itemCalculation.subtotal().add(deliveryFee),
                itemCalculation.currency(),
                fulfillment,
                zone,
                address);
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

    private static int coverageScore(DeliveryZone zone, String normalizedAddress) {
        int best = 0;
        String terms = zone.getCoverageTerms();
        if (terms == null || terms.isBlank()) return 0;
        for (String raw : terms.split("[,;|\\n\\r]+")) {
            String term = normalizeCoverage(raw);
            if (term.length() >= 3 && normalizedAddress.contains(term)) {
                best = Math.max(best, term.length());
            }
        }
        return best;
    }

    private static String normalizeCoverage(String value) {
        if (value == null) return "";
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return decomposed.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private ItemCalculation calculateItems(UUID businessId, JSONArray items) {
        if (items == null || items.isEmpty()) throw new IllegalArgumentException("Se requiere al menos un ítem.");
        List<LineCalculation> lines = new ArrayList<>();
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
            BigDecimal lineTotal = item.getPrice().multiply(BigDecimal.valueOf(quantity));
            lines.add(new LineCalculation(item, quantity, optional(requested, "notes"), lineTotal));
            subtotal = subtotal.add(lineTotal);
        }
        return new ItemCalculation(lines, subtotal, currency == null ? "CLP" : currency);
    }

    private static JSONObject calculationData(OrderCalculation calculation) {
        JSONArray lines = new JSONArray();
        for (LineCalculation line : calculation.lines()) {
            lines.put(lineData(line));
        }
        return new JSONObject()
                .put("items", lines)
                .put("fulfillmentType", calculation.fulfillmentType().name())
                .put("deliveryZoneId", calculation.deliveryZone() == null
                        ? JSONObject.NULL : calculation.deliveryZone().getId().toString())
                .put("deliveryZone", calculation.deliveryZone() == null
                        ? JSONObject.NULL : calculation.deliveryZone().getName())
                .put("address", nullable(calculation.address()))
                .put("subtotal", calculation.subtotal())
                .put("deliveryFee", calculation.deliveryFee())
                .put("total", calculation.total())
                .put("currency", calculation.currency());
    }

    private static JSONObject orderData(BusinessOrder order, List<BusinessOrderLine> lines) {
        JSONArray items = new JSONArray();
        for (BusinessOrderLine line : lines) {
            items.put(new JSONObject()
                    .put("catalogItemId", line.getCatalogItemId().toString())
                    .put("name", line.getItemName())
                    .put("quantity", line.getQuantity())
                    .put("unitPrice", line.getUnitPrice())
                    .put("lineTotal", line.getLineTotal())
                    .put("notes", nullable(line.getNotes())));
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

    private static JSONObject lineData(LineCalculation line) {
        return new JSONObject()
                .put("catalogItemId", line.item().getId().toString())
                .put("name", line.item().getName())
                .put("quantity", line.quantity())
                .put("unitPrice", line.item().getPrice())
                .put("lineTotal", line.total())
                .put("notes", nullable(line.notes()));
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

    private static BigDecimal money(Object value) {
        try { return new BigDecimal(String.valueOf(value)); }
        catch (Exception e) { throw new IllegalArgumentException("Se recibió un monto inválido."); }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static Object nullable(Object value) {
        return value == null ? JSONObject.NULL : value;
    }

    private static JSONObject success(JSONObject data) {
        return new JSONObject().put("success", true).put("data", data).put("error", JSONObject.NULL);
    }

    private static JSONObject error(String code, String message) {
        return new JSONObject()
                .put("success", false)
                .put("data", JSONObject.NULL)
                .put("error", new JSONObject().put("code", code).put("message", message));
    }

    private record LineCalculation(CatalogItem item, int quantity, String notes, BigDecimal total) {}
    private record ItemCalculation(List<LineCalculation> lines, BigDecimal subtotal, String currency) {}
    private record OrderCalculation(List<LineCalculation> lines,
                                    BigDecimal subtotal,
                                    BigDecimal deliveryFee,
                                    BigDecimal total,
                                    String currency,
                                    BusinessOrder.FulfillmentType fulfillmentType,
                                    DeliveryZone deliveryZone,
                                    String address) {}
    private record ZoneMatch(DeliveryZone zone, int score) {}
}

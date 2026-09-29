package cl.helvoca.simulator;

import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.ai.realtime.RealtimeToolService;
import cl.helvoca.booking.BookingStatus;
import cl.helvoca.call.CallTraceService;
import cl.helvoca.request.RequestPriority;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Executes the same receptionist tool contract while keeping mutations inside
 * {@link SimulatorStateService}. Read-only calls are delegated to the real
 * backend so the test uses the tenant's actual services, hours and knowledge.
 */
@Service
public class SimulatorToolExecutor {
    private static final Set<String> SIMULATOR_TOOL_NAMES = Set.of(
            "get_business_information",
            "list_services",
            "search_knowledge",
            "find_caller",
            "register_caller",
            "list_available_slots",
            "check_booking_availability",
            "create_booking",
            "list_customer_bookings",
            "reschedule_booking",
            "cancel_booking",
            "create_request",
            "record_unanswered_question",
            "verify_caller_whatsapp",
            "send_whatsapp_operation",
            "transfer_to_human",
            "end_call",
            "list_catalog",
            "get_stock",
            "list_delivery_zones",
            "validate_delivery_address",
            "quote_delivery",
            "create_quote",
            "quote_order",
            "update_order",
            "create_order",
            "get_order_status",
            "cancel_order");

    private final RealtimeToolService realTools;
    private final SimulatorStateService state;
    private final CallTraceService trace;

    public SimulatorToolExecutor(RealtimeToolService realTools,
                                 SimulatorStateService state,
                                 CallTraceService trace) {
        this.realTools = realTools;
        this.state = state;
        this.trace = trace;
    }

    public JSONArray toolDefinitions(RealtimeCallContext context) {
        JSONArray source = realTools.toolDefinitions(context);
        JSONArray safe = new JSONArray();
        if (source == null) return safe;
        for (int i = 0; i < source.length(); i++) {
            JSONObject definition = source.optJSONObject(i);
            if (definition == null) continue;
            String name = definition.optString("name", "");
            if (SIMULATOR_TOOL_NAMES.contains(name)) safe.put(new JSONObject(definition.toString()));
        }
        return safe;
    }

    public String execute(RealtimeCallContext context, String toolName, String rawArguments) {
        try {
            JSONObject args = rawArguments == null || rawArguments.isBlank()
                    ? new JSONObject()
                    : new JSONObject(rawArguments);
            return switch (toolName) {
                case "find_caller" -> traced(context, toolName, findCaller(context));
                case "register_caller" -> traced(context, toolName, registerCaller(context, args));
                case "check_booking_availability" -> checkAvailability(context, rawArguments);
                case "list_available_slots" -> listAvailableSlots(context, rawArguments);
                case "create_booking" -> traced(context, toolName, createBooking(context, args));
                case "list_customer_bookings" -> traced(context, toolName, listBookings(context));
                case "reschedule_booking" -> traced(context, toolName, rescheduleBooking(context, args));
                case "cancel_booking" -> traced(context, toolName, cancelBooking(context, args));
                case "create_request" -> traced(context, toolName, createRequest(context, args));
                case "record_unanswered_question" -> traced(context, toolName, recordQuestion(context, args));
                case "verify_caller_whatsapp" -> traced(context, toolName, verifyCallerWhatsapp(args));
                case "send_whatsapp_operation" -> traced(context, toolName, simulatedExternalAction("WHATSAPP_OPERATION"));
                case "transfer_to_human" -> traced(context, toolName, simulatedExternalAction("HUMAN_TRANSFER"));
                case "end_call" -> traced(context, toolName, simulatedExternalAction("END_CALL"));
                case "list_catalog", "get_stock", "list_delivery_zones", "validate_delivery_address",
                     "get_business_information", "list_services", "search_knowledge" ->
                        realTools.execute(context, toolName, rawArguments);
                case "quote_delivery" -> traced(context, toolName, quoteDelivery(context, args));
                case "create_quote" -> traced(context, toolName, createQuote(context, args));
                case "quote_order" -> traced(context, toolName, quoteOrder(context, args));
                case "update_order" -> traced(context, toolName, updateOrder(context, args));
                case "create_order" -> traced(context, toolName, createOrder(context, args));
                case "get_order_status" -> traced(context, toolName, getOrderStatus(context, args));
                case "cancel_order" -> traced(context, toolName, cancelOrder(context, args));
                default -> traced(context, toolName,
                        error("SIMULATOR_TOOL_BLOCKED",
                                "La herramienta no está habilitada dentro del simulador seguro."));
            };
        } catch (JSONException | IllegalArgumentException e) {
            return traced(context, toolName, error("INVALID_ARGUMENT", e.getMessage()));
        } catch (Exception e) {
            return traced(context, toolName, error("SIMULATOR_TOOL_FAILED", "No pude completar esa acción dentro de la simulación."));
        }
    }

    private JSONObject findCaller(RealtimeCallContext context) {
        var customer = state.customer(context.callId());
        if (customer == null) return success(new JSONObject().put("found", false));
        return success(new JSONObject()
                .put("found", true)
                .put("id", customer.id().toString())
                .put("name", customer.name())
                .put("phone", "simulator")
                .put("email", customer.email() == null ? JSONObject.NULL : customer.email()));
    }

    private JSONObject registerCaller(RealtimeCallContext context, JSONObject args) {
        String name = required(args, "name").trim();
        if (name.isBlank()) throw new IllegalArgumentException("El nombre no puede estar vacío.");
        String email = optional(args, "email");
        var customer = state.registerCustomer(context.callId(), name, email);
        return success(new JSONObject()
                .put("customerId", customer.id().toString())
                .put("name", customer.name())
                .put("phone", "simulator"));
    }

    private String checkAvailability(RealtimeCallContext context, String rawArguments) {
        String raw = realTools.execute(context, "check_booking_availability", rawArguments);
        JSONObject result = parse(raw);
        if (!result.optBoolean("success", false)) return raw;
        JSONObject data = result.optJSONObject("data");
        if (data == null || !data.optBoolean("available", false)) return raw;
        UUID serviceId = uuid(data.optString("serviceId", null));
        Instant startAt = instant(data.optString("startAt", null));
        Instant endAt = instant(data.optString("endAt", null));
        if (state.overlaps(context.callId(), serviceId, startAt, endAt, null)) {
            data.put("available", false);
            data.put("simulatorConflict", true);
        }
        return result.toString();
    }

    private String listAvailableSlots(RealtimeCallContext context, String rawArguments) {
        String raw = realTools.execute(context, "list_available_slots", rawArguments);
        JSONObject result = parse(raw);
        if (!result.optBoolean("success", false)) return raw;
        JSONObject data = result.optJSONObject("data");
        if (data == null) return raw;
        UUID serviceId = uuid(data.optString("serviceId", null));
        JSONArray source = data.optJSONArray("slots");
        if (source == null) return raw;
        JSONArray filtered = new JSONArray();
        for (int i = 0; i < source.length(); i++) {
            JSONObject slot = source.optJSONObject(i);
            if (slot == null) continue;
            Instant start = instant(slot.optString("startAt", null));
            Instant end = instant(slot.optString("endAt", null));
            if (!state.overlaps(context.callId(), serviceId, start, end, null)) filtered.put(slot);
        }
        data.put("slots", filtered);
        return result.toString();
    }

    private JSONObject createBooking(RealtimeCallContext context, JSONObject args) {
        if (state.customer(context.callId()) == null) {
            return error("CUSTOMER_NOT_REGISTERED", "Necesito el nombre del cliente antes de confirmar la reserva de prueba.");
        }
        String serviceIdRaw = required(args, "serviceId");
        String startAtRaw = required(args, "startAt");
        JSONObject availabilityArgs = new JSONObject()
                .put("serviceId", serviceIdRaw)
                .put("startAt", startAtRaw);
        JSONObject availability = parse(checkAvailability(context, availabilityArgs.toString()));
        if (!availability.optBoolean("success", false)) return availability;
        JSONObject data = availability.optJSONObject("data");
        if (data == null || !data.optBoolean("available", false)) {
            return error("BOOKING_SLOT_UNAVAILABLE", "Ese horario no está disponible.");
        }

        UUID serviceId = uuid(data.optString("serviceId", serviceIdRaw));
        Instant startAt = instant(data.optString("startAt", startAtRaw));
        Instant endAt = instant(data.optString("endAt", null));
        String localStart = data.optString("localStart", startAt.toString());
        String serviceName = data.optString("serviceName", "Servicio");
        var booking = state.createBooking(context.callId(), serviceId, serviceName, startAt, endAt, localStart);
        return success(bookingData(booking));
    }

    private JSONObject listBookings(RealtimeCallContext context) {
        if (state.customer(context.callId()) == null) {
            return error("CUSTOMER_NOT_REGISTERED", "No hay un cliente identificado dentro de esta simulación.");
        }
        JSONArray out = new JSONArray();
        for (var booking : state.bookings(context.callId())) {
            if (booking.status() != BookingStatus.CONFIRMED || !booking.startAt().isAfter(Instant.now())) continue;
            out.put(bookingData(booking));
        }
        return success(new JSONObject().put("bookings", out));
    }

    private JSONObject rescheduleBooking(RealtimeCallContext context, JSONObject args) {
        UUID bookingId = uuid(required(args, "bookingId"));
        var booking = state.booking(context.callId(), bookingId);
        if (booking == null) return error("BOOKING_NOT_FOUND", "No encuentro esa reserva dentro de esta simulación.");
        if (booking.status() == BookingStatus.CANCELLED) {
            return error("BOOKING_CANCELLED", "Esa reserva de prueba ya está cancelada.");
        }

        String newStartRaw = required(args, "newStartAt");
        JSONObject availabilityArgs = new JSONObject()
                .put("serviceId", booking.serviceId().toString())
                .put("startAt", newStartRaw);
        String realRaw = realTools.execute(context, "check_booking_availability", availabilityArgs.toString());
        JSONObject availability = parse(realRaw);
        if (!availability.optBoolean("success", false)) return availability;
        JSONObject data = availability.optJSONObject("data");
        if (data == null || !data.optBoolean("available", false)) {
            return error("BOOKING_SLOT_UNAVAILABLE", "Ese horario no está disponible.");
        }
        Instant startAt = instant(data.optString("startAt", newStartRaw));
        Instant endAt = instant(data.optString("endAt", null));
        if (state.overlaps(context.callId(), booking.serviceId(), startAt, endAt, bookingId)) {
            return error("BOOKING_SLOT_UNAVAILABLE", "Ese horario ya está ocupado dentro de esta simulación.");
        }
        String localStart = data.optString("localStart", startAt.toString());
        var updated = state.rescheduleBooking(context.callId(), bookingId, startAt, endAt, localStart);
        return success(bookingData(updated));
    }

    private JSONObject cancelBooking(RealtimeCallContext context, JSONObject args) {
        UUID bookingId = uuid(required(args, "bookingId"));
        var booking = state.cancelBooking(context.callId(), bookingId);
        if (booking == null) return error("BOOKING_NOT_FOUND", "No encuentro esa reserva dentro de esta simulación.");
        return success(bookingData(booking));
    }

    private JSONObject createRequest(RealtimeCallContext context, JSONObject args) {
        String type = required(args, "requestType");
        String title = required(args, "title");
        RequestPriority priority = priority(optional(args, "priority"));
        var request = state.createRequest(context.callId(), type, title, priority);
        return success(new JSONObject()
                .put("requestId", request.id().toString())
                .put("status", "OPEN")
                .put("requestType", request.type())
                .put("title", request.title())
                .put("priority", request.priority().name()));
    }

    private JSONObject recordQuestion(RealtimeCallContext context, JSONObject args) {
        String question = required(args, "question");
        var item = state.recordQuestion(context.callId(), question);
        return success(new JSONObject()
                .put("questionId", item.id().toString())
                .put("question", item.question())
                .put("occurrences", 1)
                .put("status", "SIMULATED"));
    }

    private JSONObject quoteDelivery(RealtimeCallContext context, JSONObject args) {
        String address = required(args, "address").trim();
        JSONObject validation = parse(realTools.execute(
                context,
                "validate_delivery_address",
                new JSONObject().put("address", address).toString()));
        if (!validation.optBoolean("success", false)) return validation;

        JSONObject delivery = validation.optJSONObject("data");
        if (delivery == null || !delivery.optBoolean("covered", false)) {
            return error("DELIVERY_ADDRESS_UNAVAILABLE",
                    "La dirección no está cubierta por una zona de despacho configurada.");
        }

        return success(new JSONObject()
                .put("simulated", true)
                .put("operationId", UUID.randomUUID().toString())
                .put("revision", 1)
                .put("confirmationToken", UUID.randomUUID().toString())
                .put("status", "AWAITING_CONFIRMATION")
                .put("address", delivery.optString("address", address))
                .put("deliveryZoneId", delivery.opt("deliveryZoneId"))
                .put("deliveryZone", delivery.opt("deliveryZone"))
                .put("fee", delivery.opt("fee"))
                .put("minimumOrder", delivery.opt("minimumOrder"))
                .put("confirmationRequired", true));
    }

    private JSONObject createQuote(RealtimeCallContext context, JSONObject args) {
        String title = required(args, "title").trim();
        if (title.isBlank()) throw new IllegalArgumentException("La cotización necesita un título.");

        JSONArray requestedItems = args.optJSONArray("items");
        if (requestedItems == null || requestedItems.isEmpty()) {
            return success(new JSONObject()
                    .put("simulated", true)
                    .put("operationId", UUID.randomUUID().toString())
                    .put("revision", 1)
                    .put("quoteId", UUID.randomUUID().toString())
                    .put("title", title)
                    .put("status", "REQUESTED")
                    .put("amount", JSONObject.NULL)
                    .put("currency", "CLP")
                    .put("confirmationRequired", false));
        }

        JSONObject catalogResult = parse(realTools.execute(context, "list_catalog", "{}"));
        if (!catalogResult.optBoolean("success", false)) {
            return error("CATALOG_UNAVAILABLE",
                    "No pude consultar el catálogo autoritativo para esta simulación.");
        }
        JSONObject catalogData = catalogResult.optJSONObject("data");
        JSONArray catalogItems = catalogData == null ? null : catalogData.optJSONArray("items");
        if (catalogItems == null) {
            return error("CATALOG_UNAVAILABLE",
                    "El catálogo autoritativo no devolvió productos.");
        }

        BigDecimal amount = BigDecimal.ZERO;
        String currency = null;
        JSONArray quotedItems = new JSONArray();
        for (int i = 0; i < requestedItems.length(); i++) {
            JSONObject requested = requestedItems.optJSONObject(i);
            if (requested == null) {
                return error("INVALID_ARGUMENT", "Cada ítem debe ser un objeto válido.");
            }
            UUID itemId = uuid(required(requested, "catalogItemId"));
            int quantity = requested.optInt("quantity", 0);
            if (quantity < 1 || quantity > 100) {
                return error("INVALID_QUANTITY", "La cantidad debe estar entre 1 y 100.");
            }

            JSONObject catalogItem = findCatalogItem(catalogItems, itemId);
            if (catalogItem == null) {
                return error("CATALOG_ITEM_UNAVAILABLE",
                        "El producto solicitado no existe en el catálogo activo.");
            }
            BigDecimal unitPrice = money(catalogItem.opt("price"));
            if (unitPrice == null) {
                return error("CATALOG_PRICE_UNAVAILABLE",
                        "El producto no tiene un precio configurado.");
            }
            String itemCurrency = catalogItem.optString("currency", "CLP");
            if (itemCurrency == null || itemCurrency.isBlank()) itemCurrency = "CLP";
            itemCurrency = itemCurrency.trim().toUpperCase(Locale.ROOT);
            if (currency == null) currency = itemCurrency;
            if (!currency.equals(itemCurrency)) {
                return error("CURRENCY_MISMATCH",
                        "No se pueden mezclar monedas distintas en la misma cotización.");
            }

            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
            amount = amount.add(lineTotal);
            quotedItems.put(new JSONObject()
                    .put("catalogItemId", itemId.toString())
                    .put("name", catalogItem.optString("name", "Producto"))
                    .put("quantity", quantity)
                    .put("unitPrice", unitPrice)
                    .put("lineTotal", lineTotal));
        }

        return success(new JSONObject()
                .put("simulated", true)
                .put("operationId", UUID.randomUUID().toString())
                .put("revision", 1)
                .put("quoteId", UUID.randomUUID().toString())
                .put("title", title)
                .put("status", "READY")
                .put("amount", amount)
                .put("currency", currency == null ? "CLP" : currency)
                .put("items", quotedItems)
                .put("confirmationRequired", false));
    }

    private JSONObject quoteOrder(RealtimeCallContext context, JSONObject args) {
        OrderCalculationResult result = calculateOrder(context, args);
        if (result.errorCode() != null) return error(result.errorCode(), result.errorMessage());
        OrderCalculation calculation = result.calculation();
        var order = state.createOrderDraft(
                context.callId(),
                context.businessId(),
                calculation.items(),
                calculation.fulfillmentType(),
                calculation.address(),
                calculation.subtotal(),
                calculation.deliveryFee(),
                calculation.total(),
                calculation.currency());
        return success(orderData(order, false));
    }

    private JSONObject updateOrder(RealtimeCallContext context, JSONObject args) {
        UUID operationId = uuid(required(args, "operationId"));
        if (state.order(context.callId(), operationId) == null) {
            return error("ORDER_OPERATION_NOT_FOUND",
                    "No encuentro ese borrador dentro de esta simulación.");
        }
        OrderCalculationResult result = calculateOrder(context, args);
        if (result.errorCode() != null) return error(result.errorCode(), result.errorMessage());
        OrderCalculation calculation = result.calculation();
        var order = state.updateOrderDraft(
                context.callId(),
                operationId,
                calculation.items(),
                calculation.fulfillmentType(),
                calculation.address(),
                calculation.subtotal(),
                calculation.deliveryFee(),
                calculation.total(),
                calculation.currency());
        if (order == null) {
            return error("ORDER_NOT_AWAITING_CONFIRMATION",
                    "El pedido simulado ya fue confirmado o cerrado.");
        }
        return success(orderData(order, false));
    }

    private JSONObject createOrder(RealtimeCallContext context, JSONObject args) {
        UUID operationId = uuid(required(args, "operationId"));
        UUID confirmationToken = uuid(required(args, "confirmationToken"));
        var current = state.order(context.callId(), operationId);
        if (current == null) {
            return error("ORDER_OPERATION_NOT_FOUND",
                    "No encuentro ese borrador dentro de esta simulación.");
        }

        Map<SimulatorStateService.StockKey, Integer> backendAvailability = new HashMap<>();
        if (current.orderId() == null) {
            for (var item : current.items()) {
                JSONObject stockArgs = new JSONObject().put("catalogItemId", item.catalogItemId().toString());
                if (item.variantId() != null) stockArgs.put("variantId", item.variantId().toString());
                JSONObject stock = parse(realTools.execute(context, "get_stock", stockArgs.toString()));
                if (!stock.optBoolean("success", false)) continue;
                JSONObject data = stock.optJSONObject("data");
                if (data == null || !data.optBoolean("availabilityKnown", false)) continue;
                int available = data.optInt("available", -1);
                if (available < 0) continue;
                backendAvailability.put(
                        new SimulatorStateService.StockKey(
                                context.businessId(), item.catalogItemId(), item.variantId()),
                        available);
            }
        }

        var confirmation = state.confirmOrder(
                context.callId(), operationId, confirmationToken, backendAvailability);
        if (confirmation.errorCode() != null) {
            String message = switch (confirmation.errorCode()) {
                case "STALE_ORDER_CONFIRMATION" ->
                        "La confirmación ya no corresponde a la versión más reciente del pedido simulado.";
                case "INSUFFICIENT_STOCK" ->
                        "No hay stock suficiente para confirmar el pedido simulado.";
                default -> "No encuentro ese borrador dentro de esta simulación.";
            };
            return error(confirmation.errorCode(), message);
        }
        JSONObject data = orderData(confirmation.order(), confirmation.idempotentReplay());
        return success(data);
    }

    private JSONObject getOrderStatus(RealtimeCallContext context, JSONObject args) {
        String operationRaw = optional(args, "operationId");
        String orderRaw = optional(args, "orderId");
        if (operationRaw != null) {
            var order = state.order(context.callId(), uuid(operationRaw));
            if (order == null) return error("ORDER_NOT_FOUND", "No encuentro ese pedido dentro de esta simulación.");
            return success(orderData(order, false));
        }
        if (orderRaw != null) {
            var order = state.orderById(context.callId(), uuid(orderRaw));
            if (order == null) return error("ORDER_NOT_FOUND", "No encuentro ese pedido dentro de esta simulación.");
            return success(orderData(order, false));
        }
        JSONArray orders = new JSONArray();
        for (var order : state.orders(context.callId())) orders.put(orderData(order, false));
        return success(new JSONObject().put("simulated", true).put("orders", orders));
    }

    private JSONObject cancelOrder(RealtimeCallContext context, JSONObject args) {
        UUID orderId = uuid(required(args, "orderId"));
        var cancelled = state.cancelOrder(context.callId(), orderId);
        if (cancelled == null) {
            return error("ORDER_NOT_FOUND", "No encuentro ese pedido dentro de esta simulación.");
        }
        return success(orderData(cancelled, false));
    }

    private OrderCalculationResult calculateOrder(RealtimeCallContext context, JSONObject args) {
        JSONArray requestedItems = args.optJSONArray("items");
        if (requestedItems == null || requestedItems.isEmpty()) {
            return calculationError("INVALID_ARGUMENT", "El pedido debe incluir al menos un ítem.");
        }

        JSONObject catalogResult = parse(realTools.execute(context, "list_catalog", "{}"));
        if (!catalogResult.optBoolean("success", false)) {
            return calculationError("CATALOG_UNAVAILABLE",
                    "No pude consultar el catálogo autoritativo para esta simulación.");
        }
        JSONObject catalogData = catalogResult.optJSONObject("data");
        JSONArray catalogItems = catalogData == null ? null : catalogData.optJSONArray("items");
        if (catalogItems == null) {
            return calculationError("CATALOG_UNAVAILABLE",
                    "El catálogo autoritativo no devolvió productos.");
        }

        List<SimulatorStateService.SimulatedOrderItem> items = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        String currency = null;

        for (int i = 0; i < requestedItems.length(); i++) {
            JSONObject requested = requestedItems.optJSONObject(i);
            if (requested == null) {
                return calculationError("INVALID_ARGUMENT", "Cada ítem debe ser un objeto válido.");
            }
            UUID itemId;
            try {
                itemId = uuid(required(requested, "catalogItemId"));
            } catch (IllegalArgumentException e) {
                return calculationError("INVALID_ARGUMENT", e.getMessage());
            }
            int quantity = requested.optInt("quantity", 0);
            if (quantity < 1 || quantity > 100) {
                return calculationError("INVALID_QUANTITY", "La cantidad debe estar entre 1 y 100.");
            }

            JSONObject catalogItem = findCatalogItem(catalogItems, itemId);
            if (catalogItem == null) {
                return calculationError("CATALOG_ITEM_UNAVAILABLE",
                        "El producto solicitado no existe en el catálogo activo.");
            }

            UUID variantId = null;
            String variantRaw = optional(requested, "variantId");
            JSONArray variants = catalogItem.optJSONArray("variants");
            boolean hasVariants = catalogItem.optBoolean("hasVariants", variants != null && !variants.isEmpty());
            if (hasVariants && variantRaw == null) {
                return calculationError("VARIANT_SELECTION_REQUIRED",
                        "Este producto tiene variantes y requiere una selección exacta.");
            }
            if (variantRaw != null) {
                try {
                    variantId = uuid(variantRaw);
                } catch (IllegalArgumentException e) {
                    return calculationError("INVALID_ARGUMENT", e.getMessage());
                }
                if (!containsVariant(variants, variantId)) {
                    return calculationError("VARIANT_NOT_FOUND",
                            "La variante seleccionada no pertenece al producto.");
                }
            }

            BigDecimal unitPrice = money(catalogItem.opt("price"));
            if (unitPrice == null) {
                return calculationError("CATALOG_PRICE_UNAVAILABLE",
                        "El producto no tiene un precio configurado.");
            }
            String itemCurrency = catalogItem.optString("currency", "CLP");
            if (itemCurrency == null || itemCurrency.isBlank()) itemCurrency = "CLP";
            itemCurrency = itemCurrency.trim().toUpperCase(Locale.ROOT);
            if (currency == null) currency = itemCurrency;
            if (!currency.equals(itemCurrency)) {
                return calculationError("CURRENCY_MISMATCH",
                        "No se pueden mezclar monedas distintas en el mismo pedido.");
            }

            JSONObject stockArgs = new JSONObject().put("catalogItemId", itemId.toString());
            if (variantId != null) stockArgs.put("variantId", variantId.toString());
            JSONObject stock = parse(realTools.execute(context, "get_stock", stockArgs.toString()));
            if (stock.optBoolean("success", false)) {
                JSONObject stockData = stock.optJSONObject("data");
                if (stockData != null && stockData.optBoolean("availabilityKnown", false)) {
                    int authoritative = stockData.optInt("available", -1);
                    if (authoritative >= 0) {
                        int remaining = state.remainingAvailable(
                                context.businessId(), itemId, variantId, authoritative);
                        if (remaining < quantity) {
                            return calculationError("INSUFFICIENT_STOCK",
                                    "No hay stock suficiente para esa cantidad.");
                        }
                    }
                }
            }

            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
            items.add(new SimulatorStateService.SimulatedOrderItem(
                    itemId,
                    variantId,
                    catalogItem.optString("name", "Producto"),
                    quantity,
                    unitPrice,
                    lineTotal,
                    itemCurrency));
            subtotal = subtotal.add(lineTotal);
        }

        String fulfillment = optional(args, "fulfillmentType");
        if (fulfillment == null) {
            return calculationError("INVALID_ARGUMENT", "Falta el argumento fulfillmentType.");
        }
        fulfillment = fulfillment.trim().toUpperCase(Locale.ROOT);
        BigDecimal deliveryFee = BigDecimal.ZERO;
        String address = null;

        if ("DELIVERY".equals(fulfillment)) {
            address = optional(args, "address");
            if (address == null) {
                return calculationError("DELIVERY_ADDRESS_REQUIRED",
                        "La dirección es obligatoria para delivery.");
            }
            JSONObject validation = parse(realTools.execute(
                    context,
                    "validate_delivery_address",
                    new JSONObject().put("address", address).toString()));
            if (!validation.optBoolean("success", false)) {
                JSONObject backendError = validation.optJSONObject("error");
                return calculationError(
                        backendError == null ? "DELIVERY_ADDRESS_UNAVAILABLE" : backendError.optString("code", "DELIVERY_ADDRESS_UNAVAILABLE"),
                        backendError == null ? "La dirección no pudo validarse." : backendError.optString("message", "La dirección no pudo validarse."));
            }
            JSONObject delivery = validation.optJSONObject("data");
            if (delivery != null) {
                BigDecimal configuredFee = money(delivery.opt("fee"));
                if (configuredFee != null) deliveryFee = configuredFee;
                BigDecimal minimum = money(delivery.opt("minimumOrder"));
                if (minimum != null && subtotal.compareTo(minimum) < 0) {
                    return calculationError("DELIVERY_MINIMUM_NOT_MET",
                            "El subtotal no alcanza la compra mínima de la zona.");
                }
            }
        } else if (!"PICKUP".equals(fulfillment)) {
            return calculationError("INVALID_FULFILLMENT_TYPE",
                    "fulfillmentType debe ser PICKUP o DELIVERY.");
        }

        return new OrderCalculationResult(
                new OrderCalculation(
                        List.copyOf(items),
                        fulfillment,
                        address,
                        subtotal,
                        deliveryFee,
                        subtotal.add(deliveryFee),
                        currency == null ? "CLP" : currency),
                null,
                null);
    }

    private static JSONObject findCatalogItem(JSONArray items, UUID itemId) {
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item != null && itemId.toString().equals(item.optString("id", ""))) return item;
        }
        return null;
    }

    private static boolean containsVariant(JSONArray variants, UUID variantId) {
        if (variants == null) return false;
        for (int i = 0; i < variants.length(); i++) {
            JSONObject variant = variants.optJSONObject(i);
            if (variant != null && variantId.toString().equals(variant.optString("variantId", ""))) return true;
        }
        return false;
    }

    private static BigDecimal money(Object value) {
        if (value == null || value == JSONObject.NULL) return null;
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private static JSONObject orderData(
            SimulatorStateService.SimulatedOrder order,
            boolean idempotentReplay) {
        JSONArray items = new JSONArray();
        for (var item : order.items()) {
            JSONObject data = new JSONObject()
                    .put("catalogItemId", item.catalogItemId().toString())
                    .put("variantId", item.variantId() == null ? JSONObject.NULL : item.variantId().toString())
                    .put("name", item.name())
                    .put("quantity", item.quantity())
                    .put("unitPrice", item.unitPrice())
                    .put("lineTotal", item.lineTotal());
            items.put(data);
        }
        return new JSONObject()
                .put("simulated", true)
                .put("operationId", order.operationId().toString())
                .put("orderId", order.orderId() == null ? JSONObject.NULL : order.orderId().toString())
                .put("revision", order.revision())
                .put("confirmationToken", order.confirmationToken() == null
                        ? JSONObject.NULL
                        : order.confirmationToken().toString())
                .put("status", order.status())
                .put("fulfillmentType", order.fulfillmentType())
                .put("address", order.address() == null ? JSONObject.NULL : order.address())
                .put("items", items)
                .put("subtotal", order.subtotal())
                .put("deliveryFee", order.deliveryFee())
                .put("total", order.total())
                .put("currency", order.currency())
                .put("idempotentReplay", idempotentReplay);
    }

    private static OrderCalculationResult calculationError(String code, String message) {
        return new OrderCalculationResult(null, code, message);
    }

    private JSONObject verifyCallerWhatsapp(JSONObject args) {
        if (!args.optBoolean("confirmedSameNumber", false)) {
            return error("WHATSAPP_CONFIRMATION_REQUIRED",
                    "La simulación requiere confirmación explícita de que el número actual también es WhatsApp.");
        }
        return success(new JSONObject()
                .put("simulated", true)
                .put("verified", true)
                .put("sameAsCallerNumber", true)
                .put("status", "SIMULATED"));
    }

    private static JSONObject simulatedExternalAction(String action) {
        return success(new JSONObject()
                .put("simulated", true)
                .put("action", action)
                .put("status", "SIMULATED"));
    }

    private String traced(RealtimeCallContext context, String toolName, JSONObject result) {
        try {
            trace.recordTool(context.businessId(), context.callId(), toolName, result);
        } catch (Exception ignored) {
            // Trace failures must never turn a safe simulation into a real mutation.
        }
        return result.toString();
    }

    private static JSONObject bookingData(SimulatorStateService.SimulatedBooking booking) {
        return new JSONObject()
                .put("bookingId", booking.id().toString())
                .put("status", booking.status().name())
                .put("serviceId", booking.serviceId().toString())
                .put("service", booking.serviceName())
                .put("startAt", booking.startAt().toString())
                .put("endAt", booking.endAt().toString())
                .put("localStart", booking.localStart());
    }

    private static JSONObject parse(String raw) {
        try { return new JSONObject(raw); }
        catch (Exception e) { return error("SIMULATOR_INVALID_TOOL_RESULT", "La simulación recibió una respuesta inválida del backend."); }
    }

    private static JSONObject success(JSONObject data) {
        return new JSONObject().put("success", true).put("data", data).put("error", JSONObject.NULL);
    }

    private static JSONObject error(String code, String message) {
        return new JSONObject().put("success", false).put("data", JSONObject.NULL)
                .put("error", new JSONObject().put("code", code).put("message", message));
    }

    private static String required(JSONObject args, String key) {
        String value = args.optString(key, null);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Falta el argumento " + key + ".");
        return value;
    }

    private static String optional(JSONObject args, String key) {
        if (!args.has(key) || args.isNull(key)) return null;
        String value = args.optString(key, null);
        return value == null || value.isBlank() ? null : value;
    }

    private static UUID uuid(String value) {
        try { return UUID.fromString(value); }
        catch (Exception e) { throw new IllegalArgumentException("UUID inválido."); }
    }

    private static Instant instant(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Fecha/hora inválida.");
        try { return Instant.parse(value); }
        catch (Exception first) {
            try { return OffsetDateTime.parse(value).toInstant(); }
            catch (Exception second) { throw new IllegalArgumentException("Fecha/hora inválida."); }
        }
    }

    private static RequestPriority priority(String value) {
        if (value == null || value.isBlank()) return RequestPriority.NORMAL;
        try { return RequestPriority.valueOf(value.trim().toUpperCase(Locale.ROOT)); }
        catch (Exception e) { throw new IllegalArgumentException("priority debe ser LOW, NORMAL, HIGH o URGENT."); }
    }

    private record OrderCalculation(
            List<SimulatorStateService.SimulatedOrderItem> items,
            String fulfillmentType,
            String address,
            BigDecimal subtotal,
            BigDecimal deliveryFee,
            BigDecimal total,
            String currency) {}

    private record OrderCalculationResult(
            OrderCalculation calculation,
            String errorCode,
            String errorMessage) {}
}

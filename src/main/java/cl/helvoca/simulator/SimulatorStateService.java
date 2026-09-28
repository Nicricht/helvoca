package cl.helvoca.simulator;

import cl.helvoca.booking.BookingStatus;
import cl.helvoca.request.RequestPriority;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ephemeral state for the web receptionist simulator.
 *
 * <p>Simulation mutations never touch customer, booking, request or learning
 * tables. Only the simulated call/transcript/action trace is durable so an
 * owner can inspect what Helvoca would have done.</p>
 */
@Service
public class SimulatorStateService {
    private final Map<UUID, SessionState> sessions = new ConcurrentHashMap<>();
    private final Map<StockKey, Integer> simulatedCommittedStock = new HashMap<>();

    public void start(UUID callId) {
        sessions.put(callId, new SessionState());
    }

    public synchronized void finish(UUID callId) {
        SessionState session = sessions.remove(callId);
        if (session == null) return;

        for (SimulatedOrder order : session.orders.values()) {
            if (order.orderId() == null || !"CONFIRMED".equals(order.status())) continue;
            for (SimulatedOrderItem item : order.items()) {
                StockKey key = new StockKey(order.businessId(), item.catalogItemId(), item.variantId());
                int next = Math.max(0, simulatedCommittedStock.getOrDefault(key, 0) - item.quantity());
                if (next == 0) simulatedCommittedStock.remove(key);
                else simulatedCommittedStock.put(key, next);
            }
        }
    }

    public SimulatedCustomer customer(UUID callId) {
        SessionState state = state(callId);
        synchronized (state) {
            return state.customer;
        }
    }

    public SimulatedCustomer registerCustomer(UUID callId, String name, String email) {
        SessionState state = state(callId);
        synchronized (state) {
            UUID id = state.customer == null ? UUID.randomUUID() : state.customer.id();
            state.customer = new SimulatedCustomer(id, name, email);
            return state.customer;
        }
    }

    public List<SimulatedBooking> bookings(UUID callId) {
        SessionState state = state(callId);
        synchronized (state) {
            return state.bookings.values().stream()
                    .sorted(Comparator.comparing(SimulatedBooking::startAt))
                    .toList();
        }
    }

    public SimulatedBooking booking(UUID callId, UUID bookingId) {
        SessionState state = state(callId);
        synchronized (state) {
            return state.bookings.get(bookingId);
        }
    }

    public SimulatedBooking createBooking(UUID callId,
                                          UUID serviceId,
                                          String serviceName,
                                          Instant startAt,
                                          Instant endAt,
                                          String localStart) {
        SessionState state = state(callId);
        synchronized (state) {
            SimulatedBooking booking = new SimulatedBooking(
                    UUID.randomUUID(), serviceId, serviceName, startAt, endAt, localStart, BookingStatus.CONFIRMED);
            state.bookings.put(booking.id(), booking);
            return booking;
        }
    }

    public SimulatedBooking rescheduleBooking(UUID callId,
                                              UUID bookingId,
                                              Instant startAt,
                                              Instant endAt,
                                              String localStart) {
        SessionState state = state(callId);
        synchronized (state) {
            SimulatedBooking current = state.bookings.get(bookingId);
            if (current == null) return null;
            SimulatedBooking updated = new SimulatedBooking(
                    current.id(), current.serviceId(), current.serviceName(),
                    startAt, endAt, localStart, current.status());
            state.bookings.put(bookingId, updated);
            return updated;
        }
    }

    public SimulatedBooking cancelBooking(UUID callId, UUID bookingId) {
        SessionState state = state(callId);
        synchronized (state) {
            SimulatedBooking current = state.bookings.get(bookingId);
            if (current == null) return null;
            SimulatedBooking updated = new SimulatedBooking(
                    current.id(), current.serviceId(), current.serviceName(),
                    current.startAt(), current.endAt(), current.localStart(), BookingStatus.CANCELLED);
            state.bookings.put(bookingId, updated);
            return updated;
        }
    }

    public boolean overlaps(UUID callId, UUID serviceId, Instant startAt, Instant endAt, UUID excludeId) {
        SessionState state = state(callId);
        synchronized (state) {
            return state.bookings.values().stream()
                    .filter(b -> b.status() != BookingStatus.CANCELLED)
                    .filter(b -> b.serviceId().equals(serviceId))
                    .filter(b -> excludeId == null || !b.id().equals(excludeId))
                    .anyMatch(b -> b.startAt().isBefore(endAt) && b.endAt().isAfter(startAt));
        }
    }

    public SimulatedRequest createRequest(UUID callId, String type, String title, RequestPriority priority) {
        SessionState state = state(callId);
        synchronized (state) {
            SimulatedRequest request = new SimulatedRequest(
                    UUID.randomUUID(), type, title, priority == null ? RequestPriority.NORMAL : priority);
            state.requests.add(request);
            return request;
        }
    }

    public SimulatedQuestion recordQuestion(UUID callId, String question) {
        SessionState state = state(callId);
        synchronized (state) {
            SimulatedQuestion item = new SimulatedQuestion(UUID.randomUUID(), question);
            state.questions.add(item);
            return item;
        }
    }

    public synchronized SimulatedOrder createOrderDraft(
            UUID callId,
            UUID businessId,
            List<SimulatedOrderItem> items,
            String fulfillmentType,
            String address,
            BigDecimal subtotal,
            BigDecimal deliveryFee,
            BigDecimal total,
            String currency) {
        SessionState session = state(callId);
        SimulatedOrder order = new SimulatedOrder(
                UUID.randomUUID(),
                null,
                businessId,
                1,
                UUID.randomUUID(),
                "AWAITING_CONFIRMATION",
                fulfillmentType,
                address,
                subtotal,
                deliveryFee,
                total,
                currency,
                List.copyOf(items));
        session.orders.put(order.operationId(), order);
        return order;
    }

    public synchronized SimulatedOrder updateOrderDraft(
            UUID callId,
            UUID operationId,
            List<SimulatedOrderItem> items,
            String fulfillmentType,
            String address,
            BigDecimal subtotal,
            BigDecimal deliveryFee,
            BigDecimal total,
            String currency) {
        SessionState session = state(callId);
        SimulatedOrder current = session.orders.get(operationId);
        if (current == null || current.orderId() != null) return null;
        SimulatedOrder updated = new SimulatedOrder(
                current.operationId(),
                null,
                current.businessId(),
                current.revision() + 1,
                UUID.randomUUID(),
                "AWAITING_CONFIRMATION",
                fulfillmentType,
                address,
                subtotal,
                deliveryFee,
                total,
                currency,
                List.copyOf(items));
        session.orders.put(operationId, updated);
        return updated;
    }

    public synchronized SimulatedOrder order(UUID callId, UUID operationId) {
        return state(callId).orders.get(operationId);
    }

    public synchronized List<SimulatedOrder> orders(UUID callId) {
        return List.copyOf(state(callId).orders.values());
    }

    public synchronized SimulatedOrder orderById(UUID callId, UUID orderId) {
        if (orderId == null) return null;
        return state(callId).orders.values().stream()
                .filter(order -> orderId.equals(order.orderId()))
                .findFirst()
                .orElse(null);
    }

    public synchronized int remainingAvailable(
            UUID businessId,
            UUID catalogItemId,
            UUID variantId,
            int authoritativeAvailable) {
        StockKey key = new StockKey(businessId, catalogItemId, variantId);
        int committed = simulatedCommittedStock.getOrDefault(key, 0);
        return Math.max(0, authoritativeAvailable - committed);
    }

    public synchronized OrderConfirmation confirmOrder(
            UUID callId,
            UUID operationId,
            UUID confirmationToken,
            Map<StockKey, Integer> authoritativeAvailability) {
        SessionState session = state(callId);
        SimulatedOrder current = session.orders.get(operationId);
        if (current == null) return new OrderConfirmation(null, false, "ORDER_OPERATION_NOT_FOUND");
        if (current.orderId() != null) return new OrderConfirmation(current, true, null);
        if (current.confirmationToken() == null || !current.confirmationToken().equals(confirmationToken)) {
            return new OrderConfirmation(current, false, "STALE_ORDER_CONFIRMATION");
        }

        for (SimulatedOrderItem item : current.items()) {
            StockKey key = new StockKey(current.businessId(), item.catalogItemId(), item.variantId());
            Integer backendAvailable = authoritativeAvailability.get(key);
            if (backendAvailable == null) continue;
            int remaining = Math.max(0, backendAvailable - simulatedCommittedStock.getOrDefault(key, 0));
            if (remaining < item.quantity()) {
                return new OrderConfirmation(current, false, "INSUFFICIENT_STOCK");
            }
        }

        for (SimulatedOrderItem item : current.items()) {
            StockKey key = new StockKey(current.businessId(), item.catalogItemId(), item.variantId());
            if (!authoritativeAvailability.containsKey(key)) continue;
            simulatedCommittedStock.merge(key, item.quantity(), Integer::sum);
        }

        SimulatedOrder confirmed = new SimulatedOrder(
                current.operationId(),
                UUID.randomUUID(),
                current.businessId(),
                current.revision(),
                null,
                "CONFIRMED",
                current.fulfillmentType(),
                current.address(),
                current.subtotal(),
                current.deliveryFee(),
                current.total(),
                current.currency(),
                current.items());
        session.orders.put(operationId, confirmed);
        return new OrderConfirmation(confirmed, false, null);
    }

    public synchronized SimulatedOrder cancelOrder(UUID callId, UUID orderId) {
        SessionState session = state(callId);
        SimulatedOrder current = orderById(callId, orderId);
        if (current == null) return null;
        if ("CANCELLED".equals(current.status())) return current;

        if (current.orderId() != null && "CONFIRMED".equals(current.status())) {
            for (SimulatedOrderItem item : current.items()) {
                StockKey key = new StockKey(current.businessId(), item.catalogItemId(), item.variantId());
                int next = Math.max(0, simulatedCommittedStock.getOrDefault(key, 0) - item.quantity());
                if (next == 0) simulatedCommittedStock.remove(key);
                else simulatedCommittedStock.put(key, next);
            }
        }

        SimulatedOrder cancelled = new SimulatedOrder(
                current.operationId(),
                current.orderId(),
                current.businessId(),
                current.revision(),
                null,
                "CANCELLED",
                current.fulfillmentType(),
                current.address(),
                current.subtotal(),
                current.deliveryFee(),
                current.total(),
                current.currency(),
                current.items());
        session.orders.put(current.operationId(), cancelled);
        return cancelled;
    }

    public String promptContext(UUID callId) {
        SessionState state = state(callId);
        synchronized (state) {
            StringBuilder out = new StringBuilder("\nEstado aislado de esta simulación:\n");
            if (state.customer == null) out.append("- Aún no hay cliente identificado.\n");
            else out.append("- Cliente de prueba: ").append(state.customer.name()).append(".\n");
            List<SimulatedBooking> active = state.bookings.values().stream()
                    .filter(b -> b.status() == BookingStatus.CONFIRMED)
                    .sorted(Comparator.comparing(SimulatedBooking::startAt))
                    .toList();
            if (active.isEmpty()) out.append("- No hay reservas de prueba activas.\n");
            else {
                out.append("- Reservas de prueba activas:\n");
                for (SimulatedBooking booking : active) {
                    out.append("  * bookingId=").append(booking.id())
                            .append(", servicio=").append(booking.serviceName())
                            .append(", fecha=").append(booking.localStart()).append("\n");
                }
            }
            out.append("- Ninguna mutación de esta prueba modifica datos reales del negocio.\n");
            return out.toString();
        }
    }

    private SessionState state(UUID callId) {
        return sessions.computeIfAbsent(callId, ignored -> new SessionState());
    }

    private static final class SessionState {
        private SimulatedCustomer customer;
        private final Map<UUID, SimulatedBooking> bookings = new LinkedHashMap<>();
        private final List<SimulatedRequest> requests = new ArrayList<>();
        private final List<SimulatedQuestion> questions = new ArrayList<>();
        private final Map<UUID, SimulatedOrder> orders = new LinkedHashMap<>();
    }

    public record SimulatedCustomer(UUID id, String name, String email) {}

    public record SimulatedBooking(
            UUID id,
            UUID serviceId,
            String serviceName,
            Instant startAt,
            Instant endAt,
            String localStart,
            BookingStatus status) {}

    public record SimulatedRequest(UUID id, String type, String title, RequestPriority priority) {}

    public record SimulatedQuestion(UUID id, String question) {}

    public record SimulatedOrderItem(
            UUID catalogItemId,
            UUID variantId,
            String name,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal,
            String currency) {}

    public record SimulatedOrder(
            UUID operationId,
            UUID orderId,
            UUID businessId,
            int revision,
            UUID confirmationToken,
            String status,
            String fulfillmentType,
            String address,
            BigDecimal subtotal,
            BigDecimal deliveryFee,
            BigDecimal total,
            String currency,
            List<SimulatedOrderItem> items) {}

    public record StockKey(UUID businessId, UUID catalogItemId, UUID variantId) {}

    public record OrderConfirmation(
            SimulatedOrder order,
            boolean idempotentReplay,
            String errorCode) {}
}

package cl.helvoca.simulator;

import cl.helvoca.booking.BookingStatus;
import cl.helvoca.request.RequestPriority;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
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

    public void start(UUID callId) {
        sessions.put(callId, new SessionState());
    }

    public void finish(UUID callId) {
        sessions.remove(callId);
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
}

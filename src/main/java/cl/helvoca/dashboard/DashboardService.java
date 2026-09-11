package cl.helvoca.dashboard;

import cl.helvoca.booking.Booking;
import cl.helvoca.booking.BookingRepository;
import cl.helvoca.booking.BookingStatus;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.call.CallSessionRepository;
import cl.helvoca.call.CallStatus;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.learning.UnansweredQuestionRepository;
import cl.helvoca.learning.UnansweredQuestionResponse;
import cl.helvoca.learning.UnansweredQuestionStatus;
import cl.helvoca.observability.CallToolEventRepository;
import cl.helvoca.request.BusinessRequestRepository;
import cl.helvoca.request.BusinessRequestResponse;
import cl.helvoca.request.BusinessRequestStatus;
import cl.helvoca.security.TenantProvider;
import cl.helvoca.servicecatalog.ServiceItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.List;
import java.util.UUID;

@Service
public class DashboardService {
    private final TenantProvider tenantProvider;
    private final BusinessRepository businesses;
    private final CallSessionRepository calls;
    private final BookingRepository bookings;
    private final CustomerRepository customers;
    private final ServiceItemRepository services;
    private final BusinessRequestRepository requests;
    private final UnansweredQuestionRepository unanswered;
    private final CallToolEventRepository toolEvents;

    public DashboardService(TenantProvider tenantProvider,
                            BusinessRepository businesses,
                            CallSessionRepository calls,
                            BookingRepository bookings,
                            CustomerRepository customers,
                            ServiceItemRepository services,
                            BusinessRequestRepository requests,
                            UnansweredQuestionRepository unanswered,
                            CallToolEventRepository toolEvents) {
        this.tenantProvider = tenantProvider;
        this.businesses = businesses;
        this.calls = calls;
        this.bookings = bookings;
        this.customers = customers;
        this.services = services;
        this.requests = requests;
        this.unanswered = unanswered;
        this.toolEvents = toolEvents;
    }

    @Transactional(readOnly = true)
    public DashboardOverviewResponse overview() {
        UUID businessId = tenantProvider.requireBusinessId();
        Business business = businesses.findById(businessId)
                .orElseThrow(() -> new IllegalStateException("Negocio no encontrado"));
        ZoneId zone = ZoneId.of(business.getTimezone());
        LocalDate today = LocalDate.now(zone);
        Instant from = today.atStartOfDay(zone).toInstant();
        Instant to = today.plusDays(1).atStartOfDay(zone).toInstant();
        Instant now = Instant.now();

        List<DashboardOverviewResponse.CallItem> recentCalls = calls.findTop10ByBusinessIdOrderByStartedAtDesc(businessId)
                .stream()
                .map(call -> new DashboardOverviewResponse.CallItem(
                        call.getId(), call.getCallerNumber(), call.getStatus(), call.getResolution(),
                        call.getStartedAt(), call.getDurationSeconds()))
                .toList();

        List<DashboardOverviewResponse.BookingItem> upcomingBookings = bookings
                .findTop10ByBusinessIdAndStatusAndStartAtAfterOrderByStartAtAsc(businessId, BookingStatus.CONFIRMED, now)
                .stream()
                .map(booking -> bookingItem(businessId, booking))
                .toList();

        List<BusinessRequestResponse> recentRequests = requests.findTop10ByBusinessIdOrderByCreatedAtDesc(businessId)
                .stream().map(BusinessRequestResponse::from).toList();

        List<UnansweredQuestionResponse> pendingQuestions = unanswered
                .findTop10ByBusinessIdAndStatusOrderByLastAskedAtDesc(businessId, UnansweredQuestionStatus.OPEN)
                .stream().map(UnansweredQuestionResponse::from).toList();

        return new DashboardOverviewResponse(
                calls.countByBusinessIdAndStartedAtBetween(businessId, from, to),
                calls.countByBusinessIdAndStatusAndStartedAtBetween(businessId, CallStatus.COMPLETED, from, to),
                bookings.countByBusinessIdAndCreatedAtBetween(businessId, from, to),
                requests.countByBusinessIdAndCreatedAtBetween(businessId, from, to),
                requests.countByBusinessIdAndStatus(businessId, BusinessRequestStatus.OPEN),
                customers.countByBusinessId(businessId),
                unanswered.countByBusinessIdAndStatus(businessId, UnansweredQuestionStatus.OPEN),
                toolEvents.countByBusinessIdAndSuccessFalseAndCreatedAtBetween(businessId, from, to),
                recentCalls,
                upcomingBookings,
                recentRequests,
                pendingQuestions);
    }

    private DashboardOverviewResponse.BookingItem bookingItem(UUID businessId, Booking booking) {
        String customerName = customers.findByIdAndBusinessId(booking.getCustomerId(), businessId)
                .map(customer -> customer.getName() == null ? customer.getPhone() : customer.getName())
                .orElse("Cliente");
        String serviceName = services.findByIdAndBusinessId(booking.getServiceId(), businessId)
                .map(service -> service.getName())
                .orElse("Servicio");
        return new DashboardOverviewResponse.BookingItem(
                booking.getId(), customerName, serviceName, booking.getStartAt(), booking.getStatus().name());
    }
}

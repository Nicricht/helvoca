package cl.helvoca.operations;

import cl.helvoca.booking.Booking;
import cl.helvoca.booking.BookingRepository;
import cl.helvoca.booking.BookingStatus;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.call.CallSession;
import cl.helvoca.call.CallSessionRepository;
import cl.helvoca.call.CallStatus;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.learning.QuestionStatus;
import cl.helvoca.learning.UnansweredQuestionRepository;
import cl.helvoca.request.BusinessRequestRepository;
import cl.helvoca.request.RequestStatus;
import cl.helvoca.security.TenantProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class OperationsDashboardService {
    private static final String SIMULATOR_PROVIDER = "simulator";

    private final BusinessRepository businesses;
    private final CallSessionRepository calls;
    private final BookingRepository bookings;
    private final CustomerRepository customers;
    private final BusinessRequestRepository requests;
    private final UnansweredQuestionRepository questions;
    private final TenantProvider tenantProvider;

    public OperationsDashboardService(BusinessRepository businesses,
                                      CallSessionRepository calls,
                                      BookingRepository bookings,
                                      CustomerRepository customers,
                                      BusinessRequestRepository requests,
                                      UnansweredQuestionRepository questions,
                                      TenantProvider tenantProvider) {
        this.businesses = businesses;
        this.calls = calls;
        this.bookings = bookings;
        this.customers = customers;
        this.requests = requests;
        this.questions = questions;
        this.tenantProvider = tenantProvider;
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard() {
        UUID businessId = tenantProvider.requireBusinessId();
        Business business = businesses.findById(businessId).orElseThrow();
        ZoneId zone = ZoneId.of(business.getTimezone());
        ZonedDateTime now = ZonedDateTime.now(zone);
        Instant dayStart = now.toLocalDate().atStartOfDay(zone).toInstant();
        Instant dayEnd = now.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant();

        List<CallSession> recentCalls = calls.findAllByBusinessId(
                businessId, PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "startedAt"))).getContent();
        List<Booking> allBookings = bookings.findAllByBusinessIdOrderByStartAtDesc(businessId);
        var allCustomers = customers.findAllByBusinessIdOrderByCreatedAtDesc(businessId);
        var allRequests = requests.findAllByBusinessIdOrderByCreatedAtDesc(businessId);
        var openQuestions = questions.findAllByBusinessIdAndStatusOrderByLastSeenAtDesc(businessId, QuestionStatus.OPEN);

        long callsToday = calls.countByBusinessIdAndTelephonyProviderNotAndStartedAtGreaterThanEqualAndStartedAtLessThan(
                businessId, SIMULATOR_PROVIDER, dayStart, dayEnd);
        long failuresToday = calls.countByBusinessIdAndTelephonyProviderNotAndStatusInAndStartedAtGreaterThanEqualAndStartedAtLessThan(
                businessId, SIMULATOR_PROVIDER, List.of(CallStatus.FAILED, CallStatus.NO_ANSWER), dayStart, dayEnd);
        long bookingsToday = allBookings.stream()
                .filter(b -> b.getStatus() != BookingStatus.CANCELLED && between(b.getCreatedAt(), dayStart, dayEnd)).count();
        long customersToday = allCustomers.stream().filter(c -> between(c.getCreatedAt(), dayStart, dayEnd)).count();
        long openRequests = allRequests.stream().filter(r -> r.getStatus() == RequestStatus.OPEN || r.getStatus() == RequestStatus.IN_PROGRESS).count();

        return new Dashboard(
                business.getName(), business.getTimezone(), now.toOffsetDateTime().toString(),
                callsToday, bookingsToday, customersToday, openRequests, openQuestions.size(), failuresToday,
                recentCalls.stream().map(CallItem::from).toList(),
                allRequests.stream().limit(10).map(RequestItem::from).toList(),
                openQuestions.stream().limit(10).map(QuestionItem::from).toList());
    }

    private static boolean between(Instant value, Instant start, Instant end) {
        return value != null && !value.isBefore(start) && value.isBefore(end);
    }

    public record Dashboard(
            String businessName,
            String timezone,
            String localNow,
            long callsToday,
            long bookingsToday,
            long newCustomersToday,
            long openRequests,
            long unansweredQuestions,
            long callFailuresToday,
            List<CallItem> recentCalls,
            List<RequestItem> recentRequests,
            List<QuestionItem> unanswered
    ) {}

    public record CallItem(UUID id, String callerNumber, String status, String resolution, Instant startedAt, Integer durationSeconds) {
        static CallItem from(CallSession c) {
            return new CallItem(c.getId(), c.getCallerNumber(), c.getStatus().name(), c.getResolution(), c.getStartedAt(), c.getDurationSeconds());
        }
    }

    public record RequestItem(UUID id, String type, String title, String priority, String status, Instant createdAt) {
        static RequestItem from(cl.helvoca.request.BusinessRequest r) {
            return new RequestItem(r.getId(), r.getRequestType(), r.getTitle(), r.getPriority().name(), r.getStatus().name(), r.getCreatedAt());
        }
    }

    public record QuestionItem(UUID id, String question, int occurrences, Instant lastSeenAt) {
        static QuestionItem from(cl.helvoca.learning.UnansweredQuestion q) {
            return new QuestionItem(q.getId(), q.getQuestion(), q.getOccurrences(), q.getLastSeenAt());
        }
    }
}

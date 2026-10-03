package cl.helvoca.operations;

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

import java.math.BigDecimal;
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

        List<CallSession> recentCalls = calls.findAllByBusinessIdAndCertificationFalseAndTelephonyProviderNot(
                businessId, SIMULATOR_PROVIDER, PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "startedAt"))).getContent();

        long callsToday = calls.countByBusinessIdAndCertificationFalseAndTelephonyProviderNotAndStartedAtGreaterThanEqualAndStartedAtLessThan(
                businessId, SIMULATOR_PROVIDER, dayStart, dayEnd);
        long failuresToday = calls.countByBusinessIdAndCertificationFalseAndTelephonyProviderNotAndStatusInAndStartedAtGreaterThanEqualAndStartedAtLessThan(
                businessId, SIMULATOR_PROVIDER, List.of(CallStatus.FAILED, CallStatus.NO_ANSWER), dayStart, dayEnd);
        BigDecimal estimatedCallCostToday = calls.sumEstimatedCostByBusinessAndPeriod(
                businessId, dayStart, dayEnd, SIMULATOR_PROVIDER);
        Long callDurationSecondsToday = calls.sumDurationSecondsByBusinessAndPeriod(
                businessId, dayStart, dayEnd, SIMULATOR_PROVIDER);

        long bookingsToday = bookings.countByBusinessIdAndStatusNotAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                businessId, BookingStatus.CANCELLED, dayStart, dayEnd);
        long customersToday = customers.countByBusinessIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                businessId, dayStart, dayEnd);
        long openRequests = requests.countByBusinessIdAndStatusIn(
                businessId, List.of(RequestStatus.OPEN, RequestStatus.IN_PROGRESS));
        long openQuestions = questions.countByBusinessIdAndStatus(businessId, QuestionStatus.OPEN);

        var recentRequests = requests.findTop10ByBusinessIdOrderByCreatedAtDesc(businessId);
        var recentOpenQuestions = questions.findTop10ByBusinessIdAndStatusOrderByLastSeenAtDesc(
                businessId, QuestionStatus.OPEN);

        return new Dashboard(
                business.getName(), business.getTimezone(), now.toOffsetDateTime().toString(),
                callsToday, callDurationSecondsToday == null ? 0L : callDurationSecondsToday,
                bookingsToday, customersToday, openRequests, openQuestions, failuresToday,
                estimatedCallCostToday == null ? BigDecimal.ZERO : estimatedCallCostToday,
                recentCalls.stream().map(CallItem::from).toList(),
                recentRequests.stream().map(RequestItem::from).toList(),
                recentOpenQuestions.stream().map(QuestionItem::from).toList());
    }

    public record Dashboard(
            String businessName,
            String timezone,
            String localNow,
            long callsToday,
            long callDurationSecondsToday,
            long bookingsToday,
            long newCustomersToday,
            long openRequests,
            long unansweredQuestions,
            long callFailuresToday,
            BigDecimal estimatedCallCostTodayUsd,
            List<CallItem> recentCalls,
            List<RequestItem> recentRequests,
            List<QuestionItem> unanswered
    ) {}

    public record CallItem(
            UUID id,
            String callerNumber,
            String status,
            String resolution,
            Instant startedAt,
            Integer durationSeconds,
            BigDecimal estimatedTotalCostUsd) {
        static CallItem from(CallSession c) {
            return new CallItem(c.getId(), c.getCallerNumber(), c.getStatus().name(), c.getResolution(),
                    c.getStartedAt(), c.getDurationSeconds(), c.getEstimatedTotalCostUsd());
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

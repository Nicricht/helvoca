package cl.helvoca.dashboard;

import cl.helvoca.call.CallStatus;
import cl.helvoca.request.BusinessRequestResponse;
import cl.helvoca.learning.UnansweredQuestionResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DashboardOverviewResponse(
        long callsToday,
        long completedCallsToday,
        long bookingsCreatedToday,
        long requestsCreatedToday,
        long openRequests,
        long customers,
        long unansweredQuestions,
        long toolFailuresToday,
        List<CallItem> recentCalls,
        List<BookingItem> upcomingBookings,
        List<BusinessRequestResponse> recentRequests,
        List<UnansweredQuestionResponse> pendingQuestions
) {
    public record CallItem(
            UUID id,
            String callerNumber,
            CallStatus status,
            String resolution,
            Instant startedAt,
            Integer durationSeconds
    ) {}

    public record BookingItem(
            UUID id,
            String customerName,
            String serviceName,
            Instant startAt,
            String status
    ) {}
}

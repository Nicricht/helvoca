package cl.helvoca.operations;

import cl.helvoca.booking.BookingRepository;
import cl.helvoca.booking.BookingStatus;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.call.CallSessionRepository;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.learning.QuestionStatus;
import cl.helvoca.learning.UnansweredQuestion;
import cl.helvoca.learning.UnansweredQuestionRepository;
import cl.helvoca.request.BusinessRequest;
import cl.helvoca.request.BusinessRequestRepository;
import cl.helvoca.request.RequestPriority;
import cl.helvoca.request.RequestStatus;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OperationsDashboardServiceTest {


    @Test
    void dashboardUsesTenantLocalDayWindowsAndExcludesSimulatorCallsFromEveryAggregate() {
        for (String timezone : List.of("America/Santiago", "Asia/Kathmandu", "Pacific/Auckland")) {
            UUID businessId = UUID.randomUUID();
            BusinessRepository businesses = mock(BusinessRepository.class);
            CallSessionRepository calls = mock(CallSessionRepository.class);
            BookingRepository bookings = mock(BookingRepository.class);
            CustomerRepository customers = mock(CustomerRepository.class);
            BusinessRequestRepository requests = mock(BusinessRequestRepository.class);
            UnansweredQuestionRepository questions = mock(UnansweredQuestionRepository.class);
            TenantProvider tenant = mock(TenantProvider.class);
            Business business = mock(Business.class);

            when(tenant.requireBusinessId()).thenReturn(businessId);
            when(businesses.findById(businessId)).thenReturn(Optional.of(business));
            when(business.getName()).thenReturn("Test " + timezone);
            when(business.getTimezone()).thenReturn(timezone);
            when(calls.findAllByBusinessIdAndCertificationFalseAndTelephonyProviderNot(
                    eq(businessId), eq("simulator"), any())).thenReturn(Page.empty());
            when(requests.findTop10ByBusinessIdOrderByCreatedAtDesc(businessId)).thenReturn(List.of());
            when(questions.findTop10ByBusinessIdAndStatusOrderByLastSeenAtDesc(
                    businessId, QuestionStatus.OPEN)).thenReturn(List.of());

            OperationsDashboardService.Dashboard dashboard = new OperationsDashboardService(
                    businesses, calls, bookings, customers, requests, questions, tenant).dashboard();

            java.time.ZoneId zone = java.time.ZoneId.of(timezone);
            java.time.LocalDate localDay = java.time.OffsetDateTime.parse(dashboard.localNow()).toLocalDate();
            Instant start = localDay.atStartOfDay(zone).toInstant();
            Instant end = localDay.plusDays(1).atStartOfDay(zone).toInstant();

            assertEquals(timezone, dashboard.timezone());
            assertEquals("Test " + timezone, dashboard.businessName());
            assertEquals(0L, dashboard.callDurationSecondsToday());
            assertEquals(java.math.BigDecimal.ZERO, dashboard.estimatedCallCostTodayUsd());

            verify(calls).countByBusinessIdAndCertificationFalseAndTelephonyProviderNotAndStartedAtGreaterThanEqualAndStartedAtLessThan(
                    businessId, "simulator", start, end);
            verify(calls).countByBusinessIdAndCertificationFalseAndTelephonyProviderNotAndStatusInAndStartedAtGreaterThanEqualAndStartedAtLessThan(
                    businessId, "simulator", List.of(cl.helvoca.call.CallStatus.FAILED,
                            cl.helvoca.call.CallStatus.NO_ANSWER), start, end);
            verify(calls).sumDurationSecondsByBusinessAndPeriod(businessId, start, end, "simulator");
            verify(calls).sumEstimatedCostByBusinessAndPeriod(businessId, start, end, "simulator");
            verify(bookings).countByBusinessIdAndStatusNotAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                    businessId, BookingStatus.CANCELLED, start, end);
            verify(customers).countByBusinessIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                    businessId, start, end);
            verify(calls).findAllByBusinessIdAndCertificationFalseAndTelephonyProviderNot(
                    eq(businessId), eq("simulator"), argThat(page ->
                            page.getPageNumber() == 0
                                    && page.getPageSize() == 10
                                    && page.getSort().getOrderFor("startedAt") != null
                                    && page.getSort().getOrderFor("startedAt").isDescending()));
        }
    }

    @Test
    void dashboardReturnsAuthoritativeCountsFailuresCostAndCallLifecycleWithoutConflation() {
        UUID businessId = UUID.randomUUID();
        BusinessRepository businesses = mock(BusinessRepository.class);
        CallSessionRepository calls = mock(CallSessionRepository.class);
        BookingRepository bookings = mock(BookingRepository.class);
        CustomerRepository customers = mock(CustomerRepository.class);
        BusinessRequestRepository requests = mock(BusinessRequestRepository.class);
        UnansweredQuestionRepository questions = mock(UnansweredQuestionRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);
        Business business = mock(Business.class);
        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(businesses.findById(businessId)).thenReturn(Optional.of(business));
        when(business.getName()).thenReturn("Taller Operativo");
        when(business.getTimezone()).thenReturn("America/Santiago");

        cl.helvoca.call.CallSession failedCall = new cl.helvoca.call.CallSession();
        failedCall.setStatus(cl.helvoca.call.CallStatus.FAILED);
        failedCall.setCallerNumber("+56910000000");
        failedCall.setResolution("FAILED");
        failedCall.setStartedAt(Instant.parse("2026-10-11T16:00:00Z"));
        failedCall.setDurationSeconds(40);
        when(calls.findAllByBusinessIdAndCertificationFalseAndTelephonyProviderNot(
                eq(businessId), eq("simulator"), any())).thenReturn(
                        new org.springframework.data.domain.PageImpl<>(List.of(failedCall)));
        when(calls.countByBusinessIdAndCertificationFalseAndTelephonyProviderNotAndStartedAtGreaterThanEqualAndStartedAtLessThan(
                eq(businessId), eq("simulator"), any(Instant.class), any(Instant.class))).thenReturn(3L);
        when(calls.countByBusinessIdAndCertificationFalseAndTelephonyProviderNotAndStatusInAndStartedAtGreaterThanEqualAndStartedAtLessThan(
                eq(businessId), eq("simulator"), anyList(), any(Instant.class), any(Instant.class))).thenReturn(1L);
        when(calls.sumDurationSecondsByBusinessAndPeriod(
                eq(businessId), any(Instant.class), any(Instant.class), eq("simulator"))).thenReturn(190L);
        when(calls.sumEstimatedCostByBusinessAndPeriod(
                eq(businessId), any(Instant.class), any(Instant.class), eq("simulator")))
                .thenReturn(new java.math.BigDecimal("1.75"));
        when(bookings.countByBusinessIdAndStatusNotAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(businessId), eq(BookingStatus.CANCELLED), any(Instant.class), any(Instant.class)))
                .thenReturn(2L);
        when(requests.findTop10ByBusinessIdOrderByCreatedAtDesc(businessId)).thenReturn(List.of());
        when(questions.findTop10ByBusinessIdAndStatusOrderByLastSeenAtDesc(
                businessId, QuestionStatus.OPEN)).thenReturn(List.of());

        OperationsDashboardService.Dashboard dashboard = new OperationsDashboardService(
                businesses, calls, bookings, customers, requests, questions, tenant).dashboard();

        assertEquals(3L, dashboard.callsToday());
        assertEquals(1L, dashboard.callFailuresToday());
        assertEquals(2L, dashboard.bookingsToday());
        assertEquals(190L, dashboard.callDurationSecondsToday());
        assertEquals(new java.math.BigDecimal("1.75"), dashboard.estimatedCallCostTodayUsd());
        assertEquals(1, dashboard.recentCalls().size());
        assertEquals("FAILED", dashboard.recentCalls().getFirst().status());
        assertEquals("FAILED", dashboard.recentCalls().getFirst().resolution());
        assertEquals("+56910000000", dashboard.recentCalls().getFirst().callerNumber());
        assertEquals(40, dashboard.recentCalls().getFirst().durationSeconds());
        verify(bookings).countByBusinessIdAndStatusNotAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(businessId), eq(BookingStatus.CANCELLED), any(Instant.class), any(Instant.class));
    }

    @Test
    void dashboardCountsAndRecentListsStayBoundedInRepositories() {
        UUID businessId = UUID.randomUUID();
        BusinessRepository businesses = mock(BusinessRepository.class);
        CallSessionRepository calls = mock(CallSessionRepository.class);
        BookingRepository bookings = mock(BookingRepository.class);
        CustomerRepository customers = mock(CustomerRepository.class);
        BusinessRequestRepository requests = mock(BusinessRequestRepository.class);
        UnansweredQuestionRepository questions = mock(UnansweredQuestionRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);

        Business business = mock(Business.class);
        when(business.getName()).thenReturn("Ferretería Demo");
        when(business.getTimezone()).thenReturn("America/Santiago");
        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(businesses.findById(businessId)).thenReturn(Optional.of(business));
        when(calls.findAllByBusinessIdAndCertificationFalseAndTelephonyProviderNot(
                eq(businessId), eq("simulator"), any())).thenReturn(Page.empty());

        when(bookings.countByBusinessIdAndStatusNotAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(businessId), eq(BookingStatus.CANCELLED), any(Instant.class), any(Instant.class)))
                .thenReturn(7L);
        when(customers.countByBusinessIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(businessId), any(Instant.class), any(Instant.class)))
                .thenReturn(3L);
        when(requests.countByBusinessIdAndStatusIn(
                eq(businessId), eq(List.of(RequestStatus.OPEN, RequestStatus.IN_PROGRESS))))
                .thenReturn(4L);
        when(questions.countByBusinessIdAndStatus(businessId, QuestionStatus.OPEN))
                .thenReturn(2L);

        BusinessRequest request = new BusinessRequest();
        request.setBusinessId(businessId);
        request.setRequestType("QUOTE");
        request.setTitle("Cotizar taladro");
        request.setPriority(RequestPriority.NORMAL);
        request.setStatus(RequestStatus.OPEN);
        when(requests.findTop10ByBusinessIdOrderByCreatedAtDesc(businessId))
                .thenReturn(List.of(request));

        UnansweredQuestion question = new UnansweredQuestion();
        question.setBusinessId(businessId);
        question.setQuestion("¿Tienen despacho hoy?");
        question.setOccurrences(2);
        question.setStatus(QuestionStatus.OPEN);
        question.setLastSeenAt(Instant.parse("2030-01-01T12:00:00Z"));
        when(questions.findTop10ByBusinessIdAndStatusOrderByLastSeenAtDesc(
                businessId, QuestionStatus.OPEN))
                .thenReturn(List.of(question));

        OperationsDashboardService service = new OperationsDashboardService(
                businesses, calls, bookings, customers, requests, questions, tenant);

        OperationsDashboardService.Dashboard dashboard = service.dashboard();

        assertEquals(7L, dashboard.bookingsToday());
        assertEquals(3L, dashboard.newCustomersToday());
        assertEquals(4L, dashboard.openRequests());
        assertEquals(2L, dashboard.unansweredQuestions());
        assertEquals("Cotizar taladro", dashboard.recentRequests().getFirst().title());
        assertEquals("¿Tienen despacho hoy?", dashboard.unanswered().getFirst().question());

        verify(bookings).countByBusinessIdAndStatusNotAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(businessId), eq(BookingStatus.CANCELLED), any(Instant.class), any(Instant.class));
        verify(customers).countByBusinessIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(businessId), any(Instant.class), any(Instant.class));
        verify(requests).countByBusinessIdAndStatusIn(
                businessId, List.of(RequestStatus.OPEN, RequestStatus.IN_PROGRESS));
        verify(questions).countByBusinessIdAndStatus(businessId, QuestionStatus.OPEN);
        verify(requests).findTop10ByBusinessIdOrderByCreatedAtDesc(businessId);
        verify(questions).findTop10ByBusinessIdAndStatusOrderByLastSeenAtDesc(
                businessId, QuestionStatus.OPEN);

        verify(bookings, never()).findAllByBusinessIdOrderByStartAtDesc(any());
        verify(customers, never()).findAllByBusinessIdOrderByCreatedAtDesc(any());
        verify(requests, never()).findAllByBusinessIdOrderByCreatedAtDesc(any());
        verify(questions, never()).findAllByBusinessIdAndStatusOrderByLastSeenAtDesc(any(), any());
    }
}

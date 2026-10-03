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

package cl.helvoca.operations;

import cl.helvoca.agent.AiAgentService;
import cl.helvoca.schedule.BusinessHourRepository;
import cl.helvoca.security.TenantDatabaseContext;
import cl.helvoca.servicecatalog.ServiceItem;
import cl.helvoca.servicecatalog.ServiceItemRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BookingReadinessStartupRunnerTest {

    @Test
    void evaluatesServiceScheduleAndCreateBookingToolForPilotTenant() {
        UUID businessId = UUID.randomUUID();
        TenantDatabaseContext databaseContext = mock(TenantDatabaseContext.class);
        ServiceItemRepository services = mock(ServiceItemRepository.class);
        BusinessHourRepository hours = mock(BusinessHourRepository.class);
        AiAgentService aiAgents = mock(AiAgentService.class);
        ServiceItem activeService = mock(ServiceItem.class);

        when(activeService.isActive()).thenReturn(true);
        when(services.findAllByBusinessIdOrderByNameAsc(businessId)).thenReturn(List.of(activeService));
        when(hours.countByBusinessId(businessId)).thenReturn(5L);
        when(aiAgents.toolAllowed(businessId, "create_booking")).thenReturn(true);

        BookingReadinessStartupRunner runner = new BookingReadinessStartupRunner(
                true,
                businessId.toString(),
                databaseContext,
                services,
                hours,
                aiAgents);

        BookingReadinessStartupRunner.Readiness result = runner.evaluate(businessId);

        assertTrue(result.ready());
        assertEquals(1, result.activeServices());
        assertEquals(5L, result.businessHours());
        assertTrue(result.createBookingToolAllowed());
    }

    @Test
    void bookingIsNotReadyWhenCreateBookingToolIsUnavailable() {
        UUID businessId = UUID.randomUUID();
        TenantDatabaseContext databaseContext = mock(TenantDatabaseContext.class);
        ServiceItemRepository services = mock(ServiceItemRepository.class);
        BusinessHourRepository hours = mock(BusinessHourRepository.class);
        AiAgentService aiAgents = mock(AiAgentService.class);
        ServiceItem activeService = mock(ServiceItem.class);

        when(activeService.isActive()).thenReturn(true);
        when(services.findAllByBusinessIdOrderByNameAsc(businessId)).thenReturn(List.of(activeService));
        when(hours.countByBusinessId(businessId)).thenReturn(2L);
        when(aiAgents.toolAllowed(businessId, "create_booking")).thenReturn(false);

        BookingReadinessStartupRunner runner = new BookingReadinessStartupRunner(
                true,
                businessId.toString(),
                databaseContext,
                services,
                hours,
                aiAgents);

        BookingReadinessStartupRunner.Readiness result = runner.evaluate(businessId);

        assertFalse(result.ready());
        assertEquals(1, result.activeServices());
        assertEquals(2L, result.businessHours());
        assertFalse(result.createBookingToolAllowed());
    }
}

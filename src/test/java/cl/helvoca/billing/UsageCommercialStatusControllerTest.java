package cl.helvoca.billing;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

class UsageCommercialStatusControllerTest {

    @Test
    void delegatesTenantStatusToService() {
        UsageCommercialStatusService service = mock(UsageCommercialStatusService.class);
        var expected = new UsageCommercialStatusService.UsageCommercialStatus(
                100, 70, 0, new BigDecimal("70.0"), "NOTICE",
                149, 0, 1000, 930, false);
        when(service.currentForTenant()).thenReturn(expected);

        var result = new UsageCommercialStatusController(service).current();

        assertSame(expected, result);
        verify(service).currentForTenant();
    }
}

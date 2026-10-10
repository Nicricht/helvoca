package cl.helvoca.onboarding;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BusinessImportAiQuotaControllerTest {
    @Test
    void onlyBusinessAdminsMayReadTheirQuota() {
        PreAuthorize role = BusinessImportAiQuotaController.class.getAnnotation(PreAuthorize.class);
        assertNotNull(role);
        assertEquals("hasRole('BUSINESS_ADMIN')", role.value());

        BusinessImportAiPlanQuota quota = mock(BusinessImportAiPlanQuota.class);
        var state = new BusinessImportAiPlanQuota.Snapshot("DISABLED", "BASIC",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null, false);
        when(quota.current()).thenReturn(state);
        assertSame(state, new BusinessImportAiQuotaController(quota).current());
        verify(quota).current();
    }
}

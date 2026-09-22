package cl.helvoca.retention;

import cl.helvoca.audit.AuditService;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CustomerProfileAnonymizationServiceTest {

    @Test
    void anonymizesOnlyCurrentTenantProfileAndDeletesItsIdentities() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        AuditService auditService = mock(AuditService.class);

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(jdbc.update(startsWith("UPDATE customer"), any(Object[].class))).thenReturn(1);
        when(jdbc.update(startsWith("DELETE FROM customer_identity"), any(Object[].class))).thenReturn(2);

        CustomerProfileAnonymizationService service =
                new CustomerProfileAnonymizationService(jdbc, tenantProvider, auditService);

        CustomerProfileAnonymizationService.Result result =
                service.anonymizeCurrentTenantCustomer(customerId);

        assertEquals(customerId, result.customerId());
        assertEquals(2, result.identitiesDeleted());

        InOrder order = inOrder(jdbc, auditService);
        order.verify(jdbc).update(
                argThat(sql -> sql.contains("SET name = NULL")
                        && sql.contains("phone = NULL")
                        && sql.contains("email = NULL")
                        && sql.contains("notes = NULL")
                        && sql.contains("WHERE id = ?")
                        && sql.contains("business_id = ?")),
                aryEq(new Object[]{customerId, businessId}));
        order.verify(jdbc).update(
                argThat(sql -> sql.contains("DELETE FROM customer_identity")
                        && sql.contains("business_id = ?")
                        && sql.contains("customer_id = ?")),
                aryEq(new Object[]{businessId, customerId}));
        order.verify(auditService).humanSuccess(
                businessId,
                "CUSTOMER_PROFILE_ANONYMIZE",
                "CUSTOMER",
                customerId);
        order.verifyNoMoreInteractions();
    }

    @Test
    void doesNotDeleteIdentitiesOrAuditWhenCustomerIsOutsideTenant() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        AuditService auditService = mock(AuditService.class);

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(jdbc.update(startsWith("UPDATE customer"), any(Object[].class))).thenReturn(0);

        CustomerProfileAnonymizationService service =
                new CustomerProfileAnonymizationService(jdbc, tenantProvider, auditService);

        assertThrows(NotFoundException.class, () -> service.anonymizeCurrentTenantCustomer(customerId));

        verify(jdbc, times(1)).update(startsWith("UPDATE customer"), any(Object[].class));
        verifyNoMoreInteractions(jdbc);
        verifyNoInteractions(auditService);
    }

    @Test
    void failsClosedBeforeDatabaseAccessWhenTenantCannotBeResolved() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        AuditService auditService = mock(AuditService.class);

        when(tenantProvider.requireBusinessId()).thenThrow(new IllegalStateException("missing tenant"));

        CustomerProfileAnonymizationService service =
                new CustomerProfileAnonymizationService(jdbc, tenantProvider, auditService);

        assertThrows(
                IllegalStateException.class,
                () -> service.anonymizeCurrentTenantCustomer(UUID.randomUUID()));

        verifyNoInteractions(jdbc, auditService);
    }

    @Test
    void rejectsNullCustomerBeforeResolvingTenant() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        AuditService auditService = mock(AuditService.class);

        CustomerProfileAnonymizationService service =
                new CustomerProfileAnonymizationService(jdbc, tenantProvider, auditService);

        assertThrows(IllegalArgumentException.class, () -> service.anonymizeCurrentTenantCustomer(null));

        verifyNoInteractions(jdbc, tenantProvider, auditService);
    }
}

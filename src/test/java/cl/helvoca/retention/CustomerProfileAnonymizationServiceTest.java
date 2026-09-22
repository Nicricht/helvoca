package cl.helvoca.retention;

import cl.helvoca.audit.AuditService;
import cl.helvoca.common.ConflictException;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc, times(2)).update(sql.capture(), args.capture());

        assertTrue(sql.getAllValues().get(0).contains("SET name = NULL"));
        assertTrue(sql.getAllValues().get(0).contains("phone = NULL"));
        assertTrue(sql.getAllValues().get(0).contains("email = NULL"));
        assertTrue(sql.getAllValues().get(0).contains("notes = NULL"));
        assertTrue(sql.getAllValues().get(0).contains("WHERE c.id = ?"));
        assertTrue(sql.getAllValues().get(0).contains("c.business_id = ?"));
        assertTrue(sql.getAllValues().get(0).contains("retention_legal_hold"));
        assertTrue(sql.getAllValues().get(0).contains("h.business_id = c.business_id"));
        assertTrue(sql.getAllValues().get(0).contains("h.target_type = 'CUSTOMER'"));
        assertTrue(sql.getAllValues().get(0).contains("h.target_id = c.id"));
        assertTrue(sql.getAllValues().get(0).contains("h.released_at IS NULL"));
        assertArrayEquals(new Object[]{customerId, businessId}, args.getAllValues().get(0));

        assertTrue(sql.getAllValues().get(1).contains("DELETE FROM customer_identity"));
        assertTrue(sql.getAllValues().get(1).contains("business_id = ?"));
        assertTrue(sql.getAllValues().get(1).contains("customer_id = ?"));
        assertArrayEquals(new Object[]{businessId, customerId}, args.getAllValues().get(1));

        verify(auditService).humanSuccess(
                businessId,
                "CUSTOMER_PROFILE_ANONYMIZE",
                "CUSTOMER",
                customerId);
    }

    @Test
    void refusesAnonymizationBeforeMutationWhenCustomerHasActiveLegalHold() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        AuditService auditService = mock(AuditService.class);

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(businessId), eq(customerId)))
                .thenReturn(true);

        CustomerProfileAnonymizationService service =
                new CustomerProfileAnonymizationService(jdbc, tenantProvider, auditService);

        assertThrows(
                ConflictException.class,
                () -> service.anonymizeCurrentTenantCustomer(customerId));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForObject(
                sql.capture(),
                eq(Boolean.class),
                eq(businessId),
                eq(customerId));
        assertTrue(sql.getValue().contains("retention_legal_hold"));
        assertTrue(sql.getValue().contains("h.business_id = ?"));
        assertTrue(sql.getValue().contains("h.target_type = 'CUSTOMER'"));
        assertTrue(sql.getValue().contains("h.target_id = ?"));
        assertTrue(sql.getValue().contains("h.released_at IS NULL"));

        verify(jdbc, never()).update(anyString(), any(Object[].class));
        verifyNoInteractions(auditService);
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

        verify(jdbc).queryForObject(
                anyString(),
                eq(Boolean.class),
                eq(businessId),
                eq(customerId));
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

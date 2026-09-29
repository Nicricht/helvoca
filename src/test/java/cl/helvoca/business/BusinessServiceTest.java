package cl.helvoca.business;

import cl.helvoca.audit.AuditService;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class BusinessServiceTest {
    @Test
    void currentBusinessIsResolvedFromAuthenticatedTenantNotFromClientInput() {
        UUID tenantId = UUID.randomUUID();
        BusinessRepository repository = mock(BusinessRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        AuditService auditService = mock(AuditService.class);

        Business business = new Business();
        business.setName("Tenant A");
        when(tenantProvider.requireBusinessId()).thenReturn(tenantId);
        when(repository.findById(tenantId)).thenReturn(Optional.of(business));

        BusinessService service = new BusinessService(repository, tenantProvider, auditService);
        BusinessResponse response = service.current();

        assertEquals("Tenant A", response.name());
        verify(repository).findById(tenantId);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void currentBusinessExposesCyanAsTheDefaultAppearanceTheme() {
        UUID tenantId = UUID.randomUUID();
        BusinessRepository repository = mock(BusinessRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        AuditService auditService = mock(AuditService.class);

        Business business = new Business();
        business.setName("Tenant A");
        when(tenantProvider.requireBusinessId()).thenReturn(tenantId);
        when(repository.findById(tenantId)).thenReturn(Optional.of(business));

        BusinessResponse response = new BusinessService(repository, tenantProvider, auditService).current();

        assertEquals("cyan", response.appearanceTheme());
    }

    @Test
    void updateAppearanceChangesOnlyTheAuthenticatedTenantAndAuditsTheChange() {
        UUID tenantId = UUID.randomUUID();
        BusinessRepository repository = mock(BusinessRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        AuditService auditService = mock(AuditService.class);

        Business business = new Business();
        business.setName("Tenant A");
        when(tenantProvider.requireBusinessId()).thenReturn(tenantId);
        when(repository.findById(tenantId)).thenReturn(Optional.of(business));

        BusinessAppearanceResponse response =
                new BusinessService(repository, tenantProvider, auditService).updateAppearance("violet");

        assertEquals("violet", response.theme());
        assertEquals("violet", business.getAppearanceTheme());
        verify(repository).findById(tenantId);
        verify(auditService).success(tenantId, "BUSINESS_APPEARANCE_UPDATE", "BUSINESS", tenantId);
    }

    @Test
    void updateAppearanceRejectsUnsupportedThemes() {
        UUID tenantId = UUID.randomUUID();
        BusinessRepository repository = mock(BusinessRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        AuditService auditService = mock(AuditService.class);

        Business business = new Business();
        business.setName("Tenant A");
        when(tenantProvider.requireBusinessId()).thenReturn(tenantId);
        when(repository.findById(tenantId)).thenReturn(Optional.of(business));

        BusinessService service = new BusinessService(repository, tenantProvider, auditService);

        assertThrows(IllegalArgumentException.class, () -> service.updateAppearance("rainbow"));
        assertEquals("cyan", business.getAppearanceTheme());
        verify(auditService, never()).success(any(), eq("BUSINESS_APPEARANCE_UPDATE"), any(), any());
    }
}

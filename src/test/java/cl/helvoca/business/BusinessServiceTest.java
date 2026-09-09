package cl.helvoca.business;

import cl.helvoca.audit.AuditService;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}

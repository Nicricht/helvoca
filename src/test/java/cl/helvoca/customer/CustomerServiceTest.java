package cl.helvoca.customer;

import cl.helvoca.audit.AuditService;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class CustomerServiceTest {
    @Test
    void listUsesAuthenticatedBusinessId() {
        UUID tenantId = UUID.randomUUID();
        CustomerRepository repository = mock(CustomerRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        AuditService auditService = mock(AuditService.class);

        Customer customer = new Customer();
        customer.setBusinessId(tenantId);
        customer.setName("Nico");

        when(tenantProvider.requireBusinessId()).thenReturn(tenantId);
        when(repository.findAllByBusinessIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(customer));

        CustomerService service = new CustomerService(repository, tenantProvider, auditService);
        var result = service.list();

        assertEquals(1, result.size());
        assertEquals("Nico", result.getFirst().name());
        verify(repository).findAllByBusinessIdOrderByCreatedAtDesc(tenantId);
        verifyNoMoreInteractions(repository);
    }
}

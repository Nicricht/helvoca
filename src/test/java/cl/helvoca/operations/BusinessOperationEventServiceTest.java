package cl.helvoca.operations;

import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class BusinessOperationEventServiceTest {

    @Test
    void recentAlwaysUsesAuthenticatedTenant() {
        UUID businessId = UUID.randomUUID();
        BusinessOperationEventRepository repository = mock(BusinessOperationEventRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(repository.findTop100ByBusinessIdOrderBySequenceNoDesc(businessId)).thenReturn(List.of());

        BusinessOperationEventService service = new BusinessOperationEventService(repository, tenantProvider);

        assertTrue(service.recent(null).isEmpty());
        verify(repository).findTop100ByBusinessIdOrderBySequenceNoDesc(businessId);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void operationFilterCannotEscapeTenantScope() {
        UUID businessId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        BusinessOperationEventRepository repository = mock(BusinessOperationEventRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(repository.findTop100ByBusinessIdAndOperationIdOrderBySequenceNoDesc(businessId, operationId))
                .thenReturn(List.of());

        BusinessOperationEventService service = new BusinessOperationEventService(repository, tenantProvider);

        assertTrue(service.recent(operationId).isEmpty());
        verify(repository).findTop100ByBusinessIdAndOperationIdOrderBySequenceNoDesc(businessId, operationId);
        verifyNoMoreInteractions(repository);
    }
}

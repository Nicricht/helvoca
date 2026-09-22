package cl.helvoca.request;

import cl.helvoca.operations.BusinessOperation;
import cl.helvoca.operations.BusinessOperationRepository;
import cl.helvoca.operations.UniversalOperationWorkflowService;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class BusinessRequestServiceTest {

    @Test
    void resolvedRequestCompletesUniversalOperation() {
        UUID businessId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();

        BusinessRequestRepository repository = mock(BusinessRequestRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        UniversalOperationWorkflowService universalOperations = mock(UniversalOperationWorkflowService.class);
        BusinessOperationRepository operations = mock(BusinessOperationRepository.class);
        BusinessRequest request = mock(BusinessRequest.class);

        BusinessOperation operation = new BusinessOperation();
        operation.setId(operationId);
        operation.setBusinessId(businessId);
        operation.setStatus(BusinessOperation.Status.CONFIRMED);
        operation.setRevision(7);
        operation.setMetadata(new LinkedHashMap<>());

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(repository.findByIdAndBusinessId(requestId, businessId)).thenReturn(Optional.of(request));
        when(repository.save(request)).thenReturn(request);
        when(request.getOperationId()).thenReturn(operationId);
        when(operations.findByIdAndBusinessId(operationId, businessId)).thenReturn(Optional.of(operation));

        BusinessRequestService service = new BusinessRequestService(
                repository,
                tenantProvider,
                universalOperations,
                operations);

        service.setStatus(requestId, RequestStatus.RESOLVED);

        verify(request).setStatus(RequestStatus.RESOLVED);
        verify(repository).save(request);
        verify(operations).save(operation);
        assertEquals(BusinessOperation.Status.COMPLETED, operation.getStatus());
        assertEquals(8, operation.getRevision());
        assertEquals("RESOLVED", operation.getMetadata().get("projectionStatus"));
    }
}

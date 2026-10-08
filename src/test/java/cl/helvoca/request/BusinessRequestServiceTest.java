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
        RequestLifecycleEventService lifecycleEvents = mock(RequestLifecycleEventService.class);

        BusinessOperation operation = new BusinessOperation();
        operation.setId(operationId);
        operation.setBusinessId(businessId);
        operation.setStatus(BusinessOperation.Status.CONFIRMED);
        operation.setRevision(7);
        operation.setMetadata(new LinkedHashMap<>());

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(repository.lockByIdAndBusinessId(requestId, businessId)).thenReturn(Optional.of(request));
        when(request.getId()).thenReturn(requestId);
        when(request.getStatus()).thenReturn(RequestStatus.OPEN);
        when(repository.saveAndFlush(request)).thenReturn(request);
        when(request.getOperationId()).thenReturn(operationId);
        when(operations.findByIdAndBusinessId(operationId, businessId)).thenReturn(Optional.of(operation));

        BusinessRequestService service = new BusinessRequestService(
                repository,
                tenantProvider,
                universalOperations,
                operations,
                lifecycleEvents);

        service.setStatus(requestId, RequestStatus.RESOLVED);

        verify(request).setStatus(RequestStatus.RESOLVED);
        verify(repository).saveAndFlush(request);
        verify(lifecycleEvents).recordBusinessUser(businessId, requestId, operationId,
                RequestStatus.OPEN, RequestStatus.RESOLVED);
        verify(operations).save(operation);
        assertEquals(BusinessOperation.Status.COMPLETED, operation.getStatus());
        assertEquals(8, operation.getRevision());
        assertEquals("RESOLVED", operation.getMetadata().get("projectionStatus"));
    }
    @Test
    void terminalStatusesCannotReopenAndDuplicateUpdatesDoNotWrite() {
        for (RequestStatus terminal : new RequestStatus[] {
                RequestStatus.RESOLVED, RequestStatus.CANCELLED
        }) {
            assertEquals(java.util.Set.of(), BusinessRequestService.allowedNext(terminal));
        }
        assertEquals(java.util.Set.of(RequestStatus.RESOLVED, RequestStatus.CANCELLED),
                BusinessRequestService.allowedNext(RequestStatus.IN_PROGRESS));
    }

    @Test
    void duplicateStatusReturnsWithoutSideEffects() {
        UUID businessId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        BusinessRequestRepository repository = mock(BusinessRequestRepository.class);
        TenantProvider tenants = mock(TenantProvider.class);
        BusinessRequest request = mock(BusinessRequest.class);
        when(tenants.requireBusinessId()).thenReturn(businessId);
        when(request.getStatus()).thenReturn(RequestStatus.IN_PROGRESS);
        when(repository.lockByIdAndBusinessId(id, businessId)).thenReturn(Optional.of(request));
        BusinessOperationRepository operations = mock(BusinessOperationRepository.class);
        RequestLifecycleEventService events = mock(RequestLifecycleEventService.class);
        BusinessRequestService service = new BusinessRequestService(repository, tenants,
                mock(UniversalOperationWorkflowService.class), operations, events);

        service.setStatus(id, RequestStatus.IN_PROGRESS);
        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(operations, events);
    }

    @Test
    void invalidTransitionCannotMutateDatabase() {
        UUID businessId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        BusinessRequestRepository repository = mock(BusinessRequestRepository.class);
        TenantProvider tenants = mock(TenantProvider.class);
        BusinessRequest request = mock(BusinessRequest.class);
        when(tenants.requireBusinessId()).thenReturn(businessId);
        when(request.getStatus()).thenReturn(RequestStatus.RESOLVED);
        when(repository.lockByIdAndBusinessId(id, businessId)).thenReturn(Optional.of(request));
        BusinessOperationRepository operations = mock(BusinessOperationRepository.class);
        RequestLifecycleEventService events = mock(RequestLifecycleEventService.class);
        BusinessRequestService service = new BusinessRequestService(repository, tenants,
                mock(UniversalOperationWorkflowService.class), operations, events);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.setStatus(id, RequestStatus.OPEN));
        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(operations, events);
    }

}

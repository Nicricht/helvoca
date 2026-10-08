package cl.helvoca.request;

import cl.helvoca.security.TenantDatabaseContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RequestCreationObservationDispatcherTest {
    private final RequestToolOutcomeObservationService observations =
            mock(RequestToolOutcomeObservationService.class);
    private final TenantDatabaseContext tenants = new TenantDatabaseContext();
    private final RequestCreationObservationDispatcher subject =
            new RequestCreationObservationDispatcher(observations, tenants);

    @AfterEach
    void cleanupTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void observationRunsOnlyAfterSuccessfulCommitInTheCorrectTenant() {
        UUID businessId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        subject.afterSuccessfulCommit(
                businessId, conversationId, RequestSource.AI_CALL, requestId, operationId);

        verifyNoInteractions(observations);
        List<TransactionSynchronization> callbacks = TransactionSynchronizationManager.getSynchronizations();
        assertEquals(1, callbacks.size());

        callbacks.getFirst().afterCommit();
        verify(observations).observeCreatedRequest(businessId, conversationId,
                RequestSource.AI_CALL, requestId, operationId);
        assertEquals(TenantDatabaseContext.Mode.DENIED, tenants.currentOrDenied().mode());
    }

    @Test
    void rollbackNeverRunsObservation() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        subject.afterSuccessfulCommit(
                UUID.randomUUID(), UUID.randomUUID(), RequestSource.AI_WHATSAPP,
                UUID.randomUUID(), UUID.randomUUID());

        for (TransactionSynchronization callback : TransactionSynchronizationManager.getSynchronizations()) {
            callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }
        verifyNoInteractions(observations);
    }

    @Test
    void missingTransactionNeverClaimsCommittedEvidence() {
        subject.afterSuccessfulCommit(
                UUID.randomUUID(), UUID.randomUUID(), RequestSource.AI_CALL,
                UUID.randomUUID(), UUID.randomUUID());
        verifyNoInteractions(observations);
        assertFalse(TransactionSynchronizationManager.isSynchronizationActive());
    }

    @Test
    void missingIdentifiersAndUnsupportedSourcesAreIgnored() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        subject.afterSuccessfulCommit(
                UUID.randomUUID(), UUID.randomUUID(), RequestSource.MANUAL,
                UUID.randomUUID(), UUID.randomUUID());
        subject.afterSuccessfulCommit(
                null, UUID.randomUUID(), RequestSource.AI_CALL,
                UUID.randomUUID(), UUID.randomUUID());
        assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty());
    }

    @Test
    void readOnlyObservationFailureCannotUndoCommittedRequest() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        UUID businessId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        doThrow(new IllegalStateException("simulated_metrics_failure"))
                .when(observations).observeCreatedRequest(
                        businessId, conversationId, RequestSource.AI_WHATSAPP,
                        requestId, operationId);
        subject.afterSuccessfulCommit(
                businessId, conversationId, RequestSource.AI_WHATSAPP,
                requestId, operationId);

        assertDoesNotThrow(() ->
                TransactionSynchronizationManager.getSynchronizations().getFirst().afterCommit());
        assertEquals(TenantDatabaseContext.Mode.DENIED, tenants.currentOrDenied().mode());
    }
}

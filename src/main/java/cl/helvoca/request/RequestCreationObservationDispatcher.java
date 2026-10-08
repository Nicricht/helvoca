package cl.helvoca.request;

import cl.helvoca.security.TenantDatabaseContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * Fire-and-forget local observation ONLY after the request transaction commits.
 * No provider sends, model calls, status changes or handoff creation occur here.
 */
@Component
public class RequestCreationObservationDispatcher {
    private static final Logger log = LoggerFactory.getLogger(RequestCreationObservationDispatcher.class);

    private final RequestToolOutcomeObservationService observations;
    private final TenantDatabaseContext databaseContext;

    public RequestCreationObservationDispatcher(RequestToolOutcomeObservationService observations,
                                                TenantDatabaseContext databaseContext) {
        this.observations = observations;
        this.databaseContext = databaseContext;
    }

    public void afterSuccessfulCommit(UUID businessId,
                                      UUID sourceReferenceId,
                                      RequestSource source,
                                      UUID requestId,
                                      UUID operationId) {
        if (businessId == null || sourceReferenceId == null || requestId == null || operationId == null
                || (source != RequestSource.AI_CALL && source != RequestSource.AI_WHATSAPP)) {
            return;
        }

        // A completed tool response is not proof of a committed database write.
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    databaseContext.runAsTenant(businessId, () ->
                            observations.observeCreatedRequest(
                                    businessId, sourceReferenceId, source, requestId, operationId));
                } catch (RuntimeException e) {
                    // The request is already committed. A read-only metrics/observation
                    // failure may never cause a second create_request or lie to the caller.
                    log.warn("REQUEST_CREATION_OBSERVATION_FAILED channel={} error={}",
                            source.name(), e.getClass().getSimpleName());
                }
            }
        });
    }
}

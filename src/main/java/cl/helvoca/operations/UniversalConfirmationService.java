package cl.helvoca.operations;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class UniversalConfirmationService {
    public enum Authorization {
        AUTHORIZED, IDEMPOTENT_REPLAY, STALE, EXPIRED, NOT_AWAITING, NOT_OWNED, NOT_FOUND
    }

    private final BusinessOperationRepository operations;
    private final OperationConfirmationRepository confirmations;
    private final EntityManager entityManager;

    public UniversalConfirmationService(BusinessOperationRepository operations,
                                        OperationConfirmationRepository confirmations,
                                        EntityManager entityManager) {
        this.operations = operations;
        this.confirmations = confirmations;
        this.entityManager = entityManager;
    }

    /**
     * Confirmation is tenant/customer scoped but deliberately not channel scoped.
     * This is what permits a proposal heard on VOICE to be confirmed later on
     * WHATSAPP by the same explicit customer identity.
     */
    @Transactional
    public Authorization authorize(UUID businessId,
                                   UUID operationId,
                                   UUID customerId,
                                   UUID sourceReferenceId,
                                   String trustedPhone,
                                   UUID token) {
        if (businessId == null || operationId == null) return Authorization.NOT_FOUND;
        BusinessOperation operation = operations.findByIdAndBusinessId(operationId, businessId).orElse(null);
        if (operation == null) return Authorization.NOT_FOUND;
        if (!ownedBy(operation, customerId, sourceReferenceId, trustedPhone)) return Authorization.NOT_OWNED;

        // A consumed token remains the durable idempotency key even if a downstream
        // workflow advances the operation revision after confirmation (for example,
        // payment execution/webhook processing). Resolve replay by exact token first,
        // while still requiring the token to belong to this tenant and operation.
        OperationConfirmation tokenConfirmation = token == null
                ? null
                : confirmations.findByBusinessIdAndToken(businessId, token).orElse(null);
        refreshFromDatabase(tokenConfirmation);
        if (tokenConfirmation != null
                && operationId.equals(tokenConfirmation.getOperationId())
                && tokenConfirmation.getState() == OperationConfirmation.State.CONSUMED
                && replayableStatus(operation.getStatus())) {
            return Authorization.IDEMPOTENT_REPLAY;
        }

        if (operation.getStatus() != BusinessOperation.Status.AWAITING_CONFIRMATION) {
            return Authorization.NOT_AWAITING;
        }

        OperationConfirmation confirmation = confirmations
                .findByBusinessIdAndOperationIdAndOperationRevision(
                        businessId, operationId, operation.getRevision() == null ? 1 : operation.getRevision())
                .orElse(null);
        refreshFromDatabase(confirmation);

        if (confirmation == null || token == null || !Objects.equals(token, confirmation.getToken())) {
            return Authorization.STALE;
        }
        if (confirmation.getState() == OperationConfirmation.State.EXPIRED) return Authorization.EXPIRED;
        if (confirmation.getState() != OperationConfirmation.State.AWAITING) return Authorization.STALE;
        if (confirmation.getExpiresAt() == null || !confirmation.getExpiresAt().isAfter(Instant.now())) {
            confirmation.setState(OperationConfirmation.State.EXPIRED);
            confirmation.setResolvedAt(Instant.now());
            confirmations.saveAndFlush(confirmation);
            return Authorization.EXPIRED;
        }

        // The database trigger owns the lifecycle transition that follows an
        // authorized confirmation. Do not keep an AWAITING entity pinned in the
        // persistence context, otherwise a same-transaction lookup can observe
        // stale state after the trigger consumes or expires that row.
        if (entityManager.contains(confirmation)) entityManager.detach(confirmation);
        return Authorization.AUTHORIZED;
    }

    @Transactional
    public void recordResolution(UUID businessId,
                                 UUID operationId,
                                 UUID token,
                                 BusinessOrder.Source channel,
                                 UUID sourceReferenceId) {
        if (businessId == null || operationId == null) return;
        OperationConfirmation confirmation = token == null
                ? confirmations.findFirstByBusinessIdAndOperationIdOrderByOperationRevisionDesc(businessId, operationId).orElse(null)
                : confirmations.findByBusinessIdAndToken(businessId, token).orElse(null);
        if (confirmation == null || !operationId.equals(confirmation.getOperationId())) return;

        // business_operation has an AFTER trigger that consumes/invalidates the
        // durable confirmation. Hibernate does not automatically observe a row
        // changed by a database trigger inside the same persistence context, so
        // reload it before deciding whether resolution metadata may be written.
        refreshFromDatabase(confirmation);
        if (confirmation.getState() != OperationConfirmation.State.CONSUMED) return;

        if (confirmation.getResolvedAt() == null) confirmation.setResolvedAt(Instant.now());
        if (confirmation.getResolvedChannel() == null) {
            confirmation.setResolvedChannel(channel == null ? BusinessOrder.Source.API : channel);
        }
        if (confirmation.getResolvedSourceReferenceId() == null && sourceReferenceId != null) {
            confirmation.setResolvedSourceReferenceId(sourceReferenceId);
        }
        confirmations.saveAndFlush(confirmation);
    }

    @Transactional(readOnly = true)
    public OperationConfirmation latest(UUID businessId, UUID operationId) {
        OperationConfirmation confirmation = confirmations
                .findFirstByBusinessIdAndOperationIdOrderByOperationRevisionDesc(businessId, operationId)
                .orElse(null);
        refreshFromDatabase(confirmation);
        return confirmation;
    }

    private void refreshFromDatabase(OperationConfirmation confirmation) {
        if (confirmation != null && entityManager.contains(confirmation)) {
            entityManager.refresh(confirmation);
        }
    }

    private static boolean replayableStatus(BusinessOperation.Status status) {
        return status == BusinessOperation.Status.CONFIRMED
                || status == BusinessOperation.Status.EXECUTING
                || status == BusinessOperation.Status.COMPLETED;
    }

    private static boolean ownedBy(BusinessOperation operation,
                                   UUID customerId,
                                   UUID sourceReferenceId,
                                   String trustedPhone) {
        // Once an operation has an explicit customer identity, never downgrade to
        // weaker source/phone matching. Cross-channel confirmation is allowed only
        // for that same customer. Fallback identifiers are reserved for operations
        // that genuinely have no customer binding.
        if (operation.getCustomerId() != null) {
            return customerId != null && customerId.equals(operation.getCustomerId());
        }
        if (sourceReferenceId != null && operation.getSourceReferenceId() != null
                && sourceReferenceId.equals(operation.getSourceReferenceId())) return true;
        return trustedPhone != null && !trustedPhone.isBlank()
                && operation.getContactPhone() != null
                && trustedPhone.trim().equals(operation.getContactPhone().trim());
    }
}

package cl.helvoca.operations;

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

    public UniversalConfirmationService(BusinessOperationRepository operations,
                                        OperationConfirmationRepository confirmations) {
        this.operations = operations;
        this.confirmations = confirmations;
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

        OperationConfirmation confirmation = confirmations
                .findByBusinessIdAndOperationIdAndOperationRevision(
                        businessId, operationId, operation.getRevision() == null ? 1 : operation.getRevision())
                .orElse(null);

        if (operation.getStatus() == BusinessOperation.Status.CONFIRMED
                && confirmation != null
                && confirmation.getState() == OperationConfirmation.State.CONSUMED
                && token != null
                && Objects.equals(token, confirmation.getToken())) {
            return Authorization.IDEMPOTENT_REPLAY;
        }

        if (operation.getStatus() != BusinessOperation.Status.AWAITING_CONFIRMATION) {
            return Authorization.NOT_AWAITING;
        }
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
        return confirmations.findFirstByBusinessIdAndOperationIdOrderByOperationRevisionDesc(businessId, operationId)
                .orElse(null);
    }

    private static boolean ownedBy(BusinessOperation operation,
                                   UUID customerId,
                                   UUID sourceReferenceId,
                                   String trustedPhone) {
        if (customerId != null && operation.getCustomerId() != null) {
            return customerId.equals(operation.getCustomerId());
        }
        if (sourceReferenceId != null && operation.getSourceReferenceId() != null
                && sourceReferenceId.equals(operation.getSourceReferenceId())) return true;
        return trustedPhone != null && !trustedPhone.isBlank()
                && operation.getContactPhone() != null
                && trustedPhone.trim().equals(operation.getContactPhone().trim());
    }
}

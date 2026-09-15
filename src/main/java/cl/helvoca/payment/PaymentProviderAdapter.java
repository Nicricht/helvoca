package cl.helvoca.payment;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Provider boundary for merchant/customer payments.
 *
 * Implementations own provider credentials, signature verification and remote
 * API semantics. The universal payment domain never handles card credentials.
 */
public interface PaymentProviderAdapter {
    String providerCode();

    /** Returns true only when this adapter is configured for the tenant. */
    boolean supports(UUID businessId);

    CreateResult create(CreateCommand command);

    StatusResult getStatus(StatusCommand command);

    CancelResult cancel(CancelCommand command);

    record CreateCommand(
            UUID businessId,
            UUID paymentOperationId,
            UUID targetOperationId,
            BigDecimal amount,
            String currency,
            String idempotencyKey,
            String contactPhone,
            Map<String, Object> metadata) {}

    record CreateResult(
            String externalId,
            String checkoutUrl,
            BusinessPayment.Status status,
            Map<String, Object> metadata) {}

    record StatusCommand(
            UUID businessId,
            String externalId,
            String idempotencyKey) {}

    record StatusResult(
            BusinessPayment.Status status,
            Map<String, Object> metadata) {}

    record CancelCommand(
            UUID businessId,
            String externalId,
            String idempotencyKey) {}

    record CancelResult(
            BusinessPayment.Status status,
            Map<String, Object> metadata) {}
}

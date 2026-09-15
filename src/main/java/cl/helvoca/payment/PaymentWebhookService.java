package cl.helvoca.payment;

import cl.helvoca.operations.BusinessOperation;
import cl.helvoca.operations.BusinessOperationRepository;
import cl.helvoca.operations.ConversationStateService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentWebhookService {
    public enum Result { PROCESSED, DUPLICATE, IGNORED, FAILED }

    private final PaymentWebhookEventRepository events;
    private final BusinessPaymentRepository payments;
    private final BusinessOperationRepository operations;
    private final PaymentProviderRegistry providers;
    private final ConversationStateService conversationState;

    public PaymentWebhookService(PaymentWebhookEventRepository events,
                                 BusinessPaymentRepository payments,
                                 BusinessOperationRepository operations,
                                 PaymentProviderRegistry providers,
                                 ConversationStateService conversationState) {
        this.events = events;
        this.payments = payments;
        this.operations = operations;
        this.providers = providers;
        this.conversationState = conversationState;
    }

    @Transactional
    public Result processVerified(UUID businessId,
                                  String providerCode,
                                  String eventId,
                                  String externalId,
                                  String rawBody) {
        PaymentWebhookEvent existing = events
                .findByBusinessIdAndProviderAndEventId(businessId, providerCode, eventId)
                .orElse(null);
        if (existing != null && existing.getStatus() != PaymentWebhookEvent.Status.FAILED) {
            return Result.DUPLICATE;
        }

        PaymentWebhookEvent event = existing == null ? new PaymentWebhookEvent() : existing;
        event.setBusinessId(businessId);
        event.setProvider(providerCode);
        event.setEventId(eventId);
        event.setExternalId(externalId);
        event.setPayloadHash(sha256(rawBody));
        event.setStatus(PaymentWebhookEvent.Status.RECEIVED);
        try {
            event = events.saveAndFlush(event);
        } catch (DataIntegrityViolationException duplicate) {
            return Result.DUPLICATE;
        }

        BusinessPayment payment = payments
                .findByBusinessIdAndProviderIgnoreCaseAndExternalId(businessId, providerCode, externalId)
                .orElse(null);
        if (payment == null) {
            event.setStatus(PaymentWebhookEvent.Status.IGNORED);
            event.setProcessedAt(Instant.now());
            events.saveAndFlush(event);
            return Result.IGNORED;
        }

        PaymentProviderAdapter provider = providers.byCode(businessId, providerCode).orElse(null);
        if (provider == null) {
            event.setStatus(PaymentWebhookEvent.Status.FAILED);
            event.setProcessedAt(Instant.now());
            events.saveAndFlush(event);
            return Result.FAILED;
        }

        try {
            PaymentProviderAdapter.StatusResult result = provider.getStatus(
                    new PaymentProviderAdapter.StatusCommand(
                            businessId,
                            payment.getExternalId(),
                            payment.getIdempotencyKey()));
            if (result == null || result.status() == null) {
                throw new IllegalStateException("Provider returned no verified status.");
            }

            payment.setStatus(result.status());
            payment.setMetadata(merge(payment.getMetadata(), result.metadata()));
            payment = payments.saveAndFlush(payment);
            syncUniversalOperation(payment);
            syncConversation(payment);

            event.setStatus(PaymentWebhookEvent.Status.PROCESSED);
            event.setProcessedAt(Instant.now());
            events.saveAndFlush(event);
            return Result.PROCESSED;
        } catch (Exception e) {
            event.setStatus(PaymentWebhookEvent.Status.FAILED);
            event.setProcessedAt(Instant.now());
            events.saveAndFlush(event);
            return Result.FAILED;
        }
    }

    private void syncUniversalOperation(BusinessPayment payment) {
        BusinessOperation operation = operations
                .findByIdAndBusinessId(payment.getOperationId(), payment.getBusinessId())
                .orElse(null);
        if (operation == null || operation.getType() != BusinessOperation.Type.PAYMENT) return;

        BusinessOperation.Status next = operationStatus(payment.getStatus());
        if (operation.getStatus() != next) {
            operation.setStatus(next);
            operation.setRevision(operation.getRevision() == null ? 1 : operation.getRevision() + 1);
        }
        operation.setConfirmationToken(null);
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (operation.getMetadata() != null) metadata.putAll(operation.getMetadata());
        metadata.put("paymentId", payment.getId().toString());
        metadata.put("paymentStatus", payment.getStatus().name());
        metadata.put("provider", payment.getProvider());
        metadata.put("paymentPending", paymentPending(payment.getStatus()));
        metadata.put("confirmationPending", false);
        operation.setMetadata(metadata);
        operations.saveAndFlush(operation);
    }

    private void syncConversation(BusinessPayment payment) {
        if (payment.getSourceReferenceId() == null) return;
        BusinessOperation operation = operations
                .findByIdAndBusinessId(payment.getOperationId(), payment.getBusinessId())
                .orElse(null);
        if (operation == null) return;

        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("intent", "PAYMENT");
        patch.put("lastTool", "payment_webhook");
        patch.put("operationId", operation.getId().toString());
        patch.put("operationType", "PAYMENT");
        patch.put("operationStatus", operation.getStatus().name());
        patch.put("operationRevision", operation.getRevision());
        patch.put("paymentId", payment.getId().toString());
        patch.put("paymentStatus", payment.getStatus().name());
        patch.put("provider", payment.getProvider());
        patch.put("paymentPending", paymentPending(payment.getStatus()));
        patch.put("confirmationPending", false);
        conversationState.apply(
                payment.getBusinessId(),
                payment.getSourceReferenceId(),
                payment.getSource(),
                operation.getId(),
                patch);
    }

    private static BusinessOperation.Status operationStatus(BusinessPayment.Status status) {
        if (status == null) return BusinessOperation.Status.FAILED;
        return switch (status) {
            case FAILED -> BusinessOperation.Status.FAILED;
            case CANCELLED, EXPIRED -> BusinessOperation.Status.CANCELLED;
            case REQUIRES_ACTION, PENDING, SUCCEEDED, REFUNDED -> BusinessOperation.Status.CONFIRMED;
        };
    }

    private static boolean paymentPending(BusinessPayment.Status status) {
        return status == BusinessPayment.Status.REQUIRES_ACTION || status == BusinessPayment.Status.PENDING;
    }

    private static Map<String, Object> merge(Map<String, Object> current, Map<String, Object> patch) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (current != null) merged.putAll(current);
        if (patch != null) merged.putAll(patch);
        return merged.isEmpty() ? null : merged;
    }

    private static String sha256(String body) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest((body == null ? "" : body).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            return null;
        }
    }
}

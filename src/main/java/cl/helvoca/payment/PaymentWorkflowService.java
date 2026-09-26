package cl.helvoca.payment;

import cl.helvoca.operations.BusinessOperation;
import cl.helvoca.operations.BusinessOperationRepository;
import cl.helvoca.operations.BusinessOrder;
import cl.helvoca.operations.ConversationStateService;
import cl.helvoca.operations.OperationPolicyService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class PaymentWorkflowService {
    private static final Logger log = LoggerFactory.getLogger(PaymentWorkflowService.class);
    private final BusinessOperationRepository operations;
    private final BusinessPaymentRepository payments;
    private final PaymentProviderRegistry providers;
    private final OperationPolicyService policies;
    private final ConversationStateService conversationState;

    public PaymentWorkflowService(BusinessOperationRepository operations,
                                  BusinessPaymentRepository payments,
                                  PaymentProviderRegistry providers,
                                  OperationPolicyService policies,
                                  ConversationStateService conversationState) {
        this.operations = operations;
        this.payments = payments;
        this.providers = providers;
        this.policies = policies;
        this.conversationState = conversationState;
    }

    @Transactional
    public JSONObject quote(UUID businessId,
                            UUID customerId,
                            UUID sourceReferenceId,
                            String trustedPhone,
                            BusinessOrder.Source source,
                            JSONObject args) {
        if (!hasVerifiedContext(customerId, sourceReferenceId, trustedPhone)) {
            return error("CUSTOMER_CONTEXT_REQUIRED", "No puedo verificar al cliente que solicita el pago.");
        }

        UUID targetOperationId = uuid(required(args, "targetOperationId"));
        Calculation calculation;
        try {
            calculation = calculate(businessId, customerId, sourceReferenceId, trustedPhone, targetOperationId);
        } catch (PaymentRuleException e) {
            return error(e.code, e.getMessage());
        }

        BusinessOrder.Source safeSource = source == null ? BusinessOrder.Source.API : source;
        OperationPolicyService.Policy policy = policies.resolve(BusinessOperation.Type.PAYMENT);

        BusinessOperation operation = new BusinessOperation();
        operation.setBusinessId(businessId);
        operation.setCustomerId(customerId != null ? customerId : calculation.target().getCustomerId());
        operation.setSourceReferenceId(sourceReferenceId);
        operation.setType(BusinessOperation.Type.PAYMENT);
        operation.setSource(safeSource);
        operation.setRevision(1);
        operation.setContactName(calculation.target().getContactName());
        operation.setContactPhone(!blank(trustedPhone) ? trustedPhone.trim() : calculation.target().getContactPhone());
        operation.setSubtotal(null);
        operation.setDeliveryFee(null);
        operation.setTotal(calculation.amount());
        operation.setCurrency(calculation.currency());
        operation.setStatus(policy.requiresExplicitConfirmation()
                ? BusinessOperation.Status.AWAITING_CONFIRMATION
                : BusinessOperation.Status.CONFIRMED);
        operation.setConfirmationToken(policy.requiresExplicitConfirmation() ? UUID.randomUUID() : null);
        operation.setMetadata(operationMetadata(calculation, policy.requiresExplicitConfirmation(), null));
        operation = operations.saveAndFlush(operation);

        recordConversation(businessId, sourceReferenceId, safeSource, operation, "quote_payment", null);
        return success(quoteData(operation, calculation));
    }

    @Transactional
    public JSONObject update(UUID businessId,
                             UUID customerId,
                             UUID sourceReferenceId,
                             String trustedPhone,
                             BusinessOrder.Source source,
                             JSONObject args) {
        UUID operationId = uuid(required(args, "operationId"));
        BusinessOperation operation = requireEditableOperation(
                businessId, operationId, customerId, sourceReferenceId, trustedPhone);
        if (operation == null) {
            return error("PAYMENT_OPERATION_NOT_FOUND", "No encuentro ese borrador de pago para el cliente actual.");
        }

        UUID targetOperationId = uuid(required(args, "targetOperationId"));
        Calculation calculation;
        try {
            calculation = calculate(businessId, customerId, sourceReferenceId, trustedPhone, targetOperationId);
        } catch (PaymentRuleException e) {
            return error(e.code, e.getMessage());
        }

        operation.setCustomerId(customerId != null ? customerId : calculation.target().getCustomerId());
        if (sourceReferenceId != null) operation.setSourceReferenceId(sourceReferenceId);
        if (!blank(trustedPhone)) operation.setContactPhone(trustedPhone.trim());
        if (source != null) operation.setSource(source);
        operation.setContactName(calculation.target().getContactName());
        operation.setTotal(calculation.amount());
        operation.setCurrency(calculation.currency());
        operation.setStatus(BusinessOperation.Status.AWAITING_CONFIRMATION);
        operation.setRevision(operation.getRevision() == null ? 1 : operation.getRevision() + 1);
        operation.setConfirmationToken(UUID.randomUUID());
        operation.setMetadata(operationMetadata(calculation, true, null));
        operation = operations.saveAndFlush(operation);

        recordConversation(
                businessId,
                sourceReferenceId != null ? sourceReferenceId : operation.getSourceReferenceId(),
                source == null ? operation.getSource() : source,
                operation,
                "update_payment",
                null);
        return success(quoteData(operation, calculation));
    }

    @Transactional
    public JSONObject confirm(UUID businessId,
                              UUID customerId,
                              UUID sourceReferenceId,
                              String trustedPhone,
                              BusinessOrder.Source source,
                              JSONObject args) {
        if (!hasVerifiedContext(customerId, sourceReferenceId, trustedPhone)) {
            return error("CUSTOMER_CONTEXT_REQUIRED", "No puedo verificar al cliente que solicita el pago.");
        }

        UUID operationId = uuid(required(args, "operationId"));
        BusinessPayment existing = payments.findByOperationIdAndBusinessId(operationId, businessId).orElse(null);
        if (existing != null) {
            if (!paymentOwnedBy(existing, customerId, sourceReferenceId, trustedPhone)) {
                return error("PAYMENT_NOT_FOUND", "No encuentro ese pago entre los pagos del cliente actual.");
            }
            BusinessOperation operation = operations.findByIdAndBusinessId(operationId, businessId).orElse(null);
            if (operation != null) {
                recordConversation(
                        businessId,
                        sourceReferenceId != null ? sourceReferenceId : existing.getSourceReferenceId(),
                        source == null ? existing.getSource() : source,
                        operation,
                        "create_payment",
                        existing);
            }
            return success(paymentData(existing).put("idempotentReplay", true));
        }

        BusinessOperation operation = operations.findByIdAndBusinessId(operationId, businessId).orElse(null);
        if (operation == null
                || operation.getType() != BusinessOperation.Type.PAYMENT
                || !operationOwnedBy(operation, customerId, sourceReferenceId, trustedPhone)) {
            return error("PAYMENT_OPERATION_NOT_FOUND", "No encuentro ese borrador de pago para el cliente actual.");
        }
        if (operation.getStatus() != BusinessOperation.Status.AWAITING_CONFIRMATION) {
            return error("PAYMENT_NOT_AWAITING_CONFIRMATION", "El pago ya no está esperando confirmación.");
        }

        UUID providedToken = uuid(required(args, "confirmationToken"));
        if (operation.getConfirmationToken() == null || !operation.getConfirmationToken().equals(providedToken)) {
            return error("STALE_PAYMENT_CONFIRMATION",
                    "La confirmación ya no corresponde a la versión más reciente del pago. Vuelve a presentar el monto vigente.");
        }

        UUID targetOperationId = metadataUuid(operation, "targetOperationId");
        if (targetOperationId == null) {
            return error("PAYMENT_TARGET_NOT_FOUND", "El borrador de pago no conserva una operación de origen válida.");
        }

        Calculation recalculated;
        try {
            recalculated = calculate(businessId, customerId, sourceReferenceId, trustedPhone, targetOperationId);
        } catch (PaymentRuleException e) {
            return error(e.code, e.getMessage());
        }

        if (paymentTermsChanged(operation, recalculated)) {
            operation.setTotal(recalculated.amount());
            operation.setCurrency(recalculated.currency());
            operation.setRevision(operation.getRevision() == null ? 1 : operation.getRevision() + 1);
            operation.setConfirmationToken(UUID.randomUUID());
            operation.setStatus(BusinessOperation.Status.AWAITING_CONFIRMATION);
            operation.setMetadata(operationMetadata(recalculated, true, null));
            operation = operations.saveAndFlush(operation);
            recordConversation(
                    businessId,
                    sourceReferenceId != null ? sourceReferenceId : operation.getSourceReferenceId(),
                    source == null ? operation.getSource() : source,
                    operation,
                    "create_payment",
                    null);
            return errorWithData(
                    "PAYMENT_TERMS_CHANGED",
                    "El monto o la moneda cambió. Presenta las condiciones nuevas y solicita una nueva confirmación.",
                    quoteData(operation, recalculated));
        }

        PaymentProviderAdapter provider = providers.resolve(businessId).orElse(null);
        if (provider == null || blank(provider.providerCode())) {
            return error("PAYMENT_PROVIDER_UNAVAILABLE",
                    "Este negocio todavía no tiene un proveedor de pagos comerciales configurado.");
        }

        String idempotencyKey = providerIdempotencyKey(operation);
        PaymentProviderAdapter.CreateResult providerResult;
        try {
            providerResult = provider.create(new PaymentProviderAdapter.CreateCommand(
                    businessId,
                    operation.getId(),
                    recalculated.target().getId(),
                    recalculated.amount(),
                    recalculated.currency(),
                    idempotencyKey,
                    !blank(trustedPhone) ? trustedPhone.trim() : operation.getContactPhone(),
                    Map.of(
                            "paymentOperationId", operation.getId().toString(),
                            "targetOperationId", recalculated.target().getId().toString())));
        } catch (Exception e) {
            log.warn(
                    "PAYMENT_PROVIDER_CREATE_FAILED businessId={} operationId={} provider={} reason={} message={}",
                    businessId,
                    operation.getId(),
                    provider.providerCode(),
                    e.getClass().getSimpleName(),
                    e.getMessage() == null ? "" : e.getMessage());
            return error("PAYMENT_PROVIDER_FAILED",
                    "El proveedor de pagos no pudo crear la intención. No se confirmó ningún cobro.");
        }

        if (providerResult == null || providerResult.status() == null || blank(providerResult.externalId())) {
            return error("PAYMENT_PROVIDER_FAILED",
                    "El proveedor de pagos devolvió una respuesta incompleta. No se confirmó ningún cobro.");
        }

        BusinessPayment payment = new BusinessPayment();
        payment.setOperationId(operation.getId());
        payment.setBusinessId(businessId);
        payment.setCustomerId(customerId != null ? customerId : operation.getCustomerId());
        payment.setSourceReferenceId(sourceReferenceId != null ? sourceReferenceId : operation.getSourceReferenceId());
        payment.setTargetOperationId(recalculated.target().getId());
        payment.setContactPhone(!blank(trustedPhone) ? trustedPhone.trim() : operation.getContactPhone());
        payment.setProvider(provider.providerCode().trim());
        payment.setExternalId(providerResult.externalId().trim());
        payment.setIdempotencyKey(idempotencyKey);
        payment.setAmount(recalculated.amount());
        payment.setCurrency(recalculated.currency());
        payment.setStatus(providerResult.status());
        payment.setCheckoutUrl(blank(providerResult.checkoutUrl()) ? null : providerResult.checkoutUrl());
        payment.setSource(source == null ? operation.getSource() : source);
        payment.setMetadata(providerResult.metadata());
        payment = payments.saveAndFlush(payment);

        operation.setStatus(operationStatus(payment.getStatus()));
        operation.setConfirmationToken(null);
        operation.setMetadata(operationMetadata(recalculated, false, payment));
        operation = operations.saveAndFlush(operation);

        recordConversation(
                businessId,
                sourceReferenceId != null ? sourceReferenceId : operation.getSourceReferenceId(),
                source == null ? operation.getSource() : source,
                operation,
                "create_payment",
                payment);

        return success(paymentData(payment)
                .put("operationRevision", operation.getRevision())
                .put("idempotentReplay", false));
    }

    @Transactional
    public JSONObject status(UUID businessId,
                             UUID customerId,
                             UUID sourceReferenceId,
                             String trustedPhone,
                             BusinessOrder.Source source,
                             JSONObject args) {
        String paymentIdRaw = optional(args, "paymentId");
        if (!blank(paymentIdRaw)) {
            BusinessPayment payment = payments.findByIdAndBusinessId(uuid(paymentIdRaw), businessId).orElse(null);
            if (payment == null || !paymentOwnedBy(payment, customerId, sourceReferenceId, trustedPhone)) {
                return error("PAYMENT_NOT_FOUND", "No encuentro ese pago entre los pagos del cliente actual.");
            }
            refreshFromProvider(payment, businessId);
            payment = payments.findByIdAndBusinessId(payment.getId(), businessId).orElse(payment);
            syncOperation(payment, businessId);
            BusinessOperation operation = operations.findByIdAndBusinessId(payment.getOperationId(), businessId).orElse(null);
            if (operation != null) {
                recordConversation(
                        businessId,
                        sourceReferenceId != null ? sourceReferenceId : payment.getSourceReferenceId(),
                        source == null ? payment.getSource() : source,
                        operation,
                        "get_payment_status",
                        payment);
            }
            return success(paymentData(payment));
        }

        List<BusinessPayment> recent;
        if (customerId != null) {
            recent = payments.findTop5ByBusinessIdAndCustomerIdOrderByCreatedAtDesc(businessId, customerId);
        } else if (!blank(trustedPhone)) {
            recent = payments.findTop5ByBusinessIdAndContactPhoneOrderByCreatedAtDesc(businessId, trustedPhone.trim());
        } else if (sourceReferenceId != null) {
            recent = payments.findTop5ByBusinessIdAndSourceReferenceIdOrderByCreatedAtDesc(businessId, sourceReferenceId);
        } else {
            return error("CUSTOMER_CONTEXT_REQUIRED", "No puedo verificar qué pagos pertenecen al cliente actual.");
        }

        JSONArray out = new JSONArray();
        for (BusinessPayment payment : recent) out.put(paymentData(payment));
        return success(new JSONObject().put("payments", out));
    }

    @Transactional
    public int reconcilePending(UUID businessId) {
        if (businessId == null) return 0;
        List<BusinessPayment> pending = payments.findTop50ByBusinessIdAndStatusInOrderByUpdatedAtAsc(
                businessId,
                List.of(BusinessPayment.Status.REQUIRES_ACTION, BusinessPayment.Status.PENDING));
        int changed = 0;
        for (BusinessPayment payment : pending) {
            BusinessPayment.Status before = payment.getStatus();
            String beforeExternalId = payment.getExternalId();
            refreshFromProvider(payment, businessId);
            BusinessPayment refreshed = payments.findByIdAndBusinessId(payment.getId(), businessId)
                    .orElse(payment);
            syncOperation(refreshed, businessId);
            if (refreshed.getStatus() != before
                    || !Objects.equals(beforeExternalId, refreshed.getExternalId())) {
                changed++;
            }
        }
        return changed;
    }

    @Transactional
    public JSONObject cancel(UUID businessId,
                             UUID customerId,
                             UUID sourceReferenceId,
                             String trustedPhone,
                             BusinessOrder.Source source,
                             JSONObject args) {
        UUID paymentId = uuid(required(args, "paymentId"));
        BusinessPayment payment = payments.findByIdAndBusinessId(paymentId, businessId).orElse(null);
        if (payment == null || !paymentOwnedBy(payment, customerId, sourceReferenceId, trustedPhone)) {
            return error("PAYMENT_NOT_FOUND", "No encuentro ese pago entre los pagos del cliente actual.");
        }
        if (payment.getStatus() == BusinessPayment.Status.CANCELLED) {
            return success(paymentData(payment));
        }
        if (payment.getStatus() != BusinessPayment.Status.REQUIRES_ACTION
                && payment.getStatus() != BusinessPayment.Status.PENDING) {
            return error("PAYMENT_CANNOT_BE_CANCELLED",
                    "Ese pago ya no admite cancelación automática y requiere revisión humana si corresponde.");
        }

        PaymentProviderAdapter provider = providers.byCode(businessId, payment.getProvider()).orElse(null);
        if (provider == null) {
            return error("PAYMENT_PROVIDER_UNAVAILABLE",
                    "El proveedor asociado al pago no está disponible para este negocio.");
        }

        PaymentProviderAdapter.CancelResult result;
        try {
            result = provider.cancel(new PaymentProviderAdapter.CancelCommand(
                    businessId, payment.getExternalId(), payment.getIdempotencyKey()));
        } catch (Exception e) {
            return error("PAYMENT_PROVIDER_FAILED", "El proveedor de pagos no pudo cancelar la intención.");
        }
        if (result == null || result.status() == null) {
            return error("PAYMENT_PROVIDER_FAILED",
                    "El proveedor de pagos devolvió una respuesta incompleta al cancelar.");
        }

        payment.setStatus(result.status());
        payment.setMetadata(mergeMetadata(payment.getMetadata(), result.metadata()));
        payment = payments.saveAndFlush(payment);
        syncOperation(payment, businessId);

        BusinessOperation operation = operations.findByIdAndBusinessId(payment.getOperationId(), businessId).orElse(null);
        if (operation != null) {
            recordConversation(
                    businessId,
                    sourceReferenceId != null ? sourceReferenceId : payment.getSourceReferenceId(),
                    source == null ? payment.getSource() : source,
                    operation,
                    "cancel_payment",
                    payment);
        }
        return success(paymentData(payment));
    }

    private void refreshFromProvider(BusinessPayment payment, UUID businessId) {
        if (payment == null || terminal(payment.getStatus()) || blank(payment.getExternalId())) return;
        PaymentProviderAdapter provider = providers.byCode(businessId, payment.getProvider()).orElse(null);
        if (provider == null) return;
        try {
            PaymentProviderAdapter.StatusResult result = provider.getStatus(
                    new PaymentProviderAdapter.StatusCommand(
                            businessId, payment.getExternalId(), payment.getIdempotencyKey()));
            if (result != null && result.status() != null) {
                payment.setStatus(result.status());
                payment.setMetadata(mergeMetadata(payment.getMetadata(), result.metadata()));
                payments.saveAndFlush(payment);
            }
        } catch (Exception e) {
            if (providerOrderMissing(e)) {
                recoverMissingProviderOrder(payment, businessId, provider);
            }
            // Other status read failures degrade to the last provider-verified state persisted locally.
        }
    }

    private void recoverMissingProviderOrder(BusinessPayment payment,
                                             UUID businessId,
                                             PaymentProviderAdapter provider) {
        if (payment == null
                || (payment.getStatus() != BusinessPayment.Status.REQUIRES_ACTION
                && payment.getStatus() != BusinessPayment.Status.PENDING)
                || payment.getOperationId() == null
                || payment.getTargetOperationId() == null
                || payment.getAmount() == null
                || payment.getAmount().compareTo(BigDecimal.ZERO) <= 0
                || blank(payment.getCurrency())
                || blank(payment.getExternalId())) {
            return;
        }

        String previousExternalId = payment.getExternalId();
        String recoveryKey = providerRecoveryIdempotencyKey(payment);
        try {
            PaymentProviderAdapter.CreateResult recovered = provider.create(
                    new PaymentProviderAdapter.CreateCommand(
                            businessId,
                            payment.getOperationId(),
                            payment.getTargetOperationId(),
                            payment.getAmount(),
                            payment.getCurrency(),
                            recoveryKey,
                            payment.getContactPhone(),
                            Map.of(
                                    "paymentOperationId", payment.getOperationId().toString(),
                                    "targetOperationId", payment.getTargetOperationId().toString(),
                                    "providerRecovery", true,
                                    "previousExternalId", previousExternalId)));

            if (recovered == null
                    || recovered.status() == null
                    || blank(recovered.externalId())) {
                log.warn(
                        "PAYMENT_PROVIDER_RECOVERY_INCOMPLETE businessId={} paymentId={} operationId={} provider={}",
                        businessId,
                        payment.getId(),
                        payment.getOperationId(),
                        provider.providerCode());
                return;
            }

            payment.setExternalId(recovered.externalId().trim());
            payment.setCheckoutUrl(blank(recovered.checkoutUrl()) ? null : recovered.checkoutUrl());
            payment.setIdempotencyKey(recoveryKey);
            payment.setStatus(recovered.status());

            Map<String, Object> recoveryMetadata = new LinkedHashMap<>();
            if (recovered.metadata() != null) recoveryMetadata.putAll(recovered.metadata());
            recoveryMetadata.put("recoveredFromExternalId", previousExternalId);
            recoveryMetadata.put("providerRecovery", true);
            payment.setMetadata(mergeMetadata(payment.getMetadata(), recoveryMetadata));
            payments.saveAndFlush(payment);

            log.info(
                    "PAYMENT_PROVIDER_ORDER_RECOVERED businessId={} paymentId={} operationId={} provider={} previousExternalId={} externalId={} status={}",
                    businessId,
                    payment.getId(),
                    payment.getOperationId(),
                    provider.providerCode(),
                    previousExternalId,
                    payment.getExternalId(),
                    payment.getStatus());
        } catch (Exception recoveryError) {
            log.warn(
                    "PAYMENT_PROVIDER_RECOVERY_FAILED businessId={} paymentId={} operationId={} provider={} reason={} message={}",
                    businessId,
                    payment.getId(),
                    payment.getOperationId(),
                    provider.providerCode(),
                    recoveryError.getClass().getSimpleName(),
                    recoveryError.getMessage() == null ? "" : recoveryError.getMessage());
        }
    }

    static boolean providerOrderMissing(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.toLowerCase(java.util.Locale.ROOT).contains("order_not_found")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    static String providerRecoveryIdempotencyKey(BusinessPayment payment) {
        if (payment == null || payment.getId() == null || blank(payment.getExternalId())) {
            throw new IllegalArgumentException("Payment id and external id are required for provider recovery.");
        }
        String material = "payment-provider-recovery:"
                + payment.getId()
                + ":"
                + payment.getExternalId().trim();
        return UUID.nameUUIDFromBytes(material.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private void syncOperation(BusinessPayment payment, UUID businessId) {
        BusinessOperation operation = operations.findByIdAndBusinessId(payment.getOperationId(), businessId).orElse(null);
        if (operation == null || operation.getType() != BusinessOperation.Type.PAYMENT) return;

        BusinessOperation.Status next = operationStatus(payment.getStatus());
        boolean changed = operation.getStatus() != next;
        if (changed) {
            operation.setStatus(next);
        }
        operation.setConfirmationToken(null);
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (operation.getMetadata() != null) metadata.putAll(operation.getMetadata());
        changed |= putIfChanged(metadata, "paymentStatus", payment.getStatus().name());
        changed |= putIfChanged(metadata, "paymentId", payment.getId().toString());
        changed |= putIfChanged(metadata, "provider", payment.getProvider());
        changed |= putIfChanged(metadata, "paymentPending", paymentPending(payment.getStatus()));
        changed |= putIfChanged(metadata, "confirmationPending", false);
        operation.setMetadata(metadata);
        if (changed) {
            operation.setRevision(operation.getRevision() == null ? 1 : operation.getRevision() + 1);
            operations.saveAndFlush(operation);
        }
        syncCommercialJourney(payment, businessId, operation);
    }

    private void syncCommercialJourney(BusinessPayment payment,
                                       UUID businessId,
                                       BusinessOperation paymentOperation) {
        if (payment == null || paymentOperation == null || paymentOperation.getMetadata() == null) return;
        UUID journeyId = metadataUuid(paymentOperation, "commercialJourneyOperationId");
        if (journeyId == null) return;

        BusinessOperation journey = operations.findByIdAndBusinessId(journeyId, businessId).orElse(null);
        if (journey == null) return;
        if (payment.getCustomerId() != null
                && journey.getCustomerId() != null
                && !payment.getCustomerId().equals(journey.getCustomerId())) return;

        String stage = switch (payment.getStatus()) {
            case SUCCEEDED -> "PAID";
            case REQUIRES_ACTION, PENDING -> "PAYMENT_LINK_SENT";
            case REFUNDED -> "PAYMENT_REFUNDED";
            case FAILED, CANCELLED, EXPIRED -> "PAYMENT_FAILED";
        };

        Map<String, Object> metadata = new LinkedHashMap<>();
        if (journey.getMetadata() != null) metadata.putAll(journey.getMetadata());
        boolean changed = false;
        changed |= putIfChanged(metadata, "paymentOperationId", payment.getOperationId().toString());
        changed |= putIfChanged(metadata, "paymentId", payment.getId().toString());
        changed |= putIfChanged(metadata, "paymentStatus", payment.getStatus().name());
        changed |= putIfChanged(metadata, "commercialStage", stage);
        changed |= putIfChanged(metadata, "lastAction", "PAYMENT_STATUS_VERIFIED");
        if (!changed) return;

        journey.setMetadata(metadata);
        journey.setRevision(journey.getRevision() == null ? 1 : journey.getRevision() + 1);
        operations.saveAndFlush(journey);
    }

    private static boolean putIfChanged(Map<String, Object> metadata, String key, Object value) {
        Object previous = metadata.get(key);
        if (Objects.equals(previous, value)) return false;
        metadata.put(key, value);
        return true;
    }

    private Calculation calculate(UUID businessId,
                                  UUID customerId,
                                  UUID sourceReferenceId,
                                  String trustedPhone,
                                  UUID targetOperationId) {
        BusinessOperation target = operations.findByIdAndBusinessId(targetOperationId, businessId).orElse(null);
        if (target == null
                || target.getType() == BusinessOperation.Type.PAYMENT
                || !operationOwnedBy(target, customerId, sourceReferenceId, trustedPhone)) {
            throw new PaymentRuleException(
                    "PAYMENT_TARGET_NOT_FOUND",
                    "No encuentro esa operación entre las operaciones del cliente actual.");
        }
        if (target.getStatus() != BusinessOperation.Status.CONFIRMED) {
            throw new PaymentRuleException(
                    "PAYMENT_TARGET_NOT_PAYABLE",
                    "La operación debe estar confirmada antes de iniciar su pago.");
        }
        if (target.getTotal() == null || target.getTotal().compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentRuleException(
                    "PAYMENT_AMOUNT_UNAVAILABLE",
                    "La operación no tiene un monto backend-autoritativo disponible para pagar.");
        }

        String currency = target.getCurrency() == null ? null : target.getCurrency().trim().toUpperCase();
        if (blank(currency) || currency.length() != 3) {
            throw new PaymentRuleException(
                    "PAYMENT_CURRENCY_UNAVAILABLE",
                    "La operación no tiene una moneda válida para pagar.");
        }

        BigDecimal paid = payments.findAllByBusinessIdAndTargetOperationIdOrderByCreatedAtAsc(
                        businessId, targetOperationId)
                .stream()
                .filter(payment -> payment.getStatus() == BusinessPayment.Status.SUCCEEDED)
                .map(BusinessPayment::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remaining = target.getTotal().subtract(paid);
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentRuleException(
                    "PAYMENT_ALREADY_SATISFIED",
                    "La operación ya registra el monto completo como pagado.");
        }
        return new Calculation(target, remaining, currency);
    }

    private BusinessOperation requireEditableOperation(UUID businessId,
                                                       UUID operationId,
                                                       UUID customerId,
                                                       UUID sourceReferenceId,
                                                       String trustedPhone) {
        BusinessOperation operation = operations.findByIdAndBusinessId(operationId, businessId).orElse(null);
        if (operation == null
                || operation.getType() != BusinessOperation.Type.PAYMENT
                || operation.getStatus() != BusinessOperation.Status.AWAITING_CONFIRMATION
                || !operationOwnedBy(operation, customerId, sourceReferenceId, trustedPhone)) {
            return null;
        }
        if (payments.findByOperationIdAndBusinessId(operationId, businessId).isPresent()) return null;
        return operation;
    }

    private void recordConversation(UUID businessId,
                                    UUID sourceReferenceId,
                                    BusinessOrder.Source source,
                                    BusinessOperation operation,
                                    String lastTool,
                                    BusinessPayment payment) {
        if (sourceReferenceId == null || operation == null) return;

        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("intent", "PAYMENT");
        patch.put("lastTool", lastTool);
        patch.put("operationId", operation.getId().toString());
        patch.put("operationType", "PAYMENT");
        patch.put("operationStatus", operation.getStatus().name());
        patch.put("operationRevision", operation.getRevision());
        patch.put("amount", operation.getTotal());
        patch.put("currency", operation.getCurrency());
        patch.put("confirmationPending", operation.getConfirmationToken() != null);
        patch.put("confirmationToken",
                operation.getConfirmationToken() == null ? null : operation.getConfirmationToken().toString());
        UUID targetOperationId = metadataUuid(operation, "targetOperationId");
        if (targetOperationId != null) patch.put("targetOperationId", targetOperationId.toString());

        if (payment != null) {
            patch.put("paymentId", payment.getId().toString());
            patch.put("paymentStatus", payment.getStatus().name());
            patch.put("provider", payment.getProvider());
            patch.put("checkoutUrl", payment.getCheckoutUrl());
            patch.put("paymentPending", paymentPending(payment.getStatus()));
        } else {
            patch.put("paymentPending", true);
        }

        conversationState.apply(
                businessId,
                sourceReferenceId,
                source == null ? BusinessOrder.Source.API : source,
                operation.getId(),
                patch);
    }

    private static JSONObject quoteData(BusinessOperation operation, Calculation calculation) {
        return new JSONObject()
                .put("operationId", operation.getId().toString())
                .put("revision", operation.getRevision())
                .put("status", operation.getStatus().name())
                .put("confirmationToken",
                        operation.getConfirmationToken() == null
                                ? JSONObject.NULL
                                : operation.getConfirmationToken().toString())
                .put("targetOperationId", calculation.target().getId().toString())
                .put("amount", calculation.amount())
                .put("currency", calculation.currency());
    }

    private static JSONObject paymentData(BusinessPayment payment) {
        return new JSONObject()
                .put("paymentId", payment.getId().toString())
                .put("operationId", payment.getOperationId().toString())
                .put("targetOperationId", payment.getTargetOperationId().toString())
                .put("provider", payment.getProvider())
                .put("status", payment.getStatus().name())
                .put("amount", payment.getAmount())
                .put("currency", payment.getCurrency())
                .put("checkoutUrl", nullable(payment.getCheckoutUrl()));
    }

    private static Map<String, Object> operationMetadata(Calculation calculation,
                                                         boolean confirmationPending,
                                                         BusinessPayment payment) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("intent", "PAYMENT");
        metadata.put("targetOperationId", calculation.target().getId().toString());
        metadata.put("targetOperationType", calculation.target().getType().name());
        metadata.put("confirmationPending", confirmationPending);
        metadata.put("paymentPending", payment == null || paymentPending(payment.getStatus()));
        if (payment != null) {
            metadata.put("paymentId", payment.getId().toString());
            metadata.put("paymentStatus", payment.getStatus().name());
            metadata.put("provider", payment.getProvider());
        }
        return metadata;
    }

    private static Map<String, Object> mergeMetadata(Map<String, Object> current, Map<String, Object> patch) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (current != null) merged.putAll(current);
        if (patch != null) merged.putAll(patch);
        return merged.isEmpty() ? null : merged;
    }

    private static boolean paymentTermsChanged(BusinessOperation operation, Calculation calculation) {
        return operation.getTotal() == null
                || operation.getTotal().compareTo(calculation.amount()) != 0
                || !Objects.equals(operation.getCurrency(), calculation.currency())
                || !Objects.equals(metadataUuid(operation, "targetOperationId"), calculation.target().getId());
    }

    private static boolean operationOwnedBy(BusinessOperation operation,
                                            UUID customerId,
                                            UUID sourceReferenceId,
                                            String trustedPhone) {
        if (operation == null) return false;
        if (customerId != null && customerId.equals(operation.getCustomerId())) return true;
        if (sourceReferenceId != null && sourceReferenceId.equals(operation.getSourceReferenceId())) return true;
        return !blank(trustedPhone)
                && !blank(operation.getContactPhone())
                && trustedPhone.trim().equals(operation.getContactPhone().trim());
    }

    private static boolean paymentOwnedBy(BusinessPayment payment,
                                          UUID customerId,
                                          UUID sourceReferenceId,
                                          String trustedPhone) {
        if (customerId != null && customerId.equals(payment.getCustomerId())) return true;
        if (sourceReferenceId != null && sourceReferenceId.equals(payment.getSourceReferenceId())) return true;
        return !blank(trustedPhone)
                && !blank(payment.getContactPhone())
                && trustedPhone.trim().equals(payment.getContactPhone().trim());
    }

    private static boolean hasVerifiedContext(UUID customerId, UUID sourceReferenceId, String trustedPhone) {
        return customerId != null || sourceReferenceId != null || !blank(trustedPhone);
    }

    private static UUID metadataUuid(BusinessOperation operation, String key) {
        if (operation == null || operation.getMetadata() == null) return null;
        Object value = operation.getMetadata().get(key);
        if (value == null) return null;
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
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

    private static boolean terminal(BusinessPayment.Status status) {
        return status == BusinessPayment.Status.SUCCEEDED
                || status == BusinessPayment.Status.FAILED
                || status == BusinessPayment.Status.CANCELLED
                || status == BusinessPayment.Status.EXPIRED
                || status == BusinessPayment.Status.REFUNDED;
    }

    static String providerIdempotencyKey(BusinessOperation operation) {
        if (operation == null || operation.getId() == null) {
            throw new IllegalArgumentException("Payment operation id is required.");
        }
        int revision = operation.getRevision() == null ? 1 : operation.getRevision();
        String material = operation.getId() + ":" + revision;
        return UUID.nameUUIDFromBytes(material.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static String required(JSONObject args, String key) {
        if (args == null || !args.has(key) || args.opt(key) == JSONObject.NULL) {
            throw new IllegalArgumentException("Falta el dato requerido: " + key + ".");
        }
        String value = String.valueOf(args.get(key)).trim();
        if (value.isBlank()) throw new IllegalArgumentException("Falta el dato requerido: " + key + ".");
        return value;
    }

    private static String optional(JSONObject args, String key) {
        if (args == null || !args.has(key) || args.opt(key) == JSONObject.NULL) return null;
        String value = String.valueOf(args.get(key)).trim();
        return value.isBlank() ? null : value;
    }

    private static UUID uuid(String value) {
        try {
            return UUID.fromString(value.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Se recibió un identificador inválido.");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static Object nullable(Object value) {
        return value == null ? JSONObject.NULL : value;
    }

    private static JSONObject success(JSONObject data) {
        return new JSONObject()
                .put("success", true)
                .put("data", data)
                .put("error", JSONObject.NULL);
    }

    private static JSONObject error(String code, String message) {
        return new JSONObject()
                .put("success", false)
                .put("data", JSONObject.NULL)
                .put("error", new JSONObject().put("code", code).put("message", message));
    }

    private static JSONObject errorWithData(String code, String message, JSONObject data) {
        return new JSONObject()
                .put("success", false)
                .put("data", data)
                .put("error", new JSONObject().put("code", code).put("message", message));
    }

    private record Calculation(BusinessOperation target, BigDecimal amount, String currency) {}

    private static final class PaymentRuleException extends RuntimeException {
        private final String code;

        private PaymentRuleException(String code, String message) {
            super(message);
            this.code = code;
        }
    }
}

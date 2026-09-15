package cl.helvoca.operations;

import cl.helvoca.catalog.CatalogItemRepository;
import cl.helvoca.delivery.DeliveryCoverageService;
import cl.helvoca.delivery.DeliveryWorkflowService;
import cl.helvoca.delivery.DeliveryZoneRepository;
import cl.helvoca.payment.PaymentWorkflowService;
import org.json.JSONObject;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
@Primary
public class ConfirmationAwareCommercialOperationToolService extends CommercialOperationToolService {
    private static final Set<String> CONFIRM_TOOLS = Set.of("create_order", "create_delivery", "create_payment");
    private final UniversalConfirmationService confirmations;
    private final OperationExecutionLockService executionLocks;

    public ConfirmationAwareCommercialOperationToolService(
            CatalogItemRepository catalog,
            DeliveryZoneRepository deliveryZones,
            DeliveryCoverageService deliveryCoverage,
            BusinessOrderRepository orders,
            BusinessOrderLineRepository orderLines,
            BusinessOperationRepository operations,
            BusinessOperationCapabilityService capabilities,
            OrderWorkflowService orderWorkflow,
            DeliveryWorkflowService deliveryWorkflow,
            UniversalOperationWorkflowService universalOperations,
            PaymentWorkflowService paymentWorkflow,
            ConversationStateService conversationState,
            UniversalConfirmationService confirmations,
            OperationExecutionLockService executionLocks) {
        super(catalog, deliveryZones, deliveryCoverage, orders, orderLines, operations, capabilities,
                orderWorkflow, deliveryWorkflow, universalOperations, paymentWorkflow, conversationState);
        this.confirmations = confirmations;
        this.executionLocks = executionLocks;
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public String execute(UUID businessId,
                          UUID customerId,
                          UUID sourceReferenceId,
                          String trustedPhone,
                          BusinessOrder.Source source,
                          String toolName,
                          String rawArguments) {
        UUID operationId = null;
        UUID token = null;
        if (CONFIRM_TOOLS.contains(toolName)) {
            try {
                JSONObject args = rawArguments == null || rawArguments.isBlank() ? new JSONObject() : new JSONObject(rawArguments);
                operationId = UUID.fromString(args.optString("operationId", ""));
                String tokenRaw = args.optString("confirmationToken", null);
                token = tokenRaw == null || tokenRaw.isBlank() ? null : UUID.fromString(tokenRaw);
            } catch (Exception e) {
                return error("INVALID_CONFIRMIRMATION", "La confirmación requiere una operación y token válidos.").toString();
            }

            // Fence the exact tenant operation before authorization and before any
            // domain workflow can materialize a projection or call an external
            // provider. A concurrent replay waits here, then observes the committed
            // projection/consumed confirmation from the first execution.
            executionLocks.lock(businessId, operationId);

            UniversalConfirmationService.Authorization authorization = confirmations.authorize(
                    businessId, operationId, customerId, sourceReferenceId, trustedPhone, token);
            JSONObject blocked = blocked(authorization);
            if (blocked != null) return blocked.toString();
        }

        String raw = super.execute(businessId, customerId, sourceReferenceId, trustedPhone, source, toolName, rawArguments);
        if (operationId != null && CONFIRM_TOOLS.contains(toolName)) {
            JSONObject result = new JSONObject(raw);
            if (result.optBoolean("success", false)) {
                confirmations.recordResolution(businessId, operationId, token, source, sourceReferenceId);
            }
        }
        return raw;
    }

    private static JSONObject blocked(UniversalConfirmationService.Authorization authorization) {
        return switch (authorization) {
            case AUTHORIZED, IDEMPOTENT_REPLAY -> null;
            case EXPIRED -> error("CONFIRMATION_EXPIRED",
                    "La confirmación expiró. Presenta nuevamente la versión actual antes de ejecutar.");
            case STALE -> error("STALE_CONFIRMATION",
                    "La confirmación no corresponde a la revisión vigente. Presenta nuevamente la versión actual.");
            case NOT_AWAITING -> error("OPERATION_NOT_AWAITING_CONFIRMATION",
                    "La operación ya no está esperando confirmación.");
            case NOT_OWNED, NOT_FOUND -> error("OPERATION_NOT_FOUND",
                    "No encuentro esa operación para el cliente actual.");
        };
    }

    private static JSONObject error(String code, String message) {
        return new JSONObject().put("success", false).put("data", JSONObject.NULL)
                .put("error", new JSONObject().put("code", code).put("message", message));
    }
}

package cl.helvoca.operations;

import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * Shared AI-channel guard for operation mutations.
 *
 * Read-only discovery/status tools stay available. Mutation/creation tools are
 * mapped to their universal operation type and may execute automatically only
 * when the tenant's effective automation policy allows it.
 */
@Service
public class AutomationPolicyToolGate {
    private static final Map<String, BusinessOperation.Type> OPERATION_TOOLS = Map.ofEntries(
            Map.entry("quote_order", BusinessOperation.Type.ORDER),
            Map.entry("update_order", BusinessOperation.Type.ORDER),
            Map.entry("create_order", BusinessOperation.Type.ORDER),
            Map.entry("cancel_order", BusinessOperation.Type.ORDER),
            Map.entry("quote_delivery", BusinessOperation.Type.DELIVERY),
            Map.entry("update_delivery", BusinessOperation.Type.DELIVERY),
            Map.entry("create_delivery", BusinessOperation.Type.DELIVERY),
            Map.entry("cancel_delivery", BusinessOperation.Type.DELIVERY),
            Map.entry("create_quote", BusinessOperation.Type.QUOTE),
            Map.entry("create_lead", BusinessOperation.Type.LEAD),
            Map.entry(CommercialOperationToolService.SHOWCASE_SELECTION_TOOL, BusinessOperation.Type.REQUEST),
            Map.entry("create_request", BusinessOperation.Type.REQUEST),
            Map.entry("create_booking", BusinessOperation.Type.BOOKING),
            Map.entry("reschedule_booking", BusinessOperation.Type.BOOKING),
            Map.entry("cancel_booking", BusinessOperation.Type.BOOKING),
            Map.entry("quote_payment", BusinessOperation.Type.PAYMENT),
            Map.entry("update_payment", BusinessOperation.Type.PAYMENT),
            Map.entry("create_payment", BusinessOperation.Type.PAYMENT),
            Map.entry("cancel_payment", BusinessOperation.Type.PAYMENT));

    private final OperationPolicyService policies;

    public AutomationPolicyToolGate(OperationPolicyService policies) {
        this.policies = policies;
    }

    public BusinessOperation.Type operationType(String toolName) {
        return toolName == null ? null : OPERATION_TOOLS.get(toolName);
    }

    public OperationPolicyService.Policy policy(UUID businessId, String toolName) {
        BusinessOperation.Type type = operationType(toolName);
        return type == null ? null : policies.resolve(businessId, type);
    }

    public JSONObject blockIfAutomationDisabled(UUID businessId, String toolName) {
        BusinessOperation.Type type = operationType(toolName);
        if (type == null) return null;
        OperationPolicyService.Policy policy = policies.resolve(businessId, type);
        if (policy.autoExecute()) return null;
        return new JSONObject()
                .put("success", false)
                .put("data", JSONObject.NULL)
                .put("error", new JSONObject()
                        .put("code", "AUTOMATION_DISABLED")
                        .put("message", "La automatización de " + type.name()
                                + " está deshabilitada por la política de este negocio."));
    }
}

package cl.helvoca.operations;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AutomationPolicyToolGateTest {

    @Test
    void defaultsAllowAutomaticMutationsWithoutHumanApproval() {
        AutomationPolicyToolGate gate = new AutomationPolicyToolGate(new OperationPolicyService());
        UUID businessId = UUID.randomUUID();

        assertNull(gate.blockIfAutomationDisabled(businessId, "create_order"));
        assertNull(gate.blockIfAutomationDisabled(businessId, "create_booking"));
        assertNull(gate.blockIfAutomationDisabled(businessId, "create_payment"));
        assertNull(gate.blockIfAutomationDisabled(businessId, "list_catalog"));
    }

    @Test
    void tenantCanDisableAutomationWithoutCreatingAnApprovalQueue() {
        BusinessAutomationPolicyRepository repository = mock(BusinessAutomationPolicyRepository.class);
        UUID businessId = UUID.randomUUID();

        BusinessAutomationPolicy override = new BusinessAutomationPolicy();
        override.setBusinessId(businessId);
        override.setOperationType(BusinessOperation.Type.ORDER);
        override.setAutoExecute(false);
        override.setCustomerConfirmation(BusinessAutomationPolicy.CustomerConfirmation.EXPLICIT);
        override.setPaymentRequirement(BusinessAutomationPolicy.PaymentRequirement.NONE);
        override.setRetryPolicy(BusinessAutomationPolicy.RetryPolicy.SAFE_AUTOMATIC);
        override.setMaxAutoRetries(2);
        override.setEscalationPolicy(BusinessAutomationPolicy.EscalationPolicy.ONLY_IF_UNRESOLVABLE);

        when(repository.findByBusinessIdAndOperationType(businessId, BusinessOperation.Type.ORDER))
                .thenReturn(Optional.of(override));

        AutomationPolicyToolGate gate = new AutomationPolicyToolGate(new OperationPolicyService(repository));
        JSONObject blocked = gate.blockIfAutomationDisabled(businessId, "create_order");

        assertNotNull(blocked);
        assertFalse(blocked.getBoolean("success"));
        assertEquals("AUTOMATION_DISABLED", blocked.getJSONObject("error").getString("code"));
    }

    @Test
    void transactionalSafetyFloorCannotBeWeakenedByPersistedOverride() {
        BusinessAutomationPolicyRepository repository = mock(BusinessAutomationPolicyRepository.class);
        UUID businessId = UUID.randomUUID();

        BusinessAutomationPolicy override = new BusinessAutomationPolicy();
        override.setBusinessId(businessId);
        override.setOperationType(BusinessOperation.Type.BOOKING);
        override.setAutoExecute(true);
        override.setCustomerConfirmation(BusinessAutomationPolicy.CustomerConfirmation.NONE);
        override.setPaymentRequirement(BusinessAutomationPolicy.PaymentRequirement.NONE);
        override.setRetryPolicy(BusinessAutomationPolicy.RetryPolicy.SAFE_AUTOMATIC);
        override.setMaxAutoRetries(2);
        override.setEscalationPolicy(BusinessAutomationPolicy.EscalationPolicy.ONLY_IF_UNRESOLVABLE);

        when(repository.findByBusinessIdAndOperationType(businessId, BusinessOperation.Type.BOOKING))
                .thenReturn(Optional.of(override));

        OperationPolicyService.Policy effective = new OperationPolicyService(repository)
                .resolve(businessId, BusinessOperation.Type.BOOKING);

        assertTrue(effective.requiresExplicitConfirmation());
        assertTrue(effective.autoExecute());
    }
}

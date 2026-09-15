package cl.helvoca.operations;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SafeOperationRetryEngineHandoffFailureTest {

    @Test
    void handoffPersistenceFailureStopsSafelyAndNeverClaimsHumanEscalation() {
        UUID businessId = UUID.randomUUID();
        UUID sourceReferenceId = UUID.randomUUID();

        OperationPolicyService policies = mock(OperationPolicyService.class);
        OperationRetryAuditService audit = mock(OperationRetryAuditService.class);
        HumanHandoffService handoffs = mock(HumanHandoffService.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);

        when(policies.resolve(businessId, BusinessOperation.Type.PAYMENT))
                .thenReturn(new OperationPolicyService.Policy(
                        true,
                        OperationPolicyService.ConfirmationRequirement.EXPLICIT,
                        OperationPolicyService.PaymentRequirement.NONE,
                        OperationPolicyService.RetryPolicy.NONE,
                        0,
                        OperationPolicyService.EscalationPolicy.ONLY_IF_UNRESOLVABLE,
                        false));
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(handoffs.createForUnresolvable(
                eq(businessId),
                eq(BusinessOperation.Type.PAYMENT),
                eq(sourceReferenceId),
                any(),
                eq("create_payment"),
                eq("PAYMENT_PROVIDER_NOT_CONFIGURED"),
                anyInt()))
                .thenThrow(new IllegalStateException("handoff storage unavailable"));

        SafeOperationRetryEngine engine = new SafeOperationRetryEngine(
                policies, audit, handoffs, transactionManager, millis -> { });

        String raw = engine.execute(
                businessId,
                BusinessOperation.Type.PAYMENT,
                sourceReferenceId,
                "create_payment",
                () -> failure("PAYMENT_PROVIDER_NOT_CONFIGURED").toString());

        JSONObject automation = new JSONObject(raw).getJSONObject("automation");
        assertEquals("UNRESOLVABLE", automation.getString("failureClass"));
        assertEquals("STOP_SAFELY", automation.getString("fallbackAction"));
        assertFalse(automation.getBoolean("humanEscalation"));
        assertTrue(automation.getBoolean("handoffRequested"));
        assertFalse(automation.getBoolean("handoffCreated"));
        assertTrue(automation.isNull("handoffId"));
        assertTrue(automation.isNull("handoffStatus"));

        verify(handoffs, times(1)).createForUnresolvable(
                eq(businessId),
                eq(BusinessOperation.Type.PAYMENT),
                eq(sourceReferenceId),
                any(),
                eq("create_payment"),
                eq("PAYMENT_PROVIDER_NOT_CONFIGURED"),
                anyInt());
    }

    private static JSONObject failure(String code) {
        return new JSONObject()
                .put("success", false)
                .put("data", JSONObject.NULL)
                .put("error", new JSONObject().put("code", code).put("message", "test"));
    }
}

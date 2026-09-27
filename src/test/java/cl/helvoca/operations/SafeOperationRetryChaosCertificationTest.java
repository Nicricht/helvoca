package cl.helvoca.operations;

import cl.helvoca.chaos.DeterministicFailureInjector;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SafeOperationRetryChaosCertificationTest {

    @Test
    void twoTimeoutsThenSuccessRemainBoundedAndApplyOneSuccessfulResult() {
        UUID businessId = UUID.randomUUID();
        UUID sourceReferenceId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();

        OperationPolicyService policies = mock(OperationPolicyService.class);
        OperationRetryAuditService audit = mock(OperationRetryAuditService.class);
        HumanHandoffService handoffs = mock(HumanHandoffService.class);
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);

        when(policies.resolve(businessId, BusinessOperation.Type.PAYMENT))
                .thenReturn(new OperationPolicyService.Policy(
                        true,
                        OperationPolicyService.ConfirmationRequirement.EXPLICIT,
                        OperationPolicyService.PaymentRequirement.NONE,
                        OperationPolicyService.RetryPolicy.SAFE_AUTOMATIC,
                        2,
                        OperationPolicyService.EscalationPolicy.ONLY_IF_UNRESOLVABLE,
                        false));
        when(transactions.getTransaction(any()))
                .thenAnswer(ignored -> new SimpleTransactionStatus());

        List<Long> delays = new ArrayList<>();
        SafeOperationRetryEngine engine = new SafeOperationRetryEngine(
                policies, audit, handoffs, transactions, delays::add);

        DeterministicFailureInjector injector = new DeterministicFailureInjector(
                2,
                () -> new RuntimeException(new SocketTimeoutException("provider timeout")));

        String result = engine.execute(
                businessId,
                BusinessOperation.Type.PAYMENT,
                sourceReferenceId,
                "create_payment",
                () -> injector.execute(() -> new JSONObject()
                        .put("success", true)
                        .put("data", new JSONObject().put("operationId", operationId.toString()))
                        .put("error", JSONObject.NULL)
                        .toString()));

        JSONObject json = new JSONObject(result);
        assertTrue(json.getBoolean("success"));
        assertEquals(3, injector.attempts());
        assertEquals(List.of(100L, 200L), delays);

        verify(audit, times(2)).record(
                eq(businessId),
                eq(BusinessOperation.Type.PAYMENT),
                eq(sourceReferenceId),
                isNull(),
                eq("create_payment"),
                anyInt(),
                eq(3),
                eq(OperationRetryAuditService.Outcome.RETRY_SCHEDULED),
                eq(OperationPolicyService.FailureClass.TRANSIENT),
                anyString(),
                anyInt());

        verify(audit).record(
                eq(businessId),
                eq(BusinessOperation.Type.PAYMENT),
                eq(sourceReferenceId),
                eq(operationId),
                eq("create_payment"),
                eq(3),
                eq(3),
                eq(OperationRetryAuditService.Outcome.SUCCEEDED_AFTER_RETRY),
                isNull(),
                isNull(),
                eq(0));
    }

    @Test
    void permanentTimeoutStormStopsAtConfiguredAttemptLimit() {
        UUID businessId = UUID.randomUUID();
        UUID sourceReferenceId = UUID.randomUUID();

        OperationPolicyService policies = mock(OperationPolicyService.class);
        OperationRetryAuditService audit = mock(OperationRetryAuditService.class);
        HumanHandoffService handoffs = mock(HumanHandoffService.class);
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);

        when(policies.resolve(businessId, BusinessOperation.Type.ORDER))
                .thenReturn(new OperationPolicyService.Policy(
                        true,
                        OperationPolicyService.ConfirmationRequirement.EXPLICIT,
                        OperationPolicyService.PaymentRequirement.NONE,
                        OperationPolicyService.RetryPolicy.SAFE_AUTOMATIC,
                        2,
                        OperationPolicyService.EscalationPolicy.ONLY_IF_UNRESOLVABLE,
                        false));
        when(transactions.getTransaction(any()))
                .thenAnswer(ignored -> new SimpleTransactionStatus());

        SafeOperationRetryEngine engine = new SafeOperationRetryEngine(
                policies, audit, handoffs, transactions, ignored -> { });

        DeterministicFailureInjector injector = new DeterministicFailureInjector(
                20,
                () -> new RuntimeException(new SocketTimeoutException("provider still down")));

        String result = engine.execute(
                businessId,
                BusinessOperation.Type.ORDER,
                sourceReferenceId,
                "confirm_order",
                () -> injector.execute(() -> "{\"success\":true}"));

        JSONObject json = new JSONObject(result);
        assertFalse(json.getBoolean("success"));
        assertEquals(3, injector.attempts());
        assertEquals("RETRY_LATER", json.getJSONObject("automation").getString("fallbackAction"));
        assertEquals(2, json.getJSONObject("automation").getInt("retryCount"));

        verify(audit).record(
                eq(businessId),
                eq(BusinessOperation.Type.ORDER),
                eq(sourceReferenceId),
                isNull(),
                eq("confirm_order"),
                eq(3),
                eq(3),
                eq(OperationRetryAuditService.Outcome.RETRIES_EXHAUSTED),
                eq(OperationPolicyService.FailureClass.TRANSIENT),
                anyString(),
                eq(0));
        verifyNoInteractions(handoffs);
    }
}

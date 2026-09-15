package cl.helvoca.operations;

import cl.helvoca.audit.AuditService;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AutomationPolicyAdminServiceTest {

    @Test
    void patchUsesJwtTenantAndPersistsAutomationFirstOverride() {
        UUID businessId = UUID.randomUUID();
        BusinessAutomationPolicyRepository repository = mock(BusinessAutomationPolicyRepository.class);
        OperationPolicyService policies = mock(OperationPolicyService.class);
        TenantProvider tenant = mock(TenantProvider.class);
        AuditService audit = mock(AuditService.class);

        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(repository.findByBusinessIdAndOperationType(businessId, BusinessOperation.Type.QUOTE))
                .thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(BusinessAutomationPolicy.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(policies.resolve(businessId, BusinessOperation.Type.QUOTE))
                .thenReturn(new OperationPolicyService.Policy(
                        true,
                        OperationPolicyService.ConfirmationRequirement.NONE,
                        OperationPolicyService.PaymentRequirement.NONE,
                        OperationPolicyService.RetryPolicy.SAFE_AUTOMATIC,
                        2,
                        OperationPolicyService.EscalationPolicy.ONLY_IF_UNRESOLVABLE,
                        false),
                        new OperationPolicyService.Policy(
                                true,
                                OperationPolicyService.ConfirmationRequirement.NONE,
                                OperationPolicyService.PaymentRequirement.NONE,
                                OperationPolicyService.RetryPolicy.SAFE_AUTOMATIC,
                                3,
                                OperationPolicyService.EscalationPolicy.ONLY_IF_UNRESOLVABLE,
                                true));

        AutomationPolicyAdminService service = new AutomationPolicyAdminService(
                repository, policies, tenant, audit);
        AutomationPolicyAdminService.View result = service.patch(
                BusinessOperation.Type.QUOTE,
                new AutomationPolicyAdminService.Update(
                        true,
                        null,
                        null,
                        OperationPolicyService.RetryPolicy.SAFE_AUTOMATIC,
                        3,
                        OperationPolicyService.EscalationPolicy.ONLY_IF_UNRESOLVABLE));

        verify(repository).saveAndFlush(argThat(saved ->
                businessId.equals(saved.getBusinessId())
                        && saved.getOperationType() == BusinessOperation.Type.QUOTE
                        && saved.isAutoExecute()
                        && saved.getMaxAutoRetries() == 3));
        verify(audit).success(businessId,
                "AUTOMATION_POLICY_UPDATED_QUOTE",
                "AUTOMATION_POLICY",
                businessId);
        assertTrue(result.tenantOverride());
        assertEquals(3, result.maxAutoRetries());
    }

    @Test
    void transactionalCustomerConfirmationCannotBeDisabled() {
        UUID businessId = UUID.randomUUID();
        BusinessAutomationPolicyRepository repository = mock(BusinessAutomationPolicyRepository.class);
        OperationPolicyService policies = mock(OperationPolicyService.class);
        TenantProvider tenant = mock(TenantProvider.class);
        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(policies.resolve(businessId, BusinessOperation.Type.ORDER))
                .thenReturn(new OperationPolicyService.Policy(
                        true,
                        OperationPolicyService.ConfirmationRequirement.EXPLICIT,
                        OperationPolicyService.PaymentRequirement.NONE,
                        OperationPolicyService.RetryPolicy.SAFE_AUTOMATIC,
                        2,
                        OperationPolicyService.EscalationPolicy.ONLY_IF_UNRESOLVABLE,
                        false));

        AutomationPolicyAdminService service = new AutomationPolicyAdminService(
                repository, policies, tenant, mock(AuditService.class));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.patch(
                BusinessOperation.Type.ORDER,
                new AutomationPolicyAdminService.Update(
                        true,
                        OperationPolicyService.ConfirmationRequirement.NONE,
                        null,
                        null,
                        null,
                        null)));

        assertTrue(error.getMessage().contains("explicit customer confirmation"));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void resetDeletesOnlyCurrentTenantOverrideAndReturnsDefaults() {
        UUID businessId = UUID.randomUUID();
        BusinessAutomationPolicyRepository repository = mock(BusinessAutomationPolicyRepository.class);
        OperationPolicyService policies = mock(OperationPolicyService.class);
        TenantProvider tenant = mock(TenantProvider.class);
        AuditService audit = mock(AuditService.class);
        BusinessAutomationPolicy existing = new BusinessAutomationPolicy();
        existing.setBusinessId(businessId);
        existing.setOperationType(BusinessOperation.Type.LEAD);

        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(repository.findByBusinessIdAndOperationType(businessId, BusinessOperation.Type.LEAD))
                .thenReturn(Optional.of(existing));
        when(policies.resolve(businessId, BusinessOperation.Type.LEAD))
                .thenReturn(new OperationPolicyService.Policy(
                        true,
                        OperationPolicyService.ConfirmationRequirement.NONE,
                        OperationPolicyService.PaymentRequirement.NONE,
                        OperationPolicyService.RetryPolicy.SAFE_AUTOMATIC,
                        2,
                        OperationPolicyService.EscalationPolicy.ONLY_IF_UNRESOLVABLE,
                        false));

        AutomationPolicyAdminService service = new AutomationPolicyAdminService(
                repository, policies, tenant, audit);
        AutomationPolicyAdminService.View result = service.reset(BusinessOperation.Type.LEAD);

        verify(repository).delete(existing);
        verify(repository).flush();
        verify(audit).success(businessId,
                "AUTOMATION_POLICY_RESET_LEAD",
                "AUTOMATION_POLICY",
                businessId);
        assertFalse(result.tenantOverride());
    }
}

package cl.helvoca.operations;

import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BusinessOperationCapabilityServiceTest {
    @Mock BusinessOperationCapabilityRepository repository;
    @Mock TenantProvider tenantProvider;

    @Test
    void deliveryAutomaticallyEnablesOrderAndCatalog() {
        UUID businessId = UUID.randomUUID();
        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        BusinessOperationCapabilityService service = new BusinessOperationCapabilityService(repository, tenantProvider);

        Set<BusinessOperationCapability> result = service.replaceCurrent(Set.of(BusinessOperationCapability.DELIVERY));

        assertEquals(Set.of(
                BusinessOperationCapability.DELIVERY,
                BusinessOperationCapability.ORDER,
                BusinessOperationCapability.CATALOG), result);
        verify(repository).deleteAllByBusinessId(businessId);
        verify(repository, times(2)).flush();
        verify(repository).saveAll(argThat(grants -> {
            Set<BusinessOperationCapability> actual = new HashSet<>();
            for (BusinessOperationCapabilityGrant grant : grants) {
                assertEquals(businessId, grant.getBusinessId());
                actual.add(grant.getCapability());
            }
            return actual.equals(result);
        }));
    }

    @Test
    void quoteAutomaticallyEnablesCatalogButNotOrder() {
        UUID businessId = UUID.randomUUID();
        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        BusinessOperationCapabilityService service = new BusinessOperationCapabilityService(repository, tenantProvider);

        Set<BusinessOperationCapability> result = service.replaceCurrent(Set.of(BusinessOperationCapability.QUOTE));

        assertEquals(Set.of(BusinessOperationCapability.QUOTE, BusinessOperationCapability.CATALOG), result);
    }

    @Test
    void allowedToolsComeOnlyFromPersistedTenantGrants() {
        UUID businessId = UUID.randomUUID();
        BusinessOperationCapabilityGrant order = grant(businessId, BusinessOperationCapability.ORDER);
        BusinessOperationCapabilityGrant delivery = grant(businessId, BusinessOperationCapability.DELIVERY);
        when(repository.findAllByBusinessIdOrderByCapabilityAsc(businessId)).thenReturn(List.of(order, delivery));

        BusinessOperationCapabilityService service = new BusinessOperationCapabilityService(repository, tenantProvider);
        Set<String> tools = service.allowedToolNames(businessId);

        assertTrue(tools.contains("quote_order"));
        assertTrue(tools.contains("create_order"));
        assertTrue(tools.contains("list_delivery_zones"));
        assertTrue(tools.contains("validate_delivery_address"));
        assertEquals(6, tools.size());
    }

    private static BusinessOperationCapabilityGrant grant(UUID businessId, BusinessOperationCapability capability) {
        BusinessOperationCapabilityGrant grant = new BusinessOperationCapabilityGrant();
        grant.setBusinessId(businessId);
        grant.setCapability(capability);
        return grant;
    }
}

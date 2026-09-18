package cl.helvoca.business;

import cl.helvoca.audit.AuditService;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BusinessProfileServiceTest {

    @Test
    void currentProfileIsAlwaysScopedToAuthenticatedTenant() {
        UUID businessId = UUID.randomUUID();
        BusinessProfileRepository profiles = mock(BusinessProfileRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        AuditService audit = mock(AuditService.class);

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(profiles.findById(businessId)).thenReturn(Optional.empty());

        BusinessProfileService service = new BusinessProfileService(profiles, tenantProvider, audit);
        BusinessProfileResponse response = service.current();

        assertEquals(businessId, response.businessId());
        assertEquals("CLP", response.defaultCurrency());
        verify(profiles).findById(businessId);
        verifyNoMoreInteractions(profiles);
    }

    @Test
    void upsertNormalizesProfileAndAuditsHumanChange() {
        UUID businessId = UUID.randomUUID();
        BusinessProfileRepository profiles = mock(BusinessProfileRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        AuditService audit = mock(AuditService.class);

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(profiles.findById(businessId)).thenReturn(Optional.empty());
        when(profiles.saveAndFlush(any(BusinessProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BusinessProfileService service = new BusinessProfileService(profiles, tenantProvider, audit);
        BusinessProfileResponse response = service.upsert(new BusinessProfileRequest(
                " STORE ",
                " Venta de tecnología ",
                " +56912345678 ",
                " ventas@example.com ",
                " https://example.com ",
                " Av. Siempre Viva 123 ",
                " Santiago ",
                " Santiago ",
                " Metropolitana ",
                " cl ",
                " usd "
        ));

        assertEquals(businessId, response.businessId());
        assertEquals("store", response.presetKey());
        assertEquals("Venta de tecnología", response.publicDescription());
        assertEquals("+56912345678", response.publicPhone());
        assertEquals("CL", response.countryCode());
        assertEquals("USD", response.defaultCurrency());

        verify(audit).humanSuccess(
                eq(businessId),
                eq("BUSINESS_PROFILE_UPSERT"),
                eq("BUSINESS_PROFILE"),
                eq(businessId),
                isNull(),
                anyMap());
    }
}

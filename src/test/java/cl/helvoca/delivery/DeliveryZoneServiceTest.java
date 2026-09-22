package cl.helvoca.delivery;

import cl.helvoca.audit.AuditService;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DeliveryZoneServiceTest {

    @Test
    void updateAuditsSafeBeforeAndAfterSnapshotsWithoutCoverageTerms() {
        UUID businessId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        DeliveryZoneRepository repository = mock(DeliveryZoneRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);
        AuditService audit = mock(AuditService.class);

        DeliveryZone zone = new DeliveryZone();
        zone.setId(zoneId);
        zone.setBusinessId(businessId);
        zone.setName("Zona antigua");
        zone.setCoverageTerms("Dirección privada que no debe quedar en auditoría");
        zone.setFee(new BigDecimal("2500"));
        zone.setMinimumOrder(new BigDecimal("10000"));
        zone.setActive(true);

        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(repository.findByIdAndBusinessId(zoneId, businessId)).thenReturn(Optional.of(zone));
        when(repository.saveAndFlush(any(DeliveryZone.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DeliveryZoneService service = new DeliveryZoneService(repository, tenant, audit);
        service.update(zoneId, new DeliveryZoneService.ZoneInput(
                "Zona nueva",
                "Cobertura sensible nueva",
                new BigDecimal("3000"),
                new BigDecimal("12000"),
                true));

        verify(audit).humanSuccess(
                eq(businessId),
                eq("DELIVERY_ZONE_UPDATE"),
                eq("DELIVERY_ZONE"),
                eq(zoneId),
                argThat(before -> "Zona antigua".equals(before.get("name"))
                        && !before.containsKey("coverageTerms")
                        && !before.containsValue("Dirección privada que no debe quedar en auditoría")),
                argThat(after -> "Zona nueva".equals(after.get("name"))
                        && new BigDecimal("3000").equals(after.get("fee"))
                        && !after.containsKey("coverageTerms")
                        && !after.containsValue("Cobertura sensible nueva")));
    }
}

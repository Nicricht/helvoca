package cl.helvoca.catalog;

import cl.helvoca.audit.AuditService;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UniversalCatalogServiceTest {

    @Test
    void updateAuditsSafeBeforeAndAfterSnapshotsWithoutMetadataPayload() {
        UUID businessId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        CatalogItemRepository repository = mock(CatalogItemRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);
        AuditService audit = mock(AuditService.class);

        CatalogItem item = new CatalogItem();
        item.setId(itemId);
        item.setBusinessId(businessId);
        item.setKind(CatalogItem.Kind.PRODUCT);
        item.setName("Producto antiguo");
        item.setDescription("Descripción antigua");
        item.setPrice(new BigDecimal("1000"));
        item.setCurrency("CLP");
        item.setMetadataJson("{\"internal\":\"do-not-audit\"}");
        item.setActive(true);

        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(repository.findByIdAndBusinessId(itemId, businessId)).thenReturn(Optional.of(item));
        when(repository.saveAndFlush(any(CatalogItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UniversalCatalogService service = new UniversalCatalogService(repository, tenant, audit);
        service.update(itemId, new UniversalCatalogService.ItemInput(
                CatalogItem.Kind.PRODUCT,
                "Producto nuevo",
                "Descripción nueva",
                new BigDecimal("1500"),
                "CLP",
                null,
                "{\"secret\":\"never-log-this\"}",
                true));

        verify(audit).humanSuccess(
                eq(businessId),
                eq("CATALOG_ITEM_UPDATE"),
                eq("CATALOG_ITEM"),
                eq(itemId),
                argThat(before -> "Producto antiguo".equals(before.get("name"))
                        && !before.containsKey("metadataJson")
                        && !before.containsValue("{\"internal\":\"do-not-audit\"}")),
                argThat(after -> "Producto nuevo".equals(after.get("name"))
                        && new BigDecimal("1500").equals(after.get("price"))
                        && !after.containsKey("metadataJson")
                        && !after.containsValue("{\"secret\":\"never-log-this\"}")));
    }
}

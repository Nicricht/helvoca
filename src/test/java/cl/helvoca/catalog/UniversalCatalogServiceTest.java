package cl.helvoca.catalog;

import cl.helvoca.common.ConflictException;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UniversalCatalogServiceTest {

    @Test
    void createProductNormalizesSkuAndEnablesInventoryTracking() {
        UUID businessId = UUID.randomUUID();
        CatalogItemRepository repository = mock(CatalogItemRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);
        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(repository.saveAndFlush(any(CatalogItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UniversalCatalogService service = new UniversalCatalogService(repository, tenant);
        UniversalCatalogService.ItemView result = service.create(new UniversalCatalogService.ItemInput(
                CatalogItem.Kind.PRODUCT,
                "Teclado mecánico",
                " kb-001 ",
                true,
                "Switch azul",
                new BigDecimal("39990"),
                "clp",
                null,
                null,
                true));

        assertEquals("KB-001", result.sku());
        assertTrue(result.inventoryTracked());
        assertEquals("CLP", result.currency());
        verify(repository).existsByBusinessIdAndSkuIgnoreCase(businessId, "KB-001");
    }

    @Test
    void duplicateSkuIsRejectedWithinTenant() {
        UUID businessId = UUID.randomUUID();
        CatalogItemRepository repository = mock(CatalogItemRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);
        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(repository.existsByBusinessIdAndSkuIgnoreCase(businessId, "SKU-1")).thenReturn(true);

        UniversalCatalogService service = new UniversalCatalogService(repository, tenant);

        assertThrows(ConflictException.class, () -> service.create(new UniversalCatalogService.ItemInput(
                CatalogItem.Kind.PRODUCT,
                "Producto",
                "sku-1",
                false,
                null,
                null,
                "CLP",
                null,
                null,
                true)));

        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void invalidSkuIsRejectedBeforePersistence() {
        CatalogItemRepository repository = mock(CatalogItemRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);
        when(tenant.requireBusinessId()).thenReturn(UUID.randomUUID());

        UniversalCatalogService service = new UniversalCatalogService(repository, tenant);

        assertThrows(IllegalArgumentException.class, () -> service.create(new UniversalCatalogService.ItemInput(
                CatalogItem.Kind.PRODUCT,
                "Producto",
                "sku con espacios",
                false,
                null,
                null,
                "CLP",
                null,
                null,
                true)));

        verify(repository, never()).saveAndFlush(any());
    }
}

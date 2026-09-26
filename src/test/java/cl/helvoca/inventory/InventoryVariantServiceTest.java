package cl.helvoca.inventory;

import cl.helvoca.catalog.CatalogItem;
import cl.helvoca.catalog.CatalogItemRepository;
import cl.helvoca.common.ConflictException;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InventoryVariantServiceTest {
    private InventoryProductVariantRepository variants;
    private InventoryStockRepository baseStocks;
    private InventoryMovementRepository movements;
    private CatalogItemRepository catalog;
    private TenantProvider tenant;
    private InventoryVariantService service;

    private UUID businessId;
    private UUID productId;

    @BeforeEach
    void setUp() {
        variants = mock(InventoryProductVariantRepository.class);
        baseStocks = mock(InventoryStockRepository.class);
        movements = mock(InventoryMovementRepository.class);
        catalog = mock(CatalogItemRepository.class);
        tenant = mock(TenantProvider.class);
        service = new InventoryVariantService(variants, baseStocks, movements, catalog, tenant);

        businessId = UUID.randomUUID();
        productId = UUID.randomUUID();

        CatalogItem product = new CatalogItem();
        product.setId(productId);
        product.setBusinessId(businessId);
        product.setKind(CatalogItem.Kind.PRODUCT);
        product.setName("Zapatilla");
        product.setActive(true);

        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(catalog.findByIdAndBusinessId(productId, businessId)).thenReturn(Optional.of(product));
        when(variants.saveAndFlush(any(InventoryProductVariant.class)))
                .thenAnswer(invocation -> {
                    InventoryProductVariant value = invocation.getArgument(0);
                    if (value.getId() == null) value.setId(UUID.randomUUID());
                    return value;
                });
        when(movements.save(any(InventoryMovement.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsVariantWithOwnSkuOptionsAndStock() {
        when(variants.existsByBusinessIdAndCatalogItemIdAndNameIgnoreCase(
                businessId, productId, "Negro / 42")).thenReturn(false);
        when(baseStocks.findByBusinessIdAndSkuIgnoreCase(businessId, "NIKE-N42"))
                .thenReturn(Optional.empty());
        when(variants.findByBusinessIdAndSkuIgnoreCase(businessId, "NIKE-N42"))
                .thenReturn(Optional.empty());

        InventoryVariantService.VariantView view = service.create(
                productId,
                new InventoryVariantService.VariantInput(
                        "Negro / 42",
                        "{"color":"Negro","talla":"42"}",
                        "nike-n42",
                        true,
                        5,
                        1,
                        true,
                        "initial stock"));

        assertEquals("NIKE-N42", view.sku());
        assertEquals(5, view.onHand());
        assertEquals(5, view.available());
        assertFalse(view.lowStock());
        assertTrue(view.optionValuesJson().contains(""color":"Negro""));

        ArgumentCaptor<InventoryMovement> movement =
                ArgumentCaptor.forClass(InventoryMovement.class);
        verify(movements).save(movement.capture());
        assertEquals(view.id(), movement.getValue().getVariantId());
        assertEquals(InventoryMovement.Type.CONFIGURE, movement.getValue().getType());
    }

    @Test
    void rejectsSkuAlreadyUsedByBaseInventory() {
        InventoryStock base = new InventoryStock();
        base.setBusinessId(businessId);
        base.setCatalogItemId(productId);
        base.setSku("NIKE-N42");

        when(variants.existsByBusinessIdAndCatalogItemIdAndNameIgnoreCase(
                businessId, productId, "Negro / 42")).thenReturn(false);
        when(baseStocks.findByBusinessIdAndSkuIgnoreCase(businessId, "NIKE-N42"))
                .thenReturn(Optional.of(base));

        assertThrows(ConflictException.class, () -> service.create(
                productId,
                new InventoryVariantService.VariantInput(
                        "Negro / 42", "{}", "NIKE-N42", true, 5, 1, true, null)));

        verify(variants, never()).saveAndFlush(any());
    }

    @Test
    void adjustmentCannotDropVariantBelowReservedStock() {
        UUID variantId = UUID.randomUUID();
        InventoryProductVariant variant = variant(variantId, 4, 3);
        when(variants.lockByIdAndBusinessId(variantId, businessId))
                .thenReturn(Optional.of(variant));

        assertThrows(ConflictException.class, () -> service.adjust(
                productId,
                variantId,
                new InventoryVariantService.AdjustmentInput(-2, "physical count")));

        assertEquals(4, variant.getOnHand());
        verify(movements, never()).save(any());
    }

    @Test
    void deactivationIsBlockedWhileVariantHasReservations() {
        UUID variantId = UUID.randomUUID();
        InventoryProductVariant variant = variant(variantId, 4, 1);
        when(variants.lockByIdAndBusinessId(variantId, businessId))
                .thenReturn(Optional.of(variant));

        assertThrows(ConflictException.class,
                () -> service.deactivate(productId, variantId));

        assertTrue(variant.isActive());
        verify(variants, never()).saveAndFlush(any());
    }

    @Test
    void historyIsScopedToTenantAndVariant() {
        UUID variantId = UUID.randomUUID();
        InventoryProductVariant variant = variant(variantId, 4, 0);
        when(variants.findByIdAndBusinessId(variantId, businessId))
                .thenReturn(Optional.of(variant));
        when(movements.findTop100ByBusinessIdAndVariantIdOrderByCreatedAtDesc(
                businessId, variantId)).thenReturn(List.of());

        assertTrue(service.history(productId, variantId).isEmpty());

        verify(movements).findTop100ByBusinessIdAndVariantIdOrderByCreatedAtDesc(
                businessId, variantId);
    }

    private InventoryProductVariant variant(UUID id, int onHand, int reserved) {
        InventoryProductVariant variant = new InventoryProductVariant();
        variant.setId(id);
        variant.setBusinessId(businessId);
        variant.setCatalogItemId(productId);
        variant.setName("Negro / 42");
        variant.setSku("NIKE-N42");
        variant.setOptionValuesJson("{"color":"Negro","talla":"42"}");
        variant.setTrackingEnabled(true);
        variant.setOnHand(onHand);
        variant.setReserved(reserved);
        variant.setReorderThreshold(1);
        variant.setActive(true);
        return variant;
    }
}

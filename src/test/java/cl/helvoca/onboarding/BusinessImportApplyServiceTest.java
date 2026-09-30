package cl.helvoca.onboarding;

import cl.helvoca.catalog.CatalogItem;
import cl.helvoca.catalog.UniversalCatalogService;
import cl.helvoca.inventory.InventoryService;
import cl.helvoca.security.TenantProvider;
import cl.helvoca.servicecatalog.ServiceCatalogService;
import cl.helvoca.servicecatalog.ServiceItemRequest;
import cl.helvoca.servicecatalog.ServiceItemResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BusinessImportApplyServiceTest {

    @Test
    void reimportMatchesExistingProductBySkuAndUpdatesInsteadOfDuplicating() {
        UUID businessId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        TenantProvider tenant = mock(TenantProvider.class);
        UniversalCatalogService catalog = mock(UniversalCatalogService.class);
        InventoryService inventory = mock(InventoryService.class);
        when(tenant.requireBusinessId()).thenReturn(businessId);

        when(catalog.list()).thenReturn(List.of(item(productId, "Hamburguesa antigua", "5990", "{\"keep\":true}")));
        when(inventory.list()).thenReturn(List.of(
                new InventoryService.StockView(productId, "Hamburguesa antigua", "HAM-01",
                        true, 10, 2, 8, 3, false)));
        when(catalog.update(eq(productId), any())).thenAnswer(inv -> {
            UniversalCatalogService.ItemInput input = inv.getArgument(1);
            return new UniversalCatalogService.ItemView(productId, input.kind(), input.name(), input.description(),
                    input.price(), input.currency(), input.durationMinutes(), input.metadataJson(), true, null);
        });

        BusinessImportApplyService service =
                new BusinessImportApplyService(catalog, inventory, tenant);

        var result = service.apply(new BusinessImportApplyService.ApplyRequest(List.of(
                new BusinessImportApplyService.ProductInput(
                        "Hamburguesa clásica", "Carne y queso", new BigDecimal("6990"), "CLP",
                        "HAM-01", 12, "Hamburguesas", "productos.xlsx")
        )));

        assertEquals(0, result.created());
        assertEquals(1, result.updated());
        assertEquals(1, result.inventoryConfigured());
        verify(catalog, never()).create(any());
        verify(catalog).update(eq(productId), argThat(input ->
                input.name().equals("Hamburguesa clásica")
                        && input.price().compareTo(new BigDecimal("6990")) == 0
                        && input.metadataJson().contains("\"keep\":true")
                        && input.metadataJson().contains("Hamburguesas")));
        verify(inventory).configure(eq(productId), argThat(input ->
                input.sku().equals("HAM-01")
                        && input.onHand() == 12
                        && input.reorderThreshold() == 3
                        && Boolean.TRUE.equals(input.trackingEnabled())));
    }

    @Test
    void productWithoutStockDoesNotOverwriteExistingInventory() {
        UUID businessId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        TenantProvider tenant = mock(TenantProvider.class);
        UniversalCatalogService catalog = mock(UniversalCatalogService.class);
        InventoryService inventory = mock(InventoryService.class);
        when(tenant.requireBusinessId()).thenReturn(businessId);

        when(catalog.list()).thenReturn(List.of(item(productId, "Bebida", "1990", null)));
        when(inventory.list()).thenReturn(List.of(
                new InventoryService.StockView(productId, "Bebida", "BEB-01",
                        true, 25, 4, 21, 5, false)));
        when(catalog.update(eq(productId), any())).thenReturn(item(productId, "Bebida", "1990", null));

        BusinessImportApplyService service =
                new BusinessImportApplyService(catalog, inventory, tenant);

        var result = service.apply(new BusinessImportApplyService.ApplyRequest(List.of(
                new BusinessImportApplyService.ProductInput(
                        "Bebida", null, null, "CLP", null, null, null, "menu.jpg")
        )));

        assertEquals(1, result.updated());
        assertEquals(0, result.inventoryConfigured());
        verify(inventory, never()).configure(any(), any());
    }

    @Test
    void newProductCreatesCatalogAndConfiguresImportedStock() {
        UUID businessId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        TenantProvider tenant = mock(TenantProvider.class);
        UniversalCatalogService catalog = mock(UniversalCatalogService.class);
        InventoryService inventory = mock(InventoryService.class);
        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(catalog.list()).thenReturn(List.of());
        when(inventory.list()).thenReturn(List.of());
        when(catalog.create(any())).thenReturn(item(productId, "Papas", "3490", null));

        BusinessImportApplyService service =
                new BusinessImportApplyService(catalog, inventory, tenant);

        var result = service.apply(new BusinessImportApplyService.ApplyRequest(List.of(
                new BusinessImportApplyService.ProductInput(
                        "Papas", null, new BigDecimal("3490"), "CLP",
                        "PAP-01", 30, "Extras", "menu.pdf")
        )));

        assertEquals(1, result.created());
        assertEquals(0, result.updated());
        assertEquals(1, result.inventoryConfigured());
        verify(catalog).create(any());
        verify(inventory).configure(eq(productId), argThat(input ->
                input.sku().equals("PAP-01") && input.onHand() == 30));
    }

    private static UniversalCatalogService.ItemView item(UUID id, String name, String price, String metadata) {
        return new UniversalCatalogService.ItemView(
                id,
                CatalogItem.Kind.PRODUCT,
                name,
                null,
                price == null ? null : new BigDecimal(price),
                "CLP",
                null,
                metadata,
                true,
                null);
    }

    @Test
    void serviceImportCreatesServiceWithoutInventory() throws Exception {
        UUID businessId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        TenantProvider tenant = mock(TenantProvider.class);
        UniversalCatalogService catalog = mock(UniversalCatalogService.class);
        InventoryService inventory = mock(InventoryService.class);
        ServiceCatalogService services = mock(ServiceCatalogService.class);
        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(catalog.list()).thenReturn(List.of());
        when(inventory.list()).thenReturn(List.of());
        when(services.list()).thenReturn(List.of());
        when(services.create(any())).thenAnswer(inv -> {
            ServiceItemRequest input = inv.getArgument(0);
            return new ServiceItemResponse(serviceId, input.name(), input.description(),
                    input.durationMinutes(), input.price(), true, null, null);
        });

        BusinessImportApplyService.ProductInput serviceInput =
                BusinessImportApplyService.ProductInput.class
                        .getDeclaredConstructor(String.class, String.class, BigDecimal.class, String.class,
                                String.class, Integer.class, String.class, CatalogItem.Kind.class,
                                Integer.class, String.class)
                        .newInstance("Consulta Veterinaria", "Evaluación general", new BigDecimal("20000"), "CLP",
                                null, null, "Consulta", CatalogItem.Kind.SERVICE, 30, "servicios.csv");

        var service = businessImportService(catalog, inventory, tenant, services);
        var result = service.apply(new BusinessImportApplyService.ApplyRequest(List.of(serviceInput)));

        assertEquals(1, result.created());
        assertEquals(0, result.inventoryConfigured());
        verify(services).create(argThat(input ->
                Integer.valueOf(30).equals(input.durationMinutes())
                        && input.name().equals("Consulta Veterinaria")
                        && input.price().compareTo(new BigDecimal("20000")) == 0));
        verify(catalog, never()).create(any());
        verify(inventory, never()).configure(any(), any());
    }

    @Test
    void reimportedServiceUpdatesExistingServiceInsteadOfCreatingDuplicate() throws Exception {
        UUID businessId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        TenantProvider tenant = mock(TenantProvider.class);
        UniversalCatalogService catalog = mock(UniversalCatalogService.class);
        InventoryService inventory = mock(InventoryService.class);
        ServiceCatalogService services = mock(ServiceCatalogService.class);
        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(catalog.list()).thenReturn(List.of());
        when(inventory.list()).thenReturn(List.of());
        when(services.list()).thenReturn(List.of(new ServiceItemResponse(
                serviceId, "Consulta Veterinaria", null, 20,
                new BigDecimal("18000"), true, null, null)));
        when(services.update(eq(serviceId), any())).thenAnswer(inv -> {
            ServiceItemRequest input = inv.getArgument(1);
            return new ServiceItemResponse(serviceId, input.name(), input.description(),
                    input.durationMinutes(), input.price(), true, null, null);
        });

        BusinessImportApplyService.ProductInput serviceInput =
                BusinessImportApplyService.ProductInput.class
                        .getDeclaredConstructor(String.class, String.class, BigDecimal.class, String.class,
                                String.class, Integer.class, String.class, CatalogItem.Kind.class,
                                Integer.class, String.class)
                        .newInstance("Consulta Veterinaria", null, new BigDecimal("20000"), "CLP",
                                null, null, "Consulta", CatalogItem.Kind.SERVICE, 30, "servicios.csv");

        var service = businessImportService(catalog, inventory, tenant, services);
        var result = service.apply(new BusinessImportApplyService.ApplyRequest(List.of(serviceInput)));

        assertEquals(0, result.created());
        assertEquals(1, result.updated());
        verify(services, never()).create(any());
        verify(services).update(eq(serviceId), argThat(input ->
                Integer.valueOf(30).equals(input.durationMinutes())
                        && input.price().compareTo(new BigDecimal("20000")) == 0));
        verify(catalog, never()).update(any(), any());
        verify(inventory, never()).configure(any(), any());
    }
    private static BusinessImportApplyService businessImportService(
            UniversalCatalogService catalog,
            InventoryService inventory,
            TenantProvider tenant,
            ServiceCatalogService services) throws Exception {
        return BusinessImportApplyService.class.getDeclaredConstructor(
                        UniversalCatalogService.class,
                        InventoryService.class,
                        TenantProvider.class,
                        ServiceCatalogService.class)
                .newInstance(catalog, inventory, tenant, services);
    }

}

package cl.helvoca.catalog;

import cl.helvoca.audit.AuditService;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CatalogMediaServiceTest {

    @Test
    void createsHttpsProductMediaWithTenantScopeAndAudit() {
        UUID businessId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();

        CatalogMediaRepository media = mock(CatalogMediaRepository.class);
        CatalogItemRepository catalog = mock(CatalogItemRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);
        AuditService audit = mock(AuditService.class);

        CatalogItem product = new CatalogItem();
        product.setId(itemId);
        product.setBusinessId(businessId);
        product.setKind(CatalogItem.Kind.PRODUCT);
        product.setName("Shampoo");
        product.setActive(true);

        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(catalog.findByIdAndBusinessId(itemId, businessId)).thenReturn(Optional.of(product));
        when(media.saveAndFlush(any(CatalogMedia.class))).thenAnswer(invocation -> {
            CatalogMedia saved = invocation.getArgument(0);
            saved.setId(mediaId);
            return saved;
        });

        CatalogMediaService service = new CatalogMediaService(media, catalog, tenant, audit);
        CatalogMediaService.MediaView created = service.create(
                itemId,
                new CatalogMediaService.MediaInput(
                        CatalogMedia.Type.IMAGE,
                        "https://cdn.example.test/products/shampoo.jpg",
                        "image/jpeg",
                        "Shampoo hidratante",
                        1,
                        true));

        assertEquals(mediaId, created.id());
        assertEquals(itemId, created.catalogItemId());
        assertEquals(CatalogMedia.Type.IMAGE, created.mediaType());
        assertEquals("https://cdn.example.test/products/shampoo.jpg", created.mediaUrl());
        verify(audit).humanSuccess(
                eq(businessId),
                eq("CATALOG_MEDIA_CREATE"),
                eq("CATALOG_MEDIA"),
                eq(mediaId),
                isNull(),
                argThat(after -> itemId.equals(after.get("catalogItemId"))
                        && "IMAGE".equals(after.get("mediaType"))
                        && Boolean.TRUE.equals(after.get("active"))));
    }

    @Test
    void rejectsNonHttpsUrlsAndMismatchedMimeTypes() {
        UUID businessId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        CatalogMediaRepository media = mock(CatalogMediaRepository.class);
        CatalogItemRepository catalog = mock(CatalogItemRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);
        AuditService audit = mock(AuditService.class);

        CatalogItem product = new CatalogItem();
        product.setId(itemId);
        product.setBusinessId(businessId);
        product.setKind(CatalogItem.Kind.PRODUCT);
        product.setName("Producto");
        product.setActive(true);

        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(catalog.findByIdAndBusinessId(itemId, businessId)).thenReturn(Optional.of(product));

        CatalogMediaService service = new CatalogMediaService(media, catalog, tenant, audit);

        assertThrows(IllegalArgumentException.class, () -> service.create(
                itemId,
                new CatalogMediaService.MediaInput(
                        CatalogMedia.Type.IMAGE,
                        "http://cdn.example.test/image.jpg",
                        "image/jpeg",
                        null,
                        0,
                        true)));

        assertThrows(IllegalArgumentException.class, () -> service.create(
                itemId,
                new CatalogMediaService.MediaInput(
                        CatalogMedia.Type.VIDEO,
                        "https://cdn.example.test/image.jpg",
                        "image/jpeg",
                        null,
                        0,
                        true)));

        verify(media, never()).saveAndFlush(any());
        verifyNoInteractions(audit);
    }
}

package cl.helvoca.operations;

import cl.helvoca.catalog.CatalogItem;
import cl.helvoca.catalog.CatalogItemRepository;
import cl.helvoca.catalog.CatalogMedia;
import cl.helvoca.catalog.CatalogMediaRepository;
import cl.helvoca.messaging.outbound.OutboundMessage;
import cl.helvoca.messaging.outbound.OutboundMessagingService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CatalogShowcaseMessagingServiceTest {

    @Test
    void selectsPrimaryActiveMediaAndPreservesOperation() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        CatalogItemRepository catalog = mock(CatalogItemRepository.class);
        CatalogMediaRepository media = mock(CatalogMediaRepository.class);
        BusinessOperationRepository operations = mock(BusinessOperationRepository.class);
        OutboundMessagingService outbound = mock(OutboundMessagingService.class);

        CatalogItem product = new CatalogItem();
        product.setId(productId);
        product.setBusinessId(businessId);
        product.setKind(CatalogItem.Kind.PRODUCT);
        product.setName("Shampoo");
        product.setActive(true);

        CatalogMedia image = new CatalogMedia();
        image.setId(mediaId);
        image.setBusinessId(businessId);
        image.setCatalogItemId(productId);
        image.setMediaType(CatalogMedia.Type.IMAGE);
        image.setMediaUrl("https://cdn.example.test/shampoo.jpg");
        image.setSortOrder(0);
        image.setActive(true);

        BusinessOperation operation = new BusinessOperation();
        operation.setId(operationId);
        operation.setBusinessId(businessId);
        operation.setCustomerId(customerId);
        operation.setType(BusinessOperation.Type.REQUEST);
        operation.setStatus(BusinessOperation.Status.CONFIRMED);

        OutboundMessage prepared = new OutboundMessage();
        prepared.setId(messageId);
        prepared.setBusinessId(businessId);
        prepared.setCustomerId(customerId);
        prepared.setOperationId(operationId);
        prepared.setStatus(OutboundMessage.Status.PREPARED);

        when(operations.findByIdAndBusinessId(operationId, businessId)).thenReturn(Optional.of(operation));
        when(catalog.findByIdAndBusinessId(productId, businessId)).thenReturn(Optional.of(product));
        when(media.findAllByBusinessIdAndCatalogItemIdAndActiveTrueOrderBySortOrderAscCreatedAtAsc(
                businessId, productId)).thenReturn(List.of(image));
        when(outbound.prepareCatalogMedia(
                businessId, customerId, operationId, null, mediaId)).thenReturn(prepared);

        CatalogShowcaseMessagingService service =
                new CatalogShowcaseMessagingService(catalog, media, operations, outbound);

        var result = service.prepare(
                businessId, customerId, operationId, null, List.of(productId));

        assertEquals(1, result.size());
        assertEquals(operationId, result.getFirst().message().getOperationId());
        assertEquals(productId, result.getFirst().catalogItemId());
        assertEquals(mediaId, result.getFirst().catalogMediaId());
        assertEquals(CatalogMedia.Type.IMAGE, result.getFirst().mediaType());
        verify(outbound).prepareCatalogMedia(
                businessId, customerId, operationId, null, mediaId);
        verify(operations).saveAndFlush(operation);
        assertEquals("WHATSAPP", operation.getMetadata().get("handoffChannel"));
        assertEquals("MEDIA_PREPARED", operation.getMetadata().get("commercialStage"));
    }

    @Test
    void failsClosedWhenSelectedProductHasNoActiveMedia() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        CatalogItemRepository catalog = mock(CatalogItemRepository.class);
        CatalogMediaRepository media = mock(CatalogMediaRepository.class);
        BusinessOperationRepository operations = mock(BusinessOperationRepository.class);
        OutboundMessagingService outbound = mock(OutboundMessagingService.class);

        CatalogItem product = new CatalogItem();
        product.setId(productId);
        product.setBusinessId(businessId);
        product.setKind(CatalogItem.Kind.PRODUCT);
        product.setName("Producto sin foto");
        product.setActive(true);

        BusinessOperation operation = new BusinessOperation();
        operation.setId(operationId);
        operation.setBusinessId(businessId);
        operation.setCustomerId(customerId);
        operation.setType(BusinessOperation.Type.REQUEST);
        operation.setStatus(BusinessOperation.Status.CONFIRMED);

        when(operations.findByIdAndBusinessId(operationId, businessId)).thenReturn(Optional.of(operation));
        when(catalog.findByIdAndBusinessId(productId, businessId)).thenReturn(Optional.of(product));
        when(media.findAllByBusinessIdAndCatalogItemIdAndActiveTrueOrderBySortOrderAscCreatedAtAsc(
                businessId, productId)).thenReturn(List.of());

        CatalogShowcaseMessagingService service =
                new CatalogShowcaseMessagingService(catalog, media, operations, outbound);

        var error = assertThrows(IllegalStateException.class, () -> service.prepare(
                businessId, customerId, operationId, null, List.of(productId)));

        assertTrue(error.getMessage().contains("no active media"));
        verifyNoInteractions(outbound);
        verify(operations, never()).saveAndFlush(any());
    }

    @Test
    void rejectsOperationOwnedByAnotherCustomer() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();

        CatalogItemRepository catalog = mock(CatalogItemRepository.class);
        CatalogMediaRepository media = mock(CatalogMediaRepository.class);
        BusinessOperationRepository operations = mock(BusinessOperationRepository.class);
        OutboundMessagingService outbound = mock(OutboundMessagingService.class);

        BusinessOperation operation = new BusinessOperation();
        operation.setId(operationId);
        operation.setBusinessId(businessId);
        operation.setCustomerId(UUID.randomUUID());
        when(operations.findByIdAndBusinessId(operationId, businessId)).thenReturn(Optional.of(operation));

        CatalogShowcaseMessagingService service =
                new CatalogShowcaseMessagingService(catalog, media, operations, outbound);

        assertThrows(IllegalArgumentException.class, () -> service.prepare(
                businessId, customerId, operationId, null, List.of(UUID.randomUUID())));

        verifyNoInteractions(catalog, media, outbound);
    }
}

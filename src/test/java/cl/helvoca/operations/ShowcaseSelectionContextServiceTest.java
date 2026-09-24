package cl.helvoca.operations;

import cl.helvoca.catalog.CatalogItem;
import cl.helvoca.catalog.CatalogItemRepository;
import cl.helvoca.messaging.MessagingConversation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ShowcaseSelectionContextServiceTest {

    @Test
    void resolvesOrdinalPositionsFromSharedOperationInExactShowcaseOrder() {
        UUID businessId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();

        ConversationStateService conversationState = mock(ConversationStateService.class);
        BusinessOperationRepository operations = mock(BusinessOperationRepository.class);
        CatalogItemRepository catalog = mock(CatalogItemRepository.class);
        MessagingConversation conversation = mock(MessagingConversation.class);

        when(conversation.getBusinessId()).thenReturn(businessId);
        when(conversation.getId()).thenReturn(conversationId);
        when(conversation.getCustomerId()).thenReturn(customerId);

        ConversationOperationState state = new ConversationOperationState();
        state.setActiveOperationId(operationId);
        when(conversationState.find(businessId, conversationId, BusinessOrder.Source.WHATSAPP))
                .thenReturn(state);

        BusinessOperation operation = new BusinessOperation();
        operation.setId(operationId);
        operation.setBusinessId(businessId);
        operation.setCustomerId(customerId);
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("handoffChannel", "WHATSAPP");
        metadata.put("showcaseCatalogItemIds", List.of(firstId.toString(), secondId.toString()));
        operation.setMetadata(metadata);
        when(operations.findByIdAndBusinessId(operationId, businessId)).thenReturn(Optional.of(operation));

        CatalogItem first = item(firstId, businessId, "Shampoo");
        CatalogItem second = item(secondId, businessId, "Acondicionador");
        when(catalog.findByIdAndBusinessId(firstId, businessId)).thenReturn(Optional.of(first));
        when(catalog.findByIdAndBusinessId(secondId, businessId)).thenReturn(Optional.of(second));

        ShowcaseSelectionContextService service =
                new ShowcaseSelectionContextService(conversationState, operations, catalog);

        String instructions = service.instructions(conversation);

        assertTrue(instructions.contains("operationId=" + operationId));
        assertTrue(instructions.contains("1 = Shampoo [catalogItemId=" + firstId + "]"));
        assertTrue(instructions.contains("2 = Acondicionador [catalogItemId=" + secondId + "]"));
        assertTrue(instructions.indexOf("1 = Shampoo") < instructions.indexOf("2 = Acondicionador"));
        assertTrue(instructions.contains("primero/segundo/tercero"));
        assertTrue(instructions.contains("No muestres UUID"));
    }

    @Test
    void failsClosedWhenSharedOperationBelongsToAnotherCustomer() {
        UUID businessId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();

        ConversationStateService conversationState = mock(ConversationStateService.class);
        BusinessOperationRepository operations = mock(BusinessOperationRepository.class);
        CatalogItemRepository catalog = mock(CatalogItemRepository.class);
        MessagingConversation conversation = mock(MessagingConversation.class);

        when(conversation.getBusinessId()).thenReturn(businessId);
        when(conversation.getId()).thenReturn(conversationId);
        when(conversation.getCustomerId()).thenReturn(customerId);

        ConversationOperationState state = new ConversationOperationState();
        state.setActiveOperationId(operationId);
        when(conversationState.find(businessId, conversationId, BusinessOrder.Source.WHATSAPP))
                .thenReturn(state);

        BusinessOperation operation = new BusinessOperation();
        operation.setId(operationId);
        operation.setBusinessId(businessId);
        operation.setCustomerId(UUID.randomUUID());
        operation.setMetadata(new LinkedHashMap<>(java.util.Map.of(
                "handoffChannel", "WHATSAPP",
                "showcaseCatalogItemIds", List.of(UUID.randomUUID().toString()))));
        when(operations.findByIdAndBusinessId(operationId, businessId)).thenReturn(Optional.of(operation));

        ShowcaseSelectionContextService service =
                new ShowcaseSelectionContextService(conversationState, operations, catalog);

        assertEquals("", service.instructions(conversation));
        verifyNoInteractions(catalog);
    }

    @Test
    void ignoresCorruptAndInactiveShowcaseEntriesInsteadOfGuessing() {
        UUID businessId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        UUID activeId = UUID.randomUUID();
        UUID inactiveId = UUID.randomUUID();

        ConversationStateService conversationState = mock(ConversationStateService.class);
        BusinessOperationRepository operations = mock(BusinessOperationRepository.class);
        CatalogItemRepository catalog = mock(CatalogItemRepository.class);
        MessagingConversation conversation = mock(MessagingConversation.class);

        when(conversation.getBusinessId()).thenReturn(businessId);
        when(conversation.getId()).thenReturn(conversationId);
        when(conversation.getCustomerId()).thenReturn(customerId);

        ConversationOperationState state = new ConversationOperationState();
        state.setActiveOperationId(operationId);
        when(conversationState.find(businessId, conversationId, BusinessOrder.Source.WHATSAPP))
                .thenReturn(state);

        BusinessOperation operation = new BusinessOperation();
        operation.setId(operationId);
        operation.setBusinessId(businessId);
        operation.setCustomerId(customerId);
        operation.setMetadata(new LinkedHashMap<>(java.util.Map.of(
                "handoffChannel", "WHATSAPP",
                "showcaseCatalogItemIds", List.of("not-a-uuid", activeId.toString(), inactiveId.toString()))));
        when(operations.findByIdAndBusinessId(operationId, businessId)).thenReturn(Optional.of(operation));

        when(catalog.findByIdAndBusinessId(activeId, businessId))
                .thenReturn(Optional.of(item(activeId, businessId, "Activo")));
        CatalogItem inactive = item(inactiveId, businessId, "Inactivo");
        inactive.setActive(false);
        when(catalog.findByIdAndBusinessId(inactiveId, businessId)).thenReturn(Optional.of(inactive));

        ShowcaseSelectionContextService service =
                new ShowcaseSelectionContextService(conversationState, operations, catalog);

        String instructions = service.instructions(conversation);

        assertTrue(instructions.contains("1 = Activo"));
        assertFalse(instructions.contains("Inactivo"));
        assertFalse(instructions.contains("not-a-uuid"));
    }

    private static CatalogItem item(UUID id, UUID businessId, String name) {
        CatalogItem item = new CatalogItem();
        item.setId(id);
        item.setBusinessId(businessId);
        item.setKind(CatalogItem.Kind.PRODUCT);
        item.setName(name);
        item.setCurrency("CLP");
        item.setActive(true);
        return item;
    }
}

package cl.helvoca.operations;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationStateServiceTest {
    @Mock ConversationOperationStateRepository repository;

    @Test
    void latestCorrectionReplacesConflictingValueAndIncrementsRevision() {
        UUID businessId = UUID.randomUUID();
        UUID sourceReferenceId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();

        ConversationOperationState existing = new ConversationOperationState();
        ReflectionTestUtils.setField(existing, "id", UUID.randomUUID());
        existing.setBusinessId(businessId);
        existing.setSourceReferenceId(sourceReferenceId);
        existing.setChannel(BusinessOrder.Source.WHATSAPP);
        existing.setActiveOperationId(operationId);
        existing.setRevision(3);
        existing.setState(new LinkedHashMap<>(Map.of(
                "intent", "ORDER",
                "quantity", 2,
                "deliveryAddress", "Calle Antigua 10")));

        when(repository.findByBusinessIdAndChannelAndSourceReferenceId(
                businessId, BusinessOrder.Source.WHATSAPP, sourceReferenceId))
                .thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(ConversationOperationState.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ConversationStateService service = new ConversationStateService(repository);
        ConversationOperationState saved = service.apply(
                businessId,
                sourceReferenceId,
                BusinessOrder.Source.WHATSAPP,
                null,
                Map.of("quantity", 1, "deliveryAddress", "Calle Nueva 25"));

        assertEquals(4, saved.getRevision());
        assertEquals(1, saved.getState().get("quantity"));
        assertEquals("Calle Nueva 25", saved.getState().get("deliveryAddress"));
        assertEquals("ORDER", saved.getState().get("intent"));
        assertEquals(operationId, saved.getActiveOperationId(),
                "A patch without a new operation must preserve the active operation");
    }

    @Test
    void nullPatchValueRemovesObsoleteField() {
        UUID businessId = UUID.randomUUID();
        UUID sourceReferenceId = UUID.randomUUID();

        ConversationOperationState existing = new ConversationOperationState();
        ReflectionTestUtils.setField(existing, "id", UUID.randomUUID());
        existing.setBusinessId(businessId);
        existing.setSourceReferenceId(sourceReferenceId);
        existing.setChannel(BusinessOrder.Source.VOICE);
        existing.setRevision(1);
        existing.setState(new LinkedHashMap<>(Map.of(
                "deliveryAddress", "Dirección vieja",
                "intent", "ORDER")));

        when(repository.findByBusinessIdAndChannelAndSourceReferenceId(
                businessId, BusinessOrder.Source.VOICE, sourceReferenceId))
                .thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(ConversationOperationState.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("deliveryAddress", null);
        ConversationOperationState saved = new ConversationStateService(repository).apply(
                businessId, sourceReferenceId, BusinessOrder.Source.VOICE, null, patch);

        assertFalse(saved.getState().containsKey("deliveryAddress"));
        assertEquals("ORDER", saved.getState().get("intent"));
    }

    @Test
    void tenantAndChannelArePartOfTheLookupKey() {
        UUID businessId = UUID.randomUUID();
        UUID sourceReferenceId = UUID.randomUUID();
        when(repository.findByBusinessIdAndChannelAndSourceReferenceId(
                businessId, BusinessOrder.Source.VOICE, sourceReferenceId))
                .thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(ConversationOperationState.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        new ConversationStateService(repository).apply(
                businessId, sourceReferenceId, BusinessOrder.Source.VOICE,
                UUID.randomUUID(), Map.of("intent", "REQUEST"));

        ArgumentCaptor<ConversationOperationState> captor = ArgumentCaptor.forClass(ConversationOperationState.class);
        verify(repository).saveAndFlush(captor.capture());
        assertEquals(businessId, captor.getValue().getBusinessId());
        assertEquals(BusinessOrder.Source.VOICE, captor.getValue().getChannel());
        assertEquals(sourceReferenceId, captor.getValue().getSourceReferenceId());
    }
}

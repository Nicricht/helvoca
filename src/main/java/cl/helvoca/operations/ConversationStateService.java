package cl.helvoca.operations;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class ConversationStateService {
    private final ConversationOperationStateRepository repository;

    public ConversationStateService(ConversationOperationStateRepository repository) {
        this.repository = repository;
    }

    /**
     * Applies a top-level state patch. A newer value replaces the previous
     * conflicting value. A null value removes the key entirely.
     */
    @Transactional
    public ConversationOperationState apply(UUID businessId,
                                            UUID sourceReferenceId,
                                            BusinessOrder.Source channel,
                                            UUID activeOperationId,
                                            Map<String, Object> patch) {
        if (businessId == null) throw new IllegalArgumentException("businessId is required");
        if (sourceReferenceId == null) return null;
        BusinessOrder.Source safeChannel = channel == null ? BusinessOrder.Source.API : channel;

        ConversationOperationState state = repository
                .findByBusinessIdAndChannelAndSourceReferenceId(businessId, safeChannel, sourceReferenceId)
                .orElseGet(() -> {
                    ConversationOperationState created = new ConversationOperationState();
                    created.setBusinessId(businessId);
                    created.setSourceReferenceId(sourceReferenceId);
                    created.setChannel(safeChannel);
                    created.setRevision(1);
                    created.setState(new LinkedHashMap<>());
                    return created;
                });

        boolean existing = state.getId() != null;
        Map<String, Object> next = new LinkedHashMap<>();
        if (state.getState() != null) next.putAll(state.getState());
        if (patch != null) {
            for (Map.Entry<String, Object> entry : patch.entrySet()) {
                if (entry.getValue() == null) next.remove(entry.getKey());
                else next.put(entry.getKey(), entry.getValue());
            }
        }

        state.setActiveOperationId(activeOperationId);
        state.setState(next);
        if (existing) state.setRevision(state.getRevision() == null ? 1 : state.getRevision() + 1);
        return repository.saveAndFlush(state);
    }

    @Transactional(readOnly = true)
    public ConversationOperationState find(UUID businessId,
                                           UUID sourceReferenceId,
                                           BusinessOrder.Source channel) {
        if (businessId == null || sourceReferenceId == null) return null;
        BusinessOrder.Source safeChannel = channel == null ? BusinessOrder.Source.API : channel;
        return repository.findByBusinessIdAndChannelAndSourceReferenceId(
                businessId, safeChannel, sourceReferenceId).orElse(null);
    }
}

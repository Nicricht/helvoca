package cl.helvoca.operations;

import cl.helvoca.omnichannel.OmnichannelSession;
import cl.helvoca.omnichannel.OmnichannelSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class ConversationStateService {
    private final ConversationOperationStateRepository repository;
    private final OmnichannelSessionService omnichannel;

    /** Kept for focused unit tests and non-Spring construction. */
    public ConversationStateService(ConversationOperationStateRepository repository) {
        this(repository, null);
    }

    @Autowired
    public ConversationStateService(ConversationOperationStateRepository repository,
                                    OmnichannelSessionService omnichannel) {
        this.repository = repository;
        this.omnichannel = omnichannel;
    }

    /**
     * Applies a top-level state patch. Identified voice/WhatsApp sources for the
     * same tenant customer resolve to one omnichannel session and therefore one
     * shared operational state. Anonymous sources remain channel-local.
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
        OmnichannelSession session = resolveSession(businessId, sourceReferenceId, safeChannel);

        ConversationOperationState state = lookup(
                businessId, sourceReferenceId, safeChannel, session, true);
        boolean existing = state.getId() != null;

        Map<String, Object> next = new LinkedHashMap<>();
        if (state.getState() != null) next.putAll(state.getState());
        if (patch != null) {
            for (Map.Entry<String, Object> entry : patch.entrySet()) {
                if (entry.getValue() == null) next.remove(entry.getKey());
                else next.put(entry.getKey(), entry.getValue());
            }
        }

        if (activeOperationId != null) state.setActiveOperationId(activeOperationId);
        state.setState(next);
        if (session != null) state.setOmnichannelSessionId(session.getId());
        if (existing) state.setRevision(state.getRevision() == null ? 1 : state.getRevision() + 1);
        return repository.saveAndFlush(state);
    }

    /**
     * A read may safely attach the current legacy state to an already verified
     * omnichannel session. It never creates a cross-customer association.
     */
    @Transactional
    public ConversationOperationState find(UUID businessId,
                                           UUID sourceReferenceId,
                                           BusinessOrder.Source channel) {
        if (businessId == null || sourceReferenceId == null) return null;
        BusinessOrder.Source safeChannel = channel == null ? BusinessOrder.Source.API : channel;
        OmnichannelSession session = resolveSession(businessId, sourceReferenceId, safeChannel);
        ConversationOperationState state = lookup(
                businessId, sourceReferenceId, safeChannel, session, false);
        if (state != null && session != null && state.getOmnichannelSessionId() == null) {
            state.setOmnichannelSessionId(session.getId());
            state.setRevision(state.getRevision() == null ? 1 : state.getRevision() + 1);
            return repository.saveAndFlush(state);
        }
        return state;
    }

    private ConversationOperationState lookup(UUID businessId,
                                              UUID sourceReferenceId,
                                              BusinessOrder.Source channel,
                                              OmnichannelSession session,
                                              boolean createWhenMissing) {
        if (session != null) {
            ConversationOperationState shared = repository
                    .findFirstByBusinessIdAndOmnichannelSessionIdOrderByUpdatedAtDesc(
                            businessId, session.getId())
                    .orElse(null);
            if (shared != null) return shared;
        }

        ConversationOperationState local = repository
                .findByBusinessIdAndChannelAndSourceReferenceId(businessId, channel, sourceReferenceId)
                .orElse(null);
        if (local != null || !createWhenMissing) return local;

        ConversationOperationState created = new ConversationOperationState();
        created.setBusinessId(businessId);
        created.setSourceReferenceId(sourceReferenceId);
        created.setChannel(channel);
        created.setOmnichannelSessionId(session == null ? null : session.getId());
        created.setRevision(1);
        created.setState(new LinkedHashMap<>());
        return created;
    }

    private OmnichannelSession resolveSession(UUID businessId,
                                              UUID sourceReferenceId,
                                              BusinessOrder.Source channel) {
        return omnichannel == null ? null : omnichannel.resolve(businessId, sourceReferenceId, channel);
    }
}

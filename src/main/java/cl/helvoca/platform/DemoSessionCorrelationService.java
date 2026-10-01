package cl.helvoca.platform;

import cl.helvoca.call.CallSession;
import cl.helvoca.call.CallSessionRepository;
import cl.helvoca.messaging.MessagingConversation;
import cl.helvoca.messaging.MessagingConversationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class DemoSessionCorrelationService {
    private final DemoRuntimeProperties properties;
    private final DemoSessionRepository sessions;
    private final CallSessionRepository calls;
    private final MessagingConversationRepository conversations;

    public DemoSessionCorrelationService(DemoRuntimeProperties properties,
                                         DemoSessionRepository sessions,
                                         CallSessionRepository calls,
                                         MessagingConversationRepository conversations) {
        this.properties = properties;
        this.sessions = sessions;
        this.calls = calls;
        this.conversations = conversations;
    }

    @Transactional(readOnly = true)
    public Optional<UUID> activeSessionIdForBusiness(UUID businessId) {
        UUID runtimeId = properties.runtimeBusinessUuid();
        if (runtimeId == null || businessId == null || !runtimeId.equals(businessId)) {
            return Optional.empty();
        }
        return sessions.findFirstByRuntimeBusinessIdAndStateInOrderByCreatedAtDesc(
                        runtimeId, List.of(DemoSessionState.ACTIVE))
                .map(DemoSession::getId);
    }

    @Transactional(readOnly = true)
    public Optional<UUID> resolveForSource(UUID businessId, UUID sourceReferenceId) {
        UUID runtimeId = properties.runtimeBusinessUuid();
        if (runtimeId == null || businessId == null || sourceReferenceId == null
                || !runtimeId.equals(businessId)) {
            return Optional.empty();
        }

        UUID candidate = calls.findByIdAndBusinessId(sourceReferenceId, runtimeId)
                .map(CallSession::getDemoSessionId)
                .orElse(null);
        if (candidate == null) {
            candidate = conversations.findByIdAndBusinessId(sourceReferenceId, runtimeId)
                    .map(MessagingConversation::getDemoSessionId)
                    .orElse(null);
        }
        if (candidate == null) return Optional.empty();

        return sessions.findByIdAndRuntimeBusinessId(candidate, runtimeId)
                .filter(value -> value.getState() == DemoSessionState.ACTIVE)
                .map(DemoSession::getId);
    }
}

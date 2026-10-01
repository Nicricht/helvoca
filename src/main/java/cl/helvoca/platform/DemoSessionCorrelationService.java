package cl.helvoca.platform;

import cl.helvoca.audit.AuditService;
import cl.helvoca.call.CallSession;
import cl.helvoca.call.CallSessionRepository;
import cl.helvoca.call.CallStatus;
import cl.helvoca.messaging.MessagingConversation;
import cl.helvoca.messaging.MessagingConversationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class DemoSessionCorrelationService {
    private final DemoRuntimeProperties properties;
    private final DemoSessionRepository sessions;
    private final CallSessionRepository calls;
    private final MessagingConversationRepository conversations;
    private DemoInboundSafetyReadiness inboundSafety;
    private AuditService audit;

    public DemoSessionCorrelationService(DemoRuntimeProperties properties,
                                         DemoSessionRepository sessions,
                                         CallSessionRepository calls,
                                         MessagingConversationRepository conversations) {
        this.properties = properties;
        this.sessions = sessions;
        this.calls = calls;
        this.conversations = conversations;
    }

    @Autowired
    void setInboundSafety(DemoInboundSafetyReadiness inboundSafety) {
        this.inboundSafety = inboundSafety;
    }

    @Autowired
    void setAudit(AuditService audit) {
        this.audit = audit;
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

    @Transactional
    public Optional<UUID> activeOrActivateForInboundVoice(UUID businessId) {
        UUID runtimeId = properties.runtimeBusinessUuid();
        if (runtimeId == null || businessId == null || !runtimeId.equals(businessId)) {
            return Optional.empty();
        }

        DemoSession candidate = sessions.findPreparedForRuntime(runtimeId).orElse(null);
        if (candidate == null) return Optional.empty();
        if (candidate.getState() == DemoSessionState.ACTIVE) {
            return Optional.of(candidate.getId());
        }
        if (candidate.getState() != DemoSessionState.READY || inboundSafety == null || audit == null) {
            return Optional.empty();
        }

        if (inboundSafety.safeSnapshot(runtimeId).isEmpty()) return Optional.empty();

        DemoSession locked = sessions.findForUpdateByIdAndRuntimeBusinessId(candidate.getId(), runtimeId)
                .orElse(null);
        if (locked == null) return Optional.empty();
        if (locked.getState() == DemoSessionState.ACTIVE) return Optional.of(locked.getId());
        if (locked.getState() != DemoSessionState.READY) return Optional.empty();

        Optional<Map<String, Object>> lockedSnapshot = inboundSafety.safeSnapshot(runtimeId);
        if (lockedSnapshot.isEmpty()) return Optional.empty();

        locked.setReadinessSnapshot(lockedSnapshot.get());
        locked.markActive();
        sessions.saveAndFlush(locked);
        audit.success(runtimeId, "DEMO_SESSION_AUTO_ACTIVE", "DEMO_SESSION", locked.getId());
        return Optional.of(locked.getId());
    }

    @Transactional
    public void finishIfTerminal(CallSession call) {
        UUID runtimeId = properties.runtimeBusinessUuid();
        if (runtimeId == null || call == null || call.getDemoSessionId() == null
                || !runtimeId.equals(call.getBusinessId())
                || call.getStatus() == null || !call.getStatus().terminal()) {
            return;
        }

        DemoSession session = sessions.findForUpdateByIdAndRuntimeBusinessId(
                call.getDemoSessionId(), runtimeId).orElse(null);
        if (session == null || session.getState() != DemoSessionState.ACTIVE) return;

        long liveCalls = calls.countByBusinessIdAndDemoSessionIdAndStatusIn(
                runtimeId,
                session.getId(),
                List.of(CallStatus.QUEUED, CallStatus.RINGING, CallStatus.IN_PROGRESS));
        if (liveCalls > 0) return;

        session.markFinished();
        sessions.saveAndFlush(session);
        if (audit != null) {
            audit.success(runtimeId, "DEMO_SESSION_AUTO_FINISHED", "DEMO_SESSION", session.getId());
        }
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

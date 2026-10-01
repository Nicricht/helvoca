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
import java.util.Optional;
import java.util.UUID;

@Service
public class DemoSessionCorrelationService {
    private final DemoRuntimeProperties properties;
    private final DemoSessionRepository sessions;
    private final CallSessionRepository calls;
    private final MessagingConversationRepository conversations;
    private PlatformDemoReadinessService readiness;
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
    void setReadiness(PlatformDemoReadinessService readiness) {
        this.readiness = readiness;
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
        if (candidate.getState() != DemoSessionState.READY || readiness == null || audit == null) {
            return Optional.empty();
        }

        PlatformDemoReadinessResponse current = readiness.readiness();
        if (!safeForInboundVoice(runtimeId, current)) return Optional.empty();

        DemoSession locked = sessions.findForUpdateByIdAndRuntimeBusinessId(candidate.getId(), runtimeId)
                .orElse(null);
        if (locked == null) return Optional.empty();
        if (locked.getState() == DemoSessionState.ACTIVE) return Optional.of(locked.getId());
        if (locked.getState() != DemoSessionState.READY) return Optional.empty();

        locked.setReadinessSnapshot(readinessSnapshot(current));
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

    private static boolean safeForInboundVoice(
            UUID runtimeId,
            PlatformDemoReadinessResponse value) {
        return value != null
                && value.runtimeConfigured()
                && runtimeId.equals(value.runtimeBusinessId())
                && ready(value.runtime())
                && ready(value.voiceNumber())
                && ready(value.voiceAi())
                && ready(value.businessData())
                && ready(value.operations())
                && value.payment() != null
                && "SANDBOX_ONLY".equals(value.payment().state())
                && value.externalEffects() != null
                && "DISARMED".equals(value.externalEffects().state());
    }

    private static boolean ready(PlatformDemoReadinessResponse.ReadinessItem item) {
        return item != null && "READY".equals(item.state());
    }

    private static java.util.Map<String, Object> readinessSnapshot(PlatformDemoReadinessResponse value) {
        java.util.LinkedHashMap<String, Object> snapshot = new java.util.LinkedHashMap<>();
        snapshot.put("runtimeBusinessId", value.runtimeBusinessId());
        snapshot.put("runtime", value.runtime().state());
        snapshot.put("voiceNumber", value.voiceNumber().state());
        snapshot.put("voiceAi", value.voiceAi().state());
        snapshot.put("businessData", value.businessData().state());
        snapshot.put("operations", value.operations().state());
        snapshot.put("payment", value.payment().state());
        snapshot.put("externalEffects", value.externalEffects().state());
        return snapshot;
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

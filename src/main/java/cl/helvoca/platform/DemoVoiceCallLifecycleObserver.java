package cl.helvoca.platform;

import cl.helvoca.audit.AuditService;
import cl.helvoca.call.CallDirection;
import cl.helvoca.call.CallSession;
import cl.helvoca.call.CallSessionRepository;
import cl.helvoca.call.CallStatus;
import cl.helvoca.telephony.CallLifecycleObserver;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
public class DemoVoiceCallLifecycleObserver implements CallLifecycleObserver {
    private static final List<CallStatus> LIVE_CALL_STATES =
            List.of(CallStatus.QUEUED, CallStatus.RINGING, CallStatus.IN_PROGRESS);

    private final DemoRuntimeProperties properties;
    private final DemoSessionRepository sessions;
    private final CallSessionRepository calls;
    private final AuditService audit;

    public DemoVoiceCallLifecycleObserver(
            DemoRuntimeProperties properties,
            DemoSessionRepository sessions,
            CallSessionRepository calls,
            AuditService audit) {
        this.properties = properties;
        this.sessions = sessions;
        this.calls = calls;
        this.audit = audit;
    }

    @Override
    @Transactional
    public void onInboundCallStarted(CallSession call) {
        UUID runtimeId = properties.runtimeBusinessUuid();
        if (!eligibleInbound(call, runtimeId)) return;

        UUID alreadyCorrelated = call.getDemoSessionId();
        if (alreadyCorrelated != null) {
            activateIfNeeded(alreadyCorrelated, runtimeId);
            return;
        }

        DemoSession candidate = sessions.findPreparedForRuntime(runtimeId).orElse(null);
        if (candidate == null
                || (candidate.getState() != DemoSessionState.READY
                    && candidate.getState() != DemoSessionState.ACTIVE)) {
            return;
        }

        DemoSession locked = sessions.findByIdForUpdate(candidate.getId()).orElse(null);
        if (!eligibleSession(locked, runtimeId)) return;

        call.setDemoSessionId(locked.getId());
        calls.saveAndFlush(call);
        if (locked.getState() == DemoSessionState.READY) {
            locked.markActive();
            sessions.saveAndFlush(locked);
            audit.success(runtimeId, "DEMO_SESSION_ACTIVE", "DEMO_SESSION", locked.getId());
        }
    }

    @Override
    @Transactional
    public void onCallUpdated(CallSession call) {
        UUID runtimeId = properties.runtimeBusinessUuid();
        if (runtimeId == null || call == null || call.getDemoSessionId() == null) return;
        if (!runtimeId.equals(call.getBusinessId())) return;

        DemoSession session = sessions.findByIdForUpdate(call.getDemoSessionId()).orElse(null);
        if (session == null || !runtimeId.equals(session.getRuntimeBusinessId())) return;

        if (call.getStatus() != null && !call.getStatus().terminal()) {
            if (session.getState() == DemoSessionState.READY) {
                session.markActive();
                sessions.saveAndFlush(session);
                audit.success(runtimeId, "DEMO_SESSION_ACTIVE", "DEMO_SESSION", session.getId());
            }
            return;
        }

        if (call.getStatus() == null || !call.getStatus().terminal()) return;
        if (session.getState() != DemoSessionState.ACTIVE
                && session.getState() != DemoSessionState.READY) {
            return;
        }

        long liveCalls = calls.countByDemoSessionIdAndStatusIn(session.getId(), LIVE_CALL_STATES);
        if (liveCalls > 0) return;

        session.markFinished();
        sessions.saveAndFlush(session);
        audit.success(runtimeId, "DEMO_SESSION_FINISHED", "DEMO_SESSION", session.getId());
    }

    private void activateIfNeeded(UUID sessionId, UUID runtimeId) {
        DemoSession session = sessions.findByIdForUpdate(sessionId).orElse(null);
        if (!eligibleSession(session, runtimeId)) return;
        if (session.getState() == DemoSessionState.READY) {
            session.markActive();
            sessions.saveAndFlush(session);
            audit.success(runtimeId, "DEMO_SESSION_ACTIVE", "DEMO_SESSION", session.getId());
        }
    }

    private static boolean eligibleInbound(CallSession call, UUID runtimeId) {
        return runtimeId != null
                && call != null
                && runtimeId.equals(call.getBusinessId())
                && call.getDirection() == CallDirection.INBOUND;
    }

    private static boolean eligibleSession(DemoSession session, UUID runtimeId) {
        return session != null
                && runtimeId.equals(session.getRuntimeBusinessId())
                && (session.getState() == DemoSessionState.READY
                    || session.getState() == DemoSessionState.ACTIVE);
    }
}

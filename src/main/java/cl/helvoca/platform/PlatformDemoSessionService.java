package cl.helvoca.platform;

import cl.helvoca.audit.AuditService;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessMode;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.business.BusinessStatus;
import cl.helvoca.common.ConflictException;
import cl.helvoca.common.NotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class PlatformDemoSessionService {
    private final DemoRuntimeProperties properties;
    private final DemoProfileRepository profiles;
    private final DemoSessionRepository sessions;
    private final BusinessRepository businesses;
    private final DemoRuntimePreparationService staging;
    private final AuditService audit;

    public PlatformDemoSessionService(DemoRuntimeProperties properties,
                                      DemoProfileRepository profiles,
                                      DemoSessionRepository sessions,
                                      BusinessRepository businesses,
                                      DemoRuntimePreparationService staging,
                                      AuditService audit) {
        this.properties = properties;
        this.profiles = profiles;
        this.sessions = sessions;
        this.businesses = businesses;
        this.staging = staging;
        this.audit = audit;
    }

    public PlatformDemoSessionResponse prepare(UUID profileId, PrepareRequest request) {
        if (profileId == null) throw new IllegalArgumentException("Demo profile id is required");
        String idempotencyKey = requireIdempotencyKey(request);
        String participant = normalizePhone(request == null ? null : request.expectedParticipantPhone());

        UUID runtimeId = properties.runtimeBusinessUuid();
        if (runtimeId == null) {
            throw new IllegalStateException("Dedicated DEMO runtime is not configured");
        }

        var existing = sessions.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            DemoSession value = existing.get();
            if (!profileId.equals(value.getProfileId())) {
                throw new ConflictException("Demo idempotency key is already bound to another profile");
            }
            return PlatformDemoSessionResponse.from(value);
        }

        if (sessions.findFirstOpenSession().isPresent()) {
            throw new ConflictException("Another demo session is already open");
        }

        Business runtime = businesses.findById(runtimeId)
                .orElseThrow(() -> new ConflictException("Configured DEMO runtime does not exist"));
        requireUsableRuntime(runtime);

        DemoProfile profile = profiles.findById(profileId)
                .orElseThrow(() -> new NotFoundException("Demo profile not found"));

        DemoSession session = new DemoSession();
        session.setProfileId(profileId);
        session.setRuntimeBusinessId(runtimeId);
        session.setIdempotencyKey(idempotencyKey);
        session.setExpectedParticipantPhone(participant);
        session.setState(DemoSessionState.PREPARING);

        try {
            session = sessions.saveAndFlush(session);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Another demo session was prepared concurrently");
        }

        try {
            DemoRuntimePreparationService.StageResult staged = staging.stage(runtimeId, profile);
            session.setConfigurationRevision(staged.revision());
            session.setFailureReason(null);
            session.setPreparedAt(Instant.now());
            session.setState(DemoSessionState.READY);
            session = sessions.saveAndFlush(session);

            Map<String, Object> after = new LinkedHashMap<>();
            after.put("profileId", profileId.toString());
            after.put("runtimeBusinessId", runtimeId.toString());
            after.put("state", session.getState().name());
            after.put("configurationRevision", staged.revision());
            if (participant != null) after.put("expectedParticipantPhone", participant);

            audit.platformHumanSuccess(
                    runtimeId,
                    "DEMO_SESSION_PREPARE",
                    "DEMO_SESSION",
                    session.getId(),
                    null,
                    after);

            return PlatformDemoSessionResponse.from(session);
        } catch (RuntimeException e) {
            session.setState(DemoSessionState.FAILED);
            session.setFailureReason(failureReason(e));
            session.setConfigurationRevision(null);
            session = sessions.saveAndFlush(session);
            return PlatformDemoSessionResponse.from(session);
        }
    }

    @Transactional(readOnly = true)
    public PlatformDemoSessionResponse get(UUID id) {
        if (id == null) throw new IllegalArgumentException("Demo session id is required");
        return PlatformDemoSessionResponse.from(sessions.findById(id)
                .orElseThrow(() -> new NotFoundException("Demo session not found")));
    }

    @Transactional
    public PlatformDemoSessionResponse start(UUID id) {
        DemoSession session = require(id);
        if (session.getState() == DemoSessionState.ACTIVE) return PlatformDemoSessionResponse.from(session);
        if (session.getState() != DemoSessionState.READY) {
            throw new ConflictException("Only a READY demo session can be started");
        }
        session.setState(DemoSessionState.ACTIVE);
        session.setStartedAt(Instant.now());
        session = sessions.saveAndFlush(session);
        audit.platformHumanSuccess(
                session.getRuntimeBusinessId(),
                "DEMO_SESSION_START",
                "DEMO_SESSION",
                session.getId(),
                Map.of("state", DemoSessionState.READY.name()),
                Map.of("state", DemoSessionState.ACTIVE.name()));
        return PlatformDemoSessionResponse.from(session);
    }

    @Transactional
    public PlatformDemoSessionResponse finish(UUID id) {
        DemoSession session = require(id);
        if (session.getState() == DemoSessionState.FINISHED) return PlatformDemoSessionResponse.from(session);
        if (session.getState() != DemoSessionState.ACTIVE && session.getState() != DemoSessionState.READY) {
            throw new ConflictException("Only a READY or ACTIVE demo session can be finished");
        }
        DemoSessionState before = session.getState();
        session.setState(DemoSessionState.FINISHED);
        session.setFinishedAt(Instant.now());
        session = sessions.saveAndFlush(session);
        audit.platformHumanSuccess(
                session.getRuntimeBusinessId(),
                "DEMO_SESSION_FINISH",
                "DEMO_SESSION",
                session.getId(),
                Map.of("state", before.name()),
                Map.of("state", DemoSessionState.FINISHED.name()));
        return PlatformDemoSessionResponse.from(session);
    }

    private DemoSession require(UUID id) {
        if (id == null) throw new IllegalArgumentException("Demo session id is required");
        return sessions.findById(id)
                .orElseThrow(() -> new NotFoundException("Demo session not found"));
    }

    private static void requireUsableRuntime(Business runtime) {
        if (runtime.getMode() != BusinessMode.DEMO) {
            throw new ConflictException("Configured runtime is not a DEMO tenant");
        }
        if (runtime.getStatus() != BusinessStatus.ACTIVE) {
            throw new ConflictException("Configured DEMO runtime is not active");
        }
    }

    private static String requireIdempotencyKey(PrepareRequest request) {
        String value = request == null ? null : request.idempotencyKey();
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Demo preparation idempotency key is required");
        }
        value = value.trim();
        if (value.length() > 120) {
            throw new IllegalArgumentException("Demo preparation idempotency key is too long");
        }
        return value;
    }

    private static String normalizePhone(String value) {
        if (value == null || value.isBlank()) return null;
        String phone = value.trim();
        if (!phone.matches("^\\+[1-9][0-9]{7,14}$")) {
            throw new IllegalArgumentException("Expected participant phone must use E.164 format");
        }
        return phone;
    }

    private static String failureReason(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        String message = current.getMessage();
        if (message == null || message.isBlank()) message = current.getClass().getSimpleName();
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    public record PrepareRequest(String expectedParticipantPhone, String idempotencyKey) {}
}

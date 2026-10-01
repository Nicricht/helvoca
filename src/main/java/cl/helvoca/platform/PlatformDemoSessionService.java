package cl.helvoca.platform;

import cl.helvoca.audit.AuditService;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessMode;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.business.BusinessStatus;
import cl.helvoca.common.ConflictException;
import cl.helvoca.common.NotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PlatformDemoSessionService {
    private static final Duration PREPARING_STALE_AFTER = Duration.ofMinutes(5);

    private final DemoRuntimeProperties properties;
    private final BusinessRepository businesses;
    private final DemoProfileRepository profiles;
    private final DemoSessionRepository sessions;
    private final DemoRuntimeStagingService staging;
    private final PlatformDemoReadinessService readiness;
    private final AuditService audit;

    public PlatformDemoSessionService(
            DemoRuntimeProperties properties,
            BusinessRepository businesses,
            DemoProfileRepository profiles,
            DemoSessionRepository sessions,
            DemoRuntimeStagingService staging,
            PlatformDemoReadinessService readiness,
            AuditService audit) {
        this.properties = properties;
        this.businesses = businesses;
        this.profiles = profiles;
        this.sessions = sessions;
        this.staging = staging;
        this.readiness = readiness;
        this.audit = audit;
    }

    @Transactional
    public PlatformDemoSessionResponse prepare(UUID profileId) {
        if (profileId == null) throw new IllegalArgumentException("Demo profile is required");

        UUID runtimeId = requireRuntimeId();
        Business runtime = businesses.findById(runtimeId)
                .orElseThrow(() -> new IllegalStateException("Configured DEMO runtime business does not exist"));
        requireRuntime(runtime);

        DemoProfile profile = profiles.findById(profileId)
                .orElseThrow(() -> new NotFoundException("Demo profile not found"));
        String revision = revision(profile);

        DemoSession session = sessions.findPreparedForRuntime(runtimeId).orElse(null);
        if (session != null && !matches(session, profileId, revision)) {
            throw new ConflictException("Another live demo session is already prepared or active");
        }
        if (session != null && session.getState() != DemoSessionState.PREPARING) {
            return response(session, readiness.readiness());
        }
        if (session != null && !isStalePreparing(session)) {
            return response(session, readiness.readiness());
        }

        if (session == null) {
            session = DemoSession.preparing(
                    profileId,
                    runtimeId,
                    revision,
                    platformOperator(),
                    configurationSnapshot(profile));
            try {
                session = sessions.saveAndFlush(session);
            } catch (DataIntegrityViolationException concurrentPrepare) {
                DemoSession existing = sessions.findPreparedForRuntime(runtimeId)
                        .orElseThrow(() -> concurrentPrepare);
                if (!matches(existing, profileId, revision)) {
                    throw new ConflictException("Another live demo session won concurrent preparation");
                }
                return response(existing, readiness.readiness());
            }
            audit.platformHumanSuccess(
                    runtimeId, "DEMO_SESSION_PREPARE", "DEMO_SESSION",
                    session.getId(), Map.of(), auditState(session));
        } else {
            audit.platformHumanSuccess(
                    runtimeId, "DEMO_SESSION_PREPARE_RESUME", "DEMO_SESSION",
                    session.getId(), Map.of("reason", "stale_preparing"), auditState(session));
        }

        try {
            staging.stage(profile, runtimeId);
            session.markStaged();
            session = sessions.saveAndFlush(session);

            PlatformDemoReadinessResponse snapshot = readiness.readiness();
            session.setReadinessSnapshot(snapshot(snapshot));

            if (readyForInboundVoice(snapshot)) {
                session.markReady();
                session = sessions.saveAndFlush(session);
                audit.platformHumanSuccess(
                        runtimeId, "DEMO_SESSION_READY", "DEMO_SESSION",
                        session.getId(), Map.of("state", DemoSessionState.PREPARING.name()), auditState(session));
            } else {
                session.markFailed(missingReadiness(snapshot));
                session = sessions.saveAndFlush(session);
                audit.platformHumanSuccess(
                        runtimeId, "DEMO_SESSION_FAILED", "DEMO_SESSION",
                        session.getId(), Map.of("state", DemoSessionState.PREPARING.name()), auditState(session));
            }
            return response(session, snapshot);
        } catch (RuntimeException error) {
            if (session.getState() != DemoSessionState.FAILED) {
                session.markFailed(safeFailure(error));
                session = sessions.saveAndFlush(session);
                audit.platformHumanSuccess(
                        runtimeId, "DEMO_SESSION_FAILED", "DEMO_SESSION",
                        session.getId(), Map.of("state", DemoSessionState.PREPARING.name()), auditState(session));
            }
            return response(session, readiness.readiness());
        }
    }

    @Transactional
    public PlatformDemoSessionResponse start(UUID sessionId) {
        UUID runtimeId = requireRuntimeId();
        requireRuntime(businesses.findById(runtimeId)
                .orElseThrow(() -> new IllegalStateException("Configured DEMO runtime business does not exist")));
        DemoSession session = requireSession(sessionId, runtimeId);
        if (session.getState() != DemoSessionState.READY) {
            throw new IllegalStateException("Only a READY demo session can be started");
        }

        PlatformDemoReadinessResponse current = readiness.readiness();
        requireStartReadiness(runtimeId, current);

        session.setReadinessSnapshot(snapshot(current));
        session.markActive();
        session = sessions.saveAndFlush(session);
        audit.platformHumanSuccess(
                runtimeId, "DEMO_SESSION_START", "DEMO_SESSION",
                session.getId(), null, auditState(session));
        return response(session, current);
    }

    @Transactional
    public PlatformDemoSessionResponse finish(UUID sessionId) {
        UUID runtimeId = requireRuntimeId();
        DemoSession session = requireSession(sessionId, runtimeId);
        if (session.getState() != DemoSessionState.ACTIVE) {
            throw new IllegalStateException("Only an ACTIVE demo session can be finished");
        }
        session.markFinished();
        session = sessions.saveAndFlush(session);
        audit.platformHumanSuccess(
                runtimeId, "DEMO_SESSION_FINISH", "DEMO_SESSION",
                session.getId(), null, auditState(session));
        return response(session, readiness.readiness());
    }

    @Transactional
    public PlatformDemoSessionResponse abort(UUID sessionId) {
        UUID runtimeId = requireRuntimeId();
        DemoSession session = requireSession(sessionId, runtimeId);
        if (session.getState() != DemoSessionState.READY
                && session.getState() != DemoSessionState.ACTIVE) {
            throw new IllegalStateException("Only READY or ACTIVE demo sessions can be aborted");
        }
        session.markAborted();
        session = sessions.saveAndFlush(session);
        audit.platformHumanSuccess(
                runtimeId, "DEMO_SESSION_ABORT", "DEMO_SESSION",
                session.getId(), null, auditState(session));
        return response(session, readiness.readiness());
    }

    @Transactional(readOnly = true)
    public PlatformDemoSessionResponse current() {
        UUID runtimeId = requireRuntimeId();
        return sessions.findPreparedForRuntime(runtimeId)
                .or(() -> sessions.findFirstByRuntimeBusinessIdOrderByCreatedAtDesc(runtimeId))
                .map(session -> response(session, readiness.readiness()))
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public PlatformDemoSessionResponse get(UUID sessionId) {
        UUID runtimeId = requireRuntimeId();
        return response(requireSession(sessionId, runtimeId), readiness.readiness());
    }

    private DemoSession requireSession(UUID sessionId, UUID runtimeId) {
        return sessions.findByIdAndRuntimeBusinessId(sessionId, runtimeId)
                .orElseThrow(() -> new NotFoundException("Demo session not found"));
    }

    private UUID requireRuntimeId() {
        UUID runtimeId = properties.runtimeBusinessUuid();
        if (runtimeId == null) throw new IllegalStateException("No dedicated DEMO runtime is configured");
        return runtimeId;
    }

    private static void requireRuntime(Business runtime) {
        if (runtime.getMode() != BusinessMode.DEMO) {
            throw new IllegalStateException("Configured runtime is not in DEMO mode");
        }
        if (runtime.getStatus() != BusinessStatus.ACTIVE) {
            throw new IllegalStateException("Configured DEMO runtime is not active");
        }
    }

    private static boolean isStalePreparing(DemoSession session) {
        Instant updatedAt = session.getUpdatedAt();
        return session.getState() == DemoSessionState.PREPARING
                && updatedAt != null
                && updatedAt.isBefore(Instant.now().minus(PREPARING_STALE_AFTER));
    }

    private static boolean matches(DemoSession session, UUID profileId, String revision) {
        return profileId.equals(session.getDemoProfileId())
                && revision.equals(session.getConfigurationRevision());
    }

    private static String revision(DemoProfile profile) {
        Instant updatedAt = profile.getUpdatedAt();
        if (updatedAt == null) throw new IllegalStateException("Demo profile has no persisted revision");
        return updatedAt.toString();
    }

    private static boolean readyForInboundVoice(PlatformDemoReadinessResponse value) {
        return "READY".equals(value.runtime().state())
                && "READY".equals(value.voiceNumber().state())
                && "READY".equals(value.voiceAi().state())
                && "READY".equals(value.businessData().state())
                && "READY".equals(value.operations().state());
    }

    private static void requireStartReadiness(UUID runtimeId, PlatformDemoReadinessResponse value) {
        if (value == null || !value.runtimeConfigured() || !runtimeId.equals(value.runtimeBusinessId())) {
            throw new IllegalStateException("DEMO runtime readiness is unavailable");
        }
        if (!readyForInboundVoice(value)) {
            throw new IllegalStateException(missingReadiness(value));
        }
        if (value.payment() == null || !"SANDBOX_ONLY".equals(value.payment().state())) {
            throw new IllegalStateException("DEMO payment boundary is not SANDBOX_ONLY");
        }
        if (value.externalEffects() == null || !"DISARMED".equals(value.externalEffects().state())) {
            throw new IllegalStateException("DEMO external effects are not DISARMED");
        }
    }

    private static String missingReadiness(PlatformDemoReadinessResponse value) {
        LinkedHashMap<String, String> missing = new LinkedHashMap<>();
        addMissing(missing, "runtime", value.runtime());
        addMissing(missing, "voiceNumber", value.voiceNumber());
        addMissing(missing, "voiceAi", value.voiceAi());
        addMissing(missing, "businessData", value.businessData());
        addMissing(missing, "operations", value.operations());
        return missing.isEmpty()
                ? "Required demo readiness is incomplete"
                : "Required readiness not met: " + missing;
    }

    private static void addMissing(
            Map<String, String> missing,
            String key,
            PlatformDemoReadinessResponse.ReadinessItem item) {
        if (item == null || !"READY".equals(item.state())) {
            missing.put(key, item == null ? "UNKNOWN" : item.state());
        }
    }

    private static Map<String, Object> snapshot(PlatformDemoReadinessResponse value) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("runtimeBusinessId", value.runtimeBusinessId());
        map.put("runtime", itemSnapshot(value.runtime()));
        map.put("voiceNumber", itemSnapshot(value.voiceNumber()));
        map.put("voiceAi", itemSnapshot(value.voiceAi()));
        map.put("businessData", itemSnapshot(value.businessData()));
        map.put("operations", itemSnapshot(value.operations()));
        map.put("whatsapp", itemSnapshot(value.whatsapp()));
        map.put("payment", itemSnapshot(value.payment()));
        map.put("externalEffects", itemSnapshot(value.externalEffects()));
        return map;
    }

    private static Map<String, Object> itemSnapshot(PlatformDemoReadinessResponse.ReadinessItem item) {
        if (item == null) return Map.of("state", "UNKNOWN");
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        out.put("state", item.state());
        out.put("detail", item.detail() == null ? "" : item.detail());
        return out;
    }

    private static Map<String, Object> configurationSnapshot(DemoProfile profile) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("demoProfileId", profile.getId());
        map.put("profileUpdatedAt", profile.getUpdatedAt());
        map.put("businessName", profile.getBusinessName());
        map.put("timezone", profile.getTimezone());
        map.put("language", profile.getLanguage());
        map.put("greeting", profile.getGreeting());
        map.put("instructions", profile.getInstructions());
        map.put("capabilities", profile.getCapabilities() == null ? List.of() : List.copyOf(profile.getCapabilities()));
        map.put("catalog", profile.getCatalog() == null ? Map.of() : new LinkedHashMap<>(profile.getCatalog()));
        map.put("hours", profile.getHours() == null ? Map.of() : new LinkedHashMap<>(profile.getHours()));
        map.put("knowledge", profile.getKnowledge() == null ? Map.of() : new LinkedHashMap<>(profile.getKnowledge()));
        return map;
    }

    private static String platformOperator() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) return "platform";
        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            String email = jwt.getClaimAsString("email");
            if (email != null && !email.isBlank()) return email.trim().toLowerCase();
        }
        String name = authentication.getName();
        return name == null || name.isBlank() ? "platform" : name.trim().toLowerCase();
    }

    private static Map<String, Object> auditState(DemoSession session) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("profileId", session.getDemoProfileId());
        map.put("runtimeBusinessId", session.getRuntimeBusinessId());
        map.put("correlationId", session.getCorrelationId());
        map.put("configurationRevision", session.getConfigurationRevision());
        map.put("state", session.getState().name());
        map.put("operator", session.getOperator());
        map.put("externalEffectsState", session.getExternalEffectsState());
        map.put("paymentState", session.getPaymentState());
        if (session.getConvertedPilotBusinessId() != null) {
            map.put("convertedPilotBusinessId", session.getConvertedPilotBusinessId());
        }
        if (session.getFailureReason() != null) map.put("failureReason", session.getFailureReason());
        return map;
    }

    private static String safeFailure(RuntimeException error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) return "Demo preparation failed";
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    private static PlatformDemoSessionResponse response(
            DemoSession session,
            PlatformDemoReadinessResponse readiness) {
        return new PlatformDemoSessionResponse(
                session.getId(),
                session.getCorrelationId(),
                session.getDemoProfileId(),
                session.getRuntimeBusinessId(),
                session.getState(),
                session.getConfigurationRevision(),
                session.getStagedAt(),
                session.getStartedAt(),
                session.getFinishedAt(),
                session.getFailureReason(),
                readiness,
                session.getCreatedAt(),
                session.getUpdatedAt(),
                session.getOperator(),
                new LinkedHashMap<>(session.getConfigurationSnapshot()),
                new LinkedHashMap<>(session.getReadinessSnapshot()),
                session.getExternalEffectsState(),
                session.getPaymentState(),
                session.getConvertedPilotBusinessId());
    }
}

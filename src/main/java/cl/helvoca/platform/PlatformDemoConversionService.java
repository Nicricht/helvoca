package cl.helvoca.platform;

import cl.helvoca.audit.AuditService;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessMode;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class PlatformDemoConversionService {
    private final DemoRuntimeProperties properties;
    private final DemoSessionRepository sessions;
    private final DemoProfileRepository profiles;
    private final BusinessRepository businesses;
    private final PlatformBusinessProvisioningService provisioning;
    private final DemoRuntimeStagingService staging;
    private final AuditService audit;

    public PlatformDemoConversionService(DemoRuntimeProperties properties,
                                         DemoSessionRepository sessions,
                                         DemoProfileRepository profiles,
                                         BusinessRepository businesses,
                                         PlatformBusinessProvisioningService provisioning,
                                         DemoRuntimeStagingService staging,
                                         AuditService audit) {
        this.properties = properties;
        this.sessions = sessions;
        this.profiles = profiles;
        this.businesses = businesses;
        this.provisioning = provisioning;
        this.staging = staging;
        this.audit = audit;
    }

    @Transactional
    public PlatformDemoConversionResponse convert(
            UUID sessionId,
            PlatformDemoConversionRequest request) {
        UUID runtimeId = requireConfiguredRuntime();
        DemoSession session = sessions.findForUpdateByIdAndRuntimeBusinessId(sessionId, runtimeId)
                .orElseThrow(() -> new NotFoundException("Demo session not found"));

        UUID alreadyConverted = session.getConvertedPilotBusinessId();
        if (alreadyConverted != null) {
            Business pilot = businesses.findById(alreadyConverted)
                    .orElseThrow(() -> new IllegalStateException("Converted PILOT business no longer exists"));
            if (pilot.getMode() != BusinessMode.PILOT) {
                throw new IllegalStateException("Converted business is not in PILOT mode");
            }
            return replay(session, pilot);
        }

        if (session.getState() != DemoSessionState.READY
                && session.getState() != DemoSessionState.FINISHED) {
            throw new IllegalStateException(
                    "Only READY or FINISHED demo sessions can be converted to PILOT");
        }

        DemoProfile profile = profiles.findById(session.getDemoProfileId())
                .orElseThrow(() -> new NotFoundException("Demo profile not found"));

        PlatformBusinessProvisioningResponse created = provisioning.provisionPilot(
                new PlatformBusinessProvisioningRequest(
                        profile.getBusinessName(), profile.getTimezone(), profile.getLanguage(),
                        null, request.adminName(), request.adminEmail()));

        if (created.businessId().equals(runtimeId)) {
            throw new IllegalStateException("PILOT conversion must create a fresh tenant");
        }

        staging.stagePilot(profile, created.businessId());

        session.setConvertedPilotBusinessId(created.businessId());
        sessions.saveAndFlush(session);

        audit.platformHumanSuccess(
                created.businessId(), "DEMO_SESSION_CONVERT_PILOT", "DEMO_SESSION",
                session.getId(), null,
                Map.of(
                        "demoSessionId", session.getId(),
                        "demoProfileId", session.getDemoProfileId(),
                        "runtimeBusinessId", runtimeId,
                        "pilotBusinessId", created.businessId()));

        return new PlatformDemoConversionResponse(
                session.getId(), created.businessId(), created.businessName(), BusinessMode.PILOT.name(),
                created.invitationId(), created.invitationStatus(), created.invitationExpiresAt(),
                created.invitePath(), created.onboardingPath(), false);
    }

    private UUID requireConfiguredRuntime() {
        UUID runtimeId = properties.runtimeBusinessUuid();
        if (runtimeId == null) throw new IllegalStateException("No server-owned DEMO runtime is configured");
        return runtimeId;
    }

    private static PlatformDemoConversionResponse replay(DemoSession session, Business pilot) {
        return new PlatformDemoConversionResponse(
                session.getId(), pilot.getId(), pilot.getName(), BusinessMode.PILOT.name(),
                null, "ALREADY_CREATED", null, null, "/", true);
    }
}

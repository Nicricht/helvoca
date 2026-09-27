package cl.helvoca.platform;

import cl.helvoca.common.ConflictException;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.telephony.twilio.TwilioCertificationCommandStore;
import org.springframework.stereotype.Service;

@Service
public class PlatformCertificationRunService {
    private final TwilioCertificationCommandStore commands;

    public PlatformCertificationRunService(TwilioCertificationCommandStore commands) {
        this.commands = commands;
    }

    public PlatformCertificationRunResponse create(String runId, String requestedBy) {
        String normalizedRunId = runId == null ? "" : runId.trim();
        if (!TwilioCertificationCommandStore.validRunId(normalizedRunId)) {
            throw new IllegalArgumentException("Invalid certification run id");
        }
        String actor = requestedBy == null || requestedBy.isBlank()
                ? "platform-admin"
                : requestedBy.trim().substring(0, Math.min(requestedBy.trim().length(), 180));
        if (!commands.enqueue(normalizedRunId, actor)) {
            throw new ConflictException("Certification run id already exists");
        }
        return get(normalizedRunId);
    }

    public PlatformCertificationRunResponse get(String runId) {
        String normalizedRunId = runId == null ? "" : runId.trim();
        if (!TwilioCertificationCommandStore.validRunId(normalizedRunId)) {
            throw new IllegalArgumentException("Invalid certification run id");
        }
        return commands.find(normalizedRunId)
                .map(PlatformCertificationRunService::response)
                .orElseThrow(() -> new NotFoundException("Certification run not found"));
    }

    private static PlatformCertificationRunResponse response(TwilioCertificationCommandStore.CommandStatus status) {
        return new PlatformCertificationRunResponse(
                status.runId(),
                status.status(),
                status.requestedBy(),
                status.requestedAt(),
                status.claimedAt(),
                status.expiresAt(),
                status.providerCallSid(),
                status.completedAt(),
                status.failureReason());
    }
}

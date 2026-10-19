package cl.helvoca.platform;

import cl.helvoca.common.ConflictException;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.telephony.twilio.TwilioCertificationCommandStore;
import cl.helvoca.telephony.twilio.TwilioCertificationDemoBindingGate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PlatformCertificationRunService {
    private final TwilioCertificationCommandStore commands;
    private final TwilioCertificationDemoBindingGate demoBinding;

    @Autowired
    public PlatformCertificationRunService(TwilioCertificationCommandStore commands,
            TwilioCertificationDemoBindingGate demoBinding) {
        this.commands = commands;
        this.demoBinding = demoBinding;
    }

    PlatformCertificationRunService(TwilioCertificationCommandStore commands) {
        this(commands, null);
    }

    public PlatformCertificationRunResponse create(String runId, String requestedBy) {
        String normalizedRunId = runId == null ? "" : runId.trim();
        if (!TwilioCertificationCommandStore.validRunId(normalizedRunId)) {
            throw new IllegalArgumentException("Invalid certification run id");
        }
        String actor = requestedBy == null || requestedBy.isBlank()
                ? "platform-admin"
                : requestedBy.trim().substring(0, Math.min(requestedBy.trim().length(), 180));
        if (demoBinding != null && !demoBinding.allows(demoBinding.pinnedBusinessId())) {
            throw new IllegalStateException("certification DEMO tenant/phone binding unavailable");
        }
        boolean inserted = demoBinding == null
                ? commands.enqueue(normalizedRunId, actor)
                : commands.enqueue(normalizedRunId, actor, demoBinding.pinnedBusinessId());
        if (!inserted) {
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

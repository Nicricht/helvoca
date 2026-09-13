package cl.helvoca.operations;

import cl.helvoca.call.CallAction;
import cl.helvoca.call.CallActionRepository;
import cl.helvoca.call.CallSession;
import cl.helvoca.call.CallSessionRepository;
import cl.helvoca.call.CallSummaryRepository;
import cl.helvoca.call.CallTranscriptRepository;
import cl.helvoca.security.TenantProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class CallCertificationStatusService {
    private final CallSessionRepository calls;
    private final CallActionRepository actions;
    private final CallTranscriptRepository transcripts;
    private final CallSummaryRepository summaries;
    private final TenantProvider tenantProvider;

    public CallCertificationStatusService(CallSessionRepository calls,
                                          CallActionRepository actions,
                                          CallTranscriptRepository transcripts,
                                          CallSummaryRepository summaries,
                                          TenantProvider tenantProvider) {
        this.calls = calls;
        this.actions = actions;
        this.transcripts = transcripts;
        this.summaries = summaries;
        this.tenantProvider = tenantProvider;
    }

    @Transactional(readOnly = true)
    public Status current() {
        UUID businessId = tenantProvider.requireBusinessId();
        CallSession call = calls.findFirstByBusinessIdAndCertificationTrueOrderByStartedAtDesc(businessId)
                .orElse(null);
        if (call == null) {
            return new Status(false, "NOT_RUN", null, null, null, null, null, 0, 8, List.of());
        }

        List<CallAction> persistedActions = actions.findAllByCallIdOrderByCreatedAtAsc(call.getId());
        List<Check> checks = new ArrayList<>();
        checks.add(check("MEDIA_STARTED", "Audio conectado", call.getStreamStartedAt() != null,
                "El stream de audio debe iniciar."));
        checks.add(check("AI_READY", "IA configurada", call.getAiSetupCompletedAt() != null,
                "El proveedor de IA debe completar la configuración de sesión."));
        checks.add(check("TRANSCRIPT", "Transcripción", !transcripts.findAllByCallIdOrderBySequenceNumberAsc(call.getId()).isEmpty(),
                "La llamada debe dejar transcripción persistida."));
        checks.add(check("SERVICES", "Servicios consultados", hasSuccessful(persistedActions, "SERVICES_LISTED"),
                "El agente debe consultar el catálogo real del negocio."));
        checks.add(check("AVAILABILITY", "Disponibilidad consultada",
                hasSuccessful(persistedActions, "AVAILABILITY_CHECKED") || hasSuccessful(persistedActions, "AVAILABILITY_LISTED"),
                "El agente debe comprobar disponibilidad mediante backend."));
        checks.add(check("BOOKING_CREATED", "Reserva creada", hasSuccessful(persistedActions, "BOOKING_CREATED"),
                "Debe existir una reserva creada por una tool autorizada."));
        checks.add(check("BOOKING_CANCELLED", "Reserva cancelada", hasSuccessful(persistedActions, "BOOKING_CANCELLED"),
                "La reserva de certificación debe cancelarse para no dejar datos operativos falsos."));
        checks.add(check("SUMMARY", "Resumen final", summaries.findByCallId(call.getId()).isPresent(),
                "La llamada debe finalizar con resumen persistido."));

        int passed = (int) checks.stream().filter(Check::passed).count();
        boolean allPassed = passed == checks.size();
        boolean ended = call.getStreamEndedAt() != null || (call.getStatus() != null && call.getStatus().terminal());
        String state = allPassed ? "PASSED" : ended ? "FAILED" : "IN_PROGRESS";

        return new Status(
                true,
                state,
                call.getId(),
                call.getStartedAt(),
                call.getAiProvider(),
                call.getTelephonyProvider(),
                call.getStatus() == null ? null : call.getStatus().name(),
                passed,
                checks.size(),
                checks);
    }

    private static Check check(String code, String label, boolean passed, String detail) {
        return new Check(code, label, passed, detail);
    }

    private static boolean hasSuccessful(List<CallAction> actions, String actionType) {
        return actions.stream().anyMatch(action -> action.isSuccess() && actionType.equals(action.getActionType()));
    }

    public record Status(
            boolean available,
            String state,
            UUID callId,
            Instant startedAt,
            String aiProvider,
            String telephonyProvider,
            String callStatus,
            int passedChecks,
            int totalChecks,
            List<Check> checks
    ) {}

    public record Check(String code, String label, boolean passed, String detail) {}
}

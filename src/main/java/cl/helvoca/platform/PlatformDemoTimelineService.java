package cl.helvoca.platform;

import cl.helvoca.call.CallAction;
import cl.helvoca.call.CallActionRepository;
import cl.helvoca.call.CallSession;
import cl.helvoca.call.CallSessionRepository;
import cl.helvoca.call.CallSummary;
import cl.helvoca.call.CallSummaryRepository;
import cl.helvoca.call.CallTranscript;
import cl.helvoca.call.CallTranscriptRepository;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.operations.BusinessOperation;
import cl.helvoca.operations.BusinessOperationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class PlatformDemoTimelineService {
    private final DemoRuntimeProperties properties;
    private final DemoSessionRepository sessions;
    private final CallSessionRepository calls;
    private final CallTranscriptRepository transcripts;
    private final CallActionRepository actions;
    private final BusinessOperationRepository operations;
    private final CallSummaryRepository summaries;

    public PlatformDemoTimelineService(
            DemoRuntimeProperties properties,
            DemoSessionRepository sessions,
            CallSessionRepository calls,
            CallTranscriptRepository transcripts,
            CallActionRepository actions,
            BusinessOperationRepository operations,
            CallSummaryRepository summaries) {
        this.properties = properties;
        this.sessions = sessions;
        this.calls = calls;
        this.transcripts = transcripts;
        this.actions = actions;
        this.operations = operations;
        this.summaries = summaries;
    }

    @Transactional(readOnly = true)
    public PlatformDemoTimelineResponse timeline(UUID sessionId) {
        if (sessionId == null) throw new IllegalArgumentException("Demo session is required");

        UUID runtimeId = properties.runtimeBusinessUuid();
        if (runtimeId == null) throw new IllegalStateException("No dedicated DEMO runtime is configured");

        DemoSession session = sessions.findById(sessionId)
                .filter(value -> runtimeId.equals(value.getRuntimeBusinessId()))
                .orElseThrow(() -> new NotFoundException("Demo session not found"));

        List<PlatformDemoTimelineResponse.Item> items = new ArrayList<>();
        if (session.getStagedAt() != null) {
            items.add(item(
                    "SESSION_PREPARED",
                    session.getStagedAt(),
                    "Demo preparada",
                    "La configuración aprobada quedó cargada en el runtime DEMO.",
                    session.getState().name(),
                    null,
                    session.getId()));
        }

        List<CallSession> correlatedCalls = calls.findAllByDemoSessionIdOrderByStartedAtAsc(sessionId)
                .stream()
                .filter(call -> runtimeId.equals(call.getBusinessId()))
                .filter(call -> sessionId.equals(call.getDemoSessionId()))
                .toList();

        for (CallSession call : correlatedCalls) {
            addCallEvidence(items, runtimeId, call);
        }

        items.sort(Comparator
                .comparing(PlatformDemoTimelineResponse.Item::at,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(PlatformDemoTimelineResponse.Item::kind));

        return new PlatformDemoTimelineResponse(
                session.getId(),
                session.getState(),
                correlatedCalls.size(),
                Instant.now(),
                List.copyOf(items));
    }

    private void addCallEvidence(
            List<PlatformDemoTimelineResponse.Item> items,
            UUID runtimeId,
            CallSession call) {
        UUID callId = call.getId();

        items.add(item(
                "CALL_STARTED",
                call.getStartedAt(),
                "Llamada recibida",
                "Entrante desde " + maskedPhone(call.getCallerNumber())
                        + " hacia el número DEMO.",
                call.getStatus() == null ? "UNKNOWN" : call.getStatus().name(),
                callId,
                callId));

        if (call.getAnsweredAt() != null) {
            items.add(item(
                    "CALL_ANSWERED",
                    call.getAnsweredAt(),
                    "IA conectada",
                    providerDetail(call),
                    "CONNECTED",
                    callId,
                    callId));
        }

        for (CallTranscript transcript : transcripts.findAllByCallIdOrderBySequenceNumberAsc(callId)) {
            items.add(item(
                    "TRANSCRIPT",
                    transcript.getCreatedAt(),
                    "USER".equalsIgnoreCase(transcript.getSpeaker())
                            ? "Cliente"
                            : "ASSISTANT".equalsIgnoreCase(transcript.getSpeaker()) ? "IA" : transcript.getSpeaker(),
                    transcript.getContent(),
                    transcript.getSpeaker(),
                    callId,
                    transcript.getId()));
        }

        for (CallAction action : actions.findAllByBusinessIdAndCallIdOrderByCreatedAtAsc(runtimeId, callId)) {
            items.add(item(
                    "ACTION",
                    action.getCreatedAt(),
                    action.getActionType(),
                    action.getDetail() == null || action.getDetail().isBlank()
                            ? actionDetail(action)
                            : action.getDetail(),
                    action.isSuccess() ? "SUCCESS" : "FAILED",
                    callId,
                    action.getEntityId() == null ? action.getId() : action.getEntityId()));
        }

        for (BusinessOperation operation :
                operations.findAllByBusinessIdAndSourceReferenceIdOrderByCreatedAtAsc(runtimeId, callId)) {
            items.add(item(
                    "OPERATION",
                    operation.getUpdatedAt() == null ? operation.getCreatedAt() : operation.getUpdatedAt(),
                    operation.getType() == null ? "Operación" : operation.getType().name(),
                    operationDetail(operation),
                    operation.getStatus() == null ? "UNKNOWN" : operation.getStatus().name(),
                    callId,
                    operation.getId()));
        }

        summaries.findByCallId(callId).ifPresent(summary -> items.add(item(
                "SUMMARY",
                summary.getCreatedAt(),
                summaryTitle(summary),
                summary.getSummary(),
                summary.getOutcome() == null ? "RECORDED" : summary.getOutcome(),
                callId,
                summary.getId())));

        if (call.getEndedAt() != null) {
            items.add(item(
                    "CALL_FINISHED",
                    call.getEndedAt(),
                    "Llamada terminada",
                    call.getDurationSeconds() == null
                            ? "El proveedor informó el cierre de la llamada."
                            : "Duración registrada: " + call.getDurationSeconds() + " s.",
                    call.getStatus() == null ? "UNKNOWN" : call.getStatus().name(),
                    callId,
                    callId));
        }
    }

    private static PlatformDemoTimelineResponse.Item item(
            String kind,
            Instant at,
            String title,
            String detail,
            String status,
            UUID callId,
            UUID entityId) {
        return new PlatformDemoTimelineResponse.Item(
                kind,
                at,
                title == null ? "" : title,
                detail == null ? "" : detail,
                status == null ? "" : status,
                callId,
                entityId);
    }

    private static String providerDetail(CallSession call) {
        String ai = call.getAiProvider();
        String model = call.getAiModel();
        if (ai == null || ai.isBlank()) return "La llamada fue contestada por el runtime de voz.";
        if (model == null || model.isBlank()) return "Proveedor de IA: " + ai + ".";
        return "Proveedor de IA: " + ai + " · " + model + ".";
    }

    private static String actionDetail(CallAction action) {
        if (action.getEntityType() == null || action.getEntityType().isBlank()) {
            return action.isSuccess() ? "Acción persistida correctamente." : "La acción registró un fallo.";
        }
        return action.getEntityType()
                + (action.getEntityId() == null ? "" : " · " + action.getEntityId());
    }

    private static String operationDetail(BusinessOperation operation) {
        if (operation.getTotal() == null) return "Operación persistida en el tenant DEMO.";
        String currency = operation.getCurrency() == null ? "" : " " + operation.getCurrency();
        return "Total registrado: " + operation.getTotal().stripTrailingZeros().toPlainString() + currency + ".";
    }

    private static String summaryTitle(CallSummary summary) {
        if (summary.getIntent() == null || summary.getIntent().isBlank()) return "Resumen de llamada";
        return "Resumen · " + summary.getIntent();
    }

    private static String maskedPhone(String value) {
        if (value == null || value.isBlank()) return "número oculto";
        String normalized = value.trim();
        if (normalized.length() <= 4) return "••••";
        return "••••" + normalized.substring(normalized.length() - 4);
    }
}

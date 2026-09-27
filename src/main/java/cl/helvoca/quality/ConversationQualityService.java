package cl.helvoca.quality;

import cl.helvoca.call.CallAction;
import cl.helvoca.call.CallActionRepository;
import cl.helvoca.call.CallSession;
import cl.helvoca.call.CallSessionRepository;
import cl.helvoca.call.CallTranscript;
import cl.helvoca.call.CallTranscriptRepository;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.security.TenantProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ConversationQualityService {
    private final CallSessionRepository calls;
    private final CallTranscriptRepository transcripts;
    private final CallActionRepository actions;
    private final TenantProvider tenant;
    private final ConversationQualityEngine engine = new ConversationQualityEngine();

    public ConversationQualityService(
            CallSessionRepository calls,
            CallTranscriptRepository transcripts,
            CallActionRepository actions,
            TenantProvider tenant) {
        this.calls = calls;
        this.transcripts = transcripts;
        this.actions = actions;
        this.tenant = tenant;
    }

    @Transactional(readOnly = true)
    public CallQualityReport analyze(UUID callId) {
        UUID businessId = tenant.requireBusinessId();
        CallSession call = calls.findByIdAndBusinessId(callId, businessId)
                .orElseThrow(() -> new NotFoundException("Call not found"));

        List<CallTranscript> transcriptRows =
                transcripts.findAllByCallIdOrderBySequenceNumberAsc(callId);
        List<CallAction> actionRows =
                actions.findAllByBusinessIdAndCallIdOrderByCreatedAtAsc(businessId, callId);

        var turns = transcriptRows.stream()
                .map(item -> new ConversationQualityEngine.Turn(item.getSpeaker(), item.getContent()))
                .toList();
        var effects = actionRows.stream()
                .map(item -> new ConversationQualityEngine.Action(
                        item.getActionType(),
                        item.isSuccess(),
                        item.getEntityType(),
                        item.getEntityId(),
                        item.getDetail()))
                .toList();

        ConversationQualityEngine.Report report = engine.evaluate(turns, effects);
        long assistantTurns = turns.stream()
                .filter(turn -> "ASSISTANT".equalsIgnoreCase(turn.speaker()))
                .count();
        long userTurns = turns.stream()
                .filter(turn -> "USER".equalsIgnoreCase(turn.speaker()))
                .count();
        long successfulActions = effects.stream().filter(ConversationQualityEngine.Action::success).count();

        return new CallQualityReport(
                call.getId(),
                call.getStatus() == null ? null : call.getStatus().name(),
                call.getResolution(),
                turns.size(),
                userTurns,
                assistantTurns,
                effects.size(),
                successfulActions,
                report);
    }

    public record CallQualityReport(
            UUID callId,
            String callStatus,
            String resolution,
            int totalTurns,
            long userTurns,
            long assistantTurns,
            int totalActions,
            long successfulActions,
            ConversationQualityEngine.Report quality) {}
}

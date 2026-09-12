package cl.helvoca.call;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Disabled-by-default audit hook for a specific persisted call.
 *
 * It never initiates telephony or AI traffic. When RECEPVOZ_CALL_AUDIT_ID is
 * supplied it reads the persisted call, actions, transcripts and summary once
 * at startup and logs only non-secret certification metadata.
 */
@Component
public class CallCertificationAuditRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(CallCertificationAuditRunner.class);

    private final String callId;
    private final CallSessionRepository calls;
    private final CallActionRepository actions;
    private final CallTranscriptRepository transcripts;
    private final CallSummaryRepository summaries;

    public CallCertificationAuditRunner(
            @Value("${RECEPVOZ_CALL_AUDIT_ID:}") String callId,
            CallSessionRepository calls,
            CallActionRepository actions,
            CallTranscriptRepository transcripts,
            CallSummaryRepository summaries) {
        this.callId = callId;
        this.calls = calls;
        this.actions = actions;
        this.transcripts = transcripts;
        this.summaries = summaries;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (callId == null || callId.isBlank()) return;

        final UUID id;
        try {
            id = UUID.fromString(callId.trim());
        } catch (IllegalArgumentException e) {
            log.error("RECEPVOZ_CALL_AUDIT invalid_call_id");
            return;
        }

        CallSession call = calls.findById(id).orElse(null);
        if (call == null) {
            log.error("RECEPVOZ_CALL_AUDIT call_not_found id={}", id);
            return;
        }

        List<CallAction> persistedActions = actions.findAllByCallIdOrderByCreatedAtAsc(id);
        long successful = persistedActions.stream().filter(CallAction::isSuccess).count();
        long failed = persistedActions.size() - successful;
        String actionDigest = persistedActions.stream()
                .map(action -> action.getActionType() + ":" + (action.isSuccess() ? "OK" : "FAIL"))
                .collect(Collectors.joining(","));
        int transcriptCount = transcripts.findAllByCallIdOrderBySequenceNumberAsc(id).size();
        boolean summaryPresent = summaries.findByCallId(id).isPresent();

        log.info("RECEPVOZ_CALL_AUDIT SUCCESS call={} provider={} ai_provider={} status={} resolution={} "
                        + "actions={} successful_actions={} failed_actions={} transcript_items={} summary_present={} digest={}",
                id,
                call.getTelephonyProvider(),
                call.getAiProvider(),
                call.getStatus(),
                call.getResolution(),
                persistedActions.size(),
                successful,
                failed,
                transcriptCount,
                summaryPresent,
                actionDigest.isBlank() ? "none" : actionDigest);
    }
}

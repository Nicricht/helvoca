package cl.helvoca.call;

import cl.helvoca.common.NotFoundException;
import cl.helvoca.security.TenantProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CallQueryService {
    private final CallSessionRepository calls;
    private final CallTranscriptRepository transcripts;
    private final CallSummaryRepository summaries;
    private final TenantProvider tenantProvider;

    public CallQueryService(CallSessionRepository calls,
                            CallTranscriptRepository transcripts,
                            CallSummaryRepository summaries,
                            TenantProvider tenantProvider) {
        this.calls = calls;
        this.transcripts = transcripts;
        this.summaries = summaries;
        this.tenantProvider = tenantProvider;
    }

    @Transactional(readOnly = true)
    public Page<CallResponse> list(Pageable pageable) {
        UUID businessId = tenantProvider.requireBusinessId();
        return calls.findAllByBusinessId(businessId, pageable).map(CallResponse::from);
    }

    @Transactional(readOnly = true)
    public CallDetailResponse get(UUID id) {
        UUID businessId = tenantProvider.requireBusinessId();
        CallSession call = calls.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new NotFoundException("Call not found"));
        var transcript = transcripts.findAllByCallIdOrderBySequenceNumberAsc(id)
                .stream().map(TranscriptResponse::from).toList();
        String summary = summaries.findByCallId(id).map(CallSummary::getSummary).orElse(null);
        return new CallDetailResponse(CallResponse.from(call), transcript, summary);
    }
}

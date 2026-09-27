package cl.helvoca.quality;

import cl.helvoca.call.CallAction;
import cl.helvoca.call.CallActionRepository;
import cl.helvoca.call.CallSession;
import cl.helvoca.call.CallSessionRepository;
import cl.helvoca.call.CallTranscript;
import cl.helvoca.call.CallTranscriptRepository;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.customer.Customer;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.security.TenantProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ConversationReplayCaptureService {
    private static final int SCHEMA_VERSION = 1;

    private final CallSessionRepository calls;
    private final CallTranscriptRepository transcripts;
    private final CallActionRepository actions;
    private final CustomerRepository customers;
    private final TenantProvider tenant;
    private final ConversationReplayAnonymizer anonymizer = new ConversationReplayAnonymizer();
    private final ConversationReplayRunner runner = new ConversationReplayRunner();

    public ConversationReplayCaptureService(
            CallSessionRepository calls,
            CallTranscriptRepository transcripts,
            CallActionRepository actions,
            CustomerRepository customers,
            TenantProvider tenant) {
        this.calls = calls;
        this.transcripts = transcripts;
        this.actions = actions;
        this.customers = customers;
        this.tenant = tenant;
    }

    @Transactional(readOnly = true)
    public ConversationReplayFixture capture(UUID callId) {
        UUID businessId = tenant.requireBusinessId();
        CallSession call = calls.findByIdAndBusinessId(callId, businessId)
                .orElseThrow(() -> new NotFoundException("Call not found"));

        Map<String, String> sensitive = sensitiveValues(businessId, call);
        List<ConversationReplayFixture.ReplayTurn> replayTurns = transcripts
                .findAllByCallIdOrderBySequenceNumberAsc(callId)
                .stream()
                .map(turn -> new ConversationReplayFixture.ReplayTurn(
                        turn.getSpeaker(),
                        anonymizer.redact(turn.getContent(), sensitive)))
                .toList();

        Map<UUID, String> entityRefs = new LinkedHashMap<>();
        List<ConversationReplayFixture.ReplayAction> replayActions = actions
                .findAllByBusinessIdAndCallIdOrderByCreatedAtAsc(businessId, callId)
                .stream()
                .map(action -> replayAction(action, sensitive, entityRefs))
                .toList();

        String fingerprint = fingerprint(businessId, callId);
        ConversationReplayFixture draft = new ConversationReplayFixture(
                SCHEMA_VERSION,
                "call-" + fingerprint,
                fingerprint,
                replayTurns,
                replayActions,
                new ConversationReplayFixture.Expected(true, List.of()));

        ConversationQualityEngine.Report observed = runner.run(draft).report();
        List<String> findingCodes = new ArrayList<>(new LinkedHashSet<>(
                observed.findings().stream().map(ConversationQualityEngine.Finding::code).toList()));

        return new ConversationReplayFixture(
                SCHEMA_VERSION,
                draft.name(),
                fingerprint,
                replayTurns,
                replayActions,
                new ConversationReplayFixture.Expected(observed.passed(), findingCodes));
    }

    private Map<String, String> sensitiveValues(UUID businessId, CallSession call) {
        Map<String, String> values = new LinkedHashMap<>();
        put(values, "CALLER_PHONE", call.getCallerNumber());
        put(values, "BUSINESS_PHONE", call.getDestinationNumber());

        if (call.getCustomerId() != null) {
            Customer customer = customers.findByIdAndBusinessId(call.getCustomerId(), businessId).orElse(null);
            if (customer != null) {
                put(values, "CUSTOMER_NAME", customer.getName());
                put(values, "CUSTOMER_PHONE", customer.getPhone());
                put(values, "CUSTOMER_EMAIL", customer.getEmail());
            }
        }
        return values;
    }

    private ConversationReplayFixture.ReplayAction replayAction(
            CallAction action,
            Map<String, String> sensitive,
            Map<UUID, String> entityRefs) {
        String entityRef = null;
        if (action.getEntityId() != null) {
            entityRef = entityRefs.computeIfAbsent(
                    action.getEntityId(),
                    ignored -> "ENTITY_" + (entityRefs.size() + 1));
        }
        return new ConversationReplayFixture.ReplayAction(
                action.getActionType(),
                action.isSuccess(),
                action.getEntityType(),
                entityRef,
                anonymizer.redact(action.getDetail(), sensitive));
    }

    private static void put(Map<String, String> values, String key, String value) {
        if (value != null && !value.isBlank()) values.put(key, value.trim());
    }

    private static String fingerprint(UUID businessId, UUID callId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((businessId + ":" + callId).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 8);
        } catch (Exception error) {
            throw new IllegalStateException("Unable to fingerprint replay source", error);
        }
    }
}

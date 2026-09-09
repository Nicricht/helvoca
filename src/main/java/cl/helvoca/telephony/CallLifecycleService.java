package cl.helvoca.telephony;

import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.call.CallDirection;
import cl.helvoca.call.CallSession;
import cl.helvoca.call.CallSessionRepository;
import cl.helvoca.call.CallStatus;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.phone.PhoneNumber;
import cl.helvoca.phone.PhoneNumberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Provider-neutral lifecycle for calls persisted by Helvoca.
 *
 * <p>Carrier adapters translate their own webhooks/protocols and delegate the
 * durable business state to this service.</p>
 */
@Service
public class CallLifecycleService {
    private final PhoneNumberRepository phoneNumbers;
    private final CustomerRepository customers;
    private final CallSessionRepository calls;

    public CallLifecycleService(PhoneNumberRepository phoneNumbers,
                                CustomerRepository customers,
                                CallSessionRepository calls) {
        this.phoneNumbers = phoneNumbers;
        this.customers = customers;
        this.calls = calls;
    }

    @Transactional
    public UUID startInboundCall(String telephonyProvider,
                                 String providerCallId,
                                 String from,
                                 String to) {
        CallSession existing = calls.findByProviderCallId(providerCallId).orElse(null);
        if (existing != null) return existing.getId();

        PhoneNumber phone = phoneNumbers.findByPhoneNumberAndActiveTrue(to)
                .orElseThrow(() -> new NotFoundException("Destination phone number is not registered"));

        CallSession call = new CallSession();
        call.setBusinessId(phone.getBusinessId());
        call.setPhoneNumberId(phone.getId());
        call.setTelephonyProvider(normalizeProvider(telephonyProvider));
        call.setProviderCallId(providerCallId);
        call.setCallerNumber(from);
        call.setDestinationNumber(to);
        call.setDirection(CallDirection.INBOUND);
        call.setStatus(CallStatus.RINGING);
        call.setStartedAt(Instant.now());
        customers.findFirstByBusinessIdAndPhone(phone.getBusinessId(), from)
                .ifPresent(customer -> call.setCustomerId(customer.getId()));
        return calls.saveAndFlush(call).getId();
    }

    @Transactional
    public RealtimeCallContext startTrialCall(String telephonyProvider,
                                              String providerCallId,
                                              String from,
                                              String to) {
        CallSession existing = calls.findByProviderCallId(providerCallId).orElse(null);
        if (existing != null) {
            if (existing.getStreamSid() == null || !existing.getStreamSid().startsWith("trial:")) {
                throw new IllegalArgumentException("The call already belongs to a non-trial media session");
            }
            return trialContext(existing);
        }

        PhoneNumber destinationPhone = phoneNumbers.findByPhoneNumberAndActiveTrue(to).orElse(null);
        PhoneNumber sourcePhone = destinationPhone == null
                ? phoneNumbers.findByPhoneNumberAndActiveTrue(from).orElse(null)
                : null;
        PhoneNumber phone = destinationPhone != null ? destinationPhone : sourcePhone;
        if (phone == null) {
            throw new NotFoundException("Trial phone number is not registered as source or destination");
        }

        boolean outbound = destinationPhone == null;
        String customerNumber = outbound ? to : from;
        Instant now = Instant.now();

        CallSession call = new CallSession();
        call.setBusinessId(phone.getBusinessId());
        call.setPhoneNumberId(phone.getId());
        call.setTelephonyProvider(normalizeProvider(telephonyProvider));
        call.setProviderCallId(providerCallId);
        call.setCallerNumber(customerNumber);
        call.setDestinationNumber(to);
        call.setDirection(outbound ? CallDirection.OUTBOUND : CallDirection.INBOUND);
        call.setStatus(CallStatus.IN_PROGRESS);
        call.setStartedAt(now);
        call.setAnsweredAt(now);
        call.setStreamSid(trialStreamId(providerCallId));
        call.setStreamStartedAt(now);
        customers.findFirstByBusinessIdAndPhone(phone.getBusinessId(), customerNumber)
                .ifPresent(customer -> call.setCustomerId(customer.getId()));
        return trialContext(calls.saveAndFlush(call));
    }

    @Transactional(readOnly = true)
    public RealtimeCallContext getTrialContext(String providerCallId) {
        CallSession call = calls.findByProviderCallId(providerCallId)
                .orElseThrow(() -> new NotFoundException("Trial call not found"));
        if (call.getStreamSid() == null || !call.getStreamSid().startsWith("trial:")) {
            throw new IllegalArgumentException("Call is not a trial voice session");
        }
        return trialContext(call);
    }

    @Transactional
    public void markTrialEnded(String providerCallId) {
        CallSession call = calls.findByProviderCallId(providerCallId)
                .orElseThrow(() -> new NotFoundException("Trial call not found"));
        Instant now = Instant.now();
        if (call.getStreamEndedAt() == null) call.setStreamEndedAt(now);
        if (call.getEndedAt() == null) call.setEndedAt(now);
        call.setStatus(CallStatus.COMPLETED);
        if (call.getDurationSeconds() == null && call.getStartedAt() != null) {
            call.setDurationSeconds((int) Math.max(0, Duration.between(call.getStartedAt(), now).toSeconds()));
        }
    }

    @Transactional
    public void updateStatus(String providerCallId, String providerStatus, Integer durationSeconds) {
        CallSession call = calls.findByProviderCallId(providerCallId)
                .orElseThrow(() -> new NotFoundException("Call not found"));
        CallStatus mapped = mapStatus(providerStatus);
        call.setStatus(mapped);
        Instant now = Instant.now();
        if (mapped == CallStatus.IN_PROGRESS && call.getAnsweredAt() == null) {
            call.setAnsweredAt(now);
        }
        if (mapped.terminal() && call.getEndedAt() == null) {
            call.setEndedAt(now);
        }
        if (durationSeconds != null && durationSeconds >= 0) {
            call.setDurationSeconds(durationSeconds);
        } else if (mapped.terminal() && call.getStartedAt() != null && call.getEndedAt() != null) {
            call.setDurationSeconds((int) Math.max(0, Duration.between(call.getStartedAt(), call.getEndedAt()).toSeconds()));
        }
    }

    @Transactional
    public RealtimeCallContext markStreamStarted(UUID callId,
                                                 String providerCallId,
                                                 String streamId,
                                                 String aiProvider) {
        CallSession call = calls.findById(callId)
                .orElseThrow(() -> new NotFoundException("Call not found"));
        if (!Objects.equals(call.getProviderCallId(), providerCallId)) {
            throw new IllegalArgumentException("Provider call id does not match callId");
        }
        call.setStreamSid(streamId);
        call.setAiProvider(normalizeProvider(aiProvider));
        call.setStreamStartedAt(Instant.now());
        if (call.getStatus() == CallStatus.RINGING || call.getStatus() == CallStatus.QUEUED) {
            call.setStatus(CallStatus.IN_PROGRESS);
            if (call.getAnsweredAt() == null) call.setAnsweredAt(Instant.now());
        }
        calls.saveAndFlush(call);
        return context(call);
    }

    @Transactional
    public void markStreamStopped(String streamId) {
        if (streamId == null || streamId.isBlank()) return;
        calls.findByStreamSid(streamId).ifPresent(call -> call.setStreamEndedAt(Instant.now()));
    }

    public static CallStatus mapStatus(String value) {
        if (value == null) return CallStatus.UNKNOWN;
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "queued" -> CallStatus.QUEUED;
            case "ringing" -> CallStatus.RINGING;
            case "in-progress" -> CallStatus.IN_PROGRESS;
            case "completed" -> CallStatus.COMPLETED;
            case "busy" -> CallStatus.BUSY;
            case "failed" -> CallStatus.FAILED;
            case "no-answer" -> CallStatus.NO_ANSWER;
            case "canceled", "cancelled" -> CallStatus.CANCELED;
            default -> CallStatus.UNKNOWN;
        };
    }

    private static RealtimeCallContext trialContext(CallSession call) {
        return context(call);
    }

    private static RealtimeCallContext context(CallSession call) {
        return new RealtimeCallContext(
                call.getId(),
                call.getBusinessId(),
                call.getCustomerId(),
                call.getCallerNumber(),
                call.getDestinationNumber(),
                call.getStreamSid());
    }

    private static String trialStreamId(String providerCallId) {
        return "trial:" + providerCallId;
    }

    private static String normalizeProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("Provider id is required");
        }
        return provider.trim().toLowerCase(Locale.ROOT);
    }
}

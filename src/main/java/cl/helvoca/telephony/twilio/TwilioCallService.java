package cl.helvoca.telephony.twilio;

import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.call.*;
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

@Service
public class TwilioCallService {
    private final PhoneNumberRepository phoneNumbers;
    private final CustomerRepository customers;
    private final CallSessionRepository calls;
    private final TwimlFactory twiml;

    public TwilioCallService(PhoneNumberRepository phoneNumbers,
                             CustomerRepository customers,
                             CallSessionRepository calls,
                             TwimlFactory twiml) {
        this.phoneNumbers = phoneNumbers;
        this.customers = customers;
        this.calls = calls;
        this.twiml = twiml;
    }

    @Transactional
    public String startInboundCall(String providerCallId, String from, String to) {
        CallSession existing = calls.findByProviderCallId(providerCallId).orElse(null);
        if (existing != null) {
            return twiml.connectMediaStream(existing.getId());
        }

        PhoneNumber phone = phoneNumbers.findByPhoneNumberAndActiveTrue(to)
                .orElseThrow(() -> new NotFoundException("Destination phone number is not registered"));

        CallSession call = new CallSession();
        call.setBusinessId(phone.getBusinessId());
        call.setPhoneNumberId(phone.getId());
        call.setProviderCallId(providerCallId);
        call.setCallerNumber(from);
        call.setDestinationNumber(to);
        call.setDirection(CallDirection.INBOUND);
        call.setStatus(CallStatus.RINGING);
        call.setStartedAt(Instant.now());
        customers.findFirstByBusinessIdAndPhone(phone.getBusinessId(), from)
                .ifPresent(customer -> call.setCustomerId(customer.getId()));
        call = calls.saveAndFlush(call);
        return twiml.connectMediaStream(call.getId());
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
    public RealtimeCallContext markStreamStarted(UUID callId, String providerCallId, String streamSid) {
        CallSession call = calls.findById(callId)
                .orElseThrow(() -> new NotFoundException("Call not found"));
        if (!Objects.equals(call.getProviderCallId(), providerCallId)) {
            throw new IllegalArgumentException("Twilio call SID does not match callId");
        }
        call.setStreamSid(streamSid);
        call.setStreamStartedAt(Instant.now());
        if (call.getStatus() == CallStatus.RINGING || call.getStatus() == CallStatus.QUEUED) {
            call.setStatus(CallStatus.IN_PROGRESS);
            if (call.getAnsweredAt() == null) call.setAnsweredAt(Instant.now());
        }
        calls.saveAndFlush(call);
        return new RealtimeCallContext(
                call.getId(),
                call.getBusinessId(),
                call.getCustomerId(),
                call.getCallerNumber(),
                call.getDestinationNumber(),
                streamSid);
    }

    @Transactional
    public void markStreamStopped(String streamSid) {
        if (streamSid == null || streamSid.isBlank()) return;
        calls.findByStreamSid(streamSid).ifPresent(call -> call.setStreamEndedAt(Instant.now()));
    }

    static CallStatus mapStatus(String value) {
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
}

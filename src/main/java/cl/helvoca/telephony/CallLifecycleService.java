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
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class CallLifecycleService {
    private static final List<CallStatus> ACTIVE_STATUSES =
            List.of(CallStatus.QUEUED, CallStatus.RINGING, CallStatus.IN_PROGRESS);

    private final PhoneNumberRepository phoneNumbers;
    private final CustomerRepository customers;
    private final CallSessionRepository calls;
    private final JdbcTemplate jdbc;
    private final CallCommercialProperties commercial;
    private final MeterRegistry metrics;

    public CallLifecycleService(PhoneNumberRepository phoneNumbers,
                                CustomerRepository customers,
                                CallSessionRepository calls,
                                JdbcTemplate jdbc,
                                CallCommercialProperties commercial,
                                MeterRegistry metrics) {
        this.phoneNumbers = phoneNumbers;
        this.customers = customers;
        this.calls = calls;
        this.jdbc = jdbc;
        this.commercial = commercial;
        this.metrics = metrics;
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

        lockBusinessCapacity(phone.getBusinessId());
        existing = calls.findByProviderCallId(providerCallId).orElse(null);
        if (existing != null) return existing.getId();

        int limit = commercial.getMaxConcurrentPerBusiness();
        long activeCalls = calls.countByBusinessIdAndStatusIn(phone.getBusinessId(), ACTIVE_STATUSES);
        if (limit > 0 && activeCalls >= limit) {
            metrics.counter("helvoca.calls.rejected", "reason", "capacity").increment();
            throw new CallCapacityExceededException("Concurrent call capacity reached for business");
        }

        String normalizedProvider = normalizeProvider(telephonyProvider);
        CallSession call = new CallSession();
        call.setBusinessId(phone.getBusinessId());
        call.setPhoneNumberId(phone.getId());
        call.setTelephonyProvider(normalizedProvider);
        call.setProviderCallId(providerCallId);
        call.setCallerNumber(from);
        call.setDestinationNumber(to);
        call.setDirection(CallDirection.INBOUND);
        call.setStatus(CallStatus.RINGING);
        call.setStartedAt(Instant.now());
        customers.findFirstByBusinessIdAndPhone(phone.getBusinessId(), from)
                .ifPresent(customer -> call.setCustomerId(customer.getId()));
        CallSession saved = calls.saveAndFlush(call);
        metrics.counter("helvoca.calls.started", "provider", normalizedProvider).increment();
        return saved.getId();
    }

    @Transactional
    public UUID updateStatus(String providerCallId, String providerStatus, Integer durationSeconds) {
        CallSession call = calls.findByProviderCallId(providerCallId)
                .orElseThrow(() -> new NotFoundException("Call not found"));
        updateStatusAndCommercialFields(call, providerStatus, durationSeconds, true);
        return call.getId();
    }

    @Transactional
    public UUID updateStatus(UUID callId, String providerStatus, Integer durationSeconds) {
        CallSession call = calls.findById(callId)
                .orElseThrow(() -> new NotFoundException("Call not found"));
        updateStatusAndCommercialFields(call, providerStatus, durationSeconds, false);
        return call.getId();
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
    public void markCertification(UUID callId) {
        CallSession call = calls.findById(callId)
                .orElseThrow(() -> new NotFoundException("Call not found"));
        if (!call.isCertification()) {
            call.setCertification(true);
            calls.saveAndFlush(call);
        }
    }

    @Transactional
    public void markAiSetupCompleted(UUID callId) {
        CallSession call = calls.findById(callId)
                .orElseThrow(() -> new NotFoundException("Call not found"));
        if (call.getAiSetupCompletedAt() == null) {
            call.setAiSetupCompletedAt(Instant.now());
            calls.saveAndFlush(call);
        }
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

    private void updateStatusAndCommercialFields(CallSession call,
                                                 String providerStatus,
                                                 Integer durationSeconds,
                                                 boolean carrierCallback) {
        boolean wasTerminal = call.getStatus() != null && call.getStatus().terminal();
        applyStatus(call, providerStatus, durationSeconds, carrierCallback);
        if (call.getStatus() != null && call.getStatus().terminal()) {
            updateEstimatedCost(call);
            if (!wasTerminal) {
                metrics.counter("helvoca.calls.terminal",
                        "status", call.getStatus().name().toLowerCase(Locale.ROOT)).increment();
                if (call.getDurationSeconds() != null) {
                    metrics.summary("helvoca.call.duration.seconds").record(call.getDurationSeconds());
                }
                if (call.getEstimatedTotalCostUsd() != null) {
                    metrics.summary("helvoca.call.estimated.cost.usd")
                            .record(call.getEstimatedTotalCostUsd().doubleValue());
                }
            }
        }
        calls.saveAndFlush(call);
    }

    private void updateEstimatedCost(CallSession call) {
        Integer seconds = call.getDurationSeconds();
        if (seconds == null || seconds < 0) return;
        BigDecimal minutes = BigDecimal.valueOf(seconds)
                .divide(BigDecimal.valueOf(60), 6, RoundingMode.HALF_UP);
        BigDecimal telephony = commercial.getTelephonyCostPerMinuteUsd()
                .multiply(minutes).setScale(6, RoundingMode.HALF_UP);
        BigDecimal ai = call.getAiProvider() == null || call.getAiProvider().isBlank()
                ? BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP)
                : commercial.getAiCostPerMinuteUsd()
                .multiply(minutes).setScale(6, RoundingMode.HALF_UP);
        call.setEstimatedTelephonyCostUsd(telephony);
        call.setEstimatedAiCostUsd(ai);
        call.setEstimatedTotalCostUsd(telephony.add(ai).setScale(6, RoundingMode.HALF_UP));
    }

    private void lockBusinessCapacity(UUID businessId) {
        long msb = businessId.getMostSignificantBits();
        long lsb = businessId.getLeastSignificantBits();
        int key1 = (int) (msb ^ (msb >>> 32));
        int key2 = (int) (lsb ^ (lsb >>> 32));
        jdbc.execute("SELECT pg_advisory_xact_lock(" + key1 + "," + key2 + ")");
    }

    private static void applyStatus(CallSession call,
                                    String providerStatus,
                                    Integer durationSeconds,
                                    boolean carrierCallback) {
        CallStatus mapped = mapStatus(providerStatus);
        CallStatus current = call.getStatus();
        boolean preserveApplicationFailure = carrierCallback
                && current == CallStatus.FAILED
                && mapped == CallStatus.COMPLETED;
        if (!preserveApplicationFailure) call.setStatus(mapped);

        Instant now = Instant.now();
        if (mapped == CallStatus.IN_PROGRESS && call.getAnsweredAt() == null) call.setAnsweredAt(now);
        if ((mapped.terminal() || preserveApplicationFailure) && call.getEndedAt() == null) call.setEndedAt(now);
        if (durationSeconds != null && durationSeconds >= 0) {
            call.setDurationSeconds(durationSeconds);
        } else if ((mapped.terminal() || preserveApplicationFailure)
                && call.getStartedAt() != null && call.getEndedAt() != null) {
            call.setDurationSeconds((int) Math.max(0,
                    Duration.between(call.getStartedAt(), call.getEndedAt()).toSeconds()));
        }
    }

    private static RealtimeCallContext context(CallSession call) {
        return new RealtimeCallContext(
                call.getId(), call.getBusinessId(), call.getCustomerId(), call.getCallerNumber(),
                call.getDestinationNumber(), call.getStreamSid());
    }

    private static String normalizeProvider(String provider) {
        if (provider == null || provider.isBlank()) throw new IllegalArgumentException("Provider id is required");
        return provider.trim().toLowerCase(Locale.ROOT);
    }
}

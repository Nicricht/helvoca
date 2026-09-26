package cl.helvoca.call;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface CallSessionRepository extends JpaRepository<CallSession, UUID> {
    Optional<CallSession> findByProviderCallId(String providerCallId);
    Optional<CallSession> findByStreamSid(String streamSid);
    Optional<CallSession> findByIdAndBusinessId(UUID id, UUID businessId);
    Optional<CallSession> findFirstByBusinessIdAndCertificationTrueOrderByStartedAtDesc(UUID businessId);
    Page<CallSession> findAllByBusinessId(UUID businessId, Pageable pageable);
    Page<CallSession> findAllByBusinessIdAndCertificationFalseAndTelephonyProviderNot(
            UUID businessId, String excludedProvider, Pageable pageable);
    long countByBusinessIdAndStatusIn(UUID businessId, Collection<CallStatus> statuses);
    long countByBusinessIdAndStartedAtGreaterThanEqualAndStartedAtLessThan(
            UUID businessId, Instant start, Instant end);
    long countByBusinessIdAndStatusInAndStartedAtGreaterThanEqualAndStartedAtLessThan(
            UUID businessId, Collection<CallStatus> statuses, Instant start, Instant end);
    long countByBusinessIdAndTelephonyProviderNotAndStartedAtGreaterThanEqualAndStartedAtLessThan(
            UUID businessId, String excludedProvider, Instant start, Instant end);
    long countByBusinessIdAndTelephonyProviderNotAndStatusInAndStartedAtGreaterThanEqualAndStartedAtLessThan(
            UUID businessId, String excludedProvider, Collection<CallStatus> statuses, Instant start, Instant end);
    long countByBusinessIdAndCertificationFalseAndTelephonyProviderNotAndStartedAtGreaterThanEqualAndStartedAtLessThan(
            UUID businessId, String excludedProvider, Instant start, Instant end);
    long countByBusinessIdAndCertificationFalseAndTelephonyProviderNotAndStatusInAndStartedAtGreaterThanEqualAndStartedAtLessThan(
            UUID businessId, String excludedProvider, Collection<CallStatus> statuses, Instant start, Instant end);
    long countByBusinessIdAndCertificationFalseAndTelephonyProviderNotAndResolutionAndStartedAtGreaterThanEqualAndStartedAtLessThan(
            UUID businessId, String excludedProvider, String resolution, Instant start, Instant end);

    @Query("""
        select coalesce(sum(c.estimatedTotalCostUsd), 0) from CallSession c
        where c.businessId = :businessId
          and c.startedAt >= :start
          and c.startedAt < :end
          and c.telephonyProvider <> :excludedProvider
          and c.certification = false
        """)
    BigDecimal sumEstimatedCostByBusinessAndPeriod(
            @Param("businessId") UUID businessId,
            @Param("start") Instant start,
            @Param("end") Instant end,
            @Param("excludedProvider") String excludedProvider);

    @Query("""
        select coalesce(sum(c.durationSeconds), 0) from CallSession c
        where c.businessId = :businessId
          and c.startedAt >= :start
          and c.startedAt < :end
          and c.telephonyProvider <> :excludedProvider
          and c.certification = false
          and c.durationSeconds is not null
        """)
    Long sumDurationSecondsByBusinessAndPeriod(
            @Param("businessId") UUID businessId,
            @Param("start") Instant start,
            @Param("end") Instant end,
            @Param("excludedProvider") String excludedProvider);
}

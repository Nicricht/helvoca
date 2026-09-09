package cl.helvoca.booking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {
    List<Booking> findAllByBusinessIdOrderByStartAtDesc(UUID businessId);
    Optional<Booking> findByIdAndBusinessId(UUID id, UUID businessId);

    @Query("""
        select count(b) from Booking b
        where b.businessId = :businessId
          and b.serviceId = :serviceId
          and b.status <> :cancelled
          and b.startAt < :endAt
          and b.endAt > :startAt
          and (:excludeId is null or b.id <> :excludeId)
        """)
    long countOverlaps(
            @Param("businessId") UUID businessId,
            @Param("serviceId") UUID serviceId,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt,
            @Param("cancelled") BookingStatus cancelled,
            @Param("excludeId") UUID excludeId
    );
}

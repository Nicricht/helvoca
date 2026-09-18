package cl.helvoca.booking;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingIncidentCampaignRepository extends JpaRepository<BookingIncidentCampaign, UUID> {
    Optional<BookingIncidentCampaign> findByIdAndBusinessId(UUID id, UUID businessId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from BookingIncidentCampaign c where c.id = :id and c.businessId = :businessId")
    Optional<BookingIncidentCampaign> findForUpdateByIdAndBusinessId(@Param("id") UUID id,
                                                                     @Param("businessId") UUID businessId);

    List<BookingIncidentCampaign> findTop50ByBusinessIdOrderByCreatedAtDesc(UUID businessId);
}

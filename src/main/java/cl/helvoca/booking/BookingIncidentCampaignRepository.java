package cl.helvoca.booking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingIncidentCampaignRepository extends JpaRepository<BookingIncidentCampaign, UUID> {
    Optional<BookingIncidentCampaign> findByIdAndBusinessId(UUID id, UUID businessId);
    List<BookingIncidentCampaign> findTop50ByBusinessIdOrderByCreatedAtDesc(UUID businessId);
}

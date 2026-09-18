package cl.helvoca.booking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingIncidentRecipientRepository extends JpaRepository<BookingIncidentRecipient, UUID> {
    List<BookingIncidentRecipient> findAllByCampaignIdAndBusinessIdOrderByCreatedAtAsc(UUID campaignId, UUID businessId);
    Optional<BookingIncidentRecipient> findByIdAndCampaignIdAndBusinessId(UUID id, UUID campaignId, UUID businessId);
    long countByCampaignIdAndBusinessId(UUID campaignId, UUID businessId);
}

package cl.helvoca.booking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BookingIncidentRecipientRepository extends JpaRepository<BookingIncidentRecipient, UUID> {
    List<BookingIncidentRecipient> findAllByCampaignIdAndBusinessIdOrderByCreatedAtAsc(UUID campaignId, UUID businessId);
}

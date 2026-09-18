package cl.helvoca.booking;

import cl.helvoca.customer.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class BookingIncidentCampaignService {
    private final BookingIncidentCampaignRepository campaigns;
    private final BookingIncidentRecipientRepository recipients;
    private final BookingRepository bookings;
    private final CustomerRepository customers;

    public BookingIncidentCampaignService(BookingIncidentCampaignRepository campaigns,
                                          BookingIncidentRecipientRepository recipients,
                                          BookingRepository bookings,
                                          CustomerRepository customers) {
        this.campaigns = campaigns;
        this.recipients = recipients;
        this.bookings = bookings;
        this.customers = customers;
    }

    @Transactional
    public PreparedCampaign prepare(UUID businessId,
                                    String reason,
                                    BookingIncidentCampaign.Goal goal,
                                    BookingIncidentCampaign.Strategy strategy,
                                    List<RecipientDraft> drafts) {
        if (businessId == null) throw new IllegalArgumentException("businessId is required");
        String safeReason = reason == null ? "" : reason.trim();
        if (safeReason.isBlank()) throw new IllegalArgumentException("Incident reason is required");
        if (safeReason.length() > 500) throw new IllegalArgumentException("Incident reason is too long");
        if (goal == null || strategy == null) throw new IllegalArgumentException("Goal and strategy are required");
        if (drafts == null || drafts.isEmpty()) throw new IllegalArgumentException("Select at least one customer");
        if (drafts.size() > 200) throw new IllegalArgumentException("Too many recipients in one campaign");

        Set<UUID> seenCustomers = new HashSet<>();
        Set<UUID> seenBookings = new HashSet<>();
        List<ValidatedRecipient> validated = new ArrayList<>();

        for (RecipientDraft draft : drafts) {
            if (draft == null || draft.customerId() == null) {
                throw new IllegalArgumentException("Recipient customer is required");
            }
            if (!seenCustomers.add(draft.customerId())) {
                throw new IllegalArgumentException("Customer is duplicated in campaign");
            }
            customers.findByIdAndBusinessId(draft.customerId(), businessId)
                    .orElseThrow(() -> new IllegalArgumentException("Customer does not belong to tenant"));

            if (draft.bookingIds() == null || draft.bookingIds().isEmpty()) {
                throw new IllegalArgumentException("Recipient must include at least one booking");
            }
            if (draft.bookingIds().size() > 50) {
                throw new IllegalArgumentException("Too many bookings for one recipient");
            }
            List<String> bookingIds = new ArrayList<>();
            for (UUID bookingId : draft.bookingIds()) {
                if (bookingId == null || !seenBookings.add(bookingId)) {
                    throw new IllegalArgumentException("Booking is missing or duplicated in campaign");
                }
                Booking booking = bookings.findByIdAndBusinessId(bookingId, businessId)
                        .orElseThrow(() -> new IllegalArgumentException("Booking does not belong to tenant"));
                if (!draft.customerId().equals(booking.getCustomerId())) {
                    throw new IllegalArgumentException("Booking does not belong to selected customer");
                }
                if (booking.getStatus() != BookingStatus.CONFIRMED) {
                    throw new IllegalArgumentException("Only confirmed bookings can be included");
                }
                bookingIds.add(bookingId.toString());
            }

            String content = draft.content() == null ? "" : draft.content().trim();
            if (content.isBlank()) throw new IllegalArgumentException("Prepared message is required");
            if (content.length() > 2000) throw new IllegalArgumentException("Prepared message is too long");
            if (draft.channelPreference() == null) {
                throw new IllegalArgumentException("Channel preference is required");
            }
            validated.add(new ValidatedRecipient(
                    draft.customerId(),
                    List.copyOf(bookingIds),
                    draft.channelPreference(),
                    content
            ));
        }

        BookingIncidentCampaign campaign = new BookingIncidentCampaign();
        campaign.setBusinessId(businessId);
        campaign.setReason(safeReason);
        campaign.setGoal(goal);
        campaign.setStrategy(strategy);
        campaign.setStatus(BookingIncidentCampaign.Status.PREPARED);
        campaign = campaigns.saveAndFlush(campaign);

        List<BookingIncidentRecipient> saved = new ArrayList<>();
        for (ValidatedRecipient item : validated) {
            BookingIncidentRecipient recipient = new BookingIncidentRecipient();
            recipient.setCampaignId(campaign.getId());
            recipient.setBusinessId(businessId);
            recipient.setCustomerId(item.customerId());
            recipient.setBookingIds(item.bookingIds());
            recipient.setChannelPreference(item.channelPreference());
            recipient.setContentText(item.content());
            recipient.setStatus(BookingIncidentRecipient.Status.PREPARED);
            saved.add(recipient);
        }
        saved = recipients.saveAll(saved);
        return new PreparedCampaign(campaign, List.copyOf(saved));
    }

    @Transactional(readOnly = true)
    public List<CampaignSummary> recent(UUID businessId) {
        if (businessId == null) throw new IllegalArgumentException("businessId is required");
        return campaigns.findTop50ByBusinessIdOrderByCreatedAtDesc(businessId).stream()
                .map(campaign -> new CampaignSummary(
                        campaign.getId(),
                        campaign.getReason(),
                        campaign.getGoal(),
                        campaign.getStrategy(),
                        campaign.getStatus(),
                        recipients.countByCampaignIdAndBusinessId(campaign.getId(), businessId),
                        campaign.getCreatedAt()))
                .toList();
    }

    public record CampaignSummary(UUID id,
                                  String reason,
                                  BookingIncidentCampaign.Goal goal,
                                  BookingIncidentCampaign.Strategy strategy,
                                  BookingIncidentCampaign.Status status,
                                  long recipientCount,
                                  java.time.Instant createdAt) { }

    public record RecipientDraft(UUID customerId,
                                 List<UUID> bookingIds,
                                 BookingIncidentRecipient.ChannelPreference channelPreference,
                                 String content) { }

    public record PreparedCampaign(BookingIncidentCampaign campaign,
                                   List<BookingIncidentRecipient> recipients) { }

    private record ValidatedRecipient(UUID customerId,
                                      List<String> bookingIds,
                                      BookingIncidentRecipient.ChannelPreference channelPreference,
                                      String content) { }
}

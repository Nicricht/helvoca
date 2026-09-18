package cl.helvoca.booking;

import cl.helvoca.security.TenantProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/booking-incident-campaigns")
@PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
public class BookingIncidentCampaignController {
    private final BookingIncidentCampaignService service;
    private final TenantProvider tenantProvider;

    public BookingIncidentCampaignController(BookingIncidentCampaignService service,
                                             TenantProvider tenantProvider) {
        this.service = service;
        this.tenantProvider = tenantProvider;
    }

    @PostMapping
    public ResponseEntity<View> prepare(@Valid @RequestBody PrepareRequest request) {
        var prepared = service.prepare(
                tenantProvider.requireBusinessId(),
                request.reason(),
                request.goal(),
                request.strategy(),
                request.recipients().stream()
                        .map(item -> new BookingIncidentCampaignService.RecipientDraft(
                                item.customerId(),
                                item.bookingIds(),
                                item.channelPreference(),
                                item.content()))
                        .toList()
        );
        return ResponseEntity.ok(View.from(prepared));
    }

    public record PrepareRequest(
            @NotBlank @Size(max = 500) String reason,
            @NotNull BookingIncidentCampaign.Goal goal,
            @NotNull BookingIncidentCampaign.Strategy strategy,
            @NotEmpty @Size(max = 200) @Valid List<RecipientRequest> recipients
    ) { }

    public record RecipientRequest(
            @NotNull UUID customerId,
            @NotEmpty @Size(max = 50) List<UUID> bookingIds,
            @NotNull BookingIncidentRecipient.ChannelPreference channelPreference,
            @NotBlank @Size(max = 2000) String content
    ) { }

    public record RecipientView(
            UUID id,
            UUID customerId,
            List<String> bookingIds,
            String channelPreference,
            String status,
            String content
    ) {
        static RecipientView from(BookingIncidentRecipient recipient) {
            return new RecipientView(
                    recipient.getId(),
                    recipient.getCustomerId(),
                    recipient.getBookingIds(),
                    recipient.getChannelPreference().name(),
                    recipient.getStatus().name(),
                    recipient.getContentText()
            );
        }
    }

    public record View(
            UUID id,
            String status,
            String goal,
            String strategy,
            int recipientCount,
            Instant createdAt,
            List<RecipientView> recipients
    ) {
        static View from(BookingIncidentCampaignService.PreparedCampaign prepared) {
            var campaign = prepared.campaign();
            var recipients = prepared.recipients().stream().map(RecipientView::from).toList();
            return new View(
                    campaign.getId(),
                    campaign.getStatus().name(),
                    campaign.getGoal().name(),
                    campaign.getStrategy().name(),
                    recipients.size(),
                    campaign.getCreatedAt(),
                    recipients
            );
        }
    }
}

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
    private final BookingIncidentCampaignActivationService activation;
    private final BookingIncidentDeliveryStatusService deliveryStatus;
    private final TenantProvider tenantProvider;

    public BookingIncidentCampaignController(BookingIncidentCampaignService service,
                                             BookingIncidentCampaignActivationService activation,
                                             BookingIncidentDeliveryStatusService deliveryStatus,
                                             TenantProvider tenantProvider) {
        this.service = service;
        this.activation = activation;
        this.deliveryStatus = deliveryStatus;
        this.tenantProvider = tenantProvider;
    }

    @GetMapping
    public ResponseEntity<List<HistoryView>> recent() {
        UUID businessId = tenantProvider.requireBusinessId();
        return ResponseEntity.ok(
                service.recent(businessId).stream()
                        .map(campaign -> HistoryView.from(
                                campaign,
                                activation.readiness(businessId, campaign.id()),
                                deliveryStatus.forCampaign(businessId, campaign.id())))
                        .toList()
        );
    }

    @GetMapping("/{campaignId}/activation-readiness")
    public ResponseEntity<ActivationReadinessView> activationReadiness(@PathVariable UUID campaignId) {
        return ResponseEntity.ok(ActivationReadinessView.from(
                activation.readiness(tenantProvider.requireBusinessId(), campaignId)));
    }

    @PostMapping("/{campaignId}/activate")
    public ResponseEntity<ActivationView> activate(@PathVariable UUID campaignId,
                                                   @RequestBody ActivationRequest request) {
        boolean confirmed = request != null && request.confirmed();
        return ResponseEntity.ok(ActivationView.from(
                activation.activate(tenantProvider.requireBusinessId(), campaignId, confirmed)));
    }

    @PostMapping("/{campaignId}/recipients/{recipientId}/retry")
    public ResponseEntity<RetryView> retry(
            @PathVariable UUID campaignId,
            @PathVariable UUID recipientId,
            @RequestBody RetryRequest request) {
        boolean confirmed = request != null && request.confirmed();
        return ResponseEntity.ok(RetryView.from(
                activation.retryRecipient(
                        tenantProvider.requireBusinessId(),
                        campaignId,
                        recipientId,
                        confirmed)));
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
            @NotEmpty @Size(max = 200) List<@Valid RecipientRequest> recipients
    ) { }

    public record RecipientRequest(
            @NotNull UUID customerId,
            @NotEmpty @Size(max = 50) List<UUID> bookingIds,
            @NotNull BookingIncidentRecipient.ChannelPreference channelPreference,
            @NotBlank @Size(max = 2000) String content
    ) { }

    public record HistoryView(
            UUID id,
            String reason,
            String status,
            String goal,
            String strategy,
            long recipientCount,
            Instant createdAt,
            boolean activationReady,
            List<BookingIncidentCampaignActivationService.Blocker> activationBlockers,
            List<BookingIncidentDeliveryStatusService.RecipientDeliveryStatus> recipients
    ) {
        static HistoryView from(
                BookingIncidentCampaignService.CampaignSummary campaign,
                BookingIncidentCampaignActivationService.ActivationReadiness readiness,
                List<BookingIncidentDeliveryStatusService.RecipientDeliveryStatus> recipients) {
            return new HistoryView(
                    campaign.id(),
                    campaign.reason(),
                    campaign.status().name(),
                    campaign.goal().name(),
                    campaign.strategy().name(),
                    campaign.recipientCount(),
                    campaign.createdAt(),
                    readiness.ready(),
                    readiness.blockers(),
                    recipients
            );
        }
    }

    public record ActivationRequest(boolean confirmed) { }
    public record RetryRequest(boolean confirmed) { }

    public record RetryView(UUID campaignId, UUID recipientId, String status) {
        static RetryView from(BookingIncidentCampaignActivationService.RetryResult result) {
            return new RetryView(result.campaignId(), result.recipientId(), result.status());
        }
    }

    public record ActivationReadinessView(
            boolean ready,
            String channel,
            List<BookingIncidentCampaignActivationService.Blocker> blockers
    ) {
        static ActivationReadinessView from(BookingIncidentCampaignActivationService.ActivationReadiness readiness) {
            return new ActivationReadinessView(readiness.ready(), readiness.channel(), readiness.blockers());
        }
    }

    public record ActivationView(UUID campaignId,
                                 String status,
                                 int queuedRecipients) {
        static ActivationView from(BookingIncidentCampaignActivationService.ActivationResult result) {
            return new ActivationView(result.campaignId(), result.status(), result.queuedRecipients());
        }
    }

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

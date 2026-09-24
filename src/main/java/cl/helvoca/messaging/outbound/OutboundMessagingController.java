package cl.helvoca.messaging.outbound;

import cl.helvoca.jobs.PersistentJob;
import cl.helvoca.security.TenantProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/outbound-messages")
@PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
public class OutboundMessagingController {
    private final OutboundMessagingService service;
    private final OutboundDispatchOutboxService outbox;
    private final TenantProvider tenantProvider;

    public OutboundMessagingController(OutboundMessagingService service,
                                       OutboundDispatchOutboxService outbox,
                                       TenantProvider tenantProvider) {
        this.service = service;
        this.outbox = outbox;
        this.tenantProvider = tenantProvider;
    }

    @GetMapping
    public ResponseEntity<List<View>> recent() {
        UUID businessId = tenantProvider.requireBusinessId();
        return ResponseEntity.ok(service.recent(businessId).stream().map(View::from).toList());
    }

    @PostMapping("/prepare")
    public ResponseEntity<View> prepare(@RequestBody PrepareRequest request) {
        UUID businessId = tenantProvider.requireBusinessId();
        return ResponseEntity.ok(View.from(service.prepare(
                businessId,
                request.customerId(),
                request.channel(),
                request.purpose(),
                request.operationId(),
                request.recipientIdentityId())));
    }

    @PostMapping("/prepare-catalog-media")
    public ResponseEntity<View> prepareCatalogMedia(@RequestBody CatalogMediaPrepareRequest request) {
        UUID businessId = tenantProvider.requireBusinessId();
        return ResponseEntity.ok(View.from(service.prepareCatalogMedia(
                businessId,
                request.customerId(),
                request.operationId(),
                request.recipientIdentityId(),
                request.catalogMediaId())));
    }

    @PostMapping("/{messageId}/queue")
    public ResponseEntity<QueueView> queue(@PathVariable UUID messageId) {
        PersistentJob job = outbox.queue(tenantProvider.requireBusinessId(), messageId);
        return ResponseEntity.ok(new QueueView(job.id(), job.status().name(), job.attemptCount(), job.nextAttemptAt()));
    }

    @PostMapping("/{messageId}/dispatch")
    public ResponseEntity<View> dispatch(@PathVariable UUID messageId) {
        return ResponseEntity.ok(View.from(service.dispatch(tenantProvider.requireBusinessId(), messageId)));
    }

    @PostMapping("/{messageId}/cancel")
    public ResponseEntity<View> cancel(@PathVariable UUID messageId) {
        return ResponseEntity.ok(View.from(service.cancel(tenantProvider.requireBusinessId(), messageId)));
    }

    public record PrepareRequest(UUID customerId,
                                 OutboundMessage.Channel channel,
                                 OutboundMessage.Purpose purpose,
                                 UUID operationId,
                                 UUID recipientIdentityId) { }

    public record CatalogMediaPrepareRequest(UUID customerId,
                                             UUID operationId,
                                             UUID recipientIdentityId,
                                             UUID catalogMediaId) { }

    public record QueueView(UUID jobId,
                            String status,
                            int attemptCount,
                            Instant nextAttemptAt) { }

    public record View(UUID id,
                       UUID customerId,
                       UUID operationId,
                       String channel,
                       String purpose,
                       String recipient,
                       String provider,
                       String status,
                       String content,
                       String contentType,
                       String mediaUrl,
                       String mediaMimeType,
                       String mediaCaption,
                       UUID catalogItemId,
                       String providerMessageId,
                       String failureCode,
                       Instant createdAt,
                       Instant sentAt) {
        static View from(OutboundMessage message) {
            return new View(message.getId(), message.getCustomerId(), message.getOperationId(),
                    message.getChannel().name(), message.getPurpose().name(), message.getRecipientAddress(),
                    message.getProvider(), message.getStatus().name(), message.getContentText(),
                    message.getContentType().name(), message.getMediaUrl(), message.getMediaMimeType(),
                    message.getMediaCaption(), message.getCatalogItemId(),
                    message.getProviderMessageId(), message.getFailureCode(), message.getCreatedAt(), message.getSentAt());
        }
    }
}

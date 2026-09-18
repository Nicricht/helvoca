package cl.helvoca.messaging.outbound;

import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.omnichannel.CustomerIdentity;
import cl.helvoca.omnichannel.CustomerIdentityRepository;
import cl.helvoca.operations.BusinessOperation;
import cl.helvoca.operations.BusinessOperationRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class OutboundMessagingService {
    private static final List<CustomerIdentity.VerificationStatus> VERIFIED = List.copyOf(EnumSet.of(
            CustomerIdentity.VerificationStatus.CUSTOMER_VERIFIED,
            CustomerIdentity.VerificationStatus.MANUAL_VERIFIED));

    private final OutboundMessageRepository messages;
    private final CustomerRepository customers;
    private final CustomerIdentityRepository identities;
    private final BusinessOperationRepository operations;
    private final OutboundContentResolver content;
    private final OutboundMessagingProperties properties;
    private final MessagingProviderRegistry providers;
    private final JdbcTemplate jdbc;

    public OutboundMessagingService(OutboundMessageRepository messages,
                                    CustomerRepository customers,
                                    CustomerIdentityRepository identities,
                                    BusinessOperationRepository operations,
                                    OutboundContentResolver content,
                                    OutboundMessagingProperties properties,
                                    MessagingProviderRegistry providers,
                                    JdbcTemplate jdbc) {
        this.messages = messages;
        this.customers = customers;
        this.identities = identities;
        this.operations = operations;
        this.content = content;
        this.properties = properties;
        this.providers = providers;
        this.jdbc = jdbc;
    }

    @Transactional
    public OutboundMessage prepare(UUID businessId,
                                   UUID customerId,
                                   OutboundMessage.Channel channel,
                                   OutboundMessage.Purpose purpose,
                                   UUID operationId,
                                   UUID recipientIdentityId) {
        if (businessId == null || customerId == null || operationId == null || channel == null || purpose == null) {
            throw new IllegalArgumentException("businessId, customerId, operationId, channel and purpose are required");
        }
        if (channel != OutboundMessage.Channel.WHATSAPP) {
            throw new IllegalArgumentException("Unsupported outbound channel");
        }
        if (customers.findByIdAndBusinessId(customerId, businessId).isEmpty()) {
            throw new IllegalArgumentException("Customer does not belong to tenant");
        }
        BusinessOperation operation = operations.findByIdAndBusinessId(operationId, businessId)
                .orElseThrow(() -> new IllegalArgumentException("Operation not found"));
        if (operation.getCustomerId() == null || !operation.getCustomerId().equals(customerId)) {
            throw new IllegalArgumentException("Operation does not belong to customer");
        }

        CustomerIdentity identity = resolveRecipient(businessId, customerId, recipientIdentityId);
        String rendered = content.render(businessId, customerId, purpose, operation);
        String key = purpose.name() + ":" + operationId + ":" + identity.getId() + ":r" + safeRevision(operation.getRevision());

        // Serialize the logical idempotency key before lookup+insert. The unique
        // constraint remains the final database guard; the advisory lock avoids
        // poisoning the current JPA transaction with an expected duplicate-key
        // exception when two channel requests prepare the same message at once.
        jdbc.execute("SELECT pg_advisory_xact_lock(" + businessId.hashCode() + "," + key.hashCode() + ")");
        OutboundMessage existing = messages.findByBusinessIdAndIdempotencyKey(businessId, key).orElse(null);
        if (existing != null) return existing;

        OutboundMessage message = new OutboundMessage();
        message.setBusinessId(businessId);
        message.setCustomerId(customerId);
        message.setOperationId(operationId);
        message.setRecipientIdentityId(identity.getId());
        message.setChannel(channel);
        message.setPurpose(purpose);
        message.setRecipientAddress(identity.getNormalizedValue());
        message.setStatus(OutboundMessage.Status.PREPARED);
        message.setIdempotencyKey(key);
        message.setContentText(rendered);
        return messages.saveAndFlush(message);
    }

    @Transactional
    public OutboundMessage prepareIncidentNotice(UUID businessId,
                                                 UUID customerId,
                                                 UUID operationId,
                                                 UUID campaignId,
                                                 UUID campaignRecipientId,
                                                 String contentText) {
        if (businessId == null || customerId == null || operationId == null
                || campaignId == null || campaignRecipientId == null) {
            throw new IllegalArgumentException("Incident outbound identifiers are required");
        }
        String rendered = contentText == null ? "" : contentText.trim();
        if (rendered.isBlank()) throw new IllegalArgumentException("Incident outbound content is required");
        if (rendered.length() > 2000) throw new IllegalArgumentException("Incident outbound content is too long");
        if (customers.findByIdAndBusinessId(customerId, businessId).isEmpty()) {
            throw new IllegalArgumentException("Customer does not belong to tenant");
        }
        BusinessOperation operation = operations.findByIdAndBusinessId(operationId, businessId)
                .orElseThrow(() -> new IllegalArgumentException("Operation not found"));
        if (operation.getCustomerId() == null || !operation.getCustomerId().equals(customerId)) {
            throw new IllegalArgumentException("Operation does not belong to customer");
        }

        CustomerIdentity identity = resolveRecipient(businessId, customerId, null);
        String key = "INCIDENT_NOTICE:" + campaignId + ":" + campaignRecipientId;

        jdbc.execute("SELECT pg_advisory_xact_lock(" + businessId.hashCode() + "," + key.hashCode() + ")");
        OutboundMessage existing = messages.findByBusinessIdAndIdempotencyKey(businessId, key).orElse(null);
        if (existing != null) return existing;

        OutboundMessage message = new OutboundMessage();
        message.setBusinessId(businessId);
        message.setCustomerId(customerId);
        message.setOperationId(operationId);
        message.setRecipientIdentityId(identity.getId());
        message.setChannel(OutboundMessage.Channel.WHATSAPP);
        message.setPurpose(OutboundMessage.Purpose.INCIDENT_NOTICE);
        message.setRecipientAddress(identity.getNormalizedValue());
        message.setStatus(OutboundMessage.Status.PREPARED);
        message.setIdempotencyKey(key);
        message.setContentText(rendered);
        return messages.saveAndFlush(message);
    }

    @Transactional
    public OutboundMessage dispatch(UUID businessId, UUID messageId) {
        OutboundMessage message = require(businessId, messageId);
        if (message.getStatus() == OutboundMessage.Status.SENT) return message;
        if (message.getStatus() != OutboundMessage.Status.PREPARED
                && message.getStatus() != OutboundMessage.Status.QUEUED
                && message.getStatus() != OutboundMessage.Status.FAILED) {
            throw new IllegalStateException("Outbound message cannot be dispatched from current state");
        }
        if (!properties.isDeliveryEnabled()) {
            throw new IllegalStateException("Outbound delivery is disabled");
        }

        MessagingProvider provider = providers.require(properties.getProvider(), message.getChannel());
        try {
            MessagingProvider.SendResult result = provider.send(new MessagingProvider.SendCommand(
                    businessId,
                    message.getId(),
                    message.getChannel(),
                    message.getRecipientAddress(),
                    message.getContentText(),
                    message.getIdempotencyKey()));
            if (result == null || result.providerMessageId() == null || result.providerMessageId().isBlank()) {
                throw new IllegalStateException("Provider did not confirm message id");
            }
            message.setProvider(provider.id());
            message.setProviderMessageId(result.providerMessageId().trim());
            message.setFailureCode(null);
            message.setSentAt(Instant.now());
            message.setStatus(OutboundMessage.Status.SENT);
            return messages.saveAndFlush(message);
        } catch (RuntimeException e) {
            message.setProvider(provider.id());
            message.setProviderMessageId(null);
            message.setSentAt(null);
            message.setFailureCode(safeFailureCode(e));
            message.setStatus(OutboundMessage.Status.FAILED);
            return messages.saveAndFlush(message);
        }
    }

    @Transactional
    public OutboundMessage cancel(UUID businessId, UUID messageId) {
        OutboundMessage message = require(businessId, messageId);
        if (message.getStatus() == OutboundMessage.Status.SENT) {
            throw new IllegalStateException("Sent messages cannot be cancelled");
        }
        if (message.getStatus() != OutboundMessage.Status.CANCELLED) {
            message.setStatus(OutboundMessage.Status.CANCELLED);
            message.setCancelledAt(Instant.now());
            messages.saveAndFlush(message);
        }
        return message;
    }

    @Transactional(readOnly = true)
    public List<OutboundMessage> recent(UUID businessId) {
        return messages.findTop100ByBusinessIdOrderByCreatedAtDesc(businessId);
    }

    private CustomerIdentity resolveRecipient(UUID businessId, UUID customerId, UUID identityId) {
        List<CustomerIdentity> candidates = identities
                .findAllByBusinessIdAndCustomerIdAndIdentityTypeAndVerificationStatusIn(
                        businessId, customerId, CustomerIdentity.Type.PHONE, VERIFIED);
        if (identityId != null) {
            return candidates.stream().filter(candidate -> identityId.equals(candidate.getId())).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Recipient identity is not verified for customer"));
        }
        if (candidates.size() != 1) {
            throw new IllegalStateException(candidates.isEmpty()
                    ? "Customer has no verified outbound phone identity"
                    : "Customer has multiple verified outbound phone identities; choose one explicitly");
        }
        return candidates.getFirst();
    }

    private OutboundMessage require(UUID businessId, UUID messageId) {
        if (businessId == null || messageId == null) throw new IllegalArgumentException("businessId and messageId are required");
        return messages.findByIdAndBusinessId(messageId, businessId)
                .orElseThrow(() -> new IllegalArgumentException("Outbound message not found"));
    }

    private static int safeRevision(Integer revision) { return revision == null || revision < 1 ? 1 : revision; }

    private static String safeFailureCode(RuntimeException e) {
        String value = e.getClass().getSimpleName().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]", "_");
        return value.length() <= 80 ? value : value.substring(0, 80);
    }
}

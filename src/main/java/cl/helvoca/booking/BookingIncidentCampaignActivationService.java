package cl.helvoca.booking;

import cl.helvoca.jobs.PersistentJobProperties;
import cl.helvoca.messaging.outbound.MessagingProviderRegistry;
import cl.helvoca.messaging.outbound.OutboundDispatchOutboxService;
import cl.helvoca.messaging.outbound.OutboundMessage;
import cl.helvoca.messaging.outbound.OutboundMessagingProperties;
import cl.helvoca.messaging.outbound.OutboundMessagingService;
import cl.helvoca.messaging.outbound.TwilioWhatsAppMessagingProvider;
import cl.helvoca.omnichannel.CustomerIdentity;
import cl.helvoca.omnichannel.CustomerIdentityRepository;
import cl.helvoca.operations.ChannelRuntimeReadinessService;
import cl.helvoca.phone.PhoneNumberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class BookingIncidentCampaignActivationService {
    private static final List<CustomerIdentity.VerificationStatus> VERIFIED = List.copyOf(EnumSet.of(
            CustomerIdentity.VerificationStatus.CUSTOMER_VERIFIED,
            CustomerIdentity.VerificationStatus.MANUAL_VERIFIED));

    private final BookingIncidentCampaignRepository campaigns;
    private final BookingIncidentRecipientRepository recipients;
    private final BookingRepository bookings;
    private final ChannelRuntimeReadinessService runtimeReadiness;
    private final OutboundMessagingProperties outboundProperties;
    private final PersistentJobProperties jobProperties;
    private final MessagingProviderRegistry providers;
    private final PhoneNumberRepository phones;
    private final CustomerIdentityRepository identities;
    private final OutboundMessagingService outbound;
    private final OutboundDispatchOutboxService outbox;

    public BookingIncidentCampaignActivationService(BookingIncidentCampaignRepository campaigns,
                                                    BookingIncidentRecipientRepository recipients,
                                                    BookingRepository bookings,
                                                    ChannelRuntimeReadinessService runtimeReadiness,
                                                    OutboundMessagingProperties outboundProperties,
                                                    PersistentJobProperties jobProperties,
                                                    MessagingProviderRegistry providers,
                                                    PhoneNumberRepository phones,
                                                    CustomerIdentityRepository identities,
                                                    OutboundMessagingService outbound,
                                                    OutboundDispatchOutboxService outbox) {
        this.campaigns = campaigns;
        this.recipients = recipients;
        this.bookings = bookings;
        this.runtimeReadiness = runtimeReadiness;
        this.outboundProperties = outboundProperties;
        this.jobProperties = jobProperties;
        this.providers = providers;
        this.phones = phones;
        this.identities = identities;
        this.outbound = outbound;
        this.outbox = outbox;
    }

    @Transactional(readOnly = true)
    public ActivationReadiness readiness(UUID businessId, UUID campaignId) {
        if (businessId == null || campaignId == null) {
            throw new IllegalArgumentException("businessId and campaignId are required");
        }
        BookingIncidentCampaign campaign = campaigns.findByIdAndBusinessId(campaignId, businessId)
                .orElseThrow(() -> new IllegalArgumentException("Incident campaign not found"));
        List<BookingIncidentRecipient> items =
                recipients.findAllByCampaignIdAndBusinessIdOrderByCreatedAtAsc(campaignId, businessId);
        return evaluate(businessId, campaign, items);
    }

    @Transactional
    public ActivationResult activate(UUID businessId, UUID campaignId, boolean confirmed) {
        if (!confirmed) {
            throw new IllegalArgumentException("Explicit campaign activation confirmation is required");
        }
        BookingIncidentCampaign campaign = campaigns.findForUpdateByIdAndBusinessId(campaignId, businessId)
                .orElseThrow(() -> new IllegalArgumentException("Incident campaign not found"));
        List<BookingIncidentRecipient> items =
                recipients.findAllByCampaignIdAndBusinessIdOrderByCreatedAtAsc(campaignId, businessId);

        ActivationReadiness readiness = evaluate(businessId, campaign, items);
        if (!readiness.ready()) {
            String reason = readiness.blockers().isEmpty()
                    ? "Campaign activation is not ready"
                    : readiness.blockers().getFirst().message();
            throw new IllegalStateException(reason);
        }

        int queued = 0;
        for (BookingIncidentRecipient recipient : items) {
            UUID bookingId = UUID.fromString(recipient.getBookingIds().getFirst());
            Booking booking = bookings.findByIdAndBusinessId(bookingId, businessId)
                    .orElseThrow(() -> new IllegalStateException("Prepared campaign booking no longer exists"));

            OutboundMessage message = outbound.prepareIncidentNotice(
                    businessId,
                    recipient.getCustomerId(),
                    booking.getOperationId(),
                    campaign.getId(),
                    recipient.getId(),
                    recipient.getContentText());

            outbox.queue(businessId, message.getId());
            recipient.setOutboundMessageId(message.getId());
            recipient.setStatus(BookingIncidentRecipient.Status.QUEUED);
            queued++;
        }

        recipients.saveAll(items);
        campaign.setStatus(BookingIncidentCampaign.Status.ACTIVATED);
        campaigns.saveAndFlush(campaign);
        return new ActivationResult(campaign.getId(), campaign.getStatus().name(), queued);
    }

    @Transactional
    public RetryResult retryRecipient(UUID businessId,
                                      UUID campaignId,
                                      UUID recipientId,
                                      boolean confirmed) {
        if (!confirmed) {
            throw new IllegalArgumentException("Explicit retry confirmation is required");
        }
        BookingIncidentCampaign campaign = campaigns.findForUpdateByIdAndBusinessId(campaignId, businessId)
                .orElseThrow(() -> new IllegalArgumentException("Incident campaign not found"));
        BookingIncidentRecipient recipient = recipients
                .findByIdAndCampaignIdAndBusinessId(recipientId, campaignId, businessId)
                .orElseThrow(() -> new IllegalArgumentException("Incident recipient not found"));
        if (recipient.getOutboundMessageId() == null) {
            throw new IllegalStateException("Recipient has no outbound message to retry");
        }

        ActivationReadiness readiness = evaluate(businessId, campaign, List.of(recipient));
        List<Blocker> blockers = readiness.blockers().stream()
                .filter(blocker -> !"CAMPAIGN_NOT_PREPARED".equals(blocker.code()))
                .filter(blocker -> !"RECIPIENT_NOT_PREPARED".equals(blocker.code()))
                .toList();
        if (!blockers.isEmpty()) {
            throw new IllegalStateException(blockers.getFirst().message());
        }

        outbox.retry(businessId, recipient.getOutboundMessageId());
        return new RetryResult(campaignId, recipientId, "QUEUED");
    }

    private ActivationReadiness evaluate(UUID businessId,
                                         BookingIncidentCampaign campaign,
                                         List<BookingIncidentRecipient> items) {
        Set<Blocker> blockers = new LinkedHashSet<>();

        if (campaign.getStatus() != BookingIncidentCampaign.Status.PREPARED) {
            blockers.add(new Blocker("CAMPAIGN_NOT_PREPARED", "La campaña ya no está en estado preparado."));
        }
        if (campaign.getStrategy() == BookingIncidentCampaign.Strategy.CALL) {
            blockers.add(new Blocker("CALL_CAMPAIGNS_NOT_AVAILABLE",
                    "Las campañas automáticas por llamada todavía no están disponibles."));
        }

        var runtime = runtimeReadiness.snapshot();
        if (!runtime.whatsApp().ready()) {
            blockers.add(new Blocker("WHATSAPP_RUNTIME_NOT_READY",
                    "WhatsApp todavía no está listo en producción."));
        }
        if (!outboundProperties.isDeliveryEnabled()) {
            blockers.add(new Blocker("OUTBOUND_DELIVERY_DISABLED",
                    "La entrega real de mensajes está desactivada."));
        }
        if (!TwilioWhatsAppMessagingProvider.ID.equalsIgnoreCase(outboundProperties.getProvider())) {
            blockers.add(new Blocker("OUTBOUND_PROVIDER_NOT_AUTHORIZED",
                    "El proveedor de WhatsApp outbound no está autorizado."));
        }
        if (!jobProperties.isEnabled()) {
            blockers.add(new Blocker("OUTBOUND_WORKER_DISABLED",
                    "El worker de entrega outbound está desactivado."));
        }
        try {
            providers.require(outboundProperties.getProvider(), OutboundMessage.Channel.WHATSAPP);
        } catch (RuntimeException e) {
            blockers.add(new Blocker("OUTBOUND_PROVIDER_UNAVAILABLE",
                    "El proveedor de WhatsApp outbound no está disponible."));
        }

        var activeSenders = phones
                .findAllByBusinessIdAndActiveTrueAndWhatsappEnabledTrueOrderByCreatedAtDesc(businessId);
        int senderCount = activeSenders.size();
        if (senderCount == 0) {
            blockers.add(new Blocker("WHATSAPP_SENDER_MISSING",
                    "El negocio no tiene un remitente de WhatsApp activo."));
        } else if (senderCount > 1) {
            blockers.add(new Blocker("WHATSAPP_SENDER_AMBIGUOUS",
                    "Hay más de un remitente de WhatsApp activo para el negocio."));
        } else if (activeSenders.getFirst().getWhatsappCertifiedAt() == null) {
            blockers.add(new Blocker("WHATSAPP_SENDER_NOT_CERTIFIED",
                    "El remitente de WhatsApp aún no tiene un envío real exitoso certificado por Twilio."));
        }

        if (items.isEmpty()) {
            blockers.add(new Blocker("CAMPAIGN_HAS_NO_RECIPIENTS",
                    "La campaña no tiene clientes preparados."));
        }

        for (BookingIncidentRecipient recipient : items) {
            if (recipient.getStatus() != BookingIncidentRecipient.Status.PREPARED) {
                blockers.add(new Blocker("RECIPIENT_NOT_PREPARED",
                        "Hay destinatarios que ya no están en estado preparado."));
            }
            if (recipient.getChannelPreference() == BookingIncidentRecipient.ChannelPreference.CALL) {
                blockers.add(new Blocker("RECIPIENT_REQUIRES_CALL",
                        "Hay destinatarios configurados para llamada y ese canal masivo aún no está disponible."));
            }

            List<CustomerIdentity> verified = identities
                    .findAllByBusinessIdAndCustomerIdAndIdentityTypeAndVerificationStatusIn(
                            businessId,
                            recipient.getCustomerId(),
                            CustomerIdentity.Type.PHONE,
                            VERIFIED);
            if (verified.isEmpty()) {
                blockers.add(new Blocker("RECIPIENT_PHONE_NOT_VERIFIED",
                        "Al menos un cliente no tiene un teléfono verificado para WhatsApp."));
            } else if (verified.size() > 1) {
                blockers.add(new Blocker("RECIPIENT_PHONE_AMBIGUOUS",
                        "Al menos un cliente tiene más de un teléfono verificado y requiere selección manual."));
            }

            if (recipient.getBookingIds() == null || recipient.getBookingIds().isEmpty()) {
                blockers.add(new Blocker("RECIPIENT_BOOKING_MISSING",
                        "Hay un destinatario sin una reserva asociada."));
                continue;
            }
            for (String rawBookingId : recipient.getBookingIds()) {
                try {
                    UUID bookingId = UUID.fromString(rawBookingId);
                    Booking booking = bookings.findByIdAndBusinessId(bookingId, businessId).orElse(null);
                    if (booking == null
                            || !recipient.getCustomerId().equals(booking.getCustomerId())
                            || booking.getStatus() != BookingStatus.CONFIRMED) {
                        blockers.add(new Blocker("BOOKING_NO_LONGER_ELIGIBLE",
                                "Al menos una reserva ya no está confirmada o dejó de pertenecer al cliente."));
                    }
                } catch (IllegalArgumentException e) {
                    blockers.add(new Blocker("BOOKING_REFERENCE_INVALID",
                            "La campaña contiene una referencia de reserva inválida."));
                }
            }
        }

        return new ActivationReadiness(
                blockers.isEmpty(),
                "WHATSAPP",
                List.copyOf(blockers));
    }

    public record Blocker(String code, String message) { }

    public record ActivationReadiness(boolean ready,
                                      String channel,
                                      List<Blocker> blockers) { }

    public record ActivationResult(UUID campaignId,
                                   String status,
                                   int queuedRecipients) { }

    public record RetryResult(UUID campaignId,
                              UUID recipientId,
                              String status) { }
}

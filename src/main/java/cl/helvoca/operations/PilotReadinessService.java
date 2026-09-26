package cl.helvoca.operations;

import cl.helvoca.catalog.CatalogItem;
import cl.helvoca.catalog.CatalogItemRepository;
import cl.helvoca.catalog.CatalogMediaRepository;
import cl.helvoca.messaging.outbound.MessagingProviderRegistry;
import cl.helvoca.messaging.outbound.OutboundMessage;
import cl.helvoca.messaging.outbound.OutboundMessagingProperties;
import cl.helvoca.payment.PaymentProviderConfigService;
import cl.helvoca.security.TenantProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class PilotReadinessService {
    private final CommercialReadinessService commercialReadiness;
    private final ChannelRuntimeReadinessService channelRuntime;
    private final BusinessOperationCapabilityService capabilities;
    private final CatalogItemRepository catalog;
    private final CatalogMediaRepository media;
    private final PaymentProviderConfigService payments;
    private final OutboundMessagingProperties outbound;
    private final MessagingProviderRegistry messagingProviders;
    private final TenantProvider tenantProvider;

    public PilotReadinessService(CommercialReadinessService commercialReadiness,
                                 ChannelRuntimeReadinessService channelRuntime,
                                 BusinessOperationCapabilityService capabilities,
                                 CatalogItemRepository catalog,
                                 CatalogMediaRepository media,
                                 PaymentProviderConfigService payments,
                                 OutboundMessagingProperties outbound,
                                 MessagingProviderRegistry messagingProviders,
                                 TenantProvider tenantProvider) {
        this.commercialReadiness = commercialReadiness;
        this.channelRuntime = channelRuntime;
        this.capabilities = capabilities;
        this.catalog = catalog;
        this.media = media;
        this.payments = payments;
        this.outbound = outbound;
        this.messagingProviders = messagingProviders;
        this.tenantProvider = tenantProvider;
    }

    @Transactional(readOnly = true)
    public Readiness readiness() {
        UUID businessId = tenantProvider.requireBusinessId();

        CommercialReadinessService.Readiness commercial = commercialReadiness.readiness();
        ChannelRuntimeReadinessService.ChannelRuntimeReadiness channels = channelRuntime.snapshot();

        boolean voiceReady = commercial.ready() && channels.voice().ready();

        boolean outboundProviderReady = false;
        if (outbound.isDeliveryEnabled()) {
            try {
                messagingProviders.require(outbound.getProvider(), OutboundMessage.Channel.WHATSAPP);
                outboundProviderReady = true;
            } catch (RuntimeException ignored) {
                outboundProviderReady = false;
            }
        }
        boolean whatsappReady = channels.whatsApp().ready()
                && outbound.isDeliveryEnabled()
                && outboundProviderReady;

        List<CatalogItem> activeItems = catalog.findAllByBusinessIdAndActiveTrueOrderByNameAsc(businessId);
        long itemsWithMedia = activeItems.stream()
                .filter(item -> !media
                        .findAllByBusinessIdAndCatalogItemIdAndActiveTrueOrderBySortOrderAscCreatedAtAsc(
                                businessId, item.getId())
                        .isEmpty())
                .count();
        boolean catalogReady = !activeItems.isEmpty() && itemsWithMedia > 0;

        Set<BusinessOperationCapability> enabled = capabilities.enabled(businessId);
        boolean commercialCapabilitiesReady = enabled.containsAll(Set.of(
                BusinessOperationCapability.CATALOG,
                BusinessOperationCapability.ORDER,
                BusinessOperationCapability.PAYMENT));

        PaymentProviderConfigService.View payment = payments.current();
        boolean paymentReady = payment.enabled() && payment.credentialsConfigured();

        List<Check> checks = new ArrayList<>();
        checks.add(new Check(
                "VOICE",
                "Llamadas con IA",
                voiceReady,
                voiceReady
                        ? "Telefonía y proveedor de voz están operativos."
                        : "Completa la configuración base de telefonía y proveedor de voz."));
        checks.add(new Check(
                "WHATSAPP",
                "WhatsApp bidireccional",
                whatsappReady,
                whatsappReady
                        ? "Entrada, validación y salida real de WhatsApp están habilitadas."
                        : "WhatsApp debe poder recibir y también enviar mensajes reales."));
        checks.add(new Check(
                "CATALOG",
                "Catálogo multimedia",
                catalogReady,
                catalogReady
                        ? activeItems.size() + " ítems activos; " + itemsWithMedia + " con multimedia."
                        : "Agrega al menos un producto activo con imagen, video o documento."));
        checks.add(new Check(
                "COMMERCIAL_FLOW",
                "Venta conversacional",
                commercialCapabilitiesReady,
                commercialCapabilitiesReady
                        ? "Catálogo, pedidos y pagos están habilitados para la IA."
                        : "Habilita CATALOG, ORDER y PAYMENT para el agente."));
        checks.add(new Check(
                "PAYMENTS",
                "Mercado Pago",
                paymentReady,
                paymentReady
                        ? "Proveedor de pago habilitado con credenciales configuradas."
                        : "Configura y habilita Mercado Pago antes del piloto comercial."));

        long passed = checks.stream().filter(Check::ready).count();
        List<String> blockers = checks.stream()
                .filter(check -> !check.ready())
                .map(Check::label)
                .toList();

        return new Readiness(
                passed == checks.size(),
                passed,
                checks.size(),
                List.copyOf(checks),
                blockers,
                channels.voice().selectedProvider(),
                payment.provider(),
                payment.mode() == null ? null : payment.mode().name());
    }

    public record Check(String code, String label, boolean ready, String detail) {}

    public record Readiness(
            boolean ready,
            long passed,
            long total,
            List<Check> checks,
            List<String> blockers,
            String voiceProvider,
            String paymentProvider,
            String paymentMode) {}
}

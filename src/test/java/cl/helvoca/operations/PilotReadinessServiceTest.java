package cl.helvoca.operations;

import cl.helvoca.catalog.CatalogItem;
import cl.helvoca.catalog.CatalogItemRepository;
import cl.helvoca.catalog.CatalogMedia;
import cl.helvoca.catalog.CatalogMediaRepository;
import cl.helvoca.messaging.outbound.MessagingProvider;
import cl.helvoca.messaging.outbound.MessagingProviderRegistry;
import cl.helvoca.messaging.outbound.OutboundMessage;
import cl.helvoca.messaging.outbound.OutboundMessagingProperties;
import cl.helvoca.payment.PaymentProviderConfig;
import cl.helvoca.payment.PaymentProviderConfigService;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PilotReadinessServiceTest {

    @Test
    void readyOnlyWhenAllPilotGatesAreGreen() {
        UUID businessId = UUID.randomUUID();
        CommercialReadinessService commercial = mock(CommercialReadinessService.class);
        ChannelRuntimeReadinessService channels = mock(ChannelRuntimeReadinessService.class);
        BusinessOperationCapabilityService capabilities = mock(BusinessOperationCapabilityService.class);
        CatalogItemRepository catalog = mock(CatalogItemRepository.class);
        CatalogMediaRepository media = mock(CatalogMediaRepository.class);
        PaymentProviderConfigService payments = mock(PaymentProviderConfigService.class);
        OutboundMessagingProperties outbound = new OutboundMessagingProperties();
        MessagingProviderRegistry providers = mock(MessagingProviderRegistry.class);
        TenantProvider tenant = mock(TenantProvider.class);

        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(commercial.readiness()).thenReturn(new CommercialReadinessService.Readiness(
                true, 5, 5, List.of(), Map.of(), List.of()));
        when(channels.snapshot()).thenReturn(new ChannelRuntimeReadinessService.ChannelRuntimeReadiness(
                new ChannelRuntimeReadinessService.TwilioRuntimeReadiness(true, true, true, true, "READY"),
                new ChannelRuntimeReadinessService.VoiceRuntimeReadiness(true, "gemini", "READY", List.of()),
                new ChannelRuntimeReadinessService.WhatsAppRuntimeReadiness(true, true, true, "READY")));

        CatalogItem item = mock(CatalogItem.class);
        UUID itemId = UUID.randomUUID();
        when(item.getId()).thenReturn(itemId);
        when(catalog.findAllByBusinessIdAndActiveTrueOrderByNameAsc(businessId)).thenReturn(List.of(item));
        when(media.findAllByBusinessIdAndCatalogItemIdAndActiveTrueOrderBySortOrderAscCreatedAtAsc(
                businessId, itemId)).thenReturn(List.of(mock(CatalogMedia.class)));

        when(capabilities.enabled(businessId)).thenReturn(Set.of(
                BusinessOperationCapability.CATALOG,
                BusinessOperationCapability.ORDER,
                BusinessOperationCapability.PAYMENT));
        when(payments.current()).thenReturn(new PaymentProviderConfigService.View(
                "mercadopago", PaymentProviderConfig.Mode.SANDBOX, true, true, "/webhook"));

        outbound.setDeliveryEnabled(true);
        outbound.setProvider("meta");
        when(providers.require("meta", OutboundMessage.Channel.WHATSAPP))
                .thenReturn(mock(MessagingProvider.class));

        PilotReadinessService service = new PilotReadinessService(
                commercial, channels, capabilities, catalog, media, payments,
                outbound, providers, tenant);

        PilotReadinessService.Readiness result = service.readiness();

        assertTrue(result.ready());
        assertEquals(5, result.passed());
        assertEquals(5, result.total());
        assertTrue(result.blockers().isEmpty());
        assertEquals("gemini", result.voiceProvider());
        assertEquals("mercadopago", result.paymentProvider());
        assertEquals("SANDBOX", result.paymentMode());
    }

    @Test
    void outboundWhatsappAndPaymentsRemainVisibleBlockers() {
        UUID businessId = UUID.randomUUID();
        CommercialReadinessService commercial = mock(CommercialReadinessService.class);
        ChannelRuntimeReadinessService channels = mock(ChannelRuntimeReadinessService.class);
        BusinessOperationCapabilityService capabilities = mock(BusinessOperationCapabilityService.class);
        CatalogItemRepository catalog = mock(CatalogItemRepository.class);
        CatalogMediaRepository media = mock(CatalogMediaRepository.class);
        PaymentProviderConfigService payments = mock(PaymentProviderConfigService.class);
        OutboundMessagingProperties outbound = new OutboundMessagingProperties();
        MessagingProviderRegistry providers = mock(MessagingProviderRegistry.class);
        TenantProvider tenant = mock(TenantProvider.class);

        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(commercial.readiness()).thenReturn(new CommercialReadinessService.Readiness(
                true, 5, 5, List.of(), Map.of(), List.of()));
        when(channels.snapshot()).thenReturn(new ChannelRuntimeReadinessService.ChannelRuntimeReadiness(
                new ChannelRuntimeReadinessService.TwilioRuntimeReadiness(true, true, true, true, "READY"),
                new ChannelRuntimeReadinessService.VoiceRuntimeReadiness(true, "gemini", "READY", List.of()),
                new ChannelRuntimeReadinessService.WhatsAppRuntimeReadiness(true, true, true, "READY")));

        when(catalog.findAllByBusinessIdAndActiveTrueOrderByNameAsc(businessId)).thenReturn(List.of());
        when(capabilities.enabled(businessId)).thenReturn(Set.of(BusinessOperationCapability.CATALOG));
        when(payments.current()).thenReturn(new PaymentProviderConfigService.View(
                "mercadopago", PaymentProviderConfig.Mode.SANDBOX, false, false, "/webhook"));

        outbound.setDeliveryEnabled(false);
        outbound.setProvider("NONE");

        PilotReadinessService service = new PilotReadinessService(
                commercial, channels, capabilities, catalog, media, payments,
                outbound, providers, tenant);

        PilotReadinessService.Readiness result = service.readiness();

        assertFalse(result.ready());
        assertEquals(1, result.passed());
        assertEquals(5, result.total());
        assertTrue(result.blockers().contains("WhatsApp bidireccional"));
        assertTrue(result.blockers().contains("Catálogo multimedia"));
        assertTrue(result.blockers().contains("Venta conversacional"));
        assertTrue(result.blockers().contains("Mercado Pago"));
        verifyNoInteractions(providers);
    }
}

package cl.helvoca.messaging.outbound;

import cl.helvoca.messaging.meta.MetaWhatsAppAccessTokenResolver;
import cl.helvoca.messaging.meta.MetaWhatsAppCloudClient;
import cl.helvoca.messaging.meta.MetaWhatsAppProperties;
import cl.helvoca.phone.PhoneNumber;
import cl.helvoca.phone.PhoneNumberRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MetaWhatsAppMessagingProviderTest {

    @Test
    void exposesStableProviderIdAndOnlySupportsWhatsApp() {
        var provider = provider(new MetaWhatsAppProperties(), mock(PhoneNumberRepository.class),
                mock(MetaWhatsAppCloudClient.class), Optional.empty());

        assertEquals("META_WHATSAPP_CLOUD", provider.id());
        assertTrue(provider.supports(OutboundMessage.Channel.WHATSAPP));
        assertFalse(provider.supports(null));
    }

    @Test
    void sendFailsClosedWhileMetaIsDisabled() {
        var properties = new MetaWhatsAppProperties();
        properties.setEnabled(false);
        var phones = mock(PhoneNumberRepository.class);
        var client = mock(MetaWhatsAppCloudClient.class);
        var provider = provider(properties, phones, client, Optional.empty());

        var error = assertThrows(
                IllegalStateException.class,
                () -> provider.send(command(UUID.randomUUID(), OutboundMessage.Channel.WHATSAPP)));

        assertEquals("Meta WhatsApp integration is disabled", error.getMessage());
        verifyNoInteractions(phones, client);
    }

    @Test
    void enabledProviderFailsClosedWithoutTenantCredentialResolver() {
        UUID businessId = UUID.randomUUID();
        var properties = enabledProperties();
        var phones = mock(PhoneNumberRepository.class);
        var client = mock(MetaWhatsAppCloudClient.class);
        when(phones.findAllByBusinessIdAndActiveTrueAndWhatsappEnabledTrueOrderByCreatedAtDesc(businessId))
                .thenReturn(List.of(metaSender(businessId)));

        var provider = provider(properties, phones, client, Optional.empty());

        var error = assertThrows(
                IllegalStateException.class,
                () -> provider.send(command(businessId, OutboundMessage.Channel.WHATSAPP)));

        assertEquals("Meta WhatsApp access token is not configured for tenant", error.getMessage());
        verifyNoInteractions(client);
    }

    @Test
    void sendsThroughCloudClientWhenTenantSenderAndCredentialAreResolved() {
        UUID businessId = UUID.randomUUID();
        var properties = enabledProperties();
        var phones = mock(PhoneNumberRepository.class);
        var client = mock(MetaWhatsAppCloudClient.class);
        PhoneNumber sender = metaSender(businessId);

        when(phones.findAllByBusinessIdAndActiveTrueAndWhatsappEnabledTrueOrderByCreatedAtDesc(businessId))
                .thenReturn(List.of(sender));
        when(client.sendText("123456789012345", "tenant-token", "+56911111111", "Hola"))
                .thenReturn("wamid.TEST-OUT");
        MetaWhatsAppAccessTokenResolver resolver = id ->
                id.equals(businessId) ? Optional.of("tenant-token") : Optional.empty();

        var result = provider(properties, phones, client, Optional.of(resolver))
                .send(command(businessId, OutboundMessage.Channel.WHATSAPP));

        assertEquals("wamid.TEST-OUT", result.providerMessageId());
        verify(client).sendText("123456789012345", "tenant-token", "+56911111111", "Hola");
    }

    @Test
    void sendsCatalogImageThroughMetaMediaEndpoint() {
        UUID businessId = UUID.randomUUID();
        var properties = enabledProperties();
        var phones = mock(PhoneNumberRepository.class);
        var client = mock(MetaWhatsAppCloudClient.class);
        PhoneNumber sender = metaSender(businessId);

        when(phones.findAllByBusinessIdAndActiveTrueAndWhatsappEnabledTrueOrderByCreatedAtDesc(businessId))
                .thenReturn(List.of(sender));
        when(client.sendMediaLink(
                "123456789012345",
                "tenant-token",
                "+56911111111",
                "image",
                "https://cdn.example.test/product.jpg",
                "Producto destacado"))
                .thenReturn("wamid.MEDIA-OUT");

        MetaWhatsAppAccessTokenResolver resolver = id ->
                id.equals(businessId) ? Optional.of("tenant-token") : Optional.empty();

        MessagingProvider.SendCommand command = new MessagingProvider.SendCommand(
                businessId,
                UUID.randomUUID(),
                OutboundMessage.Channel.WHATSAPP,
                "+56911111111",
                "Producto destacado",
                "idem-media",
                OutboundMessage.ContentType.IMAGE,
                "https://cdn.example.test/product.jpg",
                "image/jpeg",
                "Producto destacado");

        var result = provider(properties, phones, client, Optional.of(resolver)).send(command);

        assertEquals("wamid.MEDIA-OUT", result.providerMessageId());
        verify(client).sendMediaLink(
                "123456789012345",
                "tenant-token",
                "+56911111111",
                "image",
                "https://cdn.example.test/product.jpg",
                "Producto destacado");
        verify(client, never()).sendText(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void ignoresNonMetaWhatsappSendersForTenant() {
        UUID businessId = UUID.randomUUID();
        var properties = enabledProperties();
        var phones = mock(PhoneNumberRepository.class);
        var client = mock(MetaWhatsAppCloudClient.class);
        PhoneNumber sender = metaSender(businessId);
        sender.setWhatsappProvider("TWILIO_WHATSAPP");

        when(phones.findAllByBusinessIdAndActiveTrueAndWhatsappEnabledTrueOrderByCreatedAtDesc(businessId))
                .thenReturn(List.of(sender));

        var provider = provider(properties, phones, client, Optional.of(id -> Optional.of("tenant-token")));
        var error = assertThrows(
                IllegalStateException.class,
                () -> provider.send(command(businessId, OutboundMessage.Channel.WHATSAPP)));

        assertEquals("Tenant has no Meta WhatsApp sender", error.getMessage());
        verifyNoInteractions(client);
    }

    @Test
    void rejectsUnsupportedChannelBeforeRouting() {
        var properties = enabledProperties();
        var phones = mock(PhoneNumberRepository.class);
        var client = mock(MetaWhatsAppCloudClient.class);
        var provider = provider(properties, phones, client, Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> provider.send(command(UUID.randomUUID(), null)));

        verifyNoInteractions(phones, client);
    }

    private static MetaWhatsAppMessagingProvider provider(
            MetaWhatsAppProperties properties,
            PhoneNumberRepository phones,
            MetaWhatsAppCloudClient client,
            Optional<MetaWhatsAppAccessTokenResolver> resolver) {
        return new MetaWhatsAppMessagingProvider(properties, phones, client, resolver);
    }

    private static MetaWhatsAppProperties enabledProperties() {
        var properties = new MetaWhatsAppProperties();
        properties.setEnabled(true);
        return properties;
    }

    private static PhoneNumber metaSender(UUID businessId) {
        PhoneNumber sender = new PhoneNumber();
        sender.setBusinessId(businessId);
        sender.setPhoneNumber("+56922222222");
        sender.setWhatsappEnabled(true);
        sender.setWhatsappProvider("META_WHATSAPP_CLOUD");
        sender.setWhatsappExternalId("123456789012345");
        return sender;
    }

    private static MessagingProvider.SendCommand command(UUID businessId, OutboundMessage.Channel channel) {
        return new MessagingProvider.SendCommand(
                businessId,
                UUID.randomUUID(),
                channel,
                "+56911111111",
                "Hola",
                "idem-test");
    }
}

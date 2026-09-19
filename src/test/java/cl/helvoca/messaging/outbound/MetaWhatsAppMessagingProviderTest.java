package cl.helvoca.messaging.outbound;

import cl.helvoca.messaging.meta.MetaWhatsAppProperties;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MetaWhatsAppMessagingProviderTest {

    @Test
    void exposesStableProviderIdAndOnlySupportsWhatsApp() {
        var provider = new MetaWhatsAppMessagingProvider(new MetaWhatsAppProperties());

        assertEquals("META_WHATSAPP_CLOUD", provider.id());
        assertTrue(provider.supports(OutboundMessage.Channel.WHATSAPP));
        assertFalse(provider.supports(OutboundMessage.Channel.SMS));
    }

    @Test
    void sendFailsClosedWhileMetaIsDisabled() {
        var properties = new MetaWhatsAppProperties();
        properties.setEnabled(false);
        var provider = new MetaWhatsAppMessagingProvider(properties);

        var error = assertThrows(
                IllegalStateException.class,
                () -> provider.send(command(OutboundMessage.Channel.WHATSAPP)));

        assertEquals("Meta WhatsApp integration is disabled", error.getMessage());
    }

    @Test
    void enabledProviderStillCannotSendUntilTransportIsImplemented() {
        var properties = new MetaWhatsAppProperties();
        properties.setEnabled(true);
        var provider = new MetaWhatsAppMessagingProvider(properties);

        var error = assertThrows(
                IllegalStateException.class,
                () -> provider.send(command(OutboundMessage.Channel.WHATSAPP)));

        assertEquals("Meta WhatsApp outbound transport is not implemented yet", error.getMessage());
    }

    @Test
    void rejectsUnsupportedChannelBeforeTransport() {
        var properties = new MetaWhatsAppProperties();
        properties.setEnabled(true);
        var provider = new MetaWhatsAppMessagingProvider(properties);

        assertThrows(
                IllegalArgumentException.class,
                () -> provider.send(command(OutboundMessage.Channel.SMS)));
    }

    private static MessagingProvider.SendCommand command(OutboundMessage.Channel channel) {
        return new MessagingProvider.SendCommand(
                UUID.randomUUID(),
                UUID.randomUUID(),
                channel,
                "+56911111111",
                "Hola",
                "idem-test");
    }
}

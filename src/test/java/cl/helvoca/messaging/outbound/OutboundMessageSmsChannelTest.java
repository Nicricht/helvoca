package cl.helvoca.messaging.outbound;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OutboundMessageSmsChannelTest {
    @Test
    void supportsSmsAsAnOutboundChannel() {
        assertEquals("SMS", OutboundMessage.Channel.valueOf("SMS").name());
    }
}

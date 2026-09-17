package cl.helvoca.messaging;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MessagingMessageTimestampTest {

    @Test
    void prePersistAssignsServerTimestampWhenMessageHasNoProviderTimestamp() {
        MessagingMessage message = new MessagingMessage();
        Instant before = Instant.now();

        message.prePersist();

        assertNotNull(message.getCreatedAt());
        assertFalse(message.getCreatedAt().isBefore(before));
    }
}

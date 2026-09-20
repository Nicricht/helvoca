package cl.helvoca.billing;

import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.messaging.MessagingConversation;
import cl.helvoca.messaging.MessagingConversationRepository;
import cl.helvoca.messaging.MessagingMessage;
import cl.helvoca.messaging.MessagingMessageRepository;
import cl.helvoca.messaging.outbound.MetaWhatsAppMessagingProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers
@SpringBootTest
@Transactional
class MetaWhatsAppUsageMeteringIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("app.seed.enabled", () -> "false");
    }

    @Autowired BusinessRepository businesses;
    @Autowired MessagingConversationRepository conversations;
    @Autowired MessagingMessageRepository messages;
    @Autowired JdbcTemplate jdbc;

    @Test
    void sentMetaAssistantReplyIsMeteredExactlyOnceAcrossDeliveryUpdates() {
        Business business = new Business();
        business.setName("Meta usage tenant");
        business = businesses.saveAndFlush(business);

        MessagingConversation conversation = new MessagingConversation();
        conversation.setBusinessId(business.getId());
        conversation.setChannel("whatsapp");
        conversation.setSender("+56911111111");
        conversation.setRecipient("+56922222222");
        conversation = conversations.saveAndFlush(conversation);

        MessagingMessage message = new MessagingMessage();
        message.setConversationId(conversation.getId());
        message.setExternalMessageId("wamid.inbound-metering");
        message.setDirection("INBOUND");
        message.setRole("USER");
        message.setContent("Hola");
        message.setReplyText("Respuesta IA");
        message = messages.saveAndFlush(message);

        Instant sentAt = Instant.parse("2026-09-20T03:00:00Z");
        message.setProvider(MetaWhatsAppMessagingProvider.ID);
        message.setProviderMessageId("wamid.outbound-metering");
        message.setProviderDeliveryStatus("SENT");
        message.setSentAt(sentAt);
        message.setDeliveryUpdatedAt(sentAt);
        messages.saveAndFlush(message);

        Map<String, Object> usage = jdbc.queryForMap("""
                SELECT meter_key, quantity, unit, provider, occurred_at
                  FROM usage_meter_event
                 WHERE business_id = ?
                   AND source_type = 'MESSAGING_MESSAGE'
                   AND source_id = ?
                """, business.getId(), message.getId().toString());

        assertEquals("OUTBOUND_MESSAGES", usage.get("meter_key"));
        assertEquals(0, new BigDecimal("1").compareTo((BigDecimal) usage.get("quantity")));
        assertEquals("COUNT", usage.get("unit"));
        assertEquals(MetaWhatsAppMessagingProvider.ID, usage.get("provider"));

        message.setProviderDeliveryStatus("DELIVERED");
        message.setDeliveredAt(sentAt.plusSeconds(2));
        message.setDeliveryUpdatedAt(sentAt.plusSeconds(2));
        messages.saveAndFlush(message);

        Long count = jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM usage_meter_event
                 WHERE business_id = ?
                   AND source_type = 'MESSAGING_MESSAGE'
                   AND source_id = ?
                """, Long.class, business.getId(), message.getId().toString());

        assertEquals(1L, count);
    }

    @Test
    void nonMetaConversationMessageIsNotMeteredByMetaTrigger() {
        Business business = new Business();
        business.setName("Legacy provider tenant");
        business = businesses.saveAndFlush(business);

        MessagingConversation conversation = new MessagingConversation();
        conversation.setBusinessId(business.getId());
        conversation.setChannel("whatsapp");
        conversation.setSender("+56933333333");
        conversation.setRecipient("+56944444444");
        conversation = conversations.saveAndFlush(conversation);

        MessagingMessage message = new MessagingMessage();
        message.setConversationId(conversation.getId());
        message.setExternalMessageId("SM-inbound-metering");
        message.setDirection("INBOUND");
        message.setRole("USER");
        message.setContent("Hola");
        message.setReplyText("Respuesta");
        message.setProvider("TWILIO_WHATSAPP");
        message.setProviderMessageId("SM-outbound-metering");
        message.setProviderDeliveryStatus("SENT");
        message.setSentAt(Instant.now());
        messages.saveAndFlush(message);

        Long count = jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM usage_meter_event
                 WHERE business_id = ?
                   AND source_type = 'MESSAGING_MESSAGE'
                   AND source_id = ?
                """, Long.class, business.getId(), message.getId().toString());

        assertEquals(0L, count);
    }
}

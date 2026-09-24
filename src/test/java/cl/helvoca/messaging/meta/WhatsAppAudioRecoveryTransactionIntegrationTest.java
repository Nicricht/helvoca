package cl.helvoca.messaging.meta;

import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.messaging.MessagingMessage;
import cl.helvoca.messaging.MessagingMessageRepository;
import cl.helvoca.messaging.outbound.MetaWhatsAppMessagingProvider;
import cl.helvoca.phone.PhoneNumber;
import cl.helvoca.phone.PhoneNumberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest
class WhatsAppAudioRecoveryTransactionIntegrationTest {
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

    @Autowired WhatsAppAudioRecoveryService recovery;
    @Autowired BusinessRepository businesses;
    @Autowired PhoneNumberRepository phones;
    @Autowired MessagingMessageRepository messages;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbc;

    @Test
    void recoverySurvivesRollbackOfCallerTransactionExactlyOnce() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        UUID[] ids = tx.execute(status -> {
            Business business = new Business();
            business.setName("Audio recovery tenant");
            business = businesses.saveAndFlush(business);

            PhoneNumber phone = new PhoneNumber();
            phone.setBusinessId(business.getId());
            phone.setPhoneNumber("+56920001111");
            phone.setActive(true);
            phone.setWhatsappEnabled(true);
            phone.setWhatsappProvider(MetaWhatsAppMessagingProvider.ID);
            phone = phones.saveAndFlush(phone);
            return new UUID[]{business.getId(), phone.getId()};
        });
        assertNotNull(ids);
        UUID businessId = ids[0];
        UUID phoneNumberId = ids[1];
        String wamid = "wamid.AUDIO-ROLLBACK";

        RuntimeException rolledBack = assertThrows(RuntimeException.class, () ->
                tx.executeWithoutResult(status -> {
                    recovery.recover(businessId, phoneNumberId, wamid, "+56911112222");
                    throw new RuntimeException("force caller rollback");
                }));
        assertEquals("force caller rollback", rolledBack.getMessage());

        tx.executeWithoutResult(status -> {
            MessagingMessage inbound = messages.findByExternalMessageId(wamid).orElseThrow();
            assertEquals("INBOUND", inbound.getDirection());
            assertEquals(WhatsAppAudioRecoveryService.SOURCE_CONTENT, inbound.getContent());
            assertEquals(WhatsAppAudioRecoveryService.FAILURE_CODE, inbound.getFailureCode());
            assertEquals(WhatsAppAudioRecoveryService.RECOVERY_REPLY, inbound.getReplyText());

            List<String> keys = jdbc.queryForList("""
                    SELECT idempotency_key
                      FROM persistent_job
                     WHERE business_id = ?
                       AND job_type = 'META_WHATSAPP_RECOVERY_REPLY'
                    """, String.class, businessId);
            assertEquals(List.of("wa-audio-recovery:wamid.AUDIO-ROLLBACK"), keys);
        });

        recovery.recover(businessId, phoneNumberId, wamid, "+56911112222");

        Long messageCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM messaging_message WHERE external_message_id = ?",
                Long.class,
                wamid);
        Long jobCount = jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM persistent_job
                 WHERE business_id = ?
                   AND idempotency_key = ?
                """, Long.class, businessId, "wa-audio-recovery:wamid.AUDIO-ROLLBACK");
        assertEquals(1L, messageCount);
        assertEquals(1L, jobCount);
    }
}

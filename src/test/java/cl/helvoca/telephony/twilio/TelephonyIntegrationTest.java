package cl.helvoca.telephony.twilio;

import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.call.CallSessionRepository;
import cl.helvoca.call.CallStatus;
import cl.helvoca.phone.PhoneNumber;
import cl.helvoca.phone.PhoneNumberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest
class TelephonyIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("app.seed.enabled", () -> "false");
        registry.add("app.twilio.media-stream-url", () -> "wss://voice.example/ws/twilio");
    }

    @Autowired BusinessRepository businesses;
    @Autowired PhoneNumberRepository phoneNumbers;
    @Autowired CallSessionRepository calls;
    @Autowired TwilioCallService callService;

    @Test
    void flywayV4AndInboundCallLifecycleWorkAgainstPostgres() {
        Business business = new Business();
        business.setName("Telephony Test Business");
        business = businesses.saveAndFlush(business);

        PhoneNumber phone = new PhoneNumber();
        phone.setBusinessId(business.getId());
        phone.setProvider("TWILIO");
        phone.setPhoneNumber("+56220000000");
        phone = phoneNumbers.saveAndFlush(phone);

        String xml = callService.startInboundCall("CA-TEST-001", "+56911111111", phone.getPhoneNumber());
        assertTrue(xml.contains("wss://voice.example/ws/twilio"));
        assertTrue(xml.contains("callId"));

        var call = calls.findByProviderCallId("CA-TEST-001").orElseThrow();
        assertEquals(business.getId(), call.getBusinessId());
        assertEquals(CallStatus.RINGING, call.getStatus());

        callService.markStreamStarted(call.getId(), "CA-TEST-001", "MZ-TEST-001");
        call = calls.findByProviderCallId("CA-TEST-001").orElseThrow();
        assertEquals(CallStatus.IN_PROGRESS, call.getStatus());
        assertEquals("MZ-TEST-001", call.getStreamSid());

        callService.updateStatus("CA-TEST-001", "completed", 42);
        call = calls.findByProviderCallId("CA-TEST-001").orElseThrow();
        assertEquals(CallStatus.COMPLETED, call.getStatus());
        assertEquals(42, call.getDurationSeconds());
        assertTrue(call.getEndedAt() != null);
    }
}

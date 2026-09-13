package cl.helvoca.telephony.twilio;

import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.call.CallSessionRepository;
import cl.helvoca.call.CallStatus;
import cl.helvoca.phone.PhoneNumber;
import cl.helvoca.phone.PhoneNumberRepository;
import cl.helvoca.telephony.CallLifecycleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
        registry.add("app.voice.telephony-provider", () -> "twilio");
        registry.add("app.voice.ai-provider", () -> "openai");
    }

    @Autowired BusinessRepository businesses;
    @Autowired PhoneNumberRepository phoneNumbers;
    @Autowired CallSessionRepository calls;
    @Autowired CallLifecycleService lifecycle;
    @Autowired TwilioCallService twilio;

    @Test
    void providerCallSidAndLiveSessionStayCorrelatedAgainstPostgres() {
        Business business = new Business();
        business.setName("Telephony Test Business");
        business = businesses.saveAndFlush(business);

        PhoneNumber phone = new PhoneNumber();
        phone.setBusinessId(business.getId());
        phone.setProvider("TWILIO");
        phone.setPhoneNumber("+56220000000");
        phone = phoneNumbers.saveAndFlush(phone);

        String callSid = "CA0123456789abcdef0123456789abcdef";
        String liveSession = "live_test_001";
        var callId = lifecycle.startInboundCall("twilio", callSid, "+56911111111", phone.getPhoneNumber());

        var call = calls.findByProviderCallId(callSid).orElseThrow();
        assertEquals(callId, call.getId());
        assertEquals(business.getId(), call.getBusinessId());
        assertEquals(CallStatus.RINGING, call.getStatus());
        assertEquals("twilio", call.getTelephonyProvider());

        var context = lifecycle.markStreamStarted(callId, callSid, "live:" + liveSession, "openai-live");
        assertEquals(callId, context.callId());
        assertEquals("live:" + liveSession, context.streamSid());

        call = calls.findByProviderCallId(callSid).orElseThrow();
        assertEquals(CallStatus.IN_PROGRESS, call.getStatus());
        assertEquals("live:" + liveSession, call.getStreamSid());
        assertEquals("openai-live", call.getAiProvider());
        assertNotNull(call.getAnsweredAt());

        twilio.updateStatus(callSid, "completed", 42);
        call = calls.findByProviderCallId(callSid).orElseThrow();
        assertEquals(CallStatus.COMPLETED, call.getStatus());
        assertEquals(42, call.getDurationSeconds());
        assertNotNull(call.getEndedAt());
    }
}

package cl.helvoca.telephony.twilio;

import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.call.CallDirection;
import cl.helvoca.call.CallSession;
import cl.helvoca.call.CallSessionRepository;
import cl.helvoca.call.CallStatus;
import cl.helvoca.phone.PhoneNumber;
import cl.helvoca.phone.PhoneNumberRepository;
import cl.helvoca.testsupport.ExplicitSystemDatabaseScopeSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real PostgreSQL + Flyway V100 + forced RLS, two tenants, no carrier activity.
 */
@Testcontainers
@SpringBootTest
class TwilioTenantBindingIntegrationTest extends ExplicitSystemDatabaseScopeSupport {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("app.seed.enabled", () -> "false");
    }

    @Autowired BusinessRepository businesses;
    @Autowired PhoneNumberRepository phones;
    @Autowired CallSessionRepository calls;
    @Autowired TwilioCallBindingGuard binding;

    @Test
    void oneTenantsCarrierCannotReuseAnotherTenantsCallAndRlsStillIsolatesBoth() {
        String accountA = "ACaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        String accountB = "ACbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
        String callA = "CAaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        String callB = "CAbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
        String callerA = "+56911111111";
        String callerB = "+56922222222";
        String numberA = "+14355551101";
        String numberB = "+14355551102";
        UUID tenantA = business("Inbound Tenant A");
        UUID tenantB = business("Inbound Tenant B");
        PhoneNumber phoneA = phone(tenantA, numberA, accountA, "PNaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        PhoneNumber phoneB = phone(tenantB, numberB, accountB, "PNbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
        session(tenantA, phoneA, callA, callerA);
        session(tenantB, phoneB, callB, callerB);

        assertTrue(binding.allowsIncoming(callA, numberA, callerA, accountA));
        assertTrue(binding.allowsIncoming(callB, numberB, callerB, accountB));
        assertTrue(binding.allowsMedia(callA, numberA, callerA, accountA));
        assertTrue(binding.allowsMedia(callB, numberB, callerB, accountB));
        assertTrue(binding.allowsCallback(callA, accountA));
        assertTrue(binding.allowsCallback(callB, accountB));

        assertFalse(binding.allowsIncoming(callA, numberB, callerA, accountB));
        assertFalse(binding.allowsIncoming(callB, numberA, callerB, accountA));
        assertFalse(binding.allowsIncoming(callA, numberA, callerB, accountA));
        assertFalse(binding.allowsIncoming(callB, numberB, callerA, accountB));
        assertFalse(binding.allowsIncoming(callA, numberA, callerA, accountB));
        assertFalse(binding.allowsMedia(callA, numberB, callerA, accountB));
        assertFalse(binding.allowsMedia(callB, numberA, callerB, accountA));
        assertFalse(binding.allowsCallback(callA, accountB));
        assertFalse(binding.allowsCallback(callB, accountA));

        assertTrue(databaseContext.callAsTenant(tenantA, () -> phones.findById(phoneA.getId()).isPresent()));
        assertFalse(databaseContext.callAsTenant(tenantA, () -> phones.findById(phoneB.getId()).isPresent()));
        assertTrue(databaseContext.callAsTenant(tenantB, () -> calls.findByProviderCallId(callB).isPresent()));
        assertFalse(databaseContext.callAsTenant(tenantB, () -> calls.findByProviderCallId(callA).isPresent()));
    }

    @Test
    void certificationEvidenceIsScopedToEachTenantUnderRealPostgresRls() {
        UUID tenantA = business("Certification Evidence A");
        UUID tenantB = business("Certification Evidence B");
        PhoneNumber phoneA = phone(tenantA, "+14355551231",
                "ACcccccccccccccccccccccccccccccccc",
                "PNcccccccccccccccccccccccccccccccc");
        PhoneNumber phoneB = phone(tenantB, "+14355551232",
                "ACdddddddddddddddddddddddddddddddd",
                "PNdddddddddddddddddddddddddddddddd");
        String callSidA = "CAcccccccccccccccccccccccccccccccc";
        String callSidB = "CAdddddddddddddddddddddddddddddddd";
        session(tenantA, phoneA, callSidA, "+56911111111");
        session(tenantB, phoneB, callSidB, "+56922222222");
        CallSession a = calls.findByProviderCallId(callSidA).orElseThrow();
        a.setCertification(true);
        calls.saveAndFlush(a);
        CallSession b = calls.findByProviderCallId(callSidB).orElseThrow();
        b.setCertification(true);
        calls.saveAndFlush(b);

        assertEquals(a.getId(), databaseContext.callAsTenant(tenantA, () ->
                calls.findFirstByBusinessIdAndCertificationTrueOrderByStartedAtDesc(tenantA)
                        .orElseThrow().getId()));
        assertEquals(b.getId(), databaseContext.callAsTenant(tenantB, () ->
                calls.findFirstByBusinessIdAndCertificationTrueOrderByStartedAtDesc(tenantB)
                        .orElseThrow().getId()));
        assertTrue(databaseContext.callAsTenant(tenantA, () ->
                calls.findFirstByBusinessIdAndCertificationTrueOrderByStartedAtDesc(tenantB).isEmpty()));
        assertTrue(databaseContext.callAsTenant(tenantB, () ->
                calls.findFirstByBusinessIdAndCertificationTrueOrderByStartedAtDesc(tenantA).isEmpty()));
        assertTrue(databaseContext.callAsTenant(tenantA, () -> calls.findById(b.getId()).isEmpty()));
        assertTrue(databaseContext.callAsTenant(tenantB, () -> calls.findById(a.getId()).isEmpty()));
    }

    private UUID business(String name) {
        Business business = new Business();
        business.setName(name);
        business.setLanguage("es");
        business.setTimezone("America/Santiago");
        return businesses.saveAndFlush(business).getId();
    }

    private PhoneNumber phone(UUID tenant, String e164, String account, String pn) {
        PhoneNumber phone = new PhoneNumber();
        phone.setBusinessId(tenant);
        phone.setPhoneNumber(e164);
        phone.setProvider("TWILIO");
        phone.setActive(true);
        phone.setExternalId(pn);
        phone.setTwilioAccountSid(account);
        phone.setTwilioBindingSource("MANAGED_PURCHASE");
        phone.setVoiceVerifiedAt(Instant.now());
        return phones.saveAndFlush(phone);
    }

    private void session(UUID tenant, PhoneNumber phone, String callSid, String caller) {
        CallSession call = new CallSession();
        call.setBusinessId(tenant);
        call.setPhoneNumberId(phone.getId());
        call.setTelephonyProvider("twilio");
        call.setProviderCallId(callSid);
        call.setCallerNumber(caller);
        call.setDestinationNumber(phone.getPhoneNumber());
        call.setDirection(CallDirection.INBOUND);
        call.setStatus(CallStatus.RINGING);
        call.setStartedAt(Instant.now());
        calls.saveAndFlush(call);
    }
}

package cl.helvoca.telephony;

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

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest
class CallCapacityIntegrationTest {

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
        registry.add("app.commercial.calls.max-concurrent-per-business", () -> "1");
    }

    @Autowired CallLifecycleService lifecycle;
    @Autowired BusinessRepository businesses;
    @Autowired PhoneNumberRepository phoneNumbers;
    @Autowired CallSessionRepository calls;

    @Test
    void simultaneousCallsAreSerializedPerTenantWithoutBlockingAnotherTenant() throws Exception {
        TenantFixture tenantA = tenant("Capacity Tenant A", "+14355550101");
        TenantFixture tenantB = tenant("Capacity Tenant B", "+14355550102");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch fire = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> attempt(
                    "CA-capacity-a1", tenantA.phone(), ready, fire));
            Future<Boolean> second = executor.submit(() -> attempt(
                    "CA-capacity-a2", tenantA.phone(), ready, fire));

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            fire.countDown();

            int admitted = (first.get(15, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(15, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, admitted);
            assertEquals(1L, calls.countByBusinessIdAndStatusIn(
                    tenantA.businessId(), List.of(CallStatus.RINGING, CallStatus.IN_PROGRESS, CallStatus.QUEUED)));

            UUID tenantBCall = lifecycle.startInboundCall(
                    "twilio", "CA-capacity-b1", "+56910000002", tenantB.phone());
            assertNotNull(tenantBCall);
            assertEquals(1L, calls.countByBusinessIdAndStatusIn(
                    tenantB.businessId(), List.of(CallStatus.RINGING, CallStatus.IN_PROGRESS, CallStatus.QUEUED)));
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean attempt(String callSid,
                            String destination,
                            CountDownLatch ready,
                            CountDownLatch fire) throws Exception {
        ready.countDown();
        fire.await(5, TimeUnit.SECONDS);
        try {
            lifecycle.startInboundCall("twilio", callSid, "+56910000001", destination);
            return true;
        } catch (CallCapacityExceededException expected) {
            return false;
        }
    }

    private TenantFixture tenant(String name, String phoneValue) {
        Business business = new Business();
        business.setName(name);
        business.setTimezone("America/Santiago");
        business.setLanguage("es");
        business = businesses.saveAndFlush(business);

        PhoneNumber phone = new PhoneNumber();
        phone.setBusinessId(business.getId());
        phone.setProvider("TWILIO");
        phone.setPhoneNumber(phoneValue);
        phone.setActive(true);
        phoneNumbers.saveAndFlush(phone);
        return new TenantFixture(business.getId(), phoneValue);
    }

    private record TenantFixture(UUID businessId, String phone) {}
}

package cl.helvoca.telephony;

import cl.helvoca.billing.BusinessSubscription;
import cl.helvoca.billing.BusinessSubscriptionRepository;
import cl.helvoca.billing.PlanCode;
import cl.helvoca.billing.SubscriptionStatus;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.call.CallSessionRepository;
import cl.helvoca.call.CallStatus;
import cl.helvoca.phone.PhoneNumber;
import cl.helvoca.phone.PhoneNumberRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest
class CallLoadIntegrationTest {

    private static final List<CallStatus> ACTIVE_STATUSES =
            List.of(CallStatus.QUEUED, CallStatus.RINGING, CallStatus.IN_PROGRESS);

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

    @Autowired CallLifecycleService lifecycle;
    @Autowired BusinessRepository businesses;
    @Autowired PhoneNumberRepository phoneNumbers;
    @Autowired BusinessSubscriptionRepository subscriptions;
    @Autowired CallSessionRepository calls;

    @BeforeEach
    void configureLoadCapacity() {
        setEnterpriseCapacity(60);
    }

    @AfterEach
    void restoreCatalogCapacity() {
        setEnterpriseCapacity(10);
    }

    @Test
    void admitsFiveConcurrentCalls() throws Exception {
        assertFullAdmission(5);
    }

    @Test
    void admitsTenConcurrentCalls() throws Exception {
        assertFullAdmission(10);
    }

    @Test
    void admitsTwentyFiveConcurrentCalls() throws Exception {
        assertFullAdmission(25);
    }

    @Test
    void admitsFiftyConcurrentCalls() throws Exception {
        assertFullAdmission(50);
    }

    @Test
    void rejectsExcessLoadAtConfiguredTenantLimitWithoutErrors() throws Exception {
        setEnterpriseCapacity(10);
        TenantFixture tenant = tenant("Load Limited Tenant", nextPhone());

        LoadResult result = runBurst(tenant, 25, "limited");

        assertEquals(10, result.accepted());
        assertEquals(15, result.rejected());
        assertEquals(0, result.errors());
        assertEquals(10L, calls.countByBusinessIdAndStatusIn(tenant.businessId(), ACTIVE_STATUSES));
    }

    @Test
    void simultaneousTenantsDoNotConsumeEachOthersCapacity() throws Exception {
        setEnterpriseCapacity(25);
        TenantFixture tenantA = tenant("Load Tenant A", nextPhone());
        TenantFixture tenantB = tenant("Load Tenant B", nextPhone());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<LoadResult> a = executor.submit(() -> runBurst(tenantA, 25, "tenant-a"));
            Future<LoadResult> b = executor.submit(() -> runBurst(tenantB, 25, "tenant-b"));

            LoadResult resultA = a.get(45, TimeUnit.SECONDS);
            LoadResult resultB = b.get(45, TimeUnit.SECONDS);

            assertEquals(25, resultA.accepted());
            assertEquals(0, resultA.rejected());
            assertEquals(0, resultA.errors());
            assertEquals(25, resultB.accepted());
            assertEquals(0, resultB.rejected());
            assertEquals(0, resultB.errors());
        } finally {
            executor.shutdownNow();
        }
    }

    private void assertFullAdmission(int concurrency) throws Exception {
        TenantFixture tenant = tenant("Load Tenant " + concurrency, nextPhone());
        LoadResult result = runBurst(tenant, concurrency, "c" + concurrency);

        assertEquals(concurrency, result.accepted());
        assertEquals(0, result.rejected());
        assertEquals(0, result.errors());
        assertEquals((long) concurrency,
                calls.countByBusinessIdAndStatusIn(tenant.businessId(), ACTIVE_STATUSES));
        assertTrue(result.totalMillis() < 45_000,
                () -> "Load burst exceeded safety timeout: " + result);
    }

    private LoadResult runBurst(TenantFixture tenant,
                                int concurrency,
                                String scenario) throws Exception {
        CountDownLatch ready = new CountDownLatch(concurrency);
        CountDownLatch fire = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        List<Future<Attempt>> futures = new ArrayList<>(concurrency);

        try {
            for (int i = 0; i < concurrency; i++) {
                int index = i;
                futures.add(executor.submit(() -> attempt(
                        tenant.phone(), scenario + "-" + index, ready, fire)));
            }

            assertTrue(ready.await(15, TimeUnit.SECONDS),
                    "Workers did not become ready before load burst");
            long started = System.nanoTime();
            fire.countDown();

            int accepted = 0;
            int rejected = 0;
            int errors = 0;
            List<Long> latencies = new ArrayList<>(concurrency);

            for (Future<Attempt> future : futures) {
                Attempt attempt = future.get(45, TimeUnit.SECONDS);
                latencies.add(attempt.millis());
                if (attempt.outcome() == Outcome.ACCEPTED) accepted++;
                else if (attempt.outcome() == Outcome.REJECTED) rejected++;
                else errors++;
            }

            long totalMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            long p95Millis = percentile95(latencies);
            LoadResult result = new LoadResult(
                    concurrency, accepted, rejected, errors, totalMillis, p95Millis);

            System.out.printf(
                    "HELVOCA_CALL_LOAD scenario=%s concurrency=%d accepted=%d rejected=%d errors=%d total_ms=%d p95_ms=%d%n",
                    scenario, concurrency, accepted, rejected, errors, totalMillis, p95Millis);
            return result;
        } finally {
            executor.shutdownNow();
        }
    }

    private Attempt attempt(String destination,
                            String suffix,
                            CountDownLatch ready,
                            CountDownLatch fire) throws Exception {
        ready.countDown();
        fire.await(15, TimeUnit.SECONDS);
        long started = System.nanoTime();
        try {
            lifecycle.startInboundCall(
                    "twilio",
                    "CA-load-" + suffix + "-" + UUID.randomUUID(),
                    "+56910000000",
                    destination);
            return new Attempt(Outcome.ACCEPTED, elapsedMillis(started));
        } catch (CallCapacityExceededException expected) {
            return new Attempt(Outcome.REJECTED, elapsedMillis(started));
        } catch (RuntimeException unexpected) {
            unexpected.printStackTrace(System.err);
            return new Attempt(Outcome.ERROR, elapsedMillis(started));
        }
    }

    private TenantFixture tenant(String name, String phoneValue) {
        Business business = new Business();
        business.setName(name);
        business.setTimezone("America/Santiago");
        business.setLanguage("es");
        business = businesses.saveAndFlush(business);
        activateSubscription(business.getId());

        PhoneNumber phone = new PhoneNumber();
        phone.setBusinessId(business.getId());
        phone.setProvider("TWILIO");
        phone.setPhoneNumber(phoneValue);
        phone.setActive(true);
        phoneNumbers.saveAndFlush(phone);
        return new TenantFixture(business.getId(), phoneValue);
    }

    private void activateSubscription(UUID businessId) {
        Instant now = Instant.now();
        BusinessSubscription subscription = new BusinessSubscription();
        subscription.setBusinessId(businessId);
        subscription.setPlanCode(PlanCode.ENTERPRISE);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setCurrentPeriodStart(now.minus(1, ChronoUnit.DAYS));
        subscription.setCurrentPeriodEnd(now.plus(30, ChronoUnit.DAYS));
        subscriptions.saveAndFlush(subscription);
    }

    private void setEnterpriseCapacity(int capacity) {
        try (Connection connection = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE commercial_plan_entitlement
                        SET limit_value = ?
                      WHERE plan_code = 'ENTERPRISE'
                        AND entitlement_key = 'CONCURRENT_CALLS'
                     """)) {
            statement.setInt(1, capacity);
            assertEquals(1, statement.executeUpdate());
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to configure test-only Enterprise capacity", e);
        }
    }

    private static long elapsedMillis(long started) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }

    private static long percentile95(List<Long> values) {
        if (values.isEmpty()) return 0;
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int index = Math.max(0, (int) Math.ceil(sorted.size() * 0.95) - 1);
        return sorted.get(index);
    }

    private static String nextPhone() {
        long suffix = Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000_000L);
        return "+1435" + String.format("%09d", suffix);
    }

    private enum Outcome { ACCEPTED, REJECTED, ERROR }

    private record Attempt(Outcome outcome, long millis) {}
    private record LoadResult(
            int concurrency,
            int accepted,
            int rejected,
            int errors,
            long totalMillis,
            long p95Millis) {}
    private record TenantFixture(UUID businessId, String phone) {}
}

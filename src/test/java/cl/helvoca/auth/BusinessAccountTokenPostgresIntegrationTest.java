package cl.helvoca.auth;

import cl.helvoca.security.TenantDatabaseContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest
class BusinessAccountTokenPostgresIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("app.seed.enabled", () -> "false");
    }

    @Autowired @Qualifier("migrationDataSource") DataSource ownerSource;
    @Autowired BusinessAccountTokenService tokens;
    @Autowired TenantDatabaseContext context;
    @Autowired JdbcTemplate runtime;

    private JdbcTemplate owner;

    @BeforeEach
    void setup() {
        owner = new JdbcTemplate(ownerSource);
        owner.update("DELETE FROM business_account_token");
    }

    @Test
    void issuedResetIsHashedOnlyAndRedeemedOnceForExactBusinessUser() {
        UUID business = business();
        UUID user = user(business);
        BusinessAccountTokenService.Issued issued =
                tokens.issue(business, user, BusinessAccountTokenService.Purpose.RESET);

        assertEquals(43, issued.token().length());
        assertTrue(issued.expiresAt().isAfter(Instant.now()));
        String storedHash = owner.queryForObject(
                "SELECT token_hash FROM business_account_token WHERE user_id = ?", String.class, user);
        assertNotEquals(issued.token(), storedHash);
        assertEquals(64, storedHash.length());

        AtomicInteger called = new AtomicInteger();
        tokens.redeem(issued.token(), BusinessAccountTokenService.Purpose.RESET, claim -> {
            assertEquals(user, claim.userId());
            assertEquals(business, claim.businessId());
            called.incrementAndGet();
        });
        assertEquals(1, called.get());
        assertThrows(ResponseStatusException.class,
                () -> tokens.redeem(issued.token(), BusinessAccountTokenService.Purpose.RESET,
                        claim -> called.incrementAndGet()));
        assertEquals(1, called.get(), "a replay cannot mutate another password");
    }

    @Test
    void rotationExpiryAndWrongPurposeAlwaysRejectWithoutInvokingMutation() {
        UUID business = business();
        UUID user = user(business);
        BusinessAccountTokenService.Issued first =
                tokens.issue(business, user, BusinessAccountTokenService.Purpose.RESET);
        BusinessAccountTokenService.Issued second =
                tokens.issue(business, user, BusinessAccountTokenService.Purpose.RESET);
        AtomicInteger called = new AtomicInteger();
        assertThrows(ResponseStatusException.class,
                () -> tokens.redeem(first.token(), BusinessAccountTokenService.Purpose.RESET,
                        claim -> called.incrementAndGet()));
        assertThrows(ResponseStatusException.class,
                () -> tokens.redeem(second.token(), BusinessAccountTokenService.Purpose.VERIFY,
                        claim -> called.incrementAndGet()));
        // Simulate a token whose *originally valid* lifetime elapsed, while preserving
        // the database invariant that its expiry must be after its creation.
        owner.update("UPDATE business_account_token SET created_at=clock_timestamp()-interval '2 minutes', "
                + "expires_at=clock_timestamp()-interval '1 minute' WHERE user_id=?", user);
        assertThrows(ResponseStatusException.class,
                () -> tokens.redeem(second.token(), BusinessAccountTokenService.Purpose.RESET,
                        claim -> called.incrementAndGet()));
        assertEquals(0, called.get());
    }

    @Test
    void wrongBusinessAndPlatformUsersCannotReceiveTenantTokens() {
        UUID businessA = business();
        UUID businessB = business();
        UUID user = user(businessA);
        assertThrows(IllegalArgumentException.class,
                () -> tokens.issue(businessB, user, BusinessAccountTokenService.Purpose.RESET));
        UUID platform = user(null);
        assertThrows(IllegalArgumentException.class,
                () -> tokens.issue(businessA, platform, BusinessAccountTokenService.Purpose.RESET));

        BusinessAccountTokenService.Issued valid =
                tokens.issue(businessA, user, BusinessAccountTokenService.Purpose.RESET);
        assertThrows(DataAccessException.class,
                () -> context.runAsTenant(businessB,
                        () -> runtime.queryForObject(
                                "SELECT token_hash FROM business_account_token WHERE user_id=?",
                                String.class, user)));
        assertNotNull(valid.token());
    }

    @Test
    void verificationTokensHaveOneDayLifetimeAndWrongInputsAreRejected() {
        UUID business = business();
        UUID user = user(business);
        Instant now = Instant.now();
        BusinessAccountTokenService.Issued issued =
                tokens.issue(business, user, BusinessAccountTokenService.Purpose.VERIFY);
        assertTrue(issued.expiresAt().isAfter(now.plusSeconds(23 * 3600)));
        assertTrue(issued.expiresAt().isBefore(now.plusSeconds(25 * 3600)));

        AtomicInteger called = new AtomicInteger();
        assertThrows(ResponseStatusException.class,
                () -> tokens.redeem(null, BusinessAccountTokenService.Purpose.VERIFY, c -> called.incrementAndGet()));
        assertThrows(ResponseStatusException.class,
                () -> tokens.redeem("bad", BusinessAccountTokenService.Purpose.VERIFY, c -> called.incrementAndGet()));
        assertThrows(ResponseStatusException.class,
                () -> tokens.redeem(issued.token(), null, c -> called.incrementAndGet()));
        assertThrows(ResponseStatusException.class,
                () -> tokens.redeem(issued.token(), BusinessAccountTokenService.Purpose.VERIFY, null));
        assertThrows(IllegalArgumentException.class,
                () -> tokens.issue(null, user, BusinessAccountTokenService.Purpose.RESET));
        assertThrows(IllegalArgumentException.class,
                () -> tokens.issue(business, null, BusinessAccountTokenService.Purpose.RESET));
        assertThrows(IllegalArgumentException.class, () -> tokens.issue(business, user, null));
        assertEquals(0, called.get());

        tokens.redeem(issued.token(), BusinessAccountTokenService.Purpose.VERIFY,
                claim -> called.incrementAndGet());
        assertEquals(1, called.get());
    }

    @Test
    void credentialMutationFailureRollsBackConsumption() {
        UUID business = business();
        UUID user = user(business);
        BusinessAccountTokenService.Issued issued =
                tokens.issue(business, user, BusinessAccountTokenService.Purpose.RESET);
        assertThrows(IllegalStateException.class,
                () -> tokens.redeem(issued.token(), BusinessAccountTokenService.Purpose.RESET,
                        claim -> { throw new IllegalStateException("credential update failed"); }));

        AtomicInteger applied = new AtomicInteger();
        tokens.redeem(issued.token(), BusinessAccountTokenService.Purpose.RESET,
                claim -> applied.incrementAndGet());
        assertEquals(1, applied.get(), "failed credential transactions must preserve the claim");
    }

    @Test
    void concurrentClaimsPermitOnlyOneMutation() throws Exception {
        UUID business = business();
        UUID user = user(business);
        BusinessAccountTokenService.Issued issued =
                tokens.issue(business, user, BusinessAccountTokenService.Purpose.RESET);
        AtomicInteger applied = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        try (var threads = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Boolean> attempt = () -> {
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("threads did not start");
                try {
                    tokens.redeem(issued.token(), BusinessAccountTokenService.Purpose.RESET,
                            claim -> applied.incrementAndGet());
                    return true;
                } catch (ResponseStatusException rejected) {
                    return false;
                }
            };
            Future<Boolean> a = threads.submit(attempt);
            Future<Boolean> b = threads.submit(attempt);
            start.countDown();
            assertNotEquals(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
        }
        assertEquals(1, applied.get());
    }

    private UUID business() {
        UUID id = UUID.randomUUID();
        owner.update("INSERT INTO business(id,name) VALUES (?,?)", id, "Business " + id);
        return id;
    }

    private UUID user(UUID business) {
        UUID id = UUID.randomUUID();
        owner.update("INSERT INTO app_user(id,business_id,name,email,password_hash) VALUES (?,?,?,?,?)",
                id, business, "Owner", "account-" + id + "@example.test", "HASH");
        return id;
    }
}

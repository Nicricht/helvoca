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
        owner.update("UPDATE business_account_token SET expires_at=clock_timestamp()-interval '1 second' WHERE user_id=?", user);
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

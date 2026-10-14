package cl.helvoca.phone;

import cl.helvoca.security.TenantDatabaseContext;
import cl.helvoca.telephony.twilio.TwilioExistingNumberOwnershipVerifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Part 4/10: real Flyway V100-V103 and admin/reconciler transaction integration.
 *
 * Twilio GET is mocked. All accounts, people, phone numbers, and tenants are
 * synthetic; tests cannot connect to Twilio, Railway or production Postgres.
 */
@Testcontainers
@SpringBootTest(properties = {
        "app.telephony.twilio.existing-voice-route-enabled=false",
        "app.controlled-pilot.external-effects-enabled=false"
})
class TwilioExistingReconciliationReadinessPostgresIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        r.add("spring.flyway.enabled", () -> "true");
        r.add("app.seed.enabled", () -> "false");
    }

    @Autowired @Qualifier("migrationDataSource") DataSource migrationDataSource;
    @Autowired TenantDatabaseContext context;
    @Autowired JdbcTemplate runtime;
    @Autowired TwilioExistingEnrollmentAdminService service;
    @MockitoBean TwilioExistingNumberOwnershipVerifier carrier;

    private static final AtomicInteger SEQ = new AtomicInteger(4000);
    private static final String ACCOUNT = "AC" + "a".repeat(32);
    // Each test has its own provider SID: PostgreSQL enforces global uniqueness.
    private String PN;
    private static final String OTHER_PN = "PN" + "c".repeat(32);

    private JdbcTemplate owner;
    private UUID adminRequester;
    private UUID adminApprover;
    private UUID adminOther;
    private UUID tenant;
    private UUID phone;
    private String number;

    @BeforeEach
    void setup() {
        SecurityContextHolder.clearContext();
        reset(carrier);
        owner = new JdbcTemplate(migrationDataSource);
        adminRequester = newAdmin();
        adminApprover = newAdmin();
        adminOther = newAdmin();
        tenant = UUID.randomUUID();
        phone = UUID.randomUUID();
        PN = "PN" + phone.toString().replace("-", "");
        number = "+1202555" + String.format("%04d", SEQ.incrementAndGet());

        owner.update("INSERT INTO public.business(id,name) VALUES (?,?)",
                tenant, "Synthetic Part4 tenant");
        owner.update("""
                INSERT INTO public.phone_number
                  (id,business_id,phone_number,provider,external_id,active)
                VALUES (?, ?, ?, 'TWILIO', ?, FALSE)
                """, phone, tenant, number, PN);
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        assertEquals(TenantDatabaseContext.Mode.DENIED, context.currentOrDenied().mode());
    }

    private UUID newAdmin() {
        UUID id = UUID.randomUUID();
        String email = email(id);
        owner.update("""
                INSERT INTO public.app_user (id,business_id,name,email,password_hash,active)
                VALUES (?,NULL,'Part4 synthetic platform admin',?,'not-a-real-hash',TRUE)
                """, id, email);
        UUID role = owner.queryForObject(
                "SELECT id FROM public.role WHERE code='PLATFORM_ADMIN'", UUID.class);
        owner.update("INSERT INTO public.user_role(user_id,role_id) VALUES (?,?)", id, role);
        return id;
    }

    private static String email(UUID id) { return "part4-" + id + "@example.test"; }

    private <T> T as(UUID actor, Supplier<T> operation) {
        Jwt jwt = Jwt.withTokenValue("part4-test-jwt")
                .header("alg", "HS256")
                .subject(actor.toString())
                .issuedAt(Instant.now().minusSeconds(5))
                .expiresAt(Instant.now().plusSeconds(300))
                .claim("roles", List.of("PLATFORM_ADMIN"))
                .claim("email", email(actor))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt,
                        List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))));
        try (TenantDatabaseContext.Scope ignored = context.useSystem()) {
            return operation.get();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void validCarrier(String sid) {
        when(carrier.verify(eq(number), eq(ACCOUNT), eq(PN)))
                .thenReturn(new TwilioExistingNumberOwnershipVerifier.Outcome(
                        TwilioExistingNumberOwnershipVerifier.Code.VERIFIED,
                        new TwilioExistingNumberOwnershipVerifier.Evidence(
                                sid, ACCOUNT,
                                TwilioExistingNumberOwnershipVerifier.AccountKind.MAIN)));
    }

    private TwilioExistingEnrollmentAdminService.Request request() {
        return new TwilioExistingEnrollmentAdminService.Request(tenant, phone, ACCOUNT);
    }

    private UUID pending() {
        return as(adminRequester, () -> service.request(request()).changeId());
    }

    private UUID approved() {
        UUID change = pending();
        assertEquals("APPROVED",
                as(adminApprover, () -> service.approve(change)).state());
        return change;
    }

    private long count(String table, String key, UUID id) {
        return owner.queryForObject("SELECT COUNT(*) FROM public." + table
                + " WHERE " + key + "=?", Long.class, id);
    }

    private void assertNotPromoted() {
        assertEquals(1L, owner.queryForObject("""
                SELECT COUNT(*) FROM public.phone_number
                WHERE id=? AND twilio_binding_source IS NULL
                  AND twilio_account_sid IS NULL AND voice_verified_at IS NULL
                  AND external_id=? AND NOT active
                """, Long.class, phone, PN));
    }

    @Test
    void verifiedExistingRequiresTwoHumansAndFreshCarrierEvidenceThenPersistsExactlyOnce() {
        UUID change = approved();
        assertNotPromoted();
        assertEquals(0L, count("twilio_existing_enrollment_claim", "phone_number_id", phone));
        verifyNoInteractions(carrier);
        validCarrier(PN);

        var response = as(adminApprover, () -> service.execute(change));
        assertEquals("APPLIED", response.state());
        assertEquals("APPLIED", owner.queryForObject(
                "SELECT state FROM twilio_existing_enrollment_request WHERE change_id=?",
                String.class, change));
        assertEquals(1L, count("twilio_existing_enrollment_claim", "phone_number_id", phone));
        assertEquals(3L, count("twilio_existing_enrollment_audit", "change_id", change));
        assertEquals(1L, owner.queryForObject("""
                SELECT COUNT(*) FROM public.phone_number
                 WHERE id=? AND twilio_binding_source='VERIFIED_EXISTING'
                   AND twilio_account_sid=? AND external_id=?
                   AND voice_verified_at IS NOT NULL AND NOT active
                """, Long.class, phone, ACCOUNT, PN));
        verify(carrier, times(1)).verify(number, ACCOUNT, PN);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> as(adminApprover, () -> service.execute(change))).getStatusCode());
        verifyNoMoreInteractions(carrier);
    }

    @Test
    void duplicatedRequestsCannotReplacePendingOrApprovedHumanAuthorization() {
        UUID change = pending();
        for (UUID actor : new UUID[]{adminRequester, adminApprover, adminOther}) {
            assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                    () -> as(actor, () -> service.request(request()))).getStatusCode());
        }
        assertEquals("APPROVED",
                as(adminApprover, () -> service.approve(change)).state());
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> as(adminOther, () -> service.request(request()))).getStatusCode());
        assertEquals(1L, count("twilio_existing_enrollment_request", "phone_number_id", phone));
        // Two audit actions should have occurred: requested and approved.
        assertEquals(2L, count("twilio_existing_enrollment_audit", "change_id", change));
        assertNotPromoted();
        verifyNoInteractions(carrier);
    }

    @Test
    void mismatchedCarrierSidFailsWithoutWritingProvenanceAndCanSafelyRetry() {
        UUID change = approved();
        validCarrier(OTHER_PN);
        assertEquals("BLOCKED",
                as(adminApprover, () -> service.execute(change)).state());
        assertNotPromoted();
        assertEquals(0L, count("twilio_existing_enrollment_claim", "phone_number_id", phone));
        assertEquals("APPROVED", owner.queryForObject(
                "SELECT state FROM twilio_existing_enrollment_request WHERE change_id=?",
                String.class, change));
        assertEquals(1L, count("twilio_existing_enrollment_audit", "change_id", change)
                - 2L, "One opaque EXECUTION_BLOCKED event, not a fabricated success");

        validCarrier(PN);
        assertEquals("APPLIED",
                as(adminApprover, () -> service.execute(change)).state());
        assertEquals(1L, count("twilio_existing_enrollment_claim", "phone_number_id", phone));
        verify(carrier, times(2)).verify(number, ACCOUNT, PN);
    }

    @Test
    void unapprovedOrThirdPartyExecutionDoesNotPerformCarrierGetOrChangePhone() {
        UUID change = pending();
        assertThrows(AccessDeniedException.class,
                () -> as(adminRequester, () -> service.approve(change)));
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> as(adminApprover, () -> service.execute(change))).getStatusCode());
        assertEquals("APPROVED",
                as(adminApprover, () -> service.approve(change)).state());
        assertThrows(AccessDeniedException.class,
                () -> as(adminOther, () -> service.execute(change)));
        assertThrows(AccessDeniedException.class,
                () -> as(adminRequester, () -> service.execute(change)));
        assertEquals(0L, count("twilio_existing_enrollment_claim", "phone_number_id", phone));
        assertNotPromoted();
        verifyNoInteractions(carrier);
    }

    @Test
    void wrongTenantOrAlreadyCertifiedPhoneCannotBeEnrolledAgain() {
        UUID otherTenant = UUID.randomUUID();
        owner.update("INSERT INTO public.business(id,name) VALUES (?,?)",
                otherTenant, "Synthetic other tenant");
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> as(adminRequester, () -> service.request(
                        new TwilioExistingEnrollmentAdminService.Request(
                                otherTenant, phone, ACCOUNT)))).getStatusCode());
        UUID change = approved();
        validCarrier(PN);
        assertEquals("APPLIED",
                as(adminApprover, () -> service.execute(change)).state());
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> as(adminOther, () -> service.request(request()))).getStatusCode());
        assertEquals(1L, count("twilio_existing_enrollment_claim", "phone_number_id", phone));
        verify(carrier, times(1)).verify(number, ACCOUNT, PN);
    }
}

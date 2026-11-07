package cl.helvoca.phone;

import cl.helvoca.security.TenantDatabaseContext;
import cl.helvoca.telephony.twilio.TwilioExistingNumberOwnershipVerifier;
import cl.helvoca.telephony.twilio.TwilioVerifiedExistingVoiceRouteGate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
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
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers
@SpringBootTest
class TwilioExistingSingleOperatorEnrollmentPostgresIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    private static final UUID BUSINESS = UUID.fromString("551214ca-887b-4c49-928e-7dd2f3629cc0");
    private static final UUID PHONE_ID = UUID.fromString("5417fcbf-72b7-407a-878a-4d11da8819da");
    private static final String PHONE = "+16812706780";
    private static final String ACCOUNT = "AC" + "a".repeat(32);
    private static final String SID = "PN" + "b".repeat(32);
    private static final String PASSWORD = "PrivateDemoPassword123!";

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", postgres::getJdbcUrl);
        p.add("spring.datasource.username", postgres::getUsername);
        p.add("spring.datasource.password", postgres::getPassword);
        p.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        p.add("spring.flyway.enabled", () -> "true");
        p.add("app.seed.enabled", () -> "false");
        p.add("TWILIO_CERTIFICATION_DEMO_BUSINESS_ID", BUSINESS::toString);
        p.add("TWILIO_TEST_FROM", () -> PHONE);
        p.add("TWILIO_SINGLE_OPERATOR_DEMO_ENROLLMENT_ENABLED", () -> "true");
        p.add("RAILWAY_ENVIRONMENT_NAME", () -> "demo-isolated");
        p.add("app.telephony.twilio.existing-voice-route-enabled", () -> "true");
        p.add("app.telephony.twilio.existing-voice-route-business-id", BUSINESS::toString);
        p.add("app.telephony.twilio.existing-voice-route-phone-id", PHONE_ID::toString);
        p.add("app.telephony.twilio.existing-voice-route-account-sid", () -> ACCOUNT);
        p.add("TWILIO_CERTIFICATION_DEMO_ROUTE_ENABLED", () -> "true");
        p.add("TWILIO_CERTIFICATION_SCOPED_CUSTOMER_DEMO_ENABLED", () -> "true");
    }

    @Autowired @Qualifier("migrationDataSource") DataSource ownerDataSource;
    @Autowired JdbcTemplate jdbc;
    @Autowired TenantDatabaseContext context;
    @Autowired PasswordEncoder passwords;
    @Autowired PhoneNumberRepository phones;
    @Autowired TwilioExistingSingleOperatorEnrollmentService service;
    @Autowired TwilioVerifiedExistingVoiceRouteGate route;
    @MockitoBean TwilioExistingNumberOwnershipVerifier carrier;

    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    private void authenticate(UUID admin, long ageSeconds) {
        Jwt jwt = Jwt.withTokenValue("test-context-only")
                .header("alg", "HS256")
                .subject(admin.toString())
                .issuedAt(Instant.now().minusSeconds(ageSeconds))
                .expiresAt(Instant.now().plusSeconds(600))
                .claim("email", "operator-" + admin + "@example.test")
                .claim("roles", List.of("PLATFORM_ADMIN")).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwt, List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))));
    }

    private <T> T as(UUID admin, Supplier<T> work) {
        authenticate(admin, 20);
        try (TenantDatabaseContext.Scope ignored = context.useSystem()) {
            return work.get();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private boolean routeAllowed() {
        return context.callAsSystem(() -> route.allows(phones.findById(PHONE_ID).orElseThrow()));
    }

    @Test
    void oneOwnerNeedsFreshPasswordIndependentCarrierProofAndImmutableAudit() {
        JdbcTemplate owner = new JdbcTemplate(ownerDataSource);
        UUID admin = UUID.randomUUID();
        owner.update("""
                INSERT INTO business(id,name,status,mode)
                VALUES (?,'Isolated Aurora route test','ACTIVE','CUSTOMER')
                """, BUSINESS);
        owner.update("""
                INSERT INTO phone_number(id,business_id,phone_number,provider,active,whatsapp_enabled)
                VALUES (?,? ,?,'TWILIO',FALSE,FALSE)
                """, PHONE_ID, BUSINESS, PHONE);
        owner.update("""
                INSERT INTO app_user(id,business_id,name,email,password_hash,active)
                VALUES (?,NULL,'Isolated admin',?,?,TRUE)
                """, admin, "operator-" + admin + "@example.test", passwords.encode(PASSWORD));
        UUID roleId = owner.queryForObject("SELECT id FROM role WHERE code='PLATFORM_ADMIN'", UUID.class);
        owner.update("INSERT INTO user_role(user_id,role_id) VALUES (?,?)", admin, roleId);

        var request = new TwilioExistingSingleOperatorEnrollmentService.Request(BUSINESS, PHONE_ID, ACCOUNT);
        var confirm = new TwilioExistingSingleOperatorEnrollmentService.Confirm(PASSWORD, PHONE);
        assertFalse(routeAllowed());
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> as(admin, () -> service.request(new TwilioExistingSingleOperatorEnrollmentService.Request(
                        UUID.randomUUID(), PHONE_ID, ACCOUNT)))).getStatusCode());

        when(carrier.verify(PHONE, ACCOUNT, null)).thenReturn(
                new TwilioExistingNumberOwnershipVerifier.Outcome(
                        TwilioExistingNumberOwnershipVerifier.Code.WEBHOOK_MISMATCH, null));
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> as(admin, () -> service.request(request))).getStatusCode());
        assertFalse(routeAllowed());

        when(carrier.verify(PHONE, ACCOUNT, null)).thenReturn(
                new TwilioExistingNumberOwnershipVerifier.Outcome(
                        TwilioExistingNumberOwnershipVerifier.Code.VERIFIED,
                        new TwilioExistingNumberOwnershipVerifier.Evidence(
                                SID, ACCOUNT, TwilioExistingNumberOwnershipVerifier.AccountKind.MAIN)));
        var started = as(admin, () -> service.request(request));
        assertEquals("PENDING", started.state());
        assertEquals("PENDING", as(admin, () -> service.status(started.changeId())).state());
        assertFalse(routeAllowed());
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> as(admin, () -> service.request(request))).getStatusCode());
        assertThrows(AccessDeniedException.class,
                () -> as(UUID.randomUUID(), () -> service.confirm(started.changeId(), confirm)));

        authenticate(admin, 360);
        try (TenantDatabaseContext.Scope ignored = context.useSystem()) {
            assertThrows(AccessDeniedException.class, () -> service.confirm(started.changeId(), confirm));
        } finally {
            SecurityContextHolder.clearContext();
        }

        var wrong = new TwilioExistingSingleOperatorEnrollmentService.Confirm("IncorrectPassword", PHONE);
        assertThrows(AccessDeniedException.class,
                () -> as(admin, () -> service.confirm(started.changeId(), wrong)));
        assertEquals(1, owner.queryForObject("""
                SELECT failed_attempts FROM twilio_single_operator_demo_enrollment WHERE change_id=?
                """, Integer.class, started.changeId()));
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> as(admin, () -> service.confirm(started.changeId(),
                        new TwilioExistingSingleOperatorEnrollmentService.Confirm(PASSWORD, "+12025550999"))))
                .getStatusCode());

        when(carrier.verify(PHONE, ACCOUNT, null)).thenReturn(
                new TwilioExistingNumberOwnershipVerifier.Outcome(
                        TwilioExistingNumberOwnershipVerifier.Code.WEBHOOK_MISMATCH, null));
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> as(admin, () -> service.confirm(started.changeId(), confirm))).getStatusCode());
        assertFalse(routeAllowed());
        assertEquals(0L, owner.queryForObject("""
                SELECT COUNT(*) FROM twilio_single_operator_demo_audit
                 WHERE change_id=? AND action='CONFIRMED'
                """, Long.class, started.changeId()));

        when(carrier.verify(PHONE, ACCOUNT, null)).thenReturn(
                new TwilioExistingNumberOwnershipVerifier.Outcome(
                        TwilioExistingNumberOwnershipVerifier.Code.VERIFIED,
                        new TwilioExistingNumberOwnershipVerifier.Evidence(
                                SID, ACCOUNT, TwilioExistingNumberOwnershipVerifier.AccountKind.MAIN)));
        assertEquals("APPLIED", as(admin, () -> service.confirm(started.changeId(), confirm)).state());
        assertTrue(routeAllowed());
        assertEquals("APPLIED", as(admin, () -> service.status(started.changeId())).state());
        assertEquals(1L, owner.queryForObject("""
                SELECT COUNT(*) FROM twilio_single_operator_demo_enrollment
                 WHERE change_id=? AND actor_id=? AND phone_sid=? AND state='APPLIED'
                   AND carrier_verified_at IS NOT NULL AND confirmed_at IS NOT NULL
                """, Long.class, started.changeId(), admin, SID));
        assertEquals(1L, owner.queryForObject("""
                SELECT COUNT(*) FROM twilio_single_operator_demo_audit
                 WHERE change_id=? AND actor_id=? AND action='CONFIRMED'
                """, Long.class, started.changeId(), admin));
        assertThrows(ResponseStatusException.class,
                () -> as(admin, () -> service.confirm(started.changeId(), confirm)));
        assertThrows(DataAccessException.class, () -> owner.update("""
                UPDATE twilio_single_operator_demo_enrollment SET state='PENDING'
                 WHERE change_id=?
                """, started.changeId()));
        assertThrows(DataAccessException.class, () -> context.callAsTenant(BUSINESS, () ->
                jdbc.queryForObject("SELECT COUNT(*) FROM twilio_single_operator_demo_enrollment",
                        Long.class)));
        owner.update("UPDATE app_user SET active=FALSE WHERE id=?", admin);
        assertFalse(routeAllowed());
        verify(carrier, atLeast(2)).verify(PHONE, ACCOUNT, null);
    }
}

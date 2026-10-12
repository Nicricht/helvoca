package cl.helvoca.phone;

import cl.helvoca.security.TenantDatabaseContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Testcontainers
@SpringBootTest
class TwilioExistingEnrollmentAdminPostgresIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        r.add("spring.flyway.enabled", () -> "true");
        r.add("app.seed.enabled", () -> "false");
    }

    @Autowired @Qualifier("migrationDataSource") DataSource ownerDataSource;
    @Autowired JdbcTemplate runtime;
    @Autowired TenantDatabaseContext context;
    @Autowired TwilioExistingEnrollmentAdminService service;
    @MockitoBean TwilioExistingEnrollmentReconciler reconciler;

    private static final String ACCOUNT = "AC" + "a".repeat(32);
    private static final AtomicInteger SEQ = new AtomicInteger();
    private JdbcTemplate owner;
    private UUID adminA;
    private UUID adminB;
    private UUID adminC;
    private UUID business;
    private UUID phone;

    @BeforeEach
    void setup() {
        reset(reconciler);
        SecurityContextHolder.clearContext();
        owner = new JdbcTemplate(ownerDataSource);
        adminA = newAdmin(null);
        adminB = newAdmin(null);
        adminC = newAdmin(null);
        business = UUID.randomUUID();
        owner.update("INSERT INTO business(id,name) VALUES (?,?)",
                business, "Admin workflow tenant " + business);
        phone = insertPhone(business);
    }

    @AfterEach
    void cleanup() { SecurityContextHolder.clearContext(); }

    private UUID newAdmin(UUID linkedBusiness) {
        UUID id = UUID.randomUUID();
        owner.update("""
                INSERT INTO app_user(id,business_id,name,email,password_hash,active)
                VALUES (?,?,? ,?,'hash',TRUE)
                """, id, linkedBusiness, "Platform admin", email(id));
        UUID roleId = owner.queryForObject(
                "SELECT id FROM role WHERE code = 'PLATFORM_ADMIN'", UUID.class);
        owner.update("INSERT INTO user_role(user_id,role_id) VALUES (?,?)", id, roleId);
        return id;
    }

    private static String email(UUID id) { return "platform-" + id + "@example.test"; }

    private UUID insertPhone(UUID tenant) {
        UUID id = UUID.randomUUID();
        String number = "+1202555" + String.format("%04d", SEQ.incrementAndGet());
        owner.update("""
                INSERT INTO phone_number(id,business_id,phone_number,provider,active)
                VALUES (?, ?, ?, 'TWILIO', FALSE)
                """, id, tenant, number);
        return id;
    }

    private TwilioExistingEnrollmentAdminService.Request request() {
        return new TwilioExistingEnrollmentAdminService.Request(business, phone, ACCOUNT);
    }

    private Jwt jwt(UUID user, boolean authority, boolean roleClaim, String subject, String email) {
        Jwt.Builder builder = Jwt.withTokenValue("integration-test-only")
                .header("alg", "HS256")
                .issuedAt(Instant.now().minusSeconds(30))
                .expiresAt(Instant.now().plusSeconds(900))
                .claim("email", email);
        if (subject != null) builder.subject(subject);
        if (roleClaim) builder.claim("roles", List.of("PLATFORM_ADMIN"));
        return builder.build();
    }

    private void auth(UUID user) {
        auth(jwt(user,true,true,user.toString(),email(user)),true);
    }

    private void auth(Jwt token, boolean authority) {
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(token,
                        authority ? List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN")) : List.of()));
    }

    private <T> T as(UUID user, Supplier<T> action) {
        auth(user);
        try (TenantDatabaseContext.Scope ignored = context.useSystem()) {
            return action.get();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private long audits(UUID change) {
        return context.callAsSystem(() -> runtime.queryForObject("""
                SELECT COUNT(*) FROM twilio_existing_enrollment_audit WHERE change_id = ?
                """, Long.class, change));
    }

    private String state(UUID change) {
        return owner.queryForObject(
                "SELECT state FROM twilio_existing_enrollment_request WHERE change_id = ?",
                String.class, change);
    }

    private UUID created() {
        return as(adminA, () -> service.request(request()).changeId());
    }

    private UUID approved() {
        UUID id = created();
        assertEquals("APPROVED", as(adminB, () -> service.approve(id)).state());
        return id;
    }

    @Test
    void v103IsInstalledSystemOnlyAndAuditIsImmutableEvenForSystem() {
        assertEquals(1L, owner.queryForObject("""
                SELECT COUNT(*) FROM flyway_schema_history
                WHERE version='103' AND success
                """, Long.class));
        for (String table : List.of("twilio_existing_enrollment_request", "twilio_existing_enrollment_audit")) {
            assertTrue(Boolean.TRUE.equals(owner.queryForObject(
                    "SELECT relrowsecurity AND relforcerowsecurity FROM pg_class WHERE oid=?::regclass",
                    Boolean.class, "public." + table)));
            assertFalse(Boolean.TRUE.equals(owner.queryForObject(
                    "SELECT has_table_privilege('helvoca_runtime', ?, 'SELECT')",
                    Boolean.class, "public." + table)));
        }
        UUID change = created();
        assertThrows(DataAccessException.class, () -> context.callAsTenant(business, () ->
                runtime.queryForObject("SELECT COUNT(*) FROM twilio_existing_enrollment_request", Long.class)));
        assertThrows(DataAccessException.class, () -> context.callAsTenant(business, () ->
                runtime.queryForObject("SELECT COUNT(*) FROM twilio_existing_enrollment_audit", Long.class)));
        assertThrows(DataAccessException.class, () -> context.callAsSystem(() ->
                runtime.update("DELETE FROM twilio_existing_enrollment_audit WHERE change_id = ?",change)));
        assertThrows(DataAccessException.class, () -> context.callAsSystem(() ->
                runtime.update("UPDATE twilio_existing_enrollment_audit SET outcome = 'FAKE' WHERE change_id = ?",change)));
        assertThrows(DataAccessException.class, () -> context.callAsSystem(() ->
                runtime.update("DELETE FROM twilio_existing_enrollment_request WHERE change_id = ?",change)));
        assertEquals(1L, audits(change));
    }

    @Test
    void createApproveExecuteRequiresTwoRealAdminUsersAndPersistsAllHumanEvents() {
        UUID change = created();
        assertEquals("PENDING", state(change));
        assertEquals(1L, audits(change));
        assertEquals(adminA, owner.queryForObject(
                "SELECT requester_id FROM twilio_existing_enrollment_request WHERE change_id = ?",
                UUID.class, change));
        assertEquals("PENDING", as(adminC, () -> service.status(change)).state());

        assertEquals("APPROVED", as(adminB, () -> service.approve(change)).state());
        assertEquals(adminB, owner.queryForObject(
                "SELECT approver_id FROM twilio_existing_enrollment_request WHERE change_id = ?",
                UUID.class, change));
        assertEquals(2L, audits(change));

        when(reconciler.reconcile(any())).thenReturn(
                new TwilioExistingEnrollmentReconciler.Decision(
                        TwilioExistingEnrollmentReconciler.Code.APPLIED,null));
        assertEquals("APPLIED", as(adminB, () -> service.execute(change)).state());
        assertEquals("APPLIED", state(change));
        assertEquals(3L, audits(change));
        var captor = org.mockito.ArgumentCaptor.forClass(TwilioExistingEnrollmentPlanner.Intent.class);
        verify(reconciler).reconcile(captor.capture());
        var intent = captor.getValue();
        assertEquals(adminA, intent.requester().subjectId());
        assertEquals(adminB, intent.approver().subjectId());
        assertEquals(business, intent.businessId());
        assertEquals(phone, intent.persistedPhoneId());
        assertEquals(ACCOUNT, intent.ownerAccountSid());
        assertEquals(change, intent.changeId());
        assertEquals("EXECUTED", owner.queryForObject("""
                SELECT action FROM twilio_existing_enrollment_audit
                 WHERE change_id=? ORDER BY occurred_at DESC LIMIT 1
                """, String.class, change));
        assertThrows(ResponseStatusException.class, () -> as(adminB, () -> service.execute(change)));
    }

    @Test
    void rejectedCarrierExecutionLeavesApprovalForSafeRetryAndAuditsDenial() {
        UUID change = approved();
        when(reconciler.reconcile(any())).thenReturn(
                new TwilioExistingEnrollmentReconciler.Decision(
                        TwilioExistingEnrollmentReconciler.Code.ROW_CONFLICT, null));
        assertEquals("BLOCKED", as(adminB, () -> service.execute(change)).state());
        assertEquals("APPROVED", state(change));
        assertEquals(3L, audits(change));
        assertEquals("ROW_CONFLICT", owner.queryForObject("""
                SELECT outcome FROM twilio_existing_enrollment_audit
                 WHERE change_id=? AND action='EXECUTION_BLOCKED'
                """, String.class,change));
        reset(reconciler);
        when(reconciler.reconcile(any())).thenReturn(
                new TwilioExistingEnrollmentReconciler.Decision(
                        TwilioExistingEnrollmentReconciler.Code.ALREADY_APPLIED,null));
        assertEquals("APPLIED", as(adminB, () -> service.execute(change)).state());
        assertEquals(4L, audits(change));
    }

    @Test
    void rejectedRequestBodiesAndExistingUnverifiedPhoneRestrictionsFailClosed() {
        List<TwilioExistingEnrollmentAdminService.Request> bad = java.util.Arrays.asList(
                null, new TwilioExistingEnrollmentAdminService.Request(null,phone,ACCOUNT),
                new TwilioExistingEnrollmentAdminService.Request(business,null,ACCOUNT),
                new TwilioExistingEnrollmentAdminService.Request(business,phone,null),
                new TwilioExistingEnrollmentAdminService.Request(business,phone,"ACinvalid"));
        for (var invalid : bad) {
            assertEquals(HttpStatus.BAD_REQUEST,
                    assertThrows(ResponseStatusException.class,
                            () -> as(adminA, () -> service.request(invalid))).getStatusCode());
        }
        var absent = new TwilioExistingEnrollmentAdminService.Request(
                business,UUID.randomUUID(), ACCOUNT);
        assertEquals(HttpStatus.CONFLICT,
                assertThrows(ResponseStatusException.class,
                        () -> as(adminA, () -> service.request(absent))).getStatusCode());
        UUID other = insertPhone(business);
        owner.update("""
                UPDATE phone_number SET twilio_binding_source = 'MANAGED_PURCHASE',
                    twilio_account_sid = ?, external_id = ? WHERE id = ?
                """, ACCOUNT, "PN" + "c".repeat(32), other);
        assertEquals(HttpStatus.CONFLICT,
                assertThrows(ResponseStatusException.class,
                        () -> as(adminA, () -> service.request(
                                new TwilioExistingEnrollmentAdminService.Request(business,other,ACCOUNT))))
                        .getStatusCode());
        UUID change = created();
        assertEquals(HttpStatus.CONFLICT,
                assertThrows(ResponseStatusException.class,
                        () -> as(adminB, () -> service.request(request()))).getStatusCode());
        assertEquals(1L, audits(change));
    }

    @Test
    void independentApproverCannotBeRequesterOrSecondApproverOrReplayApproval() {
        UUID change = created();
        assertThrows(AccessDeniedException.class, () -> as(adminA, () -> service.approve(change)));
        assertEquals("PENDING", state(change));
        assertEquals("APPROVED", as(adminB, () -> service.approve(change)).state());
        assertEquals(HttpStatus.CONFLICT,
                assertThrows(ResponseStatusException.class,
                        () -> as(adminC, () -> service.approve(change))).getStatusCode());
        assertEquals(HttpStatus.CONFLICT,
                assertThrows(ResponseStatusException.class,
                        () -> as(adminB, () -> service.approve(change))).getStatusCode());
        assertEquals(2L, audits(change));
    }

    private UUID expired(boolean alreadyApproved) {
        UUID change = UUID.randomUUID();
        Instant base = Instant.now().minusSeconds(1200);
        Instant end = base.plusSeconds(900);
        owner.update("""
                INSERT INTO twilio_existing_enrollment_request(
                    change_id,business_id,phone_number_id,destination_e164,owner_account_sid,
                    requester_id,approver_id,state,requested_at,expires_at,approved_at
                ) VALUES (?, ?, ?, (SELECT phone_number FROM phone_number WHERE id=?),
                          ?, ?, ?, ?, ?, ?, ?)
                """, change,business,phone,phone,ACCOUNT,adminA,alreadyApproved?adminB:null,
                alreadyApproved?"APPROVED":"PENDING",java.sql.Timestamp.from(base),
                java.sql.Timestamp.from(end),
                alreadyApproved?java.sql.Timestamp.from(base.plusSeconds(30)):null);
        return change;
    }

    @Test
    void expiryBlocksBothApprovalAndExecutionWithoutLeakingData() {
        UUID pending = expired(false);
        assertEquals(HttpStatus.CONFLICT,
                assertThrows(ResponseStatusException.class,
                        () -> as(adminB, () -> service.approve(pending))).getStatusCode());
        UUID approved = expired(true);
        assertEquals(HttpStatus.CONFLICT,
                assertThrows(ResponseStatusException.class,
                        () -> as(adminB, () -> service.execute(approved))).getStatusCode());
        verifyNoInteractions(reconciler);
        assertEquals("PENDING", state(pending));
        assertEquals("APPROVED", state(approved));
    }

    @Test
    void unauthorizedExecuteBeforeApprovalOrByOtherAdminIsRejected() {
        UUID change = created();
        assertEquals(HttpStatus.CONFLICT,
                assertThrows(ResponseStatusException.class,
                        () -> as(adminB, () -> service.execute(change))).getStatusCode());
        as(adminB, () -> service.approve(change));
        assertThrows(AccessDeniedException.class,
                () -> as(adminA, () -> service.execute(change)));
        assertThrows(AccessDeniedException.class,
                () -> as(adminC, () -> service.execute(change)));
        verifyNoInteractions(reconciler);
    }

    @Test
    void disabledOrRevokedRequesterCannotExecuteEvenAfterApproval() {
        UUID change = approved();
        owner.update("UPDATE app_user SET active=FALSE WHERE id=?",adminA);
        assertThrows(AccessDeniedException.class,
                () -> as(adminB, () -> service.execute(change)));
        owner.update("UPDATE app_user SET active=TRUE WHERE id=?",adminA);
        owner.update("DELETE FROM user_role WHERE user_id=?",adminA);
        assertThrows(AccessDeniedException.class,
                () -> as(adminB, () -> service.execute(change)));
        assertEquals("APPROVED", state(change));
        verifyNoInteractions(reconciler);
    }

    @Test
    void absentAndNullChangeIdsReturnOnlySafeDiagnosticCodes() {
        assertEquals(HttpStatus.BAD_REQUEST,
                assertThrows(ResponseStatusException.class,
                        () -> as(adminB, () -> service.status(null))).getStatusCode());
        UUID absent=UUID.randomUUID();
        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ResponseStatusException.class,
                        () -> as(adminB, () -> service.status(absent))).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ResponseStatusException.class,
                        () -> as(adminB, () -> service.approve(absent))).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ResponseStatusException.class,
                        () -> as(adminB, () -> service.execute(absent))).getStatusCode());
    }

    @Test
    void noSystemScopeCannotPerformAnyPlatformOperation() {
        auth(adminA);
        try {
            assertThrows(AccessDeniedException.class, () -> service.request(request()));
            assertThrows(AccessDeniedException.class, () -> service.status(UUID.randomUUID()));
            assertThrows(AccessDeniedException.class, () -> context.callAsTenant(business,
                    () -> service.request(request())));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void signedJwtIdentityStillNeedsCorrectAuthoritiesAndLivePlatformRole() {
        try(TenantDatabaseContext.Scope ignored=context.useSystem()) {
            assertThrows(AccessDeniedException.class, () -> service.status(UUID.randomUUID()));
            auth(jwt(adminA,false,true,adminA.toString(),email(adminA)),false);
            assertThrows(AccessDeniedException.class, () -> service.status(UUID.randomUUID()));
            auth(jwt(adminA,true,false,adminA.toString(),email(adminA)),true);
            assertThrows(AccessDeniedException.class, () -> service.status(UUID.randomUUID()));
            auth(jwt(adminA,true,true,"not-a-uuid",email(adminA)),true);
            assertThrows(AccessDeniedException.class, () -> service.status(UUID.randomUUID()));
            auth(jwt(adminA,true,true,adminA.toString(),"wrong@example.test"),true);
            assertThrows(AccessDeniedException.class, () -> service.status(UUID.randomUUID()));
            auth(jwt(adminA,true,true,adminA.toString(),null),true);
            assertThrows(AccessDeniedException.class, () -> service.status(UUID.randomUUID()));
            owner.update("UPDATE app_user SET active=FALSE WHERE id=?",adminA);
            auth(adminA);
            assertThrows(AccessDeniedException.class, () -> service.status(UUID.randomUUID()));
            owner.update("UPDATE app_user SET active=TRUE WHERE id=?",adminA);
            owner.update("DELETE FROM user_role WHERE user_id=?",adminA);
            assertThrows(AccessDeniedException.class, () -> service.status(UUID.randomUUID()));
        }
    }

    @Test
    void roleOnTenantBoundUserDoesNotGrantPlatformAuthority() {
        UUID linked = newAdmin(business);
        assertThrows(AccessDeniedException.class,
                () -> as(linked, () -> service.request(request())));
    }

    @Test
    void immutableSqlRequestStateCannotBeRewrittenOrJumpedForward() {
        UUID change=created();
        assertThrows(DataAccessException.class, () -> context.callAsSystem(() ->
                runtime.update("""
                        UPDATE twilio_existing_enrollment_request
                           SET state='APPLIED',approver_id=?,approved_at=now(),executed_at=now()
                         WHERE change_id=?
                        """,adminB,change)));
        assertThrows(DataAccessException.class, () -> context.callAsSystem(() ->
                runtime.update("""
                        UPDATE twilio_existing_enrollment_request
                           SET owner_account_sid=? WHERE change_id=?
                        ""","AC"+"f".repeat(32),change)));
        as(adminB, () -> service.approve(change));
        assertThrows(DataAccessException.class, () -> context.callAsSystem(() ->
                runtime.update("""
                        UPDATE twilio_existing_enrollment_request
                           SET approver_id=? WHERE change_id=?
                        """,adminC,change)));
        assertEquals("APPROVED",state(change));
    }
}

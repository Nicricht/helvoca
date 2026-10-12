package cl.helvoca.phone;

import cl.helvoca.security.TenantDatabaseContext;
import cl.helvoca.telephony.twilio.TwilioCallBindingGuard;
import cl.helvoca.telephony.twilio.TwilioExistingNumberOwnershipVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Full Flyway V102 on disposable real PostgreSQL 16; mocked carrier GET only. */
@Testcontainers
@SpringBootTest
class TwilioExistingEnrollmentReconcilerPostgresIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", pg::getJdbcUrl);
        r.add("spring.datasource.username", pg::getUsername);
        r.add("spring.datasource.password", pg::getPassword);
        r.add("spring.flyway.enabled", () -> "true");
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        r.add("app.seed.enabled", () -> "false");
    }

    @MockitoBean TwilioExistingNumberOwnershipVerifier carrier;
    @Autowired @Qualifier("migrationDataSource") DataSource migrationDataSource;
    @Autowired TenantDatabaseContext scope;
    @Autowired TwilioExistingEnrollmentReconciler reconciler;
    @Autowired JdbcTemplate jdbc;
    @Autowired TwilioCallBindingGuard bindingGuard;

    private static final String ACCOUNT = "AC" + "a".repeat(32);
    private static final String OTHER_ACCOUNT = "AC" + "b".repeat(32);
    private static final AtomicInteger NUMBERS = new AtomicInteger();
    private JdbcTemplate owner;
    private UUID tenantA;
    private UUID tenantB;

    @BeforeEach
    void seed() {
        reset(carrier);
        owner = new JdbcTemplate(migrationDataSource);
        tenantA = business();
        tenantB = business();
    }

    private UUID business() {
        UUID id = UUID.randomUUID();
        owner.update("INSERT INTO business(id, name) VALUES (?, ?)",
                id, "Reconciliation " + id);
        return id;
    }

    private static String e164() {
        return "+1202555" + String.format("%04d", NUMBERS.incrementAndGet());
    }

    private static String pn() {
        return "PN" + UUID.randomUUID().toString().replace("-", "");
    }

    private UUID phone(UUID business, String number, String externalSid, boolean active) {
        UUID id = UUID.randomUUID();
        owner.update("""
                INSERT INTO phone_number(id, business_id, phone_number, provider, external_id, active)
                VALUES (?, ?, ?, 'TWILIO', ?, ?)
                """, id, business, number, externalSid, active);
        return id;
    }

    private TwilioExistingEnrollmentPlanner.Intent intent(UUID business, UUID id, String e164, UUID change) {
        return new TwilioExistingEnrollmentPlanner.Intent(
                business, id, e164, ACCOUNT, change,
                new TwilioExistingEnrollmentPlanner.Actor(
                        UUID.fromString("11111111-1111-4111-8111-111111111111"),
                        TwilioExistingEnrollmentPlanner.Authority.PLATFORM_OPERATOR),
                new TwilioExistingEnrollmentPlanner.Actor(
                        UUID.fromString("22222222-2222-4222-8222-222222222222"),
                        TwilioExistingEnrollmentPlanner.Authority.PLATFORM_APPROVER));
    }

    private TwilioExistingEnrollmentPlanner.Intent intent(UUID business, UUID id, String e164) {
        return intent(business, id, e164, UUID.randomUUID());
    }

    private TwilioExistingEnrollmentReconciler.Decision apply(TwilioExistingEnrollmentPlanner.Intent intent) {
        return scope.callAsSystem(() -> reconciler.reconcile(intent));
    }

    private void verifies(String number, String expectedSid, String carrierSid) {
        when(carrier.verify(eq(number), eq(ACCOUNT), nullable(String.class)))
                .thenReturn(new TwilioExistingNumberOwnershipVerifier.Outcome(
                        TwilioExistingNumberOwnershipVerifier.Code.VERIFIED,
                        new TwilioExistingNumberOwnershipVerifier.Evidence(
                                carrierSid, ACCOUNT,
                                TwilioExistingNumberOwnershipVerifier.AccountKind.MAIN)));
    }

    private String source(UUID id) {
        return owner.queryForObject(
                "SELECT twilio_binding_source FROM phone_number WHERE id = ?", String.class, id);
    }

    private long countClaim(UUID change) {
        return scope.callAsSystem(() -> jdbc.queryForObject(
                "SELECT COUNT(*) FROM twilio_existing_enrollment_claim WHERE change_id = ?",
                Long.class, change));
    }

    @Test
    void flyway102IsSystemOnlyAppendOnlyAndHasForcedRls() {
        assertEquals(1L, owner.queryForObject("""
                SELECT COUNT(*) FROM flyway_schema_history WHERE version = '102' AND success
                """, Long.class));
        assertTrue(Boolean.TRUE.equals(owner.queryForObject("""
                SELECT relrowsecurity AND relforcerowsecurity
                FROM pg_class WHERE oid = 'public.twilio_existing_enrollment_claim'::regclass
                """, Boolean.class)));
        assertEquals(1L, owner.queryForObject("""
                SELECT COUNT(*) FROM pg_constraint
                WHERE conname = 'fk_twilio_existing_enrollment_phone'
                """, Long.class));
        assertThrows(DataAccessException.class, () -> scope.callAsTenant(tenantA, () ->
                jdbc.queryForObject("SELECT COUNT(*) FROM twilio_existing_enrollment_claim", Long.class)));
        assertThrows(DataAccessException.class, () -> scope.callAsTenant(tenantA, () ->
                jdbc.update("DELETE FROM twilio_existing_enrollment_claim")));
        assertThrows(DataAccessException.class, () -> scope.callAsSystem(() ->
                jdbc.update("DELETE FROM twilio_existing_enrollment_claim")));
    }

    @Test
    void exactFreshCarrierProofAtomicallyStoresProvenanceAndDurableClaimWithoutActivating() throws Exception {
        String number = e164();
        String sid = pn();
        UUID id = phone(tenantA, number, null, false);
        var request = intent(tenantA, id, number);
        verifies(number, null, sid);

        assertEquals(TwilioExistingEnrollmentReconciler.Code.APPLIED, apply(request).code());
        verify(carrier).verify(number, ACCOUNT, null);
        assertEquals("VERIFIED_EXISTING", source(id));
        assertEquals(sid, owner.queryForObject(
                "SELECT external_id FROM phone_number WHERE id = ?", String.class, id));
        assertEquals(ACCOUNT, owner.queryForObject(
                "SELECT twilio_account_sid FROM phone_number WHERE id = ?", String.class, id));
        assertNotNull(owner.queryForObject(
                "SELECT voice_verified_at FROM phone_number WHERE id = ?", Timestamp.class, id));
        assertFalse(Boolean.TRUE.equals(owner.queryForObject(
                "SELECT active FROM phone_number WHERE id = ?", Boolean.class, id)));
        assertEquals(1L, countClaim(request.changeId()));
        assertEquals(tenantA, scope.callAsSystem(() -> jdbc.queryForObject(
                "SELECT business_id FROM twilio_existing_enrollment_claim WHERE change_id = ?",
                UUID.class, request.changeId())));
        assertEquals(request.requester().subjectId(), scope.callAsSystem(() -> jdbc.queryForObject(
                "SELECT requester_id FROM twilio_existing_enrollment_claim WHERE change_id = ?",
                UUID.class, request.changeId())));
        assertEquals(request.approver().subjectId(), scope.callAsSystem(() -> jdbc.queryForObject(
                "SELECT approver_id FROM twilio_existing_enrollment_claim WHERE change_id = ?",
                UUID.class, request.changeId())));
        // RLS remains intact and the phase-7 routing policy remains managed-only.
        assertEquals(0L, scope.callAsTenant(tenantB, () ->
                jdbc.queryForObject("SELECT COUNT(*) FROM phone_number WHERE id = ?",
                        Long.class, id)));
        assertFalse(bindingGuard.allowsIncoming("CA" + "a".repeat(32), number,
                "+12025550191", ACCOUNT));

        Method m = TwilioExistingEnrollmentReconciler.class.getDeclaredMethod("claim", UUID.class);
        m.setAccessible(true);
        Object internal = scope.callAsSystem(() -> {
            try { return m.invoke(reconciler, request.changeId()); }
            catch (ReflectiveOperationException ex) { throw new IllegalStateException(ex); }
        });
        assertEquals("ExistingEnrollmentClaim[REDACTED]", internal.toString());
    }

    @Test
    void sameChangeIdIsIdempotentNoSecondCarrierGetOrDbUpdate() {
        String number = e164();
        UUID id = phone(tenantA, number, null, true);
        String sid = pn();
        var request = intent(tenantA, id, number);
        verifies(number, null, sid);

        assertEquals(TwilioExistingEnrollmentReconciler.Code.APPLIED, apply(request).code());
        Instant verified = owner.queryForObject(
                "SELECT voice_verified_at FROM phone_number WHERE id = ?",
                Timestamp.class, id).toInstant();
        assertEquals(TwilioExistingEnrollmentReconciler.Code.ALREADY_APPLIED, apply(request).code());
        assertEquals(1L, countClaim(request.changeId()));
        assertEquals(verified, owner.queryForObject(
                "SELECT voice_verified_at FROM phone_number WHERE id = ?",
                Timestamp.class, id).toInstant());
        verify(carrier, times(1)).verify(number, ACCOUNT, null);
    }

    @Test
    void sameChangeKeyWithDifferentTenantPhoneOrApproversFailsClosed() {
        String number = e164();
        UUID id = phone(tenantA, number, null, true);
        var request = intent(tenantA, id, number);
        verifies(number, null, pn());
        assertEquals(TwilioExistingEnrollmentReconciler.Code.APPLIED, apply(request).code());

        var otherTenant = intent(tenantB, id, number, request.changeId());
        assertEquals(TwilioExistingEnrollmentReconciler.Code.REPLAY_CONFLICT,
                apply(otherTenant).code());
        var otherNumber = intent(tenantA, id, e164(), request.changeId());
        assertEquals(TwilioExistingEnrollmentReconciler.Code.REPLAY_CONFLICT,
                apply(otherNumber).code());
        var otherPhone = intent(tenantA, UUID.randomUUID(), number, request.changeId());
        assertEquals(TwilioExistingEnrollmentReconciler.Code.REPLAY_CONFLICT,
                apply(otherPhone).code());
        var otherAccount = new TwilioExistingEnrollmentPlanner.Intent(
                tenantA, id, number, OTHER_ACCOUNT, request.changeId(),
                request.requester(), request.approver());
        assertEquals(TwilioExistingEnrollmentReconciler.Code.REPLAY_CONFLICT,
                apply(otherAccount).code());
        var otherRequester = new TwilioExistingEnrollmentPlanner.Intent(
                tenantA, id, number, ACCOUNT, request.changeId(),
                new TwilioExistingEnrollmentPlanner.Actor(
                        UUID.randomUUID(), TwilioExistingEnrollmentPlanner.Authority.PLATFORM_OPERATOR),
                request.approver());
        assertEquals(TwilioExistingEnrollmentReconciler.Code.REPLAY_CONFLICT,
                apply(otherRequester).code());
        var otherApprover = new TwilioExistingEnrollmentPlanner.Intent(
                tenantA, id, number, ACCOUNT, request.changeId(),
                request.requester(),
                new TwilioExistingEnrollmentPlanner.Actor(
                        UUID.randomUUID(), TwilioExistingEnrollmentPlanner.Authority.PLATFORM_APPROVER));
        assertEquals(TwilioExistingEnrollmentReconciler.Code.REPLAY_CONFLICT,
                apply(otherApprover).code());
        verify(carrier, times(1)).verify(number, ACCOUNT, null);
    }

    @Test
    void tenantAndUnscopedExecutionAreDeniedBeforeCarrierOrDatabase() {
        String number = e164();
        UUID id = phone(tenantA, number, null, false);
        var request = intent(tenantA, id, number);
        assertEquals(TwilioExistingEnrollmentReconciler.Code.SYSTEM_SCOPE_REQUIRED,
                reconciler.reconcile(request).code());
        assertEquals(TwilioExistingEnrollmentReconciler.Code.SYSTEM_SCOPE_REQUIRED,
                scope.callAsTenant(tenantA, () -> reconciler.reconcile(request)).code());
        verifyNoInteractions(carrier);
        assertNull(source(id));
        assertEquals(0L, countClaim(request.changeId()));
    }

    @Test
    void missingIdentityWrongPlatformRoleOrSameReviewerIsRejectedBeforeCarrier() {
        assertEquals(TwilioExistingEnrollmentReconciler.Code.INVALID_INTENT,
                apply(null).code());
        String number = e164();
        UUID id = phone(tenantA, number, null, false);
        var base = intent(tenantA, id, number);
        var bad = List.of(
                new TwilioExistingEnrollmentPlanner.Intent(
                        null, id, number, ACCOUNT, base.changeId(), base.requester(), base.approver()),
                new TwilioExistingEnrollmentPlanner.Intent(
                        tenantA, null, number, ACCOUNT, base.changeId(), base.requester(), base.approver()),
                new TwilioExistingEnrollmentPlanner.Intent(
                        tenantA, id, number, ACCOUNT, null, base.requester(), base.approver()),
                new TwilioExistingEnrollmentPlanner.Intent(
                        tenantA, id, number, ACCOUNT, base.changeId(), null, base.approver()),
                new TwilioExistingEnrollmentPlanner.Intent(
                        tenantA, id, number, ACCOUNT, base.changeId(), base.requester(), null),
                new TwilioExistingEnrollmentPlanner.Intent(
                        tenantA, id, number, ACCOUNT, base.changeId(),
                        new TwilioExistingEnrollmentPlanner.Actor(
                                null, TwilioExistingEnrollmentPlanner.Authority.PLATFORM_OPERATOR),
                        base.approver()),
                new TwilioExistingEnrollmentPlanner.Intent(
                        tenantA, id, number, ACCOUNT, base.changeId(),
                        base.requester(),
                        new TwilioExistingEnrollmentPlanner.Actor(
                                null, TwilioExistingEnrollmentPlanner.Authority.PLATFORM_APPROVER)),
                new TwilioExistingEnrollmentPlanner.Intent(
                        tenantA, id, number, ACCOUNT, base.changeId(),
                        new TwilioExistingEnrollmentPlanner.Actor(
                                base.requester().subjectId(), TwilioExistingEnrollmentPlanner.Authority.TENANT_ADMIN),
                        base.approver()),
                new TwilioExistingEnrollmentPlanner.Intent(
                        tenantA, id, number, ACCOUNT, base.changeId(),
                        base.requester(), new TwilioExistingEnrollmentPlanner.Actor(
                        base.approver().subjectId(), TwilioExistingEnrollmentPlanner.Authority.TENANT_ADMIN)),
                new TwilioExistingEnrollmentPlanner.Intent(
                        tenantA, id, number, ACCOUNT, base.changeId(),
                        base.requester(), new TwilioExistingEnrollmentPlanner.Actor(
                        base.requester().subjectId(), TwilioExistingEnrollmentPlanner.Authority.PLATFORM_APPROVER))
        );
        for (var invalid : bad) {
            assertEquals(TwilioExistingEnrollmentReconciler.Code.INVALID_INTENT,
                    apply(invalid).code());
        }
        assertEquals("ExistingEnrollment[INVALID_INTENT]",
                apply(null).toString());
        verifyNoInteractions(carrier);
        assertNull(source(id));
    }

    @Test
    void missingPhoneWrongTenantExistingProvenanceAndBadCarrierProofCannotPromote() {
        String number = e164();
        UUID id = phone(tenantA, number, null, true);
        var request = intent(tenantA, id, number);
        assertEquals(TwilioExistingEnrollmentReconciler.Code.NOT_REVIEWABLE,
                apply(intent(tenantA, UUID.randomUUID(), number)).code());
        assertEquals(TwilioExistingEnrollmentPlanner.Code.PHONE_NOT_FOUND,
                apply(intent(tenantA, UUID.randomUUID(), number)).reviewCode());
        assertEquals(TwilioExistingEnrollmentPlanner.Code.TENANT_MISMATCH,
                apply(intent(tenantB, id, number)).reviewCode());
        assertEquals(TwilioExistingEnrollmentPlanner.Code.CARRIER_REJECTED,
                apply(request).reviewCode());
        when(carrier.verify(eq(number), eq(ACCOUNT), isNull())).thenReturn(
                new TwilioExistingNumberOwnershipVerifier.Outcome(
                        TwilioExistingNumberOwnershipVerifier.Code.ACCOUNT_NOT_ALLOWED, null));
        assertEquals(TwilioExistingEnrollmentPlanner.Code.CARRIER_REJECTED,
                apply(request).reviewCode());
        assertNull(source(id));
        assertEquals(0L, countClaim(request.changeId()));
    }

    @Test
    void cannotOverwriteManagedPurchase() {
        String number = e164();
        String sid = pn();
        UUID id = phone(tenantA, number, null, true);
        scope.callAsSystem(() -> jdbc.update("""
                UPDATE phone_number SET external_id = ?, twilio_account_sid = ?,
                twilio_binding_source = 'MANAGED_PURCHASE' WHERE id = ?
                """, sid, ACCOUNT, id));
        assertEquals(TwilioExistingEnrollmentPlanner.Code.EXISTING_PROVENANCE,
                apply(intent(tenantA, id, number)).reviewCode());
        assertEquals("MANAGED_PURCHASE", source(id));
        verifyNoInteractions(carrier);
    }

    @Test
    void staleRowIsRejectedByCompareAndSetAndDoesNotWriteClaim() {
        String number = e164();
        String sid = pn();
        UUID id = phone(tenantA, number, null, true);
        var request = intent(tenantA, id, number);
        when(carrier.verify(eq(number), eq(ACCOUNT), isNull())).thenAnswer(inv -> {
            // Simulate an independent write after the snapshot and BEFORE the CAS.
            scope.callAsSystem(() -> jdbc.update(
                    "UPDATE phone_number SET active = FALSE, updated_at = CURRENT_TIMESTAMP + INTERVAL '1 second' WHERE id = ?", id));
            return new TwilioExistingNumberOwnershipVerifier.Outcome(
                    TwilioExistingNumberOwnershipVerifier.Code.VERIFIED,
                    new TwilioExistingNumberOwnershipVerifier.Evidence(
                            sid, ACCOUNT, TwilioExistingNumberOwnershipVerifier.AccountKind.MAIN));
        });
        assertEquals(TwilioExistingEnrollmentReconciler.Code.ROW_CONFLICT,
                apply(request).code());
        assertNull(source(id));
        assertEquals(0L, countClaim(request.changeId()));
        assertFalse(Boolean.TRUE.equals(owner.queryForObject(
                "SELECT active FROM phone_number WHERE id = ?", Boolean.class, id)));
    }

    @Test
    void uniqueCarrierSidAcrossTenantsRollsBackPhoneAndClaimTogether() {
        String firstNumber = e164();
        String secondNumber = e164();
        String sharedSid = pn();
        UUID idA = phone(tenantA, firstNumber, null, true);
        UUID idB = phone(tenantB, secondNumber, null, true);
        var first = intent(tenantA, idA, firstNumber);
        var second = intent(tenantB, idB, secondNumber);
        verifies(firstNumber, null, sharedSid);
        verifies(secondNumber, null, sharedSid);
        assertEquals(TwilioExistingEnrollmentReconciler.Code.APPLIED, apply(first).code());
        assertEquals(TwilioExistingEnrollmentReconciler.Code.STORE_CONFLICT,
                apply(second).code());
        assertNull(source(idB));
        assertNull(owner.queryForObject(
                "SELECT external_id FROM phone_number WHERE id = ?", String.class, idB));
        assertEquals(0L, countClaim(second.changeId()));
        assertEquals(1L, countClaim(first.changeId()));
    }

    @Test
    void replayFailsClosedWhenStoredBindingIsAlteredAfterSuccess() {
        String number = e164();
        String sid = pn();
        UUID id = phone(tenantA, number, null, false);
        var request = intent(tenantA, id, number);
        verifies(number, null, sid);
        assertEquals(TwilioExistingEnrollmentReconciler.Code.APPLIED, apply(request).code());
        scope.callAsSystem(() -> jdbc.update(
                "UPDATE phone_number SET external_id = ? WHERE id = ?", pn(), id));
        assertEquals(TwilioExistingEnrollmentReconciler.Code.ROW_CONFLICT,
                apply(request).code());
        assertEquals(1L, countClaim(request.changeId()));
    }

    @Test
    void sameIdTwoSimultaneousTransactionsHaveExactlyOneWrite() throws Exception {
        String number = e164();
        String sid = pn();
        UUID id = phone(tenantA, number, null, false);
        var request = intent(tenantA, id, number);
        CountDownLatch bothAtCarrier = new CountDownLatch(2);
        when(carrier.verify(eq(number), eq(ACCOUNT), isNull())).thenAnswer(inv -> {
            bothAtCarrier.countDown();
            assertTrue(bothAtCarrier.await(15, TimeUnit.SECONDS));
            return new TwilioExistingNumberOwnershipVerifier.Outcome(
                    TwilioExistingNumberOwnershipVerifier.Code.VERIFIED,
                    new TwilioExistingNumberOwnershipVerifier.Evidence(
                            sid, ACCOUNT, TwilioExistingNumberOwnershipVerifier.AccountKind.MAIN));
        });
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<TwilioExistingEnrollmentReconciler.Code> first =
                    pool.submit(() -> apply(request).code());
            Future<TwilioExistingEnrollmentReconciler.Code> second =
                    pool.submit(() -> apply(request).code());
            var results = List.of(first.get(35, TimeUnit.SECONDS), second.get(35, TimeUnit.SECONDS));
            assertTrue(results.contains(TwilioExistingEnrollmentReconciler.Code.APPLIED));
            assertTrue(results.contains(TwilioExistingEnrollmentReconciler.Code.ALREADY_APPLIED));
            assertEquals(1L, countClaim(request.changeId()));
            assertEquals("VERIFIED_EXISTING", source(id));
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        }
    }

    @Test
    void inFlightDuplicateClaimIsRecheckedInsideTransactionBeforeAnyWrite() {
        String number = e164();
        String sid = pn();
        UUID id = phone(tenantA, number, null, false);
        var request = intent(tenantA, id, number);
        java.util.concurrent.atomic.AtomicBoolean once = new java.util.concurrent.atomic.AtomicBoolean(true);
        when(carrier.verify(eq(number), eq(ACCOUNT), isNull())).thenAnswer(inv -> {
            if (once.getAndSet(false)) {
                // Another successful actor commits after the outer preview's DB
                // snapshot but before its transaction. No second write is allowed.
                assertEquals(TwilioExistingEnrollmentReconciler.Code.APPLIED,
                        apply(request).code());
            }
            return new TwilioExistingNumberOwnershipVerifier.Outcome(
                    TwilioExistingNumberOwnershipVerifier.Code.VERIFIED,
                    new TwilioExistingNumberOwnershipVerifier.Evidence(
                            sid, ACCOUNT, TwilioExistingNumberOwnershipVerifier.AccountKind.MAIN));
        });
        assertEquals(TwilioExistingEnrollmentReconciler.Code.ALREADY_APPLIED,
                apply(request).code());
        assertEquals(1L, countClaim(request.changeId()));
        assertEquals("VERIFIED_EXISTING", source(id));
        verify(carrier, times(2)).verify(number, ACCOUNT, null);
    }

    @Test
    void twoDifferentChangeIdsCompetingForOneRowNeverOverwriteEachOther() throws Exception {
        String number = e164();
        String sid = pn();
        UUID id = phone(tenantA, number, null, false);
        var first = intent(tenantA, id, number);
        var second = intent(tenantA, id, number);
        CountDownLatch bothAtCarrier = new CountDownLatch(2);
        when(carrier.verify(eq(number), eq(ACCOUNT), isNull())).thenAnswer(inv -> {
            bothAtCarrier.countDown();
            assertTrue(bothAtCarrier.await(15, TimeUnit.SECONDS));
            return new TwilioExistingNumberOwnershipVerifier.Outcome(
                    TwilioExistingNumberOwnershipVerifier.Code.VERIFIED,
                    new TwilioExistingNumberOwnershipVerifier.Evidence(
                            sid, ACCOUNT, TwilioExistingNumberOwnershipVerifier.AccountKind.MAIN));
        });
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<TwilioExistingEnrollmentReconciler.Code> a = pool.submit(() -> apply(first).code());
            Future<TwilioExistingEnrollmentReconciler.Code> b = pool.submit(() -> apply(second).code());
            var codes = List.of(a.get(35, TimeUnit.SECONDS), b.get(35, TimeUnit.SECONDS));
            assertTrue(codes.contains(TwilioExistingEnrollmentReconciler.Code.APPLIED));
            assertTrue(codes.contains(TwilioExistingEnrollmentReconciler.Code.ROW_CONFLICT));
            assertEquals(1L, countClaim(first.changeId()) + countClaim(second.changeId()));
            assertEquals("VERIFIED_EXISTING", source(id));
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        }
    }
}

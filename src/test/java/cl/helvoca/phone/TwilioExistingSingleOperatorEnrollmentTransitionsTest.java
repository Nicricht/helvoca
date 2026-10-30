package cl.helvoca.phone;

import cl.helvoca.telephony.twilio.TwilioExistingNumberOwnershipVerifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TwilioExistingSingleOperatorEnrollmentTransitionsTest {
    private static final UUID BUSINESS = UUID.fromString("551214ca-887b-4c49-928e-7dd2f3629cc0");
    private static final UUID PHONE_ID = UUID.fromString("5417fcbf-72b7-407a-878a-4d11da8819da");
    private static final UUID ACTOR = UUID.fromString("551214ca-887b-4c49-928e-7dd2f3629cc1");
    private static final UUID CHANGE = UUID.fromString("551214ca-887b-4c49-928e-7dd2f3629cc2");
    private static final String NUMBER = "+16812706780";
    private static final String ACCOUNT = "AC" + "a".repeat(32);
    private static final String PHONE_SID = "PN" + "b".repeat(32);

    private JdbcTemplate jdbc;
    private TwilioExistingEnrollmentAdminService admin;
    private TwilioExistingNumberOwnershipVerifier carrier;
    private PasswordEncoder passwords;
    private PlatformTransactionManager manager;
    private TwilioExistingSingleOperatorEnrollmentService service;
    private AtomicReference<List<String>> inventory;
    private AtomicReference<List<String>> lockedInventory;
    private AtomicReference<Long> outstanding;
    private AtomicReference<TwilioExistingNumberOwnershipVerifier.Outcome> outcome;
    private AtomicReference<UUID> rowActor;
    private AtomicReference<UUID> rowBusiness;
    private AtomicReference<String> rowNumber;
    private AtomicReference<String> rowState;
    private AtomicReference<Instant> rowExpiry;
    private AtomicInteger failedAttempts;
    private AtomicInteger reads;
    private AtomicInteger phoneUpdated;
    private AtomicInteger ledgerUpdated;

    @BeforeEach
    void configure() {
        jdbc = mock(JdbcTemplate.class);
        admin = mock(TwilioExistingEnrollmentAdminService.class);
        carrier = mock(TwilioExistingNumberOwnershipVerifier.class);
        passwords = mock(PasswordEncoder.class);
        manager = mock(PlatformTransactionManager.class);
        when(admin.authenticatedPlatformActor()).thenReturn(ACTOR);
        when(manager.getTransaction(any(TransactionDefinition.class)))
                .thenAnswer(inv -> new SimpleTransactionStatus());
        service = new TwilioExistingSingleOperatorEnrollmentService(
                jdbc, admin, carrier, passwords, manager,
                true, "demo-isolated", BUSINESS.toString(), NUMBER);
        inventory = new AtomicReference<>(List.of(NUMBER));
        lockedInventory = new AtomicReference<>(List.of(NUMBER));
        outstanding = new AtomicReference<>(0L);
        outcome = new AtomicReference<>(verified(ACCOUNT));
        rowActor = new AtomicReference<>(ACTOR);
        rowBusiness = new AtomicReference<>(BUSINESS);
        rowNumber = new AtomicReference<>(NUMBER);
        rowState = new AtomicReference<>("PENDING");
        rowExpiry = new AtomicReference<>(Instant.now().plusSeconds(500));
        failedAttempts = new AtomicInteger();
        reads = new AtomicInteger();
        phoneUpdated = new AtomicInteger(1);
        ledgerUpdated = new AtomicInteger(1);
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<String>>any(),
                eq(PHONE_ID), eq(BUSINESS))).thenAnswer(inv -> {
            String sql = inv.getArgument(0);
            if (sql.contains("JOIN business")) return lockedInventory.get();
            return inventory.get();
        });
        when(jdbc.queryForObject(contains("SELECT COUNT(*)"), eq(Long.class), eq(PHONE_ID)))
                .thenAnswer(inv -> outstanding.get());
        when(jdbc.queryForObject(contains("SELECT password_hash"), eq(String.class), eq(ACTOR)))
                .thenReturn("stored-password-hash");
        when(passwords.matches(anyString(), eq("stored-password-hash"))).thenReturn(true);
        when(carrier.verify(eq(NUMBER), eq(ACCOUNT), isNull()))
                .thenAnswer(inv -> outcome.get());
        when(jdbc.query(contains("FROM twilio_single_operator_demo_enrollment"),
                org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), eq(CHANGE)))
                .thenAnswer(inv -> {
                    reads.incrementAndGet();
                    RowMapper<?> mapper = inv.getArgument(1);
                    return List.of(mapper.mapRow(pendingRow(), 0));
                });
        when(jdbc.update(contains("UPDATE phone_number"), any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> phoneUpdated.get());
        when(jdbc.update(contains("UPDATE twilio_single_operator_demo_enrollment"),
                any(), any())).thenAnswer(inv -> ledgerUpdated.get());
        freshJwt();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void freshJwt() {
        Jwt jwt = Jwt.withTokenValue("test-only")
                .header("alg", "HS256").subject(ACTOR.toString())
                .issuedAt(Instant.now().minusSeconds(30))
                .expiresAt(Instant.now().plusSeconds(600))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private TwilioExistingNumberOwnershipVerifier.Outcome verified(String account) {
        return new TwilioExistingNumberOwnershipVerifier.Outcome(
                TwilioExistingNumberOwnershipVerifier.Code.VERIFIED,
                new TwilioExistingNumberOwnershipVerifier.Evidence(PHONE_SID, account,
                        TwilioExistingNumberOwnershipVerifier.AccountKind.MAIN));
    }

    private ResultSet pendingRow() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject("change_id", UUID.class)).thenReturn(CHANGE);
        when(rs.getObject("business_id", UUID.class)).thenReturn(rowBusiness.get());
        when(rs.getObject("phone_number_id", UUID.class)).thenReturn(PHONE_ID);
        when(rs.getString("destination_e164")).thenReturn(rowNumber.get());
        when(rs.getString("owner_account_sid")).thenReturn(ACCOUNT);
        when(rs.getObject("actor_id", UUID.class)).thenReturn(rowActor.get());
        when(rs.getString("state")).thenReturn(rowState.get());
        when(rs.getTimestamp("expires_at")).thenReturn(Timestamp.from(rowExpiry.get()));
        when(rs.getInt("failed_attempts")).thenReturn(failedAttempts.get());
        return rs;
    }

    private void conflict(Runnable operation) {
        assertEquals(HttpStatus.CONFLICT,
                assertThrows(ResponseStatusException.class, operation::run).getStatusCode());
    }

    private TwilioExistingSingleOperatorEnrollmentService.Request request() {
        return new TwilioExistingSingleOperatorEnrollmentService.Request(BUSINESS, PHONE_ID, ACCOUNT);
    }

    private TwilioExistingSingleOperatorEnrollmentService.Confirm confirmation() {
        return new TwilioExistingSingleOperatorEnrollmentService.Confirm("valid-password", NUMBER);
    }

    @Test
    void requestRejectsMissingInventoryAndWrongPinnedPhone() {
        inventory.set(List.of());
        conflict(() -> service.request(request()));
        inventory.set(List.of("+12025550111"));
        conflict(() -> service.request(request()));
        verifyNoInteractions(carrier);
    }

    @Test
    void requestRejectsNullCarrierFailureAndAccountMismatch() {
        outcome.set(null);
        conflict(() -> service.request(request()));
        outcome.set(new TwilioExistingNumberOwnershipVerifier.Outcome(
                TwilioExistingNumberOwnershipVerifier.Code.WEBHOOK_MISMATCH, null));
        conflict(() -> service.request(request()));
        outcome.set(verified("AC" + "c".repeat(32)));
        conflict(() -> service.request(request()));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void requestRejectsInventoryChangingUnderLock() {
        lockedInventory.set(List.of());
        conflict(() -> service.request(request()));
        lockedInventory.set(List.of("+12025550111"));
        conflict(() -> service.request(request()));
    }

    @Test
    void requestRejectsDuplicatedOrUnknownCountBeforeInserting() {
        outstanding.set(1L);
        conflict(() -> service.request(request()));
        outstanding.set(null);
        conflict(() -> service.request(request()));
    }

    @Test
    void requestCreatesOnlyPendingEvidenceOnValidProof() {
        var result = service.request(request());
        assertEquals("PENDING", result.state());
        verify(jdbc).update(contains("INSERT INTO twilio_single_operator_demo_enrollment"),
                any(), eq(BUSINESS), eq(PHONE_ID), eq(NUMBER), eq(ACCOUNT), eq(ACTOR));
        verify(jdbc).update(contains("INSERT INTO twilio_single_operator_demo_audit"),
                any(), eq(result.changeId()), eq(ACTOR), eq("REQUESTED"));
    }

    @Test
    void confirmationFailsWhenRequestRecordMissing() {
        when(jdbc.query(contains("FROM twilio_single_operator_demo_enrollment"),
                org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), eq(CHANGE)))
                .thenReturn(List.of());
        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ResponseStatusException.class,
                        () -> service.confirm(CHANGE, confirmation())).getStatusCode());
    }

    @Test
    void confirmationRejectsWrongActorTenantAndDestination() {
        rowActor.set(UUID.randomUUID());
        assertThrows(AccessDeniedException.class, () -> service.confirm(CHANGE, confirmation()));
        rowActor.set(ACTOR);
        rowBusiness.set(UUID.randomUUID());
        assertThrows(AccessDeniedException.class, () -> service.confirm(CHANGE, confirmation()));
        rowBusiness.set(BUSINESS);
        rowNumber.set("+12025550111");
        assertThrows(AccessDeniedException.class, () -> service.confirm(CHANGE, confirmation()));
        verify(carrier, never()).verify(anyString(), anyString(), any());
    }

    @Test
    void confirmationRejectsExpiredLockedAndConsumedRequests() {
        rowState.set("APPLIED");
        conflict(() -> service.confirm(CHANGE, confirmation()));
        rowState.set("PENDING");
        failedAttempts.set(3);
        conflict(() -> service.confirm(CHANGE, confirmation()));
        failedAttempts.set(0);
        rowExpiry.set(Instant.now().minusSeconds(1));
        conflict(() -> service.confirm(CHANGE, confirmation()));
    }

    @Test
    void confirmationRejectsNullPasswordHashAndExplicitFailure() {
        when(jdbc.queryForObject(contains("SELECT password_hash"), eq(String.class), eq(ACTOR)))
                .thenReturn(null);
        assertThrows(AccessDeniedException.class, () -> service.confirm(CHANGE, confirmation()));
        when(jdbc.queryForObject(contains("SELECT password_hash"), eq(String.class), eq(ACTOR)))
                .thenReturn("stored-password-hash");
        when(passwords.matches(anyString(), eq("stored-password-hash"))).thenReturn(false);
        assertThrows(AccessDeniedException.class, () -> service.confirm(CHANGE, confirmation()));
    }

    @Test
    void confirmationRejectsCarrierFailureOrIdentityMismatch() {
        outcome.set(null);
        conflict(() -> service.confirm(CHANGE, confirmation()));
        outcome.set(new TwilioExistingNumberOwnershipVerifier.Outcome(
                TwilioExistingNumberOwnershipVerifier.Code.WEBHOOK_MISMATCH, null));
        conflict(() -> service.confirm(CHANGE, confirmation()));
        outcome.set(verified("AC" + "c".repeat(32)));
        conflict(() -> service.confirm(CHANGE, confirmation()));
        verify(jdbc, never()).update(contains("UPDATE phone_number"), any(), any(), any(),
                any(), any(), any());
    }

    @Test
    void confirmationRejectsConcurrentChangeBetweenProofs() {
        when(jdbc.query(contains("FROM twilio_single_operator_demo_enrollment"),
                org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), eq(CHANGE)))
                .thenAnswer(inv -> {
                    RowMapper<?> mapper = inv.getArgument(1);
                    if (reads.incrementAndGet() > 1) failedAttempts.set(1);
                    return List.of(mapper.mapRow(pendingRow(), 0));
                });
        conflict(() -> service.confirm(CHANGE, confirmation()));
    }

    @Test
    void confirmationRejectsFailedPhoneAndAttestationCompareAndSet() {
        phoneUpdated.set(0);
        conflict(() -> service.confirm(CHANGE, confirmation()));
        phoneUpdated.set(1);
        ledgerUpdated.set(0);
        conflict(() -> service.confirm(CHANGE, confirmation()));
    }

    @Test
    void statusReportsAbsentRequestAndPresentRequest() {
        when(jdbc.query(contains("SELECT state FROM twilio_single_operator_demo_enrollment"),
                org.mockito.ArgumentMatchers.<RowMapper<String>>any(), eq(CHANGE), eq(ACTOR),
                eq(BUSINESS.toString()))).thenReturn(List.of());
        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ResponseStatusException.class, () -> service.status(CHANGE)).getStatusCode());
        when(jdbc.query(contains("SELECT state FROM twilio_single_operator_demo_enrollment"),
                org.mockito.ArgumentMatchers.<RowMapper<String>>any(), eq(CHANGE), eq(ACTOR),
                eq(BUSINESS.toString()))).thenReturn(List.of("PENDING"));
        assertEquals("PENDING", service.status(CHANGE).state());
    }
}

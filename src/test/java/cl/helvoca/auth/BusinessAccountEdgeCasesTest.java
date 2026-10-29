package cl.helvoca.auth;

import cl.helvoca.security.TenantDatabaseContext;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Negative outcomes that are difficult to trigger through a valid FK-backed HTTP request. */
class BusinessAccountEdgeCasesTest {
    private static final UUID BUSINESS = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final String TOKEN = "a".repeat(43);

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final TenantDatabaseContext context = new TenantDatabaseContext();
    private final BusinessAccountTokenService tokens = mock(BusinessAccountTokenService.class);
    private final BusinessAccountMailService mail = mock(BusinessAccountMailService.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);

    private BusinessAccountRecoveryService recovery() {
        return new BusinessAccountRecoveryService(jdbc, context, tokens, mail, passwords);
    }

    private BusinessAccountVerificationService verification() {
        return new BusinessAccountVerificationService(jdbc, context, tokens, mail);
    }

    @Test
    void recoveryRequestTreatsNullBlankAndDisabledEmailsIdentically() {
        recovery().requestReset("unknown@example.test");
        when(mail.isEnabled()).thenReturn(true);
        recovery().requestReset(null);
        recovery().requestReset(" ");
        verifyNoInteractions(jdbc, tokens);
    }

    @Test
    void recoveryRejectsAllPasswordLengthBoundsBeforeTouchingToken() {
        for (String password : new String[] { "short", "x".repeat(73) }) {
            ResponseStatusException invalid = assertThrows(ResponseStatusException.class,
                    () -> recovery().confirmReset(TOKEN, password));
            assertEquals(HttpStatus.BAD_REQUEST, invalid.getStatusCode());
        }
        assertThrows(ResponseStatusException.class, () -> recovery().confirmReset(TOKEN, null));
        verifyNoInteractions(jdbc, tokens, passwords);
    }

    @Test
    void recoveryRefusesToConsumeIfTargetUserHasBecomeInactive() {
        doAnswer(invocation -> {
            Consumer<BusinessAccountTokenService.Claim> mutation = invocation.getArgument(2);
            mutation.accept(new BusinessAccountTokenService.Claim(BUSINESS, USER));
            return null;
        }).when(tokens).redeem(eq(TOKEN), eq(BusinessAccountTokenService.Purpose.RESET), any());
        when(passwords.encode("correct-length-password")).thenReturn("bcrypt");
        ResponseStatusException invalid = assertThrows(ResponseStatusException.class,
                () -> recovery().confirmReset(TOKEN, "correct-length-password"));
        assertEquals(HttpStatus.BAD_REQUEST, invalid.getStatusCode());
        verify(jdbc).update(contains("UPDATE app_user SET password_hash"), eq("bcrypt"), eq(USER), eq(BUSINESS));
    }

    @Test
    void verificationCannotMistakeMissingScopeOrNullDatabaseAnswerForSuccess() {
        var service = verification();
        assertFalse(service.isVerified(null, USER));
        assertFalse(service.isVerified(BUSINESS, null));
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(USER), eq(BUSINESS))).thenReturn(null);
        assertFalse(service.isVerified(BUSINESS, USER));
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(USER), eq(BUSINESS)))
                .thenReturn(Boolean.TRUE);
        assertTrue(service.isVerified(BUSINESS, USER));
    }

    @Test
    void trustedPossessionRequiresBothIdsAndExistingEligibleUser() {
        var service = verification();
        assertThrows(IllegalArgumentException.class, () -> service.markTrustedPossession(null, USER));
        assertThrows(IllegalArgumentException.class, () -> service.markTrustedPossession(BUSINESS, null));
        assertThrows(IllegalStateException.class, () -> service.markTrustedPossession(BUSINESS, USER));
        when(jdbc.update(anyString(), eq(USER), eq(BUSINESS))).thenReturn(1);
        assertDoesNotThrow(() -> service.markTrustedPossession(BUSINESS, USER));
    }

    @Test
    void verificationEmailRequiresCommittedSignupAndSurvivesProviderRejection() {
        var service = verification();
        service.scheduleRegistrationMessage(BUSINESS, USER, "owner@example.test");
        when(mail.isVerificationRequired()).thenReturn(true);
        assertThrows(IllegalStateException.class,
                () -> service.scheduleRegistrationMessage(BUSINESS, USER, "owner@example.test"));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThrows(IllegalStateException.class,
                    () -> service.scheduleRegistrationMessage(BUSINESS, USER, "owner@example.test"));
            TransactionSynchronizationManager.initSynchronization();
            when(tokens.issue(BUSINESS, USER, BusinessAccountTokenService.Purpose.VERIFY))
                    .thenReturn(new BusinessAccountTokenService.Issued(TOKEN, Instant.now().plusSeconds(3600)));
            doThrow(new IllegalStateException("SMTP outage")).when(mail).sendVerification("owner@example.test", TOKEN);
            service.scheduleRegistrationMessage(BUSINESS, USER, "owner@example.test");
            List<TransactionSynchronization> callbacks = TransactionSynchronizationManager.getSynchronizations();
            assertEquals(1, callbacks.size());
            assertDoesNotThrow(callbacks.getFirst()::afterCommit);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void resendSuppressesEveryUnqualifiedIdentityAndHandlesMailFailure() throws Exception {
        var service = verification();
        service.resend("person@example.test");
        when(mail.isEnabled()).thenReturn(true);
        service.resend("person@example.test");
        when(mail.isVerificationRequired()).thenReturn(true);
        service.resend(null);
        service.resend(" ");
        when(jdbc.query(anyString(), any(RowMapper.class), eq("person@example.test")))
                .thenReturn(List.of());
        service.resend("person@example.test");
        when(jdbc.query(anyString(), any(RowMapper.class), eq("person@example.test")))
                .thenReturn(List.of("ambiguous", "ambiguous"));
        service.resend("person@example.test");
        verifyNoInteractions(tokens);

        reset(jdbc);
        ResultSet row = mock(ResultSet.class);
        when(row.getObject(1, UUID.class)).thenReturn(BUSINESS);
        when(row.getObject(2, UUID.class)).thenReturn(USER);
        when(jdbc.query(anyString(), any(RowMapper.class), eq("person@example.test")))
                .thenAnswer(invocation -> {
                    RowMapper mapper = invocation.getArgument(1);
                    return List.of(mapper.mapRow(row, 0));
                });
        when(tokens.issue(BUSINESS, USER, BusinessAccountTokenService.Purpose.VERIFY))
                .thenReturn(new BusinessAccountTokenService.Issued(TOKEN, Instant.now().plusSeconds(3600)));
        doThrow(new IllegalStateException("SMTP outage")).when(mail).sendVerification("person@example.test", TOKEN);
        assertDoesNotThrow(() -> service.resend(" PERSON@EXAMPLE.TEST "));
        verify(tokens).issue(BUSINESS, USER, BusinessAccountTokenService.Purpose.VERIFY);
    }

    @Test
    void confirmationRefusesASecondMutationAfterAccountWasAlreadyVerified() {
        doAnswer(invocation -> {
            Consumer<BusinessAccountTokenService.Claim> mutation = invocation.getArgument(2);
            mutation.accept(new BusinessAccountTokenService.Claim(BUSINESS, USER));
            return null;
        }).when(tokens).redeem(eq(TOKEN), eq(BusinessAccountTokenService.Purpose.VERIFY), any());
        ResponseStatusException invalid = assertThrows(ResponseStatusException.class,
                () -> verification().confirm(TOKEN));
        assertEquals(HttpStatus.BAD_REQUEST, invalid.getStatusCode());
        verify(jdbc).update(contains("email_verified_at=clock_timestamp()"), eq(USER), eq(BUSINESS));
    }
}

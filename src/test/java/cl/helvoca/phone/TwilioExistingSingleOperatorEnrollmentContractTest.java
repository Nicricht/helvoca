package cl.helvoca.phone;

import cl.helvoca.telephony.twilio.TwilioExistingNumberOwnershipVerifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TwilioExistingSingleOperatorEnrollmentContractTest {
    private static final UUID BUSINESS = UUID.fromString("551214ca-887b-4c49-928e-7dd2f3629cc0");
    private static final UUID PHONE_ID = UUID.fromString("5417fcbf-72b7-407a-878a-4d11da8819da");
    private static final UUID ACTOR = UUID.fromString("c2c4d9d1-54a6-4b30-95e1-3a4e4c6902f1");
    private static final String NUMBER = "+16812706780";
    private static final String ACCOUNT = "AC" + "a".repeat(32);

    private JdbcTemplate jdbc;
    private TwilioExistingEnrollmentAdminService admin;
    private TwilioExistingNumberOwnershipVerifier carrier;
    private PasswordEncoder encoder;
    private PlatformTransactionManager transaction;

    @BeforeEach
    void createMocks() {
        jdbc = mock(JdbcTemplate.class);
        admin = mock(TwilioExistingEnrollmentAdminService.class);
        carrier = mock(TwilioExistingNumberOwnershipVerifier.class);
        encoder = mock(PasswordEncoder.class);
        transaction = mock(PlatformTransactionManager.class);
        when(admin.authenticatedPlatformActor()).thenReturn(ACTOR);
    }

    @AfterEach
    void clearJwt() {
        SecurityContextHolder.clearContext();
    }

    private TwilioExistingSingleOperatorEnrollmentService service(
            boolean enabled, String env, String business, String phone) {
        return new TwilioExistingSingleOperatorEnrollmentService(
                jdbc, admin, carrier, encoder, transaction,
                enabled, env, business, phone);
    }

    private TwilioExistingSingleOperatorEnrollmentService live() {
        return service(true, "demo-isolated", BUSINESS.toString(), NUMBER);
    }

    private void freshJwt(Instant issued) {
        Jwt.Builder builder = Jwt.withTokenValue("only-for-local-test")
                .header("alg", "HS256").subject(ACTOR.toString())
                .expiresAt(Instant.now().plusSeconds(600));
        if (issued != null) builder.issuedAt(issued);
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(builder.build()));
    }

    private static void conflict(Runnable action) {
        assertEquals(HttpStatus.CONFLICT,
                assertThrows(ResponseStatusException.class, action::run).getStatusCode());
    }

    @Test
    void demoGateRejectsEveryAbsentOrIncorrectEnvironmentPin() {
        var valid = new TwilioExistingSingleOperatorEnrollmentService.Request(BUSINESS, PHONE_ID, ACCOUNT);
        var cases = List.of(
                service(false, "demo-isolated", BUSINESS.toString(), NUMBER),
                service(true, "production", BUSINESS.toString(), NUMBER),
                service(true, "demo-isolated", "", NUMBER),
                service(true, "demo-isolated", BUSINESS.toString(), ""),
                service(true, "demo-isolated", BUSINESS.toString(), "incorrect"));
        for (var candidate : cases) conflict(() -> candidate.request(valid));
        conflict(() -> service(true, "demo-isolated", null, NUMBER).request(valid));
        conflict(() -> service(true, "demo-isolated", BUSINESS.toString(), null).request(valid));
        verifyNoInteractions(jdbc, carrier);
    }

    @Test
    void invalidRequestFieldsFailBeforeAnyDatabaseOrCarrierAccess() {
        var candidate = live();
        var invalid = java.util.Arrays.asList(
                null,
                new TwilioExistingSingleOperatorEnrollmentService.Request(null, PHONE_ID, ACCOUNT),
                new TwilioExistingSingleOperatorEnrollmentService.Request(BUSINESS, null, ACCOUNT),
                new TwilioExistingSingleOperatorEnrollmentService.Request(UUID.randomUUID(), PHONE_ID, ACCOUNT),
                new TwilioExistingSingleOperatorEnrollmentService.Request(BUSINESS, PHONE_ID, null),
                new TwilioExistingSingleOperatorEnrollmentService.Request(BUSINESS, PHONE_ID, "AC-invalid"));
        for (var value : invalid) conflict(() -> candidate.request(value));
        verifyNoInteractions(jdbc, carrier);
    }

    @Test
    void confirmationRequiresAuthenticatedRecentJwt() {
        var candidate = live();
        var confirmation = new TwilioExistingSingleOperatorEnrollmentService.Confirm("current-password", NUMBER);
        UUID id = UUID.randomUUID();
        assertThrows(AccessDeniedException.class, () -> candidate.confirm(id, confirmation));

        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("other-principal", "not-a-jwt"));
        assertThrows(AccessDeniedException.class, () -> candidate.confirm(id, confirmation));

        freshJwt(null);
        assertThrows(AccessDeniedException.class, () -> candidate.confirm(id, confirmation));

        freshJwt(Instant.now().minusSeconds(301));
        assertThrows(AccessDeniedException.class, () -> candidate.confirm(id, confirmation));

        freshJwt(Instant.now().plusSeconds(31));
        assertThrows(AccessDeniedException.class, () -> candidate.confirm(id, confirmation));
        verifyNoInteractions(jdbc, carrier);
    }

    @Test
    void invalidConfirmationNeverDisclosesOrContactsCarrier() {
        freshJwt(Instant.now().minusSeconds(20));
        UUID id = UUID.randomUUID();
        var candidate = live();
        var invalid = java.util.Arrays.asList(
                null,
                new TwilioExistingSingleOperatorEnrollmentService.Confirm(null, NUMBER),
                new TwilioExistingSingleOperatorEnrollmentService.Confirm(" ", NUMBER),
                new TwilioExistingSingleOperatorEnrollmentService.Confirm("correct", null),
                new TwilioExistingSingleOperatorEnrollmentService.Confirm("correct", "+12025550111"));
        conflict(() -> candidate.confirm(null, new TwilioExistingSingleOperatorEnrollmentService.Confirm("correct", NUMBER)));
        for (var value : invalid) conflict(() -> candidate.confirm(id, value));
        assertEquals("SingleOperatorConfirm[REDACTED]",
                new TwilioExistingSingleOperatorEnrollmentService.Confirm("private-secret", NUMBER).toString());
        verifyNoInteractions(jdbc, carrier);
    }

    @Test
    void invalidStatusIdIsRejectedWithoutDatabaseAccess() {
        var candidate = live();
        conflict(() -> candidate.status(null));
        verifyNoInteractions(jdbc, carrier);
    }
}

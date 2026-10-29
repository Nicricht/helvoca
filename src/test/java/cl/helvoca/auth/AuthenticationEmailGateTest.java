package cl.helvoca.auth;

import cl.helvoca.business.Business;
import cl.helvoca.security.JwtProperties;
import cl.helvoca.user.AppUser;
import cl.helvoca.user.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthenticationEmailGateTest {
    private final AuthenticationManager manager = mock(AuthenticationManager.class);
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final JwtEncoder encoder = mock(JwtEncoder.class);
    private final AuthService auth = new AuthService(
            manager, users, encoder, new JwtProperties("helvoca-tests", "test-secret", 60));

    @Test
    void validAuthenticationCannotFindDeletedAccountAndMustNotIssueJwt() {
        assertThrows(IllegalArgumentException.class,
                () -> auth.login(new LoginRequest("gone@example.test", "password-123")));
        verifyNoInteractions(encoder);
    }

    @Test
    void unverifiedTenantCannotGetJwtEvenWhenPasswordAuthenticationSucceeded() {
        AppUser user = account(true);
        when(users.findByEmailIgnoreCase("owner@example.test")).thenReturn(Optional.of(user));
        BusinessAccountVerificationService verification = mock(BusinessAccountVerificationService.class);
        when(verification.isRequired()).thenReturn(true);
        auth.setAccountVerification(verification);

        assertThrows(BadCredentialsException.class,
                () -> auth.login(new LoginRequest("owner@example.test", "password-123")));
        verify(verification).isVerified(user.getBusiness().getId(), user.getId());
        verifyNoInteractions(encoder);
    }

    @Test
    void disabledEmailFeatureDoesNotRequireVerificationToIssueJwt() {
        AppUser user = account(true);
        when(users.findByEmailIgnoreCase("owner@example.test")).thenReturn(Optional.of(user));
        BusinessAccountVerificationService verification = mock(BusinessAccountVerificationService.class);
        auth.setAccountVerification(verification);
        stubJwt();
        assertEquals("test-jwt", auth.login(
                new LoginRequest("owner@example.test", "password-123")).accessToken());
        verify(verification, never()).isVerified(any(), any());
    }

    @Test
    void legacyLoginWithoutEmailIntegrationAndPlatformAccountBothRemainFunctional() {
        AppUser tenant = account(true);
        when(users.findByEmailIgnoreCase("owner@example.test")).thenReturn(Optional.of(tenant));
        stubJwt();
        assertEquals("test-jwt", auth.login(
                new LoginRequest("owner@example.test", "password-123")).accessToken());

        AppUser platform = account(false);
        when(users.findByEmailIgnoreCase("owner@example.test")).thenReturn(Optional.of(platform));
        BusinessAccountVerificationService verification = mock(BusinessAccountVerificationService.class);
        when(verification.isRequired()).thenReturn(true);
        auth.setAccountVerification(verification);
        assertEquals("test-jwt", auth.login(
                new LoginRequest("owner@example.test", "password-123")).accessToken());
        verify(verification, never()).isVerified(any(), any());
    }

    private static AppUser account(boolean tenant) {
        AppUser user = new AppUser();
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        user.setName("Owner");
        user.setEmail("owner@example.test");
        user.setPasswordHash("bcrypt");
        if (tenant) {
            Business business = mock(Business.class);
            when(business.getId()).thenReturn(UUID.randomUUID());
            user.setBusiness(business);
        }
        return user;
    }

    private void stubJwt() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getTokenValue()).thenReturn("test-jwt");
        when(encoder.encode(any())).thenReturn(jwt);
    }
}

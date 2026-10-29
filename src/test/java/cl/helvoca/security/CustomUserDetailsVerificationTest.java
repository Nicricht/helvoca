package cl.helvoca.security;

import cl.helvoca.auth.BusinessAccountVerificationService;
import cl.helvoca.business.Business;
import cl.helvoca.user.AppUser;
import cl.helvoca.user.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomUserDetailsVerificationTest {
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final CustomUserDetailsService details = new CustomUserDetailsService(users);

    @Test
    void missingUserFailsClosedBeforeAnyVerificationLookup() {
        assertThrows(UsernameNotFoundException.class,
                () -> details.loadUserByUsername("deleted@example.test"));
    }

    @Test
    void unverifiedTenantCannotAuthenticateEvenWithCorrectPassword() {
        AppUser user = account(true);
        when(users.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        BusinessAccountVerificationService verification = mock(BusinessAccountVerificationService.class);
        when(verification.isRequired()).thenReturn(true);
        details.setAccountVerification(verification);
        assertThrows(UsernameNotFoundException.class,
                () -> details.loadUserByUsername(user.getEmail()));
        verify(verification).isVerified(user.getBusiness().getId(), user.getId());
    }

    @Test
    void verifiedTenantAndDisabledVerificationKeepExistingAuthentication() {
        AppUser user = account(true);
        when(users.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        BusinessAccountVerificationService verification = mock(BusinessAccountVerificationService.class);
        details.setAccountVerification(verification);
        assertEquals(user.getEmail(), details.loadUserByUsername(user.getEmail()).getUsername());
        when(verification.isRequired()).thenReturn(true);
        when(verification.isVerified(user.getBusiness().getId(), user.getId())).thenReturn(true);
        assertEquals(user.getEmail(), details.loadUserByUsername(user.getEmail()).getUsername());
    }

    @Test
    void platformAndLegacyWithoutEmailFeatureDoNotDependOnBusinessVerification() {
        AppUser user = account(false);
        when(users.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        assertEquals(user.getEmail(), details.loadUserByUsername(user.getEmail()).getUsername());

        BusinessAccountVerificationService verification = mock(BusinessAccountVerificationService.class);
        when(verification.isRequired()).thenReturn(true);
        details.setAccountVerification(verification);
        assertEquals(user.getEmail(), details.loadUserByUsername(user.getEmail()).getUsername());
        verify(verification, never()).isVerified(any(), any());
    }

    private static AppUser account(boolean tenant) {
        AppUser user = new AppUser();
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        user.setName("Operator");
        user.setEmail("operator@example.test");
        user.setPasswordHash("encoded-password");
        if (tenant) {
            Business business = mock(Business.class);
            when(business.getId()).thenReturn(UUID.randomUUID());
            user.setBusiness(business);
        }
        return user;
    }
}

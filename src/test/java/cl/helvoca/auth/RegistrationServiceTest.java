package cl.helvoca.auth;

import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.common.ConflictException;
import cl.helvoca.user.AppUser;
import cl.helvoca.user.AppUserRepository;
import cl.helvoca.user.Role;
import cl.helvoca.user.RoleCode;
import cl.helvoca.user.RoleRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RegistrationServiceTest {
    @Test
    void registerCreatesTenantOwnerWithLegacyAdminCompatibilityAndReturnsJwtLogin() {
        BusinessRepository businesses = mock(BusinessRepository.class);
        AppUserRepository users = mock(AppUserRepository.class);
        RoleRepository roles = mock(RoleRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        AuthService auth = mock(AuthService.class);
        RegistrationService service = new RegistrationService(businesses, users, roles, encoder, auth);
        BusinessAccountVerificationService verification = mock(BusinessAccountVerificationService.class);
        service.setVerification(verification);

        Role ownerRole = new Role();
        ownerRole.setCode(RoleCode.BUSINESS_OWNER);
        ownerRole.setName("Business owner");
        Role adminRole = new Role();
        adminRole.setCode(RoleCode.BUSINESS_ADMIN);
        adminRole.setName("Business admin");
        when(roles.findByCode(RoleCode.BUSINESS_OWNER)).thenReturn(Optional.of(ownerRole));
        when(roles.findByCode(RoleCode.BUSINESS_ADMIN)).thenReturn(Optional.of(adminRole));
        when(businesses.saveAndFlush(any(Business.class))).thenAnswer(i -> {
            Business saved = i.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            return saved;
        });
        when(users.saveAndFlush(any(AppUser.class))).thenAnswer(i -> {
            AppUser saved = i.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            return saved;
        });
        when(encoder.encode("very-secure-password")).thenReturn("HASH");
        LoginResponse expected = new LoginResponse("jwt", "Bearer", 3600,
                new LoginResponse.UserInfo(UUID.randomUUID(), UUID.randomUUID(), "Ana", "ana@example.com",
                        List.of("BUSINESS_ADMIN", "BUSINESS_OWNER")));
        when(auth.login(new LoginRequest("ana@example.com", "very-secure-password"))).thenReturn(expected);

        RegistrationService.RegistrationResult result = service.register(new RegisterBusinessRequest(
                "Ana", "ANA@EXAMPLE.COM", "very-secure-password", "Clínica Norte",
                "America/Santiago", "es", "+56911112222"));

        assertSame(expected, result.session());
        assertFalse(result.verificationPending());
        ArgumentCaptor<Business> businessCaptor = ArgumentCaptor.forClass(Business.class);
        verify(businesses).saveAndFlush(businessCaptor.capture());
        assertEquals("Clínica Norte", businessCaptor.getValue().getName());
        assertEquals("+56911112222", businessCaptor.getValue().getHumanTransferPhone());

        ArgumentCaptor<AppUser> userCaptor = ArgumentCaptor.forClass(AppUser.class);
        verify(users).saveAndFlush(userCaptor.capture());
        AppUser user = userCaptor.getValue();
        assertEquals("ana@example.com", user.getEmail());
        assertEquals("HASH", user.getPasswordHash());
        verify(verification).markTrustedPossession(businessCaptor.getValue().getId(), user.getId());
        assertTrue(user.getRoles().contains(ownerRole));
        assertTrue(user.getRoles().contains(adminRole));
        assertSame(businessCaptor.getValue(), user.getBusiness());
    }

    @Test
    void registerRejectsDuplicateEmailBeforeCreatingBusiness() {
        BusinessRepository businesses = mock(BusinessRepository.class);
        AppUserRepository users = mock(AppUserRepository.class);
        when(users.existsByEmailIgnoreCase("ana@example.com")).thenReturn(true);
        RegistrationService service = new RegistrationService(
                businesses, users, mock(RoleRepository.class), mock(PasswordEncoder.class), mock(AuthService.class));

        assertThrows(ConflictException.class, () -> service.register(new RegisterBusinessRequest(
                "Ana", "ana@example.com", "very-secure-password", "Negocio",
                "America/Santiago", "es", null)));
        verifyNoInteractions(businesses);
    }

    @Test
    void registerCannotCreateAnOwnerWithoutBothSeededBusinessRoles() {
        BusinessRepository businesses = mock(BusinessRepository.class);
        AppUserRepository users = mock(AppUserRepository.class);
        RoleRepository roles = mock(RoleRepository.class);
        when(roles.findByCode(RoleCode.BUSINESS_OWNER)).thenReturn(Optional.of(new Role()));
        when(businesses.saveAndFlush(any(Business.class))).thenAnswer(i -> i.getArgument(0));
        RegistrationService service = new RegistrationService(
                businesses, users, roles, mock(PasswordEncoder.class), mock(AuthService.class));
        assertThrows(IllegalStateException.class, () -> service.register(new RegisterBusinessRequest(
                "Ana", "new@example.com", "very-secure-password", "Negocio",
                "America/Santiago", "es", null)));
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void legacyStandaloneRegistrationWithoutEmailFeatureStillReturnsLogin() {
        BusinessRepository businesses = mock(BusinessRepository.class);
        AppUserRepository users = mock(AppUserRepository.class);
        RoleRepository roles = mock(RoleRepository.class);
        AuthService auth = mock(AuthService.class);
        when(roles.findByCode(RoleCode.BUSINESS_OWNER)).thenReturn(Optional.of(new Role()));
        when(roles.findByCode(RoleCode.BUSINESS_ADMIN)).thenReturn(Optional.of(new Role()));
        when(businesses.saveAndFlush(any(Business.class))).thenAnswer(i -> {
            Business saved = i.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            return saved;
        });
        when(users.saveAndFlush(any(AppUser.class))).thenAnswer(i -> {
            AppUser saved = i.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            return saved;
        });
        RegistrationService service = new RegistrationService(
                businesses, users, roles, mock(PasswordEncoder.class), auth);
        RegistrationService.RegistrationResult result = service.register(new RegisterBusinessRequest(
                "Ana", "legacy@example.com", "very-secure-password", "Negocio",
                "America/Santiago", "es", null));
        assertFalse(result.verificationPending());
        verify(auth).login(new LoginRequest("legacy@example.com", "very-secure-password"));
    }
}

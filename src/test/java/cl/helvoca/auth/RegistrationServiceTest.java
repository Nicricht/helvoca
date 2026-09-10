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
    void registerCreatesTenantAdminAndReturnsJwtLogin() {
        BusinessRepository businesses = mock(BusinessRepository.class);
        AppUserRepository users = mock(AppUserRepository.class);
        RoleRepository roles = mock(RoleRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        AuthService auth = mock(AuthService.class);
        RegistrationService service = new RegistrationService(businesses, users, roles, encoder, auth);

        Role adminRole = new Role();
        adminRole.setCode(RoleCode.BUSINESS_ADMIN);
        adminRole.setName("Business admin");
        when(roles.findByCode(RoleCode.BUSINESS_ADMIN)).thenReturn(Optional.of(adminRole));
        when(businesses.saveAndFlush(any(Business.class))).thenAnswer(i -> i.getArgument(0));
        when(users.saveAndFlush(any(AppUser.class))).thenAnswer(i -> i.getArgument(0));
        when(encoder.encode("very-secure-password")).thenReturn("HASH");
        LoginResponse expected = new LoginResponse("jwt", "Bearer", 3600,
                new LoginResponse.UserInfo(UUID.randomUUID(), UUID.randomUUID(), "Ana", "ana@example.com",
                        List.of("BUSINESS_ADMIN")));
        when(auth.login(new LoginRequest("ana@example.com", "very-secure-password"))).thenReturn(expected);

        LoginResponse result = service.register(new RegisterBusinessRequest(
                "Ana", "ANA@EXAMPLE.COM", "very-secure-password", "Clínica Norte",
                "America/Santiago", "es", "+56911112222"));

        assertSame(expected, result);
        ArgumentCaptor<Business> businessCaptor = ArgumentCaptor.forClass(Business.class);
        verify(businesses).saveAndFlush(businessCaptor.capture());
        assertEquals("Clínica Norte", businessCaptor.getValue().getName());
        assertEquals("+56911112222", businessCaptor.getValue().getHumanTransferPhone());

        ArgumentCaptor<AppUser> userCaptor = ArgumentCaptor.forClass(AppUser.class);
        verify(users).saveAndFlush(userCaptor.capture());
        AppUser user = userCaptor.getValue();
        assertEquals("ana@example.com", user.getEmail());
        assertEquals("HASH", user.getPasswordHash());
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
}

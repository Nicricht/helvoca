package cl.helvoca.security;

import cl.helvoca.auth.AuthService;
import cl.helvoca.auth.LoginRequest;
import cl.helvoca.user.AppUser;
import cl.helvoca.user.AppUserRepository;
import cl.helvoca.user.Role;
import cl.helvoca.user.RoleCode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PermissionJwtContractTest {

    @Test
    void loginEmitsPermissionsDerivedFromRoles() {
        AuthenticationManager authentication = mock(AuthenticationManager.class);
        AppUserRepository users = mock(AppUserRepository.class);
        JwtEncoder encoder = mock(JwtEncoder.class);
        JwtProperties properties = mock(JwtProperties.class);

        when(properties.accessTokenMinutes()).thenReturn(60L);
        when(properties.issuer()).thenReturn("recepvoz-test");

        AppUser user = new AppUser();
        ReflectionTestUtils.setField(user, "id", java.util.UUID.randomUUID());
        user.setName("Cocina");
        user.setEmail("cocina@example.cl");
        Role kitchen = new Role();
        kitchen.setCode(RoleCode.KITCHEN);
        kitchen.setName("Cocina");
        user.getRoles().add(kitchen);
        when(users.findByEmailIgnoreCase("cocina@example.cl")).thenReturn(Optional.of(user));

        Jwt encoded = mock(Jwt.class);
        when(encoded.getTokenValue()).thenReturn("jwt");
        when(encoder.encode(any())).thenReturn(encoded);

        new AuthService(authentication, users, encoder, properties)
                .login(new LoginRequest("cocina@example.cl", "password"));

        ArgumentCaptor<JwtEncoderParameters> captor = ArgumentCaptor.forClass(JwtEncoderParameters.class);
        verify(encoder).encode(captor.capture());

        Object permissionsClaim = captor.getValue().getClaims().getClaims().get("permissions");
        assertInstanceOf(List.class, permissionsClaim);
        @SuppressWarnings("unchecked")
        List<String> permissions = (List<String>) permissionsClaim;
        assertTrue(permissions.contains("ORDERS_READ"));
        assertTrue(permissions.contains("ORDERS_PREPARE"));
        assertFalse(permissions.contains("BILLING_MANAGE"));
    }

    @Test
    void jwtAuthenticationConverterDerivesPermissionsForPreDeploymentTokens() {
        Instant now = Instant.now();
        Jwt jwt = new Jwt(
                "legacy-token",
                now,
                now.plusSeconds(300),
                java.util.Map.of("alg", "HS256"),
                java.util.Map.of(
                        "sub", "user-legacy",
                        "roles", List.of("KITCHEN")));

        var authentication = new SecurityConfig().jwtAuthenticationConverter().convert(jwt);
        assertNotNull(authentication);
        var authorities = authentication.getAuthorities().stream().map(Object::toString)
                .collect(java.util.stream.Collectors.toSet());

        assertTrue(authorities.contains("ROLE_KITCHEN"));
        assertTrue(authorities.contains("PERM_ORDERS_READ"));
        assertTrue(authorities.contains("PERM_ORDERS_PREPARE"));
        assertFalse(authorities.contains("PERM_BILLING_MANAGE"));
    }

    @Test
    void jwtAuthenticationConverterExposesRoleAndPermissionAuthorities() {
        Instant now = Instant.now();
        Jwt jwt = new Jwt(
                "token",
                now,
                now.plusSeconds(300),
                java.util.Map.of("alg", "HS256"),
                java.util.Map.of(
                        "sub", "user-1",
                        "roles", List.of("KITCHEN"),
                        "permissions", List.of("ORDERS_READ", "ORDERS_PREPARE")));

        var authentication = new SecurityConfig().jwtAuthenticationConverter().convert(jwt);
        assertNotNull(authentication);
        var authorities = authentication.getAuthorities().stream().map(Object::toString).collect(java.util.stream.Collectors.toSet());

        assertTrue(authorities.contains("ROLE_KITCHEN"));
        assertTrue(authorities.contains("PERM_ORDERS_READ"));
        assertTrue(authorities.contains("PERM_ORDERS_PREPARE"));
    }
}

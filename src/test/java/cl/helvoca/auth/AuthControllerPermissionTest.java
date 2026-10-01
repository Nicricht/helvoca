package cl.helvoca.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class AuthControllerPermissionTest {

    private final AuthController controller =
            new AuthController(mock(AuthService.class), mock(RegistrationService.class));

    @Test
    void meUsesPermissionClaimWhenTokenAlreadyContainsIt() {
        Jwt jwt = jwt(Map.of(
                "sub", "user-1",
                "email", "kitchen@example.cl",
                "roles", List.of("KITCHEN"),
                "permissions", List.of("ORDERS_READ"),
                "business_id", "business-1"));

        Map<String, Object> body = controller.me(jwt).getBody();

        assertNotNull(body);
        assertEquals(List.of("KITCHEN"), body.get("roles"));
        assertEquals(List.of("ORDERS_READ"), body.get("permissions"));
        assertEquals("business-1", body.get("businessId"));
    }

    @Test
    void meDerivesPermissionsForLegacyTokenAndIgnoresUnknownFutureRole() {
        Jwt jwt = jwt(Map.of(
                "sub", "user-2",
                "email", "legacy@example.cl",
                "roles", List.of("KITCHEN", "FUTURE_ROLE")));

        Map<String, Object> body = controller.me(jwt).getBody();

        assertNotNull(body);
        @SuppressWarnings("unchecked")
        List<String> permissions = (List<String>) body.get("permissions");
        assertTrue(permissions.contains("ORDERS_READ"));
        assertTrue(permissions.contains("ORDERS_PREPARE"));
        assertFalse(permissions.contains("BILLING_MANAGE"));
        assertEquals("", body.get("businessId"));
    }

    @Test
    void meHandlesTokenWithoutRolesOrPermissions() {
        Jwt jwt = jwt(Map.of(
                "sub", "user-3",
                "email", "minimal@example.cl"));

        Map<String, Object> body = controller.me(jwt).getBody();

        assertNotNull(body);
        assertEquals(List.of(), body.get("roles"));
        assertEquals(List.of(), body.get("permissions"));
        assertEquals("", body.get("businessId"));
    }

    private static Jwt jwt(Map<String, Object> claims) {
        return new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(300),
                Map.of("alg", "HS256"),
                claims);
    }
}

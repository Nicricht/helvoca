package cl.helvoca.auth;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import cl.helvoca.security.RolePermissionCatalog;
import cl.helvoca.user.RoleCode;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final RegistrationService registrationService;

    public AuthController(AuthService authService, RegistrationService registrationService) {
        this.authService = authService;
        this.registrationService = registrationService;
    }

    @PostMapping("/register")
    public LoginResponse register(@Valid @RequestBody RegisterBusinessRequest request) {
        return registrationService.register(request);
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(@AuthenticationPrincipal Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles == null) roles = List.of();

        List<String> permissions = jwt.getClaimAsStringList("permissions");
        if (permissions == null) {
            Set<RoleCode> roleCodes = roles.stream()
                    .map(value -> {
                        try {
                            return RoleCode.valueOf(value);
                        } catch (IllegalArgumentException ignored) {
                            return null;
                        }
                    })
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toSet());
            permissions = RolePermissionCatalog.permissionsFor(roleCodes).stream()
                    .map(Enum::name)
                    .sorted()
                    .toList();
        }

        return ResponseEntity.ok(Map.of(
                "userId", jwt.getSubject(),
                "email", jwt.getClaimAsString("email"),
                "roles", roles,
                "permissions", permissions,
                "businessId", jwt.hasClaim("business_id") ? jwt.getClaimAsString("business_id") : ""));
    }
}

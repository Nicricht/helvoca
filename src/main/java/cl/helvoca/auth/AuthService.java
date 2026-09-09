package cl.helvoca.auth;

import cl.helvoca.security.JwtProperties;
import cl.helvoca.user.AppUserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AuthService {
    private final AuthenticationManager authenticationManager;
    private final AppUserRepository users;
    private final JwtEncoder encoder;
    private final JwtProperties properties;

    public AuthService(AuthenticationManager authenticationManager,
                       AppUserRepository users,
                       JwtEncoder encoder,
                       JwtProperties properties) {
        this.authenticationManager = authenticationManager;
        this.users = users;
        this.encoder = encoder;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password()));

        var user = users.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(properties.accessTokenMinutes() * 60);
        var roles = user.getRoles().stream().map(r -> r.getCode().name()).sorted().toList();

        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(user.getId().toString())
                .claim("name", user.getName())
                .claim("email", user.getEmail())
                .claim("roles", roles);

        if (user.getBusiness() != null) {
            claims.claim("business_id", user.getBusiness().getId().toString());
        }

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();

        return new LoginResponse(
                token,
                "Bearer",
                properties.accessTokenMinutes() * 60,
                new LoginResponse.UserInfo(
                        user.getId(),
                        user.getBusiness() == null ? null : user.getBusiness().getId(),
                        user.getName(),
                        user.getEmail(),
                        roles));
    }
}

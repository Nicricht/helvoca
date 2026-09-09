package cl.helvoca.auth;

import java.util.List;
import java.util.UUID;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        UserInfo user) {
    public record UserInfo(UUID id, UUID businessId, String name, String email, List<String> roles) {}
}

package cl.helvoca.user;

import java.util.List;
import java.util.UUID;

public record UserResponse(UUID id, String name, String email, boolean active, List<RoleCode> roles) {
    public static UserResponse from(AppUser user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.isActive(),
                user.getRoles().stream().map(Role::getCode).sorted().toList());
    }
}

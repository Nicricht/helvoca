package cl.helvoca.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record InviteUserRequest(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Email @Size(max = 180) String email,
        @NotNull RoleCode role
) {}

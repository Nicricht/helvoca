package cl.helvoca.customer;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record CustomerRequest(
        @Size(max = 150) String name,
        @Size(max = 30) String phone,
        @Email @Size(max = 180) String email,
        String notes
) {}

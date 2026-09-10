package cl.helvoca.business;
import jakarta.validation.constraints.NotBlank; import jakarta.validation.constraints.Size;
public record UpdateBusinessRequest(@NotBlank @Size(max=150) String name,@NotBlank @Size(max=60) String timezone,@NotBlank @Size(max=10) String language,@Size(max=32) String humanTransferPhone) {}

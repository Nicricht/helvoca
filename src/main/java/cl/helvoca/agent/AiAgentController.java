package cl.helvoca.agent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai-agent")
public class AiAgentController {
    private final AiAgentService service;

    public AiAgentController(AiAgentService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
    public Response current() { return Response.from(service.current()); }

    @PutMapping
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public Response upsert(@Valid @RequestBody Request request) {
        return Response.from(service.upsert(
                request.name(), request.language(), request.voice(), request.greeting(),
                request.instructions(), request.active(), request.capabilities()));
    }

    public record Request(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 10) String language,
            @Size(max = 100) String voice,
            @Size(max = 1200) String greeting,
            @Size(max = 8000) String instructions,
            boolean active,
            Set<AiCapability> capabilities) {}

    public record Response(
            UUID id,
            String name,
            String language,
            String voice,
            String greeting,
            String instructions,
            boolean active,
            Set<AiCapability> capabilities,
            Instant createdAt,
            Instant updatedAt) {
        static Response from(AiAgent agent) {
            return new Response(agent.getId(), agent.getName(), agent.getLanguage(), agent.getVoice(),
                    agent.getGreeting(), agent.getInstructions(), agent.isActive(), agent.getCapabilities(),
                    agent.getCreatedAt(), agent.getUpdatedAt());
        }
    }
}

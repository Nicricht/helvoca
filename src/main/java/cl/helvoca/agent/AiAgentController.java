package cl.helvoca.agent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai-agent")
public class AiAgentController {
    private final AiAgentService service;

    public AiAgentController(AiAgentService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
    public Response current() {
        return Response.from(service.current(), service.configuredCurrent());
    }

    @GetMapping("/voices")
    @PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
    public List<VoiceProfileResponse> voices() {
        return AgentVoiceProfile.catalog().stream()
                .map(VoiceProfileResponse::from)
                .toList();
    }

    @PutMapping
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public Response update(@Valid @RequestBody Request request) {
        AiAgent saved = service.upsert(
                request.name(), request.language(), request.voice(), request.greeting(),
                request.instructions(), request.active(), request.capabilities());
        return Response.from(saved, true);
    }

    public record Request(
            @Size(max = 100) String name,
            @Size(max = 10) String language,
            @Size(max = 100) String voice,
            @Size(max = 2000) String greeting,
            @Size(max = 4000) String instructions,
            boolean active,
            Set<AiCapability> capabilities
    ) {}

    public record VoiceProfileResponse(
            String code,
            String name,
            String description
    ) {
        static VoiceProfileResponse from(AgentVoiceProfile profile) {
            return new VoiceProfileResponse(profile.code(), profile.displayName(), profile.description());
        }
    }

    public record Response(
            UUID id,
            boolean configured,
            String name,
            String language,
            String voice,
            String greeting,
            String instructions,
            boolean active,
            Set<AiCapability> capabilities,
            Instant createdAt,
            Instant updatedAt
    ) {
        static Response from(AiAgent agent, boolean configured) {
            return new Response(
                    agent.getId(), configured, agent.getName(), agent.getLanguage(), agent.getVoice(),
                    agent.getGreeting(), agent.getInstructions(), agent.isActive(), agent.getCapabilities(),
                    agent.getCreatedAt(), agent.getUpdatedAt());
        }
    }
}

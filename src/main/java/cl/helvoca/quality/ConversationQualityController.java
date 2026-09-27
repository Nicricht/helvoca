package cl.helvoca.quality;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/quality")
@PreAuthorize("hasRole('BUSINESS_ADMIN')")
public class ConversationQualityController {
    private final ConversationQualityService service;

    public ConversationQualityController(ConversationQualityService service) {
        this.service = service;
    }

    @GetMapping("/calls/{callId}")
    public ConversationQualityService.CallQualityReport analyze(@PathVariable UUID callId) {
        return service.analyze(callId);
    }
}

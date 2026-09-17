package cl.helvoca.messaging;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/messaging/conversations")
@PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
public class MessagingConversationQueryController {
    private final MessagingConversationQueryService service;

    public MessagingConversationQueryController(MessagingConversationQueryService service) {
        this.service = service;
    }

    @GetMapping
    public List<MessagingConversationQueryService.ConversationItem> list() {
        return service.listWhatsApp();
    }

    @GetMapping("/{id}")
    public MessagingConversationQueryService.ConversationDetail detail(@PathVariable UUID id) {
        return service.whatsappDetail(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
    }
}

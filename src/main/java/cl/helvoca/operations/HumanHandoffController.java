package cl.helvoca.operations;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/handoffs")
@PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
public class HumanHandoffController {
    private final HumanHandoffService service;

    public HumanHandoffController(HumanHandoffService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<HumanHandoffService.HandoffView>> recent(
            @RequestParam(value = "status", required = false) String status) {
        return ResponseEntity.ok(service.recent(status));
    }

    @GetMapping("/{handoffId}/events")
    public ResponseEntity<List<HumanHandoffService.EventView>> history(@PathVariable UUID handoffId) {
        return ResponseEntity.ok(service.history(handoffId));
    }

    @PostMapping("/{handoffId}/acknowledge")
    public ResponseEntity<HumanHandoffService.HandoffView> acknowledge(
            @PathVariable UUID handoffId,
            Authentication authentication) {
        return ResponseEntity.ok(service.acknowledge(handoffId, actor(authentication)));
    }

    @PostMapping("/{handoffId}/assign")
    public ResponseEntity<HumanHandoffService.HandoffView> assign(
            @PathVariable UUID handoffId,
            @RequestBody AssignRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(service.assign(handoffId, request.assignee(), actor(authentication)));
    }

    @PostMapping("/{handoffId}/resolve")
    public ResponseEntity<HumanHandoffService.HandoffView> resolve(
            @PathVariable UUID handoffId,
            Authentication authentication) {
        return ResponseEntity.ok(service.resolve(handoffId, actor(authentication)));
    }

    @PostMapping("/{handoffId}/cancel")
    public ResponseEntity<HumanHandoffService.HandoffView> cancel(
            @PathVariable UUID handoffId,
            Authentication authentication) {
        return ResponseEntity.ok(service.cancel(handoffId, actor(authentication)));
    }

    public record AssignRequest(String assignee) { }

    private static String actor(Authentication authentication) {
        return authentication == null ? null : authentication.getName();
    }
}

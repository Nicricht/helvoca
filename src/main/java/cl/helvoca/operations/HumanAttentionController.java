package cl.helvoca.operations;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Read-only data contract for the future Home attention inbox. */
@RestController
@RequestMapping("/api/v1/operations/attention")
@PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
public class HumanAttentionController {
    private final HumanAttentionService service;

    public HumanAttentionController(HumanAttentionService service) {
        this.service = service;
    }

    @GetMapping
    public List<HumanAttentionService.AttentionItem> pending() {
        return service.pending();
    }
}

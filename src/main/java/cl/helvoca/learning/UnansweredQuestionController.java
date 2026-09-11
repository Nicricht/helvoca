package cl.helvoca.learning;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/unanswered-questions")
public class UnansweredQuestionController {
    private final UnansweredQuestionService service;

    public UnansweredQuestionController(UnansweredQuestionService service) {
        this.service = service;
    }

    @GetMapping
    public List<UnansweredQuestionResponse> list() {
        return service.list();
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public UnansweredQuestionResponse resolve(@PathVariable UUID id,
                                              @Valid @RequestBody ResolveUnansweredQuestionRequest request) {
        return service.resolve(id, request);
    }

    @PostMapping("/{id}/ignore")
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public UnansweredQuestionResponse ignore(@PathVariable UUID id) {
        return service.ignore(id);
    }
}

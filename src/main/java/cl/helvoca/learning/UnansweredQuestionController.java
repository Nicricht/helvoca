package cl.helvoca.learning;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/learning/questions")
@PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
public class UnansweredQuestionController {
    private final UnansweredQuestionService service;

    public UnansweredQuestionController(UnansweredQuestionService service) { this.service = service; }

    @GetMapping
    public List<UnansweredQuestionDtos.Response> listOpen() { return service.listOpen(); }

    @PostMapping("/{id}/answer")
    public UnansweredQuestionDtos.Response answer(@PathVariable UUID id,
                                                   @Valid @RequestBody UnansweredQuestionDtos.Answer input) {
        return service.answer(id, input.answer());
    }

    @PostMapping("/{id}/dismiss")
    public void dismiss(@PathVariable UUID id) { service.dismiss(id); }
}

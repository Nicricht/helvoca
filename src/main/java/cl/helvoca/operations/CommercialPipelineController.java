package cl.helvoca.operations;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/commercial/pipeline")
@PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
public class CommercialPipelineController {
    private final CommercialPipelineService service;

    public CommercialPipelineController(CommercialPipelineService service) {
        this.service = service;
    }

    @GetMapping
    public CommercialPipelineService.PipelineResponse get() {
        return service.get();
    }
}

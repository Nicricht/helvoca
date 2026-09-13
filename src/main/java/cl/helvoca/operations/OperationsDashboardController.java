package cl.helvoca.operations;

import cl.helvoca.voice.VoiceCallRouter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/operations")
@PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
public class OperationsDashboardController {
    private final OperationsDashboardService service;
    private final CommercialReadinessService readiness;
    private final VoiceCallRouter voiceRouter;

    public OperationsDashboardController(OperationsDashboardService service,
                                         CommercialReadinessService readiness,
                                         VoiceCallRouter voiceRouter) {
        this.service = service;
        this.readiness = readiness;
        this.voiceRouter = voiceRouter;
    }

    @GetMapping("/dashboard")
    public OperationsDashboardService.Dashboard dashboard() { return service.dashboard(); }

    @GetMapping("/readiness")
    public CommercialReadinessService.Readiness readiness() { return readiness.readiness(); }

    @GetMapping("/voice-readiness")
    public VoiceCallRouter.VoiceReadiness voiceReadiness() { return voiceRouter.readiness(); }
}

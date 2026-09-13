package cl.helvoca.simulator;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/simulator")
@PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
public class ReceptionistSimulatorController {
    private final ReceptionistSimulatorService service;

    public ReceptionistSimulatorController(ReceptionistSimulatorService service) {
        this.service = service;
    }

    @PostMapping("/sessions")
    public ReceptionistSimulatorService.SessionResponse start() {
        return service.start();
    }

    @PostMapping("/sessions/{id}/messages")
    public ReceptionistSimulatorService.MessageResponse message(@PathVariable UUID id,
                                                                 @RequestBody MessageInput input) {
        return service.message(id, input == null ? null : input.message());
    }

    @PostMapping("/sessions/{id}/finish")
    public ReceptionistSimulatorService.SessionResponse finish(@PathVariable UUID id) {
        return service.finish(id);
    }

    public record MessageInput(String message) {}
}

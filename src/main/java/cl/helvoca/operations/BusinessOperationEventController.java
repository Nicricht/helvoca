package cl.helvoca.operations;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/operation-events")
@PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
public class BusinessOperationEventController {
    private final BusinessOperationEventService service;

    public BusinessOperationEventController(BusinessOperationEventService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<BusinessOperationEventService.EventView>> recent(
            @RequestParam(value = "operationId", required = false) UUID operationId) {
        return ResponseEntity.ok(service.recent(operationId));
    }
}

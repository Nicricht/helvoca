package cl.helvoca.retention;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/data-retention")
@PreAuthorize("hasRole('BUSINESS_ADMIN')")
public class DataRetentionController {
    private final DataRetentionInventoryService service;

    public DataRetentionController(DataRetentionInventoryService service) {
        this.service = service;
    }

    @GetMapping("/dry-run")
    public DataRetentionInventoryService.Inventory dryRun() {
        return service.dryRun();
    }
}

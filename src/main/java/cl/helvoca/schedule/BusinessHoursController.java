package cl.helvoca.schedule;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/business/hours")
public class BusinessHoursController {
    private final BusinessHoursAdminService service;

    public BusinessHoursController(BusinessHoursAdminService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
    public List<BusinessHourResponse> list() {
        return service.list();
    }

    @PutMapping
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public List<BusinessHourResponse> replace(@Valid @RequestBody BusinessHoursRequest request) {
        return service.replace(request);
    }
}

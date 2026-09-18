package cl.helvoca.customer;

import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers")
@PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
public class CustomerController {
    private final CustomerService service;
    private final CustomerProfileService profileService;
    private final CustomerExportService exportService;

    public CustomerController(
            CustomerService service,
            CustomerProfileService profileService,
            CustomerExportService exportService) {
        this.service = service;
        this.profileService = profileService;
        this.exportService = exportService;
    }

    @GetMapping
    public List<CustomerResponse> list() { return service.list(); }

    @GetMapping("/export")
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public ResponseEntity<byte[]> export(@RequestParam(defaultValue = "csv") String format) {
        CustomerExportFile file = exportService.export(format);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.filename() + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.content());
    }

    @GetMapping("/{id}")
    public CustomerResponse get(@PathVariable UUID id) { return service.get(id); }

    @GetMapping("/{id}/profile")
    public CustomerProfileResponse profile(@PathVariable UUID id) { return profileService.get(id); }

    @GetMapping("/search")
    public CustomerResponse search(@RequestParam String phone) { return service.findByPhone(phone); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse create(@Valid @RequestBody CustomerRequest request) {
        return service.create(request);
    }

    @PatchMapping("/{id}")
    public CustomerResponse update(@PathVariable UUID id, @Valid @RequestBody CustomerRequest request) {
        return service.update(id, request);
    }
}

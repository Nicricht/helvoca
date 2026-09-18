package cl.helvoca.customer;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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

    public CustomerController(CustomerService service, CustomerProfileService profileService) {
        this.service = service;
        this.profileService = profileService;
    }

    @GetMapping
    public List<CustomerResponse> list() { return service.list(); }

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

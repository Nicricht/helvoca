package cl.helvoca.customer;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {
    private final CustomerService service;

    public CustomerController(CustomerService service) { this.service = service; }

    @GetMapping
    public List<CustomerResponse> list() { return service.list(); }

    @GetMapping("/{id}")
    public CustomerResponse get(@PathVariable UUID id) { return service.get(id); }

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

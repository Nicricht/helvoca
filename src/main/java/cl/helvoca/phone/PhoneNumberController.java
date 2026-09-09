package cl.helvoca.phone;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/phone-numbers")
public class PhoneNumberController {
    private final PhoneNumberService service;

    public PhoneNumberController(PhoneNumberService service) { this.service = service; }

    @GetMapping
    public List<PhoneNumberResponse> list() { return service.list(); }

    @PostMapping
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public PhoneNumberResponse create(@Valid @RequestBody PhoneNumberRequest request) {
        return service.create(request);
    }

    @PatchMapping("/{id}/active")
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public PhoneNumberResponse setActive(@PathVariable UUID id,
                                         @RequestBody PhoneNumberActiveRequest request) {
        return service.setActive(id, request.active());
    }
}

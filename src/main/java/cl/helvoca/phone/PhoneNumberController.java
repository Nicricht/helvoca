package cl.helvoca.phone;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/phone-numbers")
public class PhoneNumberController {
    private final PhoneNumberService service;
    private final PhoneProvisioningService provisioning;

    public PhoneNumberController(PhoneNumberService service,
                                 PhoneProvisioningService provisioning) {
        this.service = service;
        this.provisioning = provisioning;
    }

    @GetMapping
    public List<PhoneNumberResponse> list() { return service.list(); }

    @PostMapping
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public PhoneNumberResponse create(@Valid @RequestBody PhoneNumberRequest request) {
        return service.create(request);
    }

    @GetMapping("/provisioning/status")
    @PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
    public PhoneProvisioningStatusResponse provisioningStatus() {
        return provisioning.status();
    }

    @GetMapping("/provisioning/available")
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public List<AvailablePhoneNumberResponse> available(@RequestParam(defaultValue = "CL") String country,
                                                        @RequestParam(required = false) String areaCode,
                                                        @RequestParam(defaultValue = "5") int limit) {
        return provisioning.available(country, areaCode, limit);
    }

    @PostMapping("/provisioning")
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public PhoneNumberResponse provision(@Valid @RequestBody ProvisionPhoneNumberRequest request) {
        return provisioning.provision(request);
    }

    @PatchMapping("/{id}/active")
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public PhoneNumberResponse setActive(@PathVariable UUID id,
                                         @RequestBody PhoneNumberActiveRequest request) {
        return service.setActive(id, request.active());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public void detach(@PathVariable UUID id) {
        service.detach(id);
    }
}

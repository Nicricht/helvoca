package cl.helvoca.business;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/business")
public class BusinessController {
    private final BusinessService service;
    private final BusinessProfileService profileService;

    public BusinessController(BusinessService service, BusinessProfileService profileService) {
        this.service = service;
        this.profileService = profileService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
    public BusinessResponse current() {
        return service.current();
    }

    @PatchMapping
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public BusinessResponse update(@Valid @RequestBody UpdateBusinessRequest request) {
        return service.update(request);
    }

    @GetMapping("/profile")
    @PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
    public BusinessProfileResponse profile() {
        return profileService.current();
    }

    @PutMapping("/profile")
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public BusinessProfileResponse updateProfile(@Valid @RequestBody BusinessProfileRequest request) {
        return profileService.upsert(request);
    }
}

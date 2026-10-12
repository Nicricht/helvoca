package cl.helvoca.phone;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * No tenant endpoints, carrier SID input, auto-approval or activation.
 * HTTP JWT is validated by Spring Security; the service checks the live DB role.
 */
@RestController
@ConditionalOnProperty(name = "app.telephony.twilio.existing-enrollment-admin-enabled", havingValue = "true")
@RequestMapping("/api/v1/platform/twilio-existing-enrollments")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class TwilioExistingEnrollmentAdminController {
    private final TwilioExistingEnrollmentAdminService service;

    public TwilioExistingEnrollmentAdminController(TwilioExistingEnrollmentAdminService service) {
        this.service = service;
    }

    @PostMapping
    public TwilioExistingEnrollmentAdminService.View request(
            @RequestBody TwilioExistingEnrollmentAdminService.Request request) {
        return service.request(request);
    }

    @PostMapping("/{changeId}/approve")
    public TwilioExistingEnrollmentAdminService.View approve(@PathVariable UUID changeId) {
        return service.approve(changeId);
    }

    @PostMapping("/{changeId}/execute")
    public TwilioExistingEnrollmentAdminService.View execute(@PathVariable UUID changeId) {
        return service.execute(changeId);
    }

    @GetMapping("/{changeId}")
    public TwilioExistingEnrollmentAdminService.View status(@PathVariable UUID changeId) {
        return service.status(changeId);
    }
}

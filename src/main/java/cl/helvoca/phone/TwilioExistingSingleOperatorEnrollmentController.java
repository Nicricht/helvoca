package cl.helvoca.phone;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@ConditionalOnProperty(name = "TWILIO_SINGLE_OPERATOR_DEMO_ENROLLMENT_ENABLED",
        havingValue = "true")
@RequestMapping("/api/v1/platform/twilio-single-operator-demo")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class TwilioExistingSingleOperatorEnrollmentController {
    private final TwilioExistingSingleOperatorEnrollmentService service;

    public TwilioExistingSingleOperatorEnrollmentController(
            TwilioExistingSingleOperatorEnrollmentService service) {
        this.service = service;
    }

    @PostMapping
    public TwilioExistingSingleOperatorEnrollmentService.View request(
            @RequestBody TwilioExistingSingleOperatorEnrollmentService.Request request) {
        return service.request(request);
    }

    @PostMapping("/{changeId}/confirm")
    public TwilioExistingSingleOperatorEnrollmentService.View confirm(
            @PathVariable UUID changeId,
            @RequestBody TwilioExistingSingleOperatorEnrollmentService.Confirm confirm) {
        return service.confirm(changeId, confirm);
    }

    @GetMapping("/{changeId}")
    public TwilioExistingSingleOperatorEnrollmentService.View status(@PathVariable UUID changeId) {
        return service.status(changeId);
    }
}

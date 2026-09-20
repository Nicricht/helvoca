package cl.helvoca.messaging.meta;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/channels/whatsapp/meta")
public class MetaWhatsAppTenantConfigurationController {
    private final MetaWhatsAppTenantConfigurationService service;
    private final MetaWhatsAppTenantHealthService healthService;
    private final MetaWhatsAppDeploymentReadinessService deploymentReadinessService;
    private final MetaWhatsAppEmbeddedSignupReadinessService embeddedSignupReadinessService;
    private final MetaWhatsAppEmbeddedSignupBootstrapService embeddedSignupBootstrapService;
    private final MetaWhatsAppEmbeddedSignupAuthorizationCodeService embeddedSignupAuthorizationCodeService;
    private final MetaWhatsAppEmbeddedSignupSelectedWabaAssignmentService embeddedSignupSelectedWabaAssignmentService;
    private final MetaWhatsAppEmbeddedSignupSelectedWabaSubscriptionService embeddedSignupSelectedWabaSubscriptionService;

    public MetaWhatsAppTenantConfigurationController(
            MetaWhatsAppTenantConfigurationService service,
            MetaWhatsAppTenantHealthService healthService,
            MetaWhatsAppDeploymentReadinessService deploymentReadinessService,
            MetaWhatsAppEmbeddedSignupReadinessService embeddedSignupReadinessService,
            MetaWhatsAppEmbeddedSignupBootstrapService embeddedSignupBootstrapService,
            MetaWhatsAppEmbeddedSignupAuthorizationCodeService embeddedSignupAuthorizationCodeService,
            MetaWhatsAppEmbeddedSignupSelectedWabaAssignmentService embeddedSignupSelectedWabaAssignmentService,
            MetaWhatsAppEmbeddedSignupSelectedWabaSubscriptionService embeddedSignupSelectedWabaSubscriptionService) {
        this.service = service;
        this.healthService = healthService;
        this.deploymentReadinessService = deploymentReadinessService;
        this.embeddedSignupReadinessService = embeddedSignupReadinessService;
        this.embeddedSignupBootstrapService = embeddedSignupBootstrapService;
        this.embeddedSignupAuthorizationCodeService = embeddedSignupAuthorizationCodeService;
        this.embeddedSignupSelectedWabaAssignmentService = embeddedSignupSelectedWabaAssignmentService;
        this.embeddedSignupSelectedWabaSubscriptionService = embeddedSignupSelectedWabaSubscriptionService;
    }

    @GetMapping("/config")
    @PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
    public MetaWhatsAppTenantStatusResponse status() {
        return service.status();
    }

    @GetMapping("/health")
    @PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
    public MetaWhatsAppTenantHealthResponse health() {
        return healthService.health();
    }

    @GetMapping("/deployment/readiness")
    @PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
    public MetaWhatsAppDeploymentReadinessResponse deploymentReadiness() {
        return deploymentReadinessService.readiness();
    }

    @GetMapping("/embedded-signup/readiness")
    @PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
    public MetaWhatsAppEmbeddedSignupReadinessResponse embeddedSignupReadiness() {
        return embeddedSignupReadinessService.readiness();
    }

    @GetMapping("/embedded-signup/bootstrap")
    @PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
    public MetaWhatsAppEmbeddedSignupBootstrapResponse embeddedSignupBootstrap() {
        return embeddedSignupBootstrapService.bootstrap();
    }

    @PostMapping("/embedded-signup/authorization-code")
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public MetaWhatsAppEmbeddedSignupAuthorizationCodeResponse acceptAuthorizationCode(
            @Valid @RequestBody MetaWhatsAppEmbeddedSignupAuthorizationCodeRequest request) {
        return embeddedSignupAuthorizationCodeService.accept(request);
    }

    @PostMapping("/embedded-signup/waba/assign-system-user")
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public MetaWhatsAppEmbeddedSignupSelectedWabaAssignmentResult assignSystemUserToSelectedWaba(
            @Valid @RequestBody MetaWhatsAppEmbeddedSignupSelectedWabaRequest request) {
        return embeddedSignupSelectedWabaAssignmentService.ensureAssigned(request.wabaId());
    }

    @PostMapping("/embedded-signup/waba/subscribe-app")
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public MetaWhatsAppEmbeddedSignupSelectedWabaSubscriptionResult subscribeAppToSelectedWaba(
            @Valid @RequestBody MetaWhatsAppEmbeddedSignupSelectedWabaRequest request) {
        return embeddedSignupSelectedWabaSubscriptionService.ensureSubscribed(request.wabaId());
    }

    @PostMapping("/config/deactivate")
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public MetaWhatsAppTenantStatusResponse deactivate() {
        return service.deactivate();
    }

    @PostMapping("/config/activate")
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public MetaWhatsAppTenantStatusResponse activate() {
        return service.activate();
    }

    @PutMapping("/config")
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public MetaWhatsAppTenantConfigurationResponse replace(
            @Valid @RequestBody MetaWhatsAppTenantConfigurationRequest request) {
        return service.replace(request);
    }
}

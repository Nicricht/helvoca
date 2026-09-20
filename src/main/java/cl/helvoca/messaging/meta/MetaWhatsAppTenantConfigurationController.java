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

    public MetaWhatsAppTenantConfigurationController(
            MetaWhatsAppTenantConfigurationService service,
            MetaWhatsAppTenantHealthService healthService) {
        this.service = service;
        this.healthService = healthService;
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

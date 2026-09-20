package cl.helvoca.messaging.meta;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/channels/whatsapp/meta")
public class MetaWhatsAppTenantConfigurationController {
    private final MetaWhatsAppTenantConfigurationService service;

    public MetaWhatsAppTenantConfigurationController(
            MetaWhatsAppTenantConfigurationService service) {
        this.service = service;
    }

    @PutMapping("/config")
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public MetaWhatsAppTenantConfigurationResponse replace(
            @Valid @RequestBody MetaWhatsAppTenantConfigurationRequest request) {
        return service.replace(request);
    }
}

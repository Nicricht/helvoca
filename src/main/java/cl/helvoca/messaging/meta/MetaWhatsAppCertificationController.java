package cl.helvoca.messaging.meta;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/channels/whatsapp/meta/certification")
public class MetaWhatsAppCertificationController {
    private final MetaWhatsAppCertificationReadinessService service;

    public MetaWhatsAppCertificationController(
            MetaWhatsAppCertificationReadinessService service) {
        this.service = service;
    }

    @GetMapping("/readiness")
    @PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
    public MetaWhatsAppCertificationReadinessResponse readiness() {
        return service.readiness();
    }
}

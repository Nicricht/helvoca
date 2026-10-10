package cl.helvoca.onboarding;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/onboarding/import/ai-quota")
@PreAuthorize("hasRole('BUSINESS_ADMIN')")
public class BusinessImportAiQuotaController {
    private final BusinessImportAiPlanQuota quota;

    public BusinessImportAiQuotaController(BusinessImportAiPlanQuota quota) {
        this.quota = quota;
    }

    @GetMapping
    public BusinessImportAiPlanQuota.Snapshot current() {
        return quota.current();
    }
}

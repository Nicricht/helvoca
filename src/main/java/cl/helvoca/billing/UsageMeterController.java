package cl.helvoca.billing;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/usage")
@PreAuthorize("hasRole('BUSINESS_ADMIN')")
public class UsageMeterController {
    private final UsageMeterService usageMeterService;

    public UsageMeterController(UsageMeterService usageMeterService) {
        this.usageMeterService = usageMeterService;
    }

    @GetMapping("/summary")
    public List<UsageMeterService.UsageSummary> summary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return usageMeterService.summarize(from, to);
    }
}

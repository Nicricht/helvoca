package cl.helvoca.billing;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/subscription")
public class SubscriptionController {
    private final BusinessSubscriptionService subscriptions;

    public SubscriptionController(BusinessSubscriptionService subscriptions) {
        this.subscriptions = subscriptions;
    }

    @GetMapping
    public BusinessSubscriptionService.SubscriptionView current() {
        return subscriptions.currentForTenant();
    }

    @GetMapping("/plans")
    public List<BusinessSubscriptionService.PlanView> plans() {
        return subscriptions.plans();
    }
}

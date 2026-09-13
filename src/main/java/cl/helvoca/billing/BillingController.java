package cl.helvoca.billing;

import cl.helvoca.security.TenantProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/billing")
public class BillingController {
    private final BillingSubscriptionService billing;
    private final MercadoPagoProperties properties;
    private final TenantProvider tenantProvider;

    public BillingController(BillingSubscriptionService billing,
                             MercadoPagoProperties properties,
                             TenantProvider tenantProvider) {
        this.billing = billing;
        this.properties = properties;
        this.tenantProvider = tenantProvider;
    }

    @PostMapping("/checkout")
    public BillingSubscriptionService.CheckoutResponse checkout(@AuthenticationPrincipal Jwt jwt,
                                                                @RequestBody CheckoutRequest request) {
        if (!properties.checkoutConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Billing checkout is not enabled");
        }
        String email = jwt == null ? null : jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) throw new IllegalArgumentException("Authenticated account has no email");
        return billing.createCheckout(tenantProvider.requireBusinessId(), email, request.plan());
    }

    public record CheckoutRequest(String plan) {}
}

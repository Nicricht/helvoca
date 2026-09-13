package cl.helvoca.billing;

import cl.helvoca.security.TenantProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/billing")
@PreAuthorize("hasRole('BUSINESS_ADMIN')")
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

    @GetMapping("/status")
    public BillingSubscriptionService.BillingStatus status() {
        return billing.status(tenantProvider.requireBusinessId());
    }

    @PostMapping("/checkout")
    public BillingSubscriptionService.CheckoutResponse checkout(@AuthenticationPrincipal Jwt jwt,
                                                                @RequestBody CheckoutRequest request) {
        requireCheckoutConfigured();
        String email = jwt == null ? null : jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) throw new IllegalArgumentException("Authenticated account has no email");
        return billing.createCheckout(tenantProvider.requireBusinessId(), email, request.plan());
    }

    @PostMapping("/refresh")
    public BillingSubscriptionService.BillingStatus refresh() {
        requireCheckoutConfigured();
        return billing.refresh(tenantProvider.requireBusinessId());
    }

    private void requireCheckoutConfigured() {
        if (!properties.checkoutConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Billing checkout is not enabled");
        }
    }

    public record CheckoutRequest(String plan) {}
}

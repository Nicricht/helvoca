package cl.helvoca.omnichannel;

import cl.helvoca.audit.AuditService;
import cl.helvoca.security.TenantProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers/{customerId}/identities")
@PreAuthorize("hasRole('BUSINESS_ADMIN')")
public class CustomerIdentityController {
    private final CustomerIdentityService identities;
    private final TenantProvider tenantProvider;
    private final AuditService audit;

    public CustomerIdentityController(CustomerIdentityService identities,
                                      TenantProvider tenantProvider,
                                      AuditService audit) {
        this.identities = identities;
        this.tenantProvider = tenantProvider;
        this.audit = audit;
    }

    /**
     * Administrative verification is explicit and tenant scoped. Merely storing
     * a phone on a customer record never calls this endpoint automatically.
     */
    @PostMapping("/phone/verify")
    public ResponseEntity<IdentityView> verifyPhone(@PathVariable UUID customerId,
                                                    @RequestBody VerifyPhoneRequest request) {
        UUID businessId = tenantProvider.requireBusinessId();
        CustomerIdentity identity = identities.verifyPhone(
                businessId,
                customerId,
                request.phone(),
                CustomerIdentity.VerificationStatus.MANUAL_VERIFIED,
                "BUSINESS_ADMIN");
        audit.success(businessId, "CUSTOMER_IDENTITY_VERIFY", "CUSTOMER_IDENTITY", identity.getId());
        return ResponseEntity.ok(IdentityView.from(identity));
    }

    public record VerifyPhoneRequest(String phone) { }

    public record IdentityView(UUID id,
                               UUID customerId,
                               String type,
                               String normalizedValue,
                               String verificationStatus,
                               Instant verifiedAt) {
        static IdentityView from(CustomerIdentity identity) {
            return new IdentityView(
                    identity.getId(),
                    identity.getCustomerId(),
                    identity.getIdentityType().name(),
                    identity.getNormalizedValue(),
                    identity.getVerificationStatus().name(),
                    identity.getVerifiedAt());
        }
    }
}

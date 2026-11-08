package cl.helvoca.telephony.twilio;

import cl.helvoca.operations.ControlledPilotExternalEffectGuard;
import cl.helvoca.phone.PhoneNumber;
import cl.helvoca.security.TenantDatabaseContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Extra fail-closed routing gate for numbers truthfully enrolled as VERIFIED_EXISTING.
 *
 * Never performs a carrier call or modifies the database. Commercial routing is
 * disabled unless a pinned tenant, phone and carrier account, an explicitly
 * armed pilot, and durable two-human execution evidence ALL agree.
 *
 * Must be called from TwilioCallBindingGuard's system database scope.
 */
@Component
public final class TwilioVerifiedExistingVoiceRouteGate {
    private final JdbcTemplate jdbc;
    private final ControlledPilotExternalEffectGuard pilot;
    private final TenantDatabaseContext databaseContext;
    private final boolean enabled;
    private final String allowedBusinessId;
    private final String allowedPhoneId;
    private final String allowedAccountSid;
    private final boolean demoEnabled;
    private final boolean scopedCustomerDemoEnabled;
    @Value("${TWILIO_SINGLE_OPERATOR_DEMO_ENROLLMENT_ENABLED:false}")
    private boolean singleOperatorDemoEnabled;
    @Value("${RAILWAY_ENVIRONMENT_NAME:}")
    private String environmentName;

    @Autowired
    public TwilioVerifiedExistingVoiceRouteGate(
            JdbcTemplate jdbc,
            ControlledPilotExternalEffectGuard pilot,
            TenantDatabaseContext databaseContext,
            @Value("${app.telephony.twilio.existing-voice-route-enabled:false}") boolean enabled,
            @Value("${app.telephony.twilio.existing-voice-route-business-id:}") String allowedBusinessId,
            @Value("${app.telephony.twilio.existing-voice-route-phone-id:}") String allowedPhoneId,
            @Value("${app.telephony.twilio.existing-voice-route-account-sid:}") String allowedAccountSid,
            @Value("${TWILIO_CERTIFICATION_DEMO_ROUTE_ENABLED:false}") boolean demoEnabled,
            @Value("${TWILIO_CERTIFICATION_SCOPED_CUSTOMER_DEMO_ENABLED:false}") boolean scopedCustomerDemoEnabled) {
        this.jdbc = jdbc;
        this.pilot = pilot;
        this.databaseContext = databaseContext;
        this.enabled = enabled;
        this.allowedBusinessId = allowedBusinessId;
        this.allowedPhoneId = allowedPhoneId;
        this.allowedAccountSid = allowedAccountSid;
        this.demoEnabled = demoEnabled;
        this.scopedCustomerDemoEnabled = scopedCustomerDemoEnabled;
    }

    TwilioVerifiedExistingVoiceRouteGate(JdbcTemplate jdbc,
            ControlledPilotExternalEffectGuard pilot, TenantDatabaseContext databaseContext,
            boolean enabled, String allowedBusinessId, String allowedPhoneId, String allowedAccountSid) {
        this(jdbc, pilot, databaseContext, enabled, allowedBusinessId,
                allowedPhoneId, allowedAccountSid, false, false);
    }

    TwilioVerifiedExistingVoiceRouteGate(JdbcTemplate jdbc,
            ControlledPilotExternalEffectGuard pilot, TenantDatabaseContext databaseContext,
            boolean enabled, String allowedBusinessId, String allowedPhoneId,
            String allowedAccountSid, boolean demoEnabled) {
        this(jdbc, pilot, databaseContext, enabled, allowedBusinessId,
                allowedPhoneId, allowedAccountSid, demoEnabled, false);
    }

    public boolean allows(PhoneNumber phone) {
        if (!enabled || phone == null || phone.getId() == null || phone.getBusinessId() == null
                || !"VERIFIED_EXISTING".equals(phone.getTwilioBindingSource())
                || phone.getVoiceVerifiedAt() == null
                || !"TWILIO".equalsIgnoreCase(phone.getProvider()) || !phone.isActive()
                || !phone.getBusinessId().toString().equals(allowedBusinessId)
                || !phone.getId().toString().equals(allowedPhoneId)
                || phone.getTwilioAccountSid() == null
                || !phone.getTwilioAccountSid().equals(allowedAccountSid)
                || phone.getExternalId() == null || phone.getPhoneNumber() == null
                || databaseContext.currentOrDenied().mode() != TenantDatabaseContext.Mode.SYSTEM) {
            return false;
        }
        try {
            // NOT_PILOT_MANAGED is "allowed" for legacy tenants; explicitly reject
            // it rather than treating the global pilot switch as a universal kill switch.
            if (!allowsScopedControl(phone)) {
                return false;
            }

            // Every component of the identity and human approval must be matched
            // against system-only, immutable carrier and platform audit evidence.
            boolean dualApproval = Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT EXISTS (
                        SELECT 1
                          FROM public.twilio_existing_enrollment_claim c
                          JOIN public.twilio_existing_enrollment_request r
                            ON r.change_id = c.change_id
                         WHERE c.phone_number_id = ?
                           AND c.business_id = ?
                           AND c.destination_e164 = ?
                           AND c.owner_account_sid = ?
                           AND c.phone_sid = ?
                           AND r.phone_number_id = c.phone_number_id
                           AND r.business_id = c.business_id
                           AND r.destination_e164 = c.destination_e164
                           AND r.owner_account_sid = c.owner_account_sid
                           AND r.requester_id = c.requester_id
                           AND r.approver_id = c.approver_id
                           AND c.requester_id <> c.approver_id
                           AND r.state = 'APPLIED'
                           AND r.approved_at IS NOT NULL
                           AND r.executed_at IS NOT NULL
                           AND EXISTS (
                               SELECT 1 FROM public.twilio_existing_enrollment_audit a
                                WHERE a.change_id = c.change_id
                                  AND a.actor_id = c.approver_id
                                  AND a.action = 'EXECUTED'
                                  AND a.outcome = 'APPLIED'
                           )
                    )
                    """, Boolean.class, phone.getId(), phone.getBusinessId(),
                    phone.getPhoneNumber(), phone.getTwilioAccountSid(), phone.getExternalId()));
            return dualApproval || (demoEnabled && singleOperatorDemoEnabled
                    && "demo-isolated".equals(environmentName)
                    && hasSingleOperatorProof(phone));
        } catch (RuntimeException ignored) {
            // Absent tables, RLS denial, ambiguous configuration, DB outage,
            // or unexpected pilot state must never fail open or leak SQL details.
            return false;
        }
    }

    private boolean hasSingleOperatorProof(PhoneNumber phone) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM public.twilio_single_operator_demo_enrollment s
                    JOIN public.twilio_single_operator_demo_audit a
                      ON a.change_id = s.change_id AND a.actor_id = s.actor_id
                    JOIN public.app_user u
                      ON u.id = s.actor_id AND u.active AND u.business_id IS NULL
                    JOIN public.user_role ur ON ur.user_id = u.id
                    JOIN public.role r ON r.id = ur.role_id AND r.code = 'PLATFORM_ADMIN'
                    WHERE s.phone_number_id = ? AND s.business_id = ?
                      AND s.destination_e164 = ? AND s.owner_account_sid = ?
                      AND s.phone_sid = ? AND s.state = 'APPLIED'
                      AND s.carrier_verified_at IS NOT NULL
                      AND s.confirmed_at IS NOT NULL
                      AND a.action = 'CONFIRMED'
                )
                """, Boolean.class, phone.getId(), phone.getBusinessId(),
                phone.getPhoneNumber(), phone.getTwilioAccountSid(),
                phone.getExternalId()));
    }

    private boolean allowsScopedControl(PhoneNumber phone) {
        if (demoEnabled) {
            // A DEMO-specific route is scoped by the existing exact tenant/phone/account pins
            // and the same durable two-human enrollment audit. It never opens a global flag.
            return Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT EXISTS (SELECT 1 FROM business b
                        WHERE b.id = ? AND (b.mode = 'DEMO'
                            OR (b.mode = 'CUSTOMER' AND ?))
                          AND b.status = 'ACTIVE'
                          AND NOT EXISTS (SELECT 1 FROM pilot_launch_control p
                                          WHERE p.business_id = b.id))
                    """, Boolean.class, phone.getBusinessId(), scopedCustomerDemoEnabled));
        }
        var decision = pilot.evaluate(phone.getBusinessId(),
                ControlledPilotExternalEffectGuard.Effect.VOICE);
        return decision.pilotManaged() && decision.allowed()
                && "RUNNING".equals(decision.status())
                && pilot.globalExternalEffectsEnabled();
    }
}

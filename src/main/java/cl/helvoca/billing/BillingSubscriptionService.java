package cl.helvoca.billing;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

@Service
public class BillingSubscriptionService {
    private static final String PROVIDER = "mercadopago";
    private static final int PAST_DUE_GRACE_DAYS = 3;

    private final BusinessSubscriptionRepository subscriptions;
    private final SubscriptionPaymentGateway gateway;
    private final MercadoPagoProperties properties;
    private final JdbcTemplate jdbc;

    public BillingSubscriptionService(BusinessSubscriptionRepository subscriptions,
                                      SubscriptionPaymentGateway gateway,
                                      MercadoPagoProperties properties,
                                      JdbcTemplate jdbc) {
        this.subscriptions = subscriptions;
        this.gateway = gateway;
        this.properties = properties;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public BillingStatus status(UUID businessId) {
        BusinessSubscription subscription = subscriptions.findByBusinessId(businessId)
                .orElseThrow(() -> new IllegalStateException("Business subscription is not initialized"));
        return snapshot(subscription);
    }

    @Transactional
    public CheckoutResponse createCheckout(UUID businessId, String payerEmail, String publicPlanCode) {
        if (!properties.checkoutConfigured()) throw new IllegalStateException("Mercado Pago checkout is disabled or incomplete");
        PlanCode plan = resolvePublicPlan(publicPlanCode);
        if (plan.isCustomPricing()) throw new IllegalArgumentException("Enterprise requiere cotización personalizada.");

        lockBusiness(businessId);
        BusinessSubscription subscription = subscriptions.findByBusinessId(businessId)
                .orElseThrow(() -> new IllegalStateException("Business subscription is not initialized"));

        if (PROVIDER.equals(subscription.getBillingProvider())
                && plan == subscription.getPendingPlanCode()
                && notBlank(subscription.getExternalSubscriptionId())
                && notBlank(subscription.getBillingCheckoutUrl())) {
            return new CheckoutResponse(subscription.getExternalSubscriptionId(), subscription.getBillingCheckoutUrl(),
                    plan.getPublicCode(), plan.getDisplayName(), plan.getMonthlyPriceClp(), true);
        }

        SubscriptionPaymentGateway.Checkout checkout = gateway.createCheckout(businessId, payerEmail, plan);
        subscription.setBillingProvider(PROVIDER);
        subscription.setPendingPlanCode(plan);
        subscription.setExternalSubscriptionId(checkout.subscriptionId());
        subscription.setBillingCheckoutUrl(checkout.checkoutUrl());
        subscriptions.saveAndFlush(subscription);
        return new CheckoutResponse(checkout.subscriptionId(), checkout.checkoutUrl(), plan.getPublicCode(),
                plan.getDisplayName(), plan.getMonthlyPriceClp(), false);
    }

    @Transactional
    public BillingStatus refresh(UUID businessId) {
        if (!properties.checkoutConfigured()) throw new IllegalStateException("Mercado Pago checkout is disabled or incomplete");
        lockBusiness(businessId);
        BusinessSubscription local = subscriptions.findByBusinessId(businessId)
                .orElseThrow(() -> new IllegalStateException("Business subscription is not initialized"));

        if (!PROVIDER.equals(local.getBillingProvider()) || !notBlank(local.getExternalSubscriptionId())) {
            return snapshot(local);
        }

        SubscriptionPaymentGateway.RemoteSubscription remote = gateway.getSubscription(local.getExternalSubscriptionId());
        requireExpectedReference(local, remote.externalReference());
        applyRemoteSubscription(local, remote);
        return snapshot(local);
    }

    @Transactional
    public void reconcileSubscription(String externalSubscriptionId) {
        SubscriptionPaymentGateway.RemoteSubscription remote = gateway.getSubscription(externalSubscriptionId);
        BusinessSubscription local = subscriptions.findByExternalSubscriptionId(remote.id())
                .orElseThrow(() -> new IllegalArgumentException("Unknown Mercado Pago subscription"));
        requireExpectedReference(local, remote.externalReference());
        applyRemoteSubscription(local, remote);
    }

    @Transactional
    public void reconcileAuthorizedPayment(String externalInvoiceId) {
        SubscriptionPaymentGateway.RemoteInvoice invoice = gateway.getInvoice(externalInvoiceId);
        if (invoice.subscriptionId() == null || invoice.subscriptionId().isBlank()) {
            throw new IllegalArgumentException("Mercado Pago invoice has no subscription id");
        }
        BusinessSubscription local = subscriptions.findByExternalSubscriptionId(invoice.subscriptionId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown Mercado Pago subscription"));

        String status = normalized(invoice.status());
        if ("approved".equals(status) || "processed".equals(status)) {
            Instant start = invoice.debitDate() == null ? Instant.now() : invoice.debitDate().toInstant();
            local.setStatus(SubscriptionStatus.ACTIVE);
            local.setCurrentPeriodStart(start);
            local.setCurrentPeriodEnd(start.atOffset(ZoneOffset.UTC).plusMonths(1).toInstant());
            local.setGraceUntil(null);
            if (local.getPendingPlanCode() != null) {
                local.setPlanCode(local.getPendingPlanCode());
                local.setPendingPlanCode(null);
                local.setBillingCheckoutUrl(null);
            }
            subscriptions.saveAndFlush(local);
            return;
        }

        if ("rejected".equals(status) || "cancelled".equals(status) || "canceled".equals(status)) {
            Instant now = Instant.now();
            local.setStatus(SubscriptionStatus.PAST_DUE);
            local.setGraceUntil(now.plus(PAST_DUE_GRACE_DAYS, ChronoUnit.DAYS));
            subscriptions.saveAndFlush(local);
        }
    }

    private void applyRemoteSubscription(BusinessSubscription local,
                                         SubscriptionPaymentGateway.RemoteSubscription remote) {
        String status = normalized(remote.status());
        switch (status) {
            case "authorized" -> {
                activate(local, remote.nextPaymentDate() == null ? null : remote.nextPaymentDate().toInstant());
                subscriptions.saveAndFlush(local);
            }
            case "paused" -> {
                local.setStatus(SubscriptionStatus.SUSPENDED);
                local.setGraceUntil(null);
                subscriptions.saveAndFlush(local);
            }
            case "cancelled", "canceled" -> {
                local.setStatus(SubscriptionStatus.CANCELED);
                local.setGraceUntil(null);
                local.setPendingPlanCode(null);
                local.setBillingCheckoutUrl(null);
                subscriptions.saveAndFlush(local);
            }
            case "pending" -> {
                // Provider has not authorized the subscription yet. Never activate from browser state alone.
            }
            default -> {
                // Unknown provider states fail closed and leave the current local entitlement unchanged.
            }
        }
    }

    private BillingStatus snapshot(BusinessSubscription local) {
        PlanCode current = local.getPlanCode();
        PlanCode pending = local.getPendingPlanCode();
        boolean awaitingProviderVerification = pending != null && notBlank(local.getExternalSubscriptionId());
        return new BillingStatus(
                blankToNull(local.getBillingProvider()),
                properties.isEnabled(),
                properties.checkoutConfigured(),
                current.getPublicCode(),
                current.getDisplayName(),
                current.getMonthlyPriceClp(),
                local.getStatus().name(),
                pending == null ? null : pending.getPublicCode(),
                pending == null ? null : pending.getDisplayName(),
                pending == null ? null : pending.getMonthlyPriceClp(),
                pending == null ? null : blankToNull(local.getBillingCheckoutUrl()),
                awaitingProviderVerification);
    }

    private void activate(BusinessSubscription local, Instant suggestedEnd) {
        Instant now = Instant.now();
        if (local.getPendingPlanCode() != null) {
            local.setPlanCode(local.getPendingPlanCode());
            local.setPendingPlanCode(null);
        }
        local.setStatus(SubscriptionStatus.ACTIVE);
        local.setCurrentPeriodStart(now);
        Instant defaultEnd = now.atOffset(ZoneOffset.UTC).plusMonths(1).toInstant();
        local.setCurrentPeriodEnd(suggestedEnd != null && suggestedEnd.isAfter(now) ? suggestedEnd : defaultEnd);
        local.setGraceUntil(null);
        local.setBillingCheckoutUrl(null);
    }

    private static PlanCode resolvePublicPlan(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("plan is required");
        return Arrays.stream(PlanCode.values())
                .filter(plan -> plan.getPublicCode().equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown plan"));
    }

    private static void requireExpectedReference(BusinessSubscription local, String reference) {
        PlanCode expectedPlan = local.getPendingPlanCode() == null ? local.getPlanCode() : local.getPendingPlanCode();
        String expected = "helvoca:" + local.getBusinessId() + ":" + expectedPlan.name();
        if (!expected.equals(reference)) throw new IllegalStateException("Mercado Pago external reference mismatch");
    }

    private void lockBusiness(UUID businessId) {
        long msb = businessId.getMostSignificantBits();
        long lsb = businessId.getLeastSignificantBits();
        int key1 = (int) (msb ^ (msb >>> 32));
        int key2 = (int) (lsb ^ (lsb >>> 32));
        jdbc.execute("SELECT pg_advisory_xact_lock(" + key1 + "," + key2 + ")");
    }

    private static boolean notBlank(String value) { return value != null && !value.isBlank(); }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String normalized(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }

    public record CheckoutResponse(String subscriptionId, String checkoutUrl, String planCode,
                                   String planName, int monthlyPriceClp, boolean reused) {}

    public record BillingStatus(
            String provider,
            boolean billingEnabled,
            boolean checkoutConfigured,
            String currentPlanCode,
            String currentPlanName,
            int currentMonthlyPriceClp,
            String subscriptionStatus,
            String pendingPlanCode,
            String pendingPlanName,
            Integer pendingMonthlyPriceClp,
            String checkoutUrl,
            boolean awaitingProviderVerification) {}
}

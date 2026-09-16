package cl.helvoca.billing;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
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
    private final CommercialPlanCatalogService catalog;

    public BillingSubscriptionService(BusinessSubscriptionRepository subscriptions,
                                      SubscriptionPaymentGateway gateway,
                                      MercadoPagoProperties properties,
                                      JdbcTemplate jdbc,
                                      CommercialPlanCatalogService catalog) {
        this.subscriptions = subscriptions;
        this.gateway = gateway;
        this.properties = properties;
        this.jdbc = jdbc;
        this.catalog = catalog;
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
        CommercialPlanCatalogService.Plan plan = catalog.findActiveByPublicCode(publicPlanCode);
        if (plan.customPricing()) throw new IllegalArgumentException("Enterprise requiere cotización personalizada.");
        PaymentPlan paymentPlan = new PaymentPlan(plan.code(), plan.displayName(), plan.monthlyPriceClp(), plan.customPricing());

        lockBusiness(businessId);
        BusinessSubscription subscription = subscriptions.findByBusinessId(businessId)
                .orElseThrow(() -> new IllegalStateException("Business subscription is not initialized"));

        if (PROVIDER.equals(subscription.getBillingProvider())
                && plan.code().equalsIgnoreCase(subscription.getPendingPlanCode())
                && notBlank(subscription.getExternalSubscriptionId())
                && notBlank(subscription.getBillingCheckoutUrl())) {
            return new CheckoutResponse(subscription.getExternalSubscriptionId(), subscription.getBillingCheckoutUrl(),
                    plan.publicCode(), plan.displayName(), requireFixedPrice(plan), true);
        }

        SubscriptionPaymentGateway.Checkout checkout = gateway.createCheckout(businessId, payerEmail, paymentPlan);
        subscription.setBillingProvider(PROVIDER);
        subscription.setPendingPlanCode(plan.code());
        subscription.setExternalSubscriptionId(checkout.subscriptionId());
        subscription.setBillingCheckoutUrl(checkout.checkoutUrl());
        subscriptions.saveAndFlush(subscription);
        return new CheckoutResponse(checkout.subscriptionId(), checkout.checkoutUrl(), plan.publicCode(),
                plan.displayName(), requireFixedPrice(plan), false);
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

        String expectedExternalId = local.getExternalSubscriptionId();
        SubscriptionPaymentGateway.RemoteSubscription remote = gateway.getSubscription(expectedExternalId);
        if (!expectedExternalId.equals(remote.id())) {
            throw new IllegalStateException("Mercado Pago subscription id mismatch");
        }
        requireExpectedReference(local, remote.externalReference());
        applyRemoteSubscription(local, remote);
        return snapshot(local);
    }

    @Transactional
    public void reconcileSubscription(String externalSubscriptionId) {
        SubscriptionPaymentGateway.RemoteSubscription remote = gateway.getSubscription(externalSubscriptionId);
        if (!externalSubscriptionId.equals(remote.id())) {
            throw new IllegalStateException("Mercado Pago subscription id mismatch");
        }
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
                catalog.requireByCode(local.getPendingPlanCode());
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
                // The recurring mandate is linked, but service entitlements change only after a verified paid invoice.
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
        CommercialPlanCatalogService.Plan current = catalog.requireByCode(local.getPlanCode());
        CommercialPlanCatalogService.Plan pending = local.getPendingPlanCode() == null
                ? null : catalog.requireByCode(local.getPendingPlanCode());
        boolean awaitingProviderVerification = pending != null && notBlank(local.getExternalSubscriptionId());
        return new BillingStatus(
                blankToNull(local.getBillingProvider()),
                properties.isEnabled(),
                properties.checkoutConfigured(),
                current.publicCode(),
                current.displayName(),
                current.monthlyPriceClp() == null ? 0 : current.monthlyPriceClp(),
                local.getStatus().name(),
                pending == null ? null : pending.publicCode(),
                pending == null ? null : pending.displayName(),
                pending == null ? null : pending.monthlyPriceClp(),
                pending == null ? null : blankToNull(local.getBillingCheckoutUrl()),
                awaitingProviderVerification);
    }

    private static void requireExpectedReference(BusinessSubscription local, String reference) {
        String expectedPlan = local.getPendingPlanCode() == null ? local.getPlanCode() : local.getPendingPlanCode();
        if (!notBlank(expectedPlan)) throw new IllegalStateException("Subscription plan is missing");
        String expected = "helvoca:" + local.getBusinessId() + ":" + expectedPlan.trim().toUpperCase(Locale.ROOT);
        if (!expected.equals(reference)) throw new IllegalStateException("Mercado Pago external reference mismatch");
    }

    private static int requireFixedPrice(CommercialPlanCatalogService.Plan plan) {
        if (plan.monthlyPriceClp() == null || plan.monthlyPriceClp() <= 0) {
            throw new IllegalStateException("Commercial plan has no fixed positive checkout price");
        }
        return plan.monthlyPriceClp();
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

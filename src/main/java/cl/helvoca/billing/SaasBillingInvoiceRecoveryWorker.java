package cl.helvoca.billing;

import cl.helvoca.security.TenantDatabaseContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Bounded, read-only provider discovery for SaaS invoices that were not delivered
 * through the provider webhook. It is deliberately disabled unless explicitly enabled.
 */
@Component
public class SaasBillingInvoiceRecoveryWorker {
    private static final Logger log = LoggerFactory.getLogger(SaasBillingInvoiceRecoveryWorker.class);
    private static final String PROVIDER = "mercadopago";
    private static final int OFFSET = 0;
    private static final Set<SubscriptionStatus> ELIGIBLE_STATUSES =
            Set.copyOf(EnumSet.of(SubscriptionStatus.TRIALING,
                    SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE));
    private static final Set<String> RECONCILABLE_PAYMENT_STATUSES =
            Set.of("approved", "rejected", "cancelled", "canceled");

    private final MercadoPagoProperties properties;
    private final BusinessSubscriptionRepository subscriptions;
    private final SubscriptionPaymentGateway gateway;
    private final BillingSubscriptionService billing;
    private final JdbcTemplate jdbc;
    private final TenantDatabaseContext databaseContext;
    private final TransactionOperations readTransactions;
    private final TransactionOperations writeTransactions;

    @Autowired
    public SaasBillingInvoiceRecoveryWorker(MercadoPagoProperties properties,
                                             BusinessSubscriptionRepository subscriptions,
                                             SubscriptionPaymentGateway gateway,
                                             BillingSubscriptionService billing,
                                             JdbcTemplate jdbc,
                                             TenantDatabaseContext databaseContext,
                                             PlatformTransactionManager transactionManager) {
        this(properties, subscriptions, gateway, billing, jdbc, databaseContext,
                readOnlyTransactions(transactionManager), new TransactionTemplate(transactionManager));
    }

    SaasBillingInvoiceRecoveryWorker(MercadoPagoProperties properties,
                                     BusinessSubscriptionRepository subscriptions,
                                     SubscriptionPaymentGateway gateway,
                                     BillingSubscriptionService billing,
                                     JdbcTemplate jdbc,
                                     TenantDatabaseContext databaseContext,
                                     TransactionOperations readTransactions,
                                     TransactionOperations writeTransactions) {
        this.properties = properties;
        this.subscriptions = subscriptions;
        this.gateway = gateway;
        this.billing = billing;
        this.jdbc = jdbc;
        this.databaseContext = databaseContext;
        this.readTransactions = readTransactions;
        this.writeTransactions = writeTransactions;
    }

    @Scheduled(
            initialDelayString = "${HELVOCA_SAAS_BILLING_INVOICE_RECOVERY_INITIAL_DELAY_MS:60000}",
            fixedDelayString = "${HELVOCA_SAAS_BILLING_INVOICE_RECOVERY_DELAY_MS:300000}")
    public void poll() {
        if (!properties.isInvoiceRecoveryEnabled()) return;

        List<BusinessSubscription> candidates;
        try {
            candidates = databaseContext.callAsSystem(() -> readTransactions.execute(status -> {
                List<BusinessSubscription> found = subscriptions.findInvoiceRecoveryCandidates(
                        PROVIDER,
                        ELIGIBLE_STATUSES,
                        PageRequest.of(0, properties.getInvoiceRecoveryPageLimit(),
                                Sort.by(Sort.Direction.ASC, "updatedAt")));
                return found == null ? List.of() : List.copyOf(found);
            }));
        } catch (RuntimeException error) {
            log.warn("SaaS invoice recovery candidate discovery failed; poll skipped: {}",
                    error.getClass().getSimpleName());
            return;
        }

        for (BusinessSubscription candidate : candidates) {
            recoverCandidate(candidate);
        }
    }

    private void recoverCandidate(BusinessSubscription candidate) {
        String externalSubscriptionId = normalized(candidate == null
                ? null : candidate.getExternalSubscriptionId());
        if (externalSubscriptionId == null) return;

        try {
            databaseContext.callAsSystem(() -> writeTransactions.execute(status -> {
                if (!tryClaim(externalSubscriptionId)) return null;

                BusinessSubscription local = subscriptions.findByExternalSubscriptionId(externalSubscriptionId)
                        .orElse(null);
                if (!eligible(local, externalSubscriptionId)) return null;

                int limit = properties.getInvoiceRecoveryPageLimit();
                SubscriptionPaymentGateway.InvoicePage page =
                        gateway.searchInvoices(externalSubscriptionId, OFFSET, limit);
                if (!validPage(page, limit)) return null;

                List<SubscriptionPaymentGateway.RemoteInvoice> invoices = new ArrayList<>();
                for (String invoiceId : new LinkedHashSet<>(page.invoiceIds())) {
                    try {
                        SubscriptionPaymentGateway.RemoteInvoice invoice = gateway.getInvoice(invoiceId);
                        if (validInvoice(invoiceId, invoice, externalSubscriptionId)
                                && RECONCILABLE_PAYMENT_STATUSES.contains(normalizedStatus(invoice.paymentStatus()))) {
                            invoices.add(invoice);
                        }
                    } catch (RuntimeException error) {
                        log.warn("SaaS invoice recovery invoice lookup failed; continuing: {}",
                                error.getClass().getSimpleName());
                    }
                }
                invoices.sort(Comparator
                        .comparing(SubscriptionPaymentGateway.RemoteInvoice::debitDate,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(SubscriptionPaymentGateway.RemoteInvoice::id));

                for (SubscriptionPaymentGateway.RemoteInvoice invoice : invoices) {
                    try {
                        billing.reconcileAuthorizedPayment(invoice.id());
                    } catch (RuntimeException error) {
                        log.warn("SaaS invoice recovery reconciliation failed; continuing: {}",
                                error.getClass().getSimpleName());
                    }
                }
                return null;
            }));
        } catch (RuntimeException error) {
            log.warn("SaaS invoice recovery candidate failed; continuing poll: {}",
                    error.getClass().getSimpleName());
        }
    }

    private boolean tryClaim(String externalSubscriptionId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT pg_try_advisory_xact_lock(hashtextextended(?, 0))",
                Boolean.class,
                externalSubscriptionId));
    }

    private static boolean eligible(BusinessSubscription subscription, String externalSubscriptionId) {
        return subscription != null
                && PROVIDER.equalsIgnoreCase(normalized(subscription.getBillingProvider()))
                && externalSubscriptionId.equals(normalized(subscription.getExternalSubscriptionId()))
                && subscription.getStatus() != null
                && ELIGIBLE_STATUSES.contains(subscription.getStatus());
    }

    private static boolean validPage(SubscriptionPaymentGateway.InvoicePage page, int limit) {
        return page != null
                && page.total() >= 0
                && page.offset() == OFFSET
                && page.limit() == limit
                && page.invoiceIds() != null
                && page.invoiceIds().size() <= limit
                && page.total() >= page.invoiceIds().size()
                && page.invoiceIds().stream().allMatch(value -> normalized(value) != null);
    }

    private static boolean validInvoice(String expectedId,
                                        SubscriptionPaymentGateway.RemoteInvoice invoice,
                                        String expectedSubscriptionId) {
        return invoice != null
                && expectedId.equals(normalized(invoice.id()))
                && expectedSubscriptionId.equals(normalized(invoice.subscriptionId()));
    }

    private static TransactionOperations readOnlyTransactions(PlatformTransactionManager manager) {
        TransactionTemplate template = new TransactionTemplate(manager);
        template.setReadOnly(true);
        return template;
    }

    private static String normalized(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private static String normalizedStatus(String value) {
        String normalized = normalized(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }
}

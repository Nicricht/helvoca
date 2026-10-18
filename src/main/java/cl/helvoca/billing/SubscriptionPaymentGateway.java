package cl.helvoca.billing;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface SubscriptionPaymentGateway {
    Checkout createCheckout(UUID businessId, String payerEmail, PaymentPlan plan);
    RemoteSubscription getSubscription(String externalSubscriptionId);
    RemoteInvoice getInvoice(String externalInvoiceId);

    // Read-only discovery. Does not reconcile or grant any entitlements.
    InvoicePage searchInvoices(String externalSubscriptionId, int offset, int limit);

    record InvoicePage(int total, int offset, int limit, List<String> invoiceIds) {}

    record Checkout(String subscriptionId, String checkoutUrl, String status, String externalReference) {}
    record RemoteSubscription(String id, String status, String externalReference, OffsetDateTime nextPaymentDate) {}
    record RemoteInvoice(String id, String subscriptionId, String invoiceStatus, String paymentStatus,
                         String summarized, OffsetDateTime debitDate) {}
}

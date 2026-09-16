package cl.helvoca.billing;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface SubscriptionPaymentGateway {
    Checkout createCheckout(UUID businessId, String payerEmail, PaymentPlan plan);
    RemoteSubscription getSubscription(String externalSubscriptionId);
    RemoteInvoice getInvoice(String externalInvoiceId);

    record Checkout(String subscriptionId, String checkoutUrl, String status, String externalReference) {}
    record RemoteSubscription(String id, String status, String externalReference, OffsetDateTime nextPaymentDate) {}
    record RemoteInvoice(String id, String subscriptionId, String status, String summarized, OffsetDateTime debitDate) {}
}

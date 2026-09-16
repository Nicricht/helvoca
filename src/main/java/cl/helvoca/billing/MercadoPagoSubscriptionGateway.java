package cl.helvoca.billing;

import com.mercadopago.client.invoice.InvoiceClient;
import com.mercadopago.client.preapproval.PreApprovalAutoRecurringCreateRequest;
import com.mercadopago.client.preapproval.PreapprovalClient;
import com.mercadopago.client.preapproval.PreapprovalCreateRequest;
import com.mercadopago.core.MPRequestOptions;
import com.mercadopago.resources.invoice.Invoice;
import com.mercadopago.resources.preapproval.Preapproval;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class MercadoPagoSubscriptionGateway implements SubscriptionPaymentGateway {
    private final MercadoPagoProperties properties;

    public MercadoPagoSubscriptionGateway(MercadoPagoProperties properties) {
        this.properties = properties;
    }

    @Override
    public Checkout createCheckout(UUID businessId, String payerEmail, PaymentPlan plan) {
        if (!properties.checkoutConfigured()) throw new IllegalStateException("Mercado Pago checkout is not configured");
        if (plan == null) throw new IllegalArgumentException("Payment plan is required");
        if (plan.customPricing()) throw new IllegalArgumentException("Enterprise requires a custom commercial agreement");
        if (plan.monthlyPriceClp() == null || plan.monthlyPriceClp() <= 0) {
            throw new IllegalArgumentException("Payment plan requires a fixed positive price");
        }
        if (payerEmail == null || payerEmail.isBlank()) throw new IllegalArgumentException("Payer email is required");

        try {
            PreApprovalAutoRecurringCreateRequest recurring = PreApprovalAutoRecurringCreateRequest.builder()
                    .frequency(1)
                    .frequencyType("months")
                    .transactionAmount(BigDecimal.valueOf(plan.monthlyPriceClp()))
                    .currencyId("CLP")
                    .build();
            String reference = "helvoca:" + businessId + ":" + plan.code();
            PreapprovalCreateRequest request = PreapprovalCreateRequest.builder()
                    .payerEmail(payerEmail.trim())
                    .backUrl(properties.getBackUrl())
                    .reason("Helvoca " + plan.displayName())
                    .externalReference(reference)
                    .status("pending")
                    .autoRecurring(recurring)
                    .build();
            Preapproval result = new PreapprovalClient().create(request, options());
            String checkoutUrl = firstNonBlank(result.getInitPoint(), result.getSandboxInitPoint());
            if (result.getId() == null || result.getId().isBlank() || checkoutUrl == null) {
                throw new IllegalStateException("Mercado Pago returned an incomplete subscription checkout");
            }
            return new Checkout(result.getId(), checkoutUrl, result.getStatus(), result.getExternalReference());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Mercado Pago checkout failed", e);
        }
    }

    @Override
    public RemoteSubscription getSubscription(String externalSubscriptionId) {
        try {
            Preapproval result = new PreapprovalClient().get(externalSubscriptionId, options());
            return new RemoteSubscription(result.getId(), result.getStatus(), result.getExternalReference(), result.getNextPaymentDate());
        } catch (Exception e) {
            throw new IllegalStateException("Mercado Pago subscription lookup failed", e);
        }
    }

    @Override
    public RemoteInvoice getInvoice(String externalInvoiceId) {
        try {
            Invoice invoice = new InvoiceClient().get(Long.parseLong(externalInvoiceId), options());
            String paymentStatus = invoice.getPayment() == null ? invoice.getStatus() : invoice.getPayment().getStatus();
            return new RemoteInvoice(String.valueOf(invoice.getId()), invoice.getPreapprovalId(), paymentStatus,
                    invoice.getSummarized(), invoice.getDebitDate());
        } catch (Exception e) {
            throw new IllegalStateException("Mercado Pago invoice lookup failed", e);
        }
    }

    private MPRequestOptions options() {
        return MPRequestOptions.builder()
                .accessToken(properties.getAccessToken())
                .connectionTimeout(5_000)
                .socketTimeout(10_000)
                .maxRetries(2)
                .build();
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) return first;
        if (second != null && !second.isBlank()) return second;
        return null;
    }
}

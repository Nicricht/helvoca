package cl.helvoca.billing;

import com.mercadopago.client.invoice.InvoiceClient;
import com.mercadopago.core.MPRequestOptions;
import com.mercadopago.resources.invoice.Invoice;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MercadoPagoSubscriptionGatewayTest {

    @Test
    void getInvoiceKeepsInvoiceLifecycleAndApprovedPaymentStatusDistinct() throws Exception {
        MercadoPagoProperties properties = configuredProperties();
        Invoice invoice = mock(Invoice.class, RETURNS_DEEP_STUBS);
        OffsetDateTime debitDate = OffsetDateTime.of(2026, 9, 27, 18, 0, 0, 0, ZoneOffset.UTC);
        when(invoice.getId()).thenReturn(123L);
        when(invoice.getPreapprovalId()).thenReturn("pre-123");
        when(invoice.getStatus()).thenReturn("processed");
        when(invoice.getPayment().getStatus()).thenReturn("approved");
        when(invoice.getSummarized()).thenReturn("paid");
        when(invoice.getDebitDate()).thenReturn(debitDate);

        try (MockedConstruction<InvoiceClient> clients = mockConstruction(
                InvoiceClient.class,
                (client, context) -> when(client.get(eq(123L), any(MPRequestOptions.class))).thenReturn(invoice))) {
            var remote = new MercadoPagoSubscriptionGateway(properties).getInvoice("123");

            assertEquals("123", remote.id());
            assertEquals("pre-123", remote.subscriptionId());
            assertEquals("processed", remote.invoiceStatus());
            assertEquals("approved", remote.paymentStatus());
            assertEquals("paid", remote.summarized());
            assertEquals(debitDate, remote.debitDate());
            assertEquals(1, clients.constructed().size());
        }
    }

    @Test
    void getInvoiceDoesNotTreatProcessedInvoiceAsApprovedWhenPaymentIsMissing() throws Exception {
        MercadoPagoProperties properties = configuredProperties();
        Invoice invoice = mock(Invoice.class);
        when(invoice.getId()).thenReturn(124L);
        when(invoice.getPreapprovalId()).thenReturn("pre-124");
        when(invoice.getStatus()).thenReturn("processed");
        when(invoice.getPayment()).thenReturn(null);

        try (MockedConstruction<InvoiceClient> ignored = mockConstruction(
                InvoiceClient.class,
                (client, context) -> when(client.get(eq(124L), any(MPRequestOptions.class))).thenReturn(invoice))) {
            var remote = new MercadoPagoSubscriptionGateway(properties).getInvoice("124");

            assertEquals("processed", remote.invoiceStatus());
            assertNull(remote.paymentStatus());
        }
    }

    @Test
    void invalidInvoiceIdentifierFailsClosed() {
        var gateway = new MercadoPagoSubscriptionGateway(configuredProperties());

        IllegalStateException error = assertThrows(
                IllegalStateException.class, () -> gateway.getInvoice("not-a-number"));

        assertEquals("Mercado Pago invoice lookup failed", error.getMessage());
        assertInstanceOf(NumberFormatException.class, error.getCause());
    }

    private static MercadoPagoProperties configuredProperties() {
        MercadoPagoProperties properties = new MercadoPagoProperties();
        properties.setEnabled(true);
        properties.setAccessToken("test-token");
        properties.setBackUrl("https://example.test/billing/return");
        return properties;
    }
}

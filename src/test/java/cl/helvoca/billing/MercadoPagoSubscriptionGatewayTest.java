package cl.helvoca.billing;

import com.mercadopago.client.invoice.InvoiceClient;
import com.mercadopago.core.MPRequestOptions;
import com.mercadopago.net.MPSearchRequest;
import com.mercadopago.net.MPResultsResourcesPage;
import com.mercadopago.resources.ResultsPaging;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.invoice.Invoice;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

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


    @Test
    void searchesOneBoundedInvoicePageOnlyForRequestedPreapproval() throws Exception {
        var properties = configuredProperties();
        Invoice invoice = mock(Invoice.class);
        when(invoice.getId()).thenReturn(611L);
        when(invoice.getPreapprovalId()).thenReturn("pre-tenant-a");
        @SuppressWarnings("unchecked")
        MPResultsResourcesPage<Invoice> result = mock(MPResultsResourcesPage.class);
        ResultsPaging paging = mock(ResultsPaging.class);
        when(paging.getTotal()).thenReturn(21);
        when(paging.getOffset()).thenReturn(20);
        when(paging.getLimit()).thenReturn(1);
        when(result.getPaging()).thenReturn(paging);
        when(result.getResults()).thenReturn(List.of(invoice));
        try (MockedConstruction<InvoiceClient> clients = mockConstruction(InvoiceClient.class,
                (client, context) -> when(client.search(any(MPSearchRequest.class),
                        any(MPRequestOptions.class))).thenReturn(result))) {
            var page = new MercadoPagoSubscriptionGateway(properties).searchInvoices("pre-tenant-a", 20, 1);
            assertEquals(21, page.total());
            assertEquals(20, page.offset());
            assertEquals(1, page.limit());
            assertEquals(List.of("611"), page.invoiceIds());
            var request = org.mockito.ArgumentCaptor.forClass(MPSearchRequest.class);
            verify(clients.constructed().getFirst()).search(request.capture(), any(MPRequestOptions.class));
            assertEquals("pre-tenant-a", request.getValue().getParameters().get("preapproval_id"));
            assertEquals(20, request.getValue().getParameters().get("offset"));
            assertEquals(1, request.getValue().getParameters().get("limit"));
        }
    }

    @Test
    void blankSubscriptionAndUnboundedPagesAreRejectedBeforeProviderAccess() {
        var gateway = new MercadoPagoSubscriptionGateway(configuredProperties());
        assertThrows(IllegalArgumentException.class, () -> gateway.searchInvoices(null, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> gateway.searchInvoices(" ", 0, 1));
        assertThrows(IllegalArgumentException.class, () -> gateway.searchInvoices("pre", -1, 1));
        assertThrows(IllegalArgumentException.class, () -> gateway.searchInvoices("pre", 0, 0));
        assertThrows(IllegalArgumentException.class, () -> gateway.searchInvoices("pre", 0, 21));
    }

    @Test
    void rejectsInvoiceBelongingToAnotherSubscriptionWithoutReconciling() throws Exception {
        Invoice invoice = mock(Invoice.class);
        when(invoice.getId()).thenReturn(77L);
        when(invoice.getPreapprovalId()).thenReturn("pre-other");
        @SuppressWarnings("unchecked")
        MPResultsResourcesPage<Invoice> result = mock(MPResultsResourcesPage.class);
        ResultsPaging paging = mock(ResultsPaging.class);
        when(paging.getTotal()).thenReturn(1);
        when(paging.getOffset()).thenReturn(0);
        when(paging.getLimit()).thenReturn(20);
        when(result.getPaging()).thenReturn(paging);
        when(result.getResults()).thenReturn(List.of(invoice));
        try (MockedConstruction<InvoiceClient> clients = mockConstruction(InvoiceClient.class,
                (client, context) -> when(client.search(any(MPSearchRequest.class),
                        any(MPRequestOptions.class))).thenReturn(result))) {
            assertThrows(IllegalStateException.class, () ->
                    new MercadoPagoSubscriptionGateway(configuredProperties()).searchInvoices("pre-a", 0, 20));
            assertEquals(1, clients.constructed().size());
        }
    }

    @Test
    void refusesIncompleteAndInconsistentProviderPages() throws Exception {
        @SuppressWarnings("unchecked")
        MPResultsResourcesPage<Invoice> result = mock(MPResultsResourcesPage.class);
        ResultsPaging paging = mock(ResultsPaging.class);
        when(result.getPaging()).thenReturn(paging);
        when(result.getResults()).thenReturn(List.of());
        when(paging.getTotal()).thenReturn(5);
        when(paging.getOffset()).thenReturn(8);
        when(paging.getLimit()).thenReturn(20);
        try (MockedConstruction<InvoiceClient> clients = mockConstruction(InvoiceClient.class,
                (client, context) -> when(client.search(any(MPSearchRequest.class),
                        any(MPRequestOptions.class))).thenReturn(result))) {
            var gateway = new MercadoPagoSubscriptionGateway(configuredProperties());
            assertThrows(IllegalStateException.class, () -> gateway.searchInvoices("pre-a", 0, 20));
            when(paging.getOffset()).thenReturn(0);
            when(paging.getTotal()).thenReturn(-1);
            assertThrows(IllegalStateException.class, () -> gateway.searchInvoices("pre-a", 0, 20));
            when(paging.getTotal()).thenReturn(0);
            when(result.getResults()).thenReturn(null);
            assertThrows(IllegalStateException.class, () -> gateway.searchInvoices("pre-a", 0, 20));
            when(result.getResults()).thenReturn(List.of());
            when(result.getPaging()).thenReturn(null);
            assertThrows(IllegalStateException.class, () -> gateway.searchInvoices("pre-a", 0, 20));
            assertEquals(4, clients.constructed().size());
        }
    }

    @Test
    void wrapsSdkCheckedFailureWithoutLeakingProviderPayload() throws Exception {
        try (MockedConstruction<InvoiceClient> clients = mockConstruction(InvoiceClient.class,
                (client, context) -> when(client.search(any(MPSearchRequest.class),
                        any(MPRequestOptions.class))).thenThrow(new MPException("private-provider-error")))) {
            var gateway = new MercadoPagoSubscriptionGateway(configuredProperties());
            var error = assertThrows(IllegalStateException.class,
                    () -> gateway.searchInvoices("pre-a", 0, 20));
            assertEquals("Mercado Pago invoice search failed", error.getMessage());
            assertEquals(1, clients.constructed().size());
        }
    }

    private static MercadoPagoProperties configuredProperties() {
        MercadoPagoProperties properties = new MercadoPagoProperties();
        properties.setEnabled(true);
        properties.setAccessToken("test-token");
        properties.setBackUrl("https://example.test/billing/return");
        return properties;
    }
}

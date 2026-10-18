package cl.helvoca.billing;

import cl.helvoca.security.TenantDatabaseContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.mockito.InOrder;

class SaasBillingInvoiceRecoveryWorkerTest {

    @Test
    void publicConstructorCreatesReadOnlyAndWriteTransactionTemplates() {
        Scenario scenario = new Scenario();

        assertDoesNotThrow(() -> new SaasBillingInvoiceRecoveryWorker(
                scenario.properties, scenario.subscriptions, scenario.gateway, scenario.billing,
                scenario.jdbc, new TenantDatabaseContext(), mock(PlatformTransactionManager.class)));
    }

    @Test
    void disabledByDefaultDoesNotReadPostgresOrMercadoPago() {
        Scenario scenario = new Scenario();

        scenario.worker.poll();

        verifyNoInteractions(scenario.subscriptions, scenario.gateway, scenario.billing,
                scenario.jdbc);
    }


    @Test
    void scanAdvancesBeyondTwentyCandidatesAndResetsAfterShortPage() {
        Scenario scenario = new Scenario();
        scenario.properties.setInvoiceRecoveryEnabled(true);
        List<BusinessSubscription> firstTwenty = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            firstTwenty.add(subscription(null, SubscriptionStatus.TRIALING));
        }
        BusinessSubscription twentyFirst = subscription("pre-twenty-first", SubscriptionStatus.ACTIVE);
        when(scenario.subscriptions.findInvoiceRecoveryCandidates(
                eq("mercadopago"), anyCollection(), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    Pageable page = invocation.getArgument(2);
                    return page.getPageNumber() == 0 ? firstTwenty : List.of(twentyFirst);
                });
        when(scenario.jdbc.queryForObject(anyString(), eq(Boolean.class), eq("pre-twenty-first")))
                .thenReturn(true);
        when(scenario.subscriptions.findByExternalSubscriptionId("pre-twenty-first"))
                .thenReturn(Optional.of(twentyFirst));
        when(scenario.gateway.searchInvoices("pre-twenty-first", 0, 20))
                .thenReturn(new SubscriptionPaymentGateway.InvoicePage(0, 0, 20, List.of()));

        scenario.worker.poll();
        scenario.worker.poll();
        scenario.worker.poll();

        ArgumentCaptor<Pageable> pages = ArgumentCaptor.forClass(Pageable.class);
        verify(scenario.subscriptions, times(3)).findInvoiceRecoveryCandidates(
                eq("mercadopago"), anyCollection(), pages.capture());
        assertEquals(List.of(0, 1, 0),
                pages.getAllValues().stream().map(Pageable::getPageNumber).toList());
        verify(scenario.gateway, times(1)).searchInvoices("pre-twenty-first", 0, 20);
        verifyNoInteractions(scenario.billing);
    }

    @Test
    void scanAdvancesPastTwoFullPagesThenWrapsOnEmptyPage() {
        Scenario scenario = new Scenario();
        scenario.properties.setInvoiceRecoveryEnabled(true);
        List<BusinessSubscription> fullPage = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            fullPage.add(subscription(null, SubscriptionStatus.TRIALING));
        }
        when(scenario.subscriptions.findInvoiceRecoveryCandidates(
                eq("mercadopago"), anyCollection(), any(Pageable.class)))
                .thenAnswer(invocation -> ((Pageable) invocation.getArgument(2)).getPageNumber() < 2
                        ? fullPage : List.of());

        scenario.worker.poll();
        scenario.worker.poll();
        scenario.worker.poll();
        scenario.worker.poll();

        ArgumentCaptor<Pageable> pages = ArgumentCaptor.forClass(Pageable.class);
        verify(scenario.subscriptions, times(4)).findInvoiceRecoveryCandidates(
                eq("mercadopago"), anyCollection(), pages.capture());
        assertEquals(List.of(0, 1, 2, 0),
                pages.getAllValues().stream().map(Pageable::getPageNumber).toList());
        verifyNoInteractions(scenario.gateway, scenario.billing, scenario.jdbc);
    }

    @Test
    void boundedDiscoveryDeduplicatesInvoicesRevalidatesTenantAndProcessesInDebitOrder() {
        Scenario scenario = new Scenario();
        scenario.properties.setInvoiceRecoveryEnabled(true);
        BusinessSubscription subscription = subscription("pre-a", SubscriptionStatus.TRIALING);
        when(scenario.subscriptions.findInvoiceRecoveryCandidates(
                eq("mercadopago"), anyCollection(), any(Pageable.class)))
                .thenReturn(List.of(subscription));
        when(scenario.subscriptions.findByExternalSubscriptionId("pre-a"))
                .thenReturn(Optional.of(subscription));
        when(scenario.jdbc.queryForObject(anyString(), eq(Boolean.class), eq("pre-a")))
                .thenReturn(true);
        when(scenario.gateway.searchInvoices("pre-a", 0, 20))
                .thenReturn(new SubscriptionPaymentGateway.InvoicePage(
                        3, 0, 20, List.of("invoice-new", "invoice-old", "invoice-new")));
        when(scenario.gateway.getInvoice("invoice-new"))
                .thenReturn(invoice("invoice-new", "pre-a", "approved", "2026-11-01T00:00:00Z"));
        when(scenario.gateway.getInvoice("invoice-old"))
                .thenReturn(invoice("invoice-old", "pre-a", "rejected", "2026-10-01T00:00:00Z"));

        scenario.worker.poll();

        InOrder order = inOrder(scenario.gateway, scenario.billing);
        order.verify(scenario.gateway).searchInvoices("pre-a", 0, 20);
        order.verify(scenario.gateway).getInvoice("invoice-new");
        order.verify(scenario.gateway).getInvoice("invoice-old");
        order.verify(scenario.billing).reconcileAuthorizedPayment("invoice-old");
        order.verify(scenario.billing).reconcileAuthorizedPayment("invoice-new");
        verify(scenario.gateway, times(1)).getInvoice("invoice-old");
        verify(scenario.gateway, times(1)).getInvoice("invoice-new");
    }

    @Test
    void providerFailureForOneSubscriptionDoesNotStopTheNextCandidate() {
        Scenario scenario = new Scenario();
        scenario.properties.setInvoiceRecoveryEnabled(true);
        BusinessSubscription first = subscription("pre-first", SubscriptionStatus.ACTIVE);
        BusinessSubscription second = subscription("pre-second", SubscriptionStatus.PAST_DUE);
        when(scenario.subscriptions.findInvoiceRecoveryCandidates(
                eq("mercadopago"), anyCollection(), any(Pageable.class)))
                .thenReturn(List.of(first, second));
        when(scenario.subscriptions.findByExternalSubscriptionId(anyString()))
                .thenAnswer(invocation -> Optional.of(
                        "pre-first".equals(invocation.getArgument(0)) ? first : second));
        when(scenario.jdbc.queryForObject(anyString(), eq(Boolean.class), anyString()))
                .thenReturn(true);
        when(scenario.gateway.searchInvoices("pre-first", 0, 20))
                .thenThrow(new IllegalStateException("provider failure"));
        when(scenario.gateway.searchInvoices("pre-second", 0, 20))
                .thenReturn(new SubscriptionPaymentGateway.InvoicePage(
                        1, 0, 20, List.of("invoice-second")));
        when(scenario.gateway.getInvoice("invoice-second"))
                .thenReturn(invoice("invoice-second", "pre-second", "approved", "2026-11-01T00:00:00Z"));

        assertDoesNotThrow(() -> scenario.worker.poll());

        verify(scenario.billing).reconcileAuthorizedPayment("invoice-second");
    }

    @Test
    void mismatchedOrUnsettledInvoicesFailClosedWithoutCallingReconciler() {
        Scenario scenario = new Scenario();
        scenario.properties.setInvoiceRecoveryEnabled(true);
        BusinessSubscription subscription = subscription("pre-a", SubscriptionStatus.TRIALING);
        when(scenario.subscriptions.findInvoiceRecoveryCandidates(
                eq("mercadopago"), anyCollection(), any(Pageable.class)))
                .thenReturn(List.of(subscription));
        when(scenario.subscriptions.findByExternalSubscriptionId("pre-a"))
                .thenReturn(Optional.of(subscription));
        when(scenario.jdbc.queryForObject(anyString(), eq(Boolean.class), eq("pre-a")))
                .thenReturn(true);
        when(scenario.gateway.searchInvoices("pre-a", 0, 20))
                .thenReturn(new SubscriptionPaymentGateway.InvoicePage(
                        2, 0, 20, List.of("invoice-wrong", "invoice-pending")));
        when(scenario.gateway.getInvoice("invoice-wrong"))
                .thenReturn(invoice("invoice-wrong", "pre-other", "approved", "2026-11-01T00:00:00Z"));
        when(scenario.gateway.getInvoice("invoice-pending"))
                .thenReturn(invoice("invoice-pending", "pre-a", "pending", "2026-11-02T00:00:00Z"));

        scenario.worker.poll();

        verifyNoInteractions(scenario.billing);
    }

    @Test
    void candidateAndDiscoveryFailuresFailClosedWithoutProviderAccess() {
        Scenario scenario = new Scenario();
        scenario.properties.setInvoiceRecoveryEnabled(true);
        BusinessSubscription blank = subscription(" ", SubscriptionStatus.TRIALING);
        BusinessSubscription wrongProvider = subscription("pre-wrong-provider", SubscriptionStatus.TRIALING);
        wrongProvider.setBillingProvider("other-provider");
        BusinessSubscription wrongExternalId = subscription("pre-wrong-external", SubscriptionStatus.TRIALING);
        BusinessSubscription localForWrongExternalId = subscription("pre-local-external", SubscriptionStatus.TRIALING);
        BusinessSubscription missingStatus = subscription("pre-missing-status", null);
        BusinessSubscription canceled = subscription("pre-canceled", SubscriptionStatus.CANCELED);
        BusinessSubscription missingLocal = subscription("pre-missing-local", SubscriptionStatus.TRIALING);
        BusinessSubscription leaseBusy = subscription("pre-lease-busy", SubscriptionStatus.ACTIVE);
        List<BusinessSubscription> candidates = new ArrayList<>();
        candidates.add(subscription(null, SubscriptionStatus.TRIALING));
        candidates.add(blank);
        candidates.add(wrongProvider);
        candidates.add(wrongExternalId);
        candidates.add(missingStatus);
        candidates.add(canceled);
        candidates.add(missingLocal);
        candidates.add(leaseBusy);
        when(scenario.subscriptions.findInvoiceRecoveryCandidates(
                eq("mercadopago"), anyCollection(), any(Pageable.class)))
                .thenReturn(candidates);
        when(scenario.subscriptions.findByExternalSubscriptionId(anyString()))
                .thenAnswer(invocation -> switch (invocation.getArgument(0, String.class)) {
                    case "pre-wrong-provider" -> Optional.of(wrongProvider);
                    case "pre-wrong-external" -> Optional.of(localForWrongExternalId);
                    case "pre-missing-status" -> Optional.of(missingStatus);
                    case "pre-canceled" -> Optional.of(canceled);
                    case "pre-missing-local" -> Optional.empty();
                    case "pre-lease-busy" -> Optional.of(leaseBusy);
                    default -> Optional.empty();
                });
        when(scenario.jdbc.queryForObject(anyString(), eq(Boolean.class), anyString()))
                .thenAnswer(invocation -> !"pre-lease-busy".equals(invocation.getArgument(2, String.class)));

        scenario.worker.poll();

        verifyNoInteractions(scenario.gateway, scenario.billing);

        Scenario emptyDiscovery = new Scenario();
        emptyDiscovery.properties.setInvoiceRecoveryEnabled(true);
        when(emptyDiscovery.subscriptions.findInvoiceRecoveryCandidates(
                eq("mercadopago"), anyCollection(), any(Pageable.class))).thenReturn(null);
        TransactionOperations emptyRead = mock(TransactionOperations.class);
        doAnswer(invocation -> {
            org.springframework.transaction.support.TransactionCallback<?> action = invocation.getArgument(0);
            return action.doInTransaction(mock(TransactionStatus.class));
        }).when(emptyRead).execute(any());
        emptyDiscovery.worker = new SaasBillingInvoiceRecoveryWorker(
                emptyDiscovery.properties, emptyDiscovery.subscriptions, emptyDiscovery.gateway,
                emptyDiscovery.billing, emptyDiscovery.jdbc, new TenantDatabaseContext(),
                emptyRead, emptyDiscovery.transactions);
        assertDoesNotThrow(() -> emptyDiscovery.worker.poll());
        verify(emptyDiscovery.subscriptions).findInvoiceRecoveryCandidates(
                eq("mercadopago"), anyCollection(), any(Pageable.class));
        verifyNoInteractions(emptyDiscovery.gateway, emptyDiscovery.billing, emptyDiscovery.jdbc);

        Scenario nullCandidate = new Scenario();
        nullCandidate.properties.setInvoiceRecoveryEnabled(true);
        TransactionOperations nullCandidateRead = mock(TransactionOperations.class);
        List<BusinessSubscription> nullCandidateList = new ArrayList<>();
        nullCandidateList.add(null);
        when(nullCandidateRead.execute(any())).thenReturn(nullCandidateList);
        nullCandidate.worker = new SaasBillingInvoiceRecoveryWorker(
                nullCandidate.properties, nullCandidate.subscriptions, nullCandidate.gateway,
                nullCandidate.billing, nullCandidate.jdbc, new TenantDatabaseContext(),
                nullCandidateRead, nullCandidate.transactions);
        assertDoesNotThrow(() -> nullCandidate.worker.poll());
        verifyNoInteractions(nullCandidate.subscriptions, nullCandidate.gateway,
                nullCandidate.billing, nullCandidate.jdbc);

        Scenario discoveryFailure = new Scenario();
        discoveryFailure.properties.setInvoiceRecoveryEnabled(true);
        TransactionOperations failingRead = mock(TransactionOperations.class);
        when(failingRead.execute(any())).thenThrow(new IllegalStateException("database unavailable"));
        discoveryFailure.worker = new SaasBillingInvoiceRecoveryWorker(
                discoveryFailure.properties, discoveryFailure.subscriptions, discoveryFailure.gateway,
                discoveryFailure.billing, discoveryFailure.jdbc, new TenantDatabaseContext(),
                failingRead, discoveryFailure.transactions);

        assertDoesNotThrow(() -> discoveryFailure.worker.poll());
        verifyNoInteractions(discoveryFailure.subscriptions, discoveryFailure.gateway,
                discoveryFailure.billing, discoveryFailure.jdbc);
    }

    @Test
    void malformedPagesFailClosedBeforeInvoiceLookup() {
        Scenario scenario = new Scenario();
        scenario.properties.setInvoiceRecoveryEnabled(true);
        Map<String, SubscriptionPaymentGateway.InvoicePage> pages = new LinkedHashMap<>();
        pages.put("pre-null-page", null);
        pages.put("pre-negative-total", new SubscriptionPaymentGateway.InvoicePage(
                -1, 0, 20, List.of()));
        pages.put("pre-wrong-offset", new SubscriptionPaymentGateway.InvoicePage(
                0, 1, 20, List.of()));
        pages.put("pre-wrong-limit", new SubscriptionPaymentGateway.InvoicePage(
                0, 0, 19, List.of()));
        pages.put("pre-null-ids", new SubscriptionPaymentGateway.InvoicePage(
                0, 0, 20, null));
        pages.put("pre-too-many-ids", new SubscriptionPaymentGateway.InvoicePage(
                21, 0, 20, Collections.nCopies(21, "invoice")));
        pages.put("pre-total-too-small", new SubscriptionPaymentGateway.InvoicePage(
                0, 0, 20, List.of("invoice")));
        pages.put("pre-blank-id", new SubscriptionPaymentGateway.InvoicePage(
                1, 0, 20, List.of(" ")));
        List<BusinessSubscription> candidates = pages.keySet().stream()
                .map(id -> subscription(id, SubscriptionStatus.ACTIVE)).toList();
        when(scenario.subscriptions.findInvoiceRecoveryCandidates(
                eq("mercadopago"), anyCollection(), any(Pageable.class)))
                .thenReturn(candidates);
        when(scenario.subscriptions.findByExternalSubscriptionId(anyString()))
                .thenAnswer(invocation -> Optional.of(
                        candidates.stream().filter(value -> value.getExternalSubscriptionId()
                                .equals(invocation.getArgument(0, String.class))).findFirst().orElseThrow()));
        when(scenario.jdbc.queryForObject(anyString(), eq(Boolean.class), anyString()))
                .thenReturn(true);
        when(scenario.gateway.searchInvoices(anyString(), eq(0), eq(20)))
                .thenAnswer(invocation -> pages.get(invocation.getArgument(0, String.class)));

        scenario.worker.poll();

        verify(scenario.gateway, times(pages.size())).searchInvoices(anyString(), eq(0), eq(20));
        verifyNoInteractions(scenario.billing);
        verify(scenario.gateway, never()).getInvoice(anyString());
    }

    @Test
    void invoiceLookupAndReconciliationFailuresAreContained() {
        Scenario scenario = new Scenario();
        scenario.properties.setInvoiceRecoveryEnabled(true);
        BusinessSubscription subscription = subscription("pre-invoice-errors", SubscriptionStatus.TRIALING);
        when(scenario.subscriptions.findInvoiceRecoveryCandidates(
                eq("mercadopago"), anyCollection(), any(Pageable.class)))
                .thenReturn(List.of(subscription));
        when(scenario.subscriptions.findByExternalSubscriptionId("pre-invoice-errors"))
                .thenReturn(Optional.of(subscription));
        when(scenario.jdbc.queryForObject(anyString(), eq(Boolean.class), eq("pre-invoice-errors")))
                .thenReturn(true);
        when(scenario.gateway.searchInvoices("pre-invoice-errors", 0, 20))
                .thenReturn(new SubscriptionPaymentGateway.InvoicePage(
                        6, 0, 20, List.of("invoice-null", "invoice-mismatch", "invoice-pending",
                        "invoice-null-status", "invoice-lookup-failure", "invoice-approved")));
        when(scenario.gateway.getInvoice("invoice-null")).thenReturn(null);
        when(scenario.gateway.getInvoice("invoice-mismatch"))
                .thenReturn(invoice("other-invoice", "pre-invoice-errors", "approved", "2026-11-01T00:00:00Z"));
        when(scenario.gateway.getInvoice("invoice-pending"))
                .thenReturn(invoice("invoice-pending", "pre-invoice-errors", "pending", "2026-11-01T00:00:00Z"));
        when(scenario.gateway.getInvoice("invoice-null-status"))
                .thenReturn(invoiceWithStatus("invoice-null-status", "pre-invoice-errors", null,
                        "2026-11-01T00:00:00Z"));
        when(scenario.gateway.getInvoice("invoice-lookup-failure"))
                .thenThrow(new IllegalStateException("provider timeout"));
        when(scenario.gateway.getInvoice("invoice-approved"))
                .thenReturn(invoice("invoice-approved", "pre-invoice-errors", "approved", "2026-11-01T00:00:00Z"));
        doThrow(new IllegalStateException("database conflict"))
                .when(scenario.billing).reconcileAuthorizedPayment("invoice-approved");

        assertDoesNotThrow(() -> scenario.worker.poll());

        verify(scenario.billing).reconcileAuthorizedPayment("invoice-approved");
        verifyNoMoreInteractions(scenario.billing);
    }

    private static SubscriptionPaymentGateway.RemoteInvoice invoice(
            String id, String subscriptionId, String paymentStatus, String debitDate) {
        return invoiceWithStatus(id, subscriptionId, paymentStatus, debitDate);
    }

    private static SubscriptionPaymentGateway.RemoteInvoice invoiceWithStatus(
            String id, String subscriptionId, String paymentStatus, String debitDate) {
        return new SubscriptionPaymentGateway.RemoteInvoice(
                id, subscriptionId, "processed", paymentStatus, "summary",
                OffsetDateTime.parse(debitDate).withOffsetSameInstant(ZoneOffset.UTC));
    }

    private static BusinessSubscription subscription(String externalId, SubscriptionStatus status) {
        BusinessSubscription subscription = new BusinessSubscription();
        subscription.setBusinessId(UUID.randomUUID());
        subscription.setPlanCode("BASIC");
        subscription.setStatus(status);
        subscription.setCurrentPeriodStart(OffsetDateTime.parse("2026-09-01T00:00:00Z").toInstant());
        subscription.setCurrentPeriodEnd(OffsetDateTime.parse("2026-10-01T00:00:00Z").toInstant());
        subscription.setBillingProvider("mercadopago");
        subscription.setExternalSubscriptionId(externalId);
        return subscription;
    }

    private static final class Scenario {
        final BusinessSubscriptionRepository subscriptions = mock(BusinessSubscriptionRepository.class);
        final SubscriptionPaymentGateway gateway = mock(SubscriptionPaymentGateway.class);
        final BillingSubscriptionService billing = mock(BillingSubscriptionService.class);
        final JdbcTemplate jdbc = mock(JdbcTemplate.class);
        final MercadoPagoProperties properties = new MercadoPagoProperties();
        final TransactionOperations transactions = new TransactionOperations() {
            @Override
            public <T> T execute(org.springframework.transaction.support.TransactionCallback<T> action) {
                return action.doInTransaction(mock(TransactionStatus.class));
            }
        };
        SaasBillingInvoiceRecoveryWorker worker = new SaasBillingInvoiceRecoveryWorker(
                properties, subscriptions, gateway, billing, jdbc,
                new TenantDatabaseContext(), transactions, transactions);
    }
}

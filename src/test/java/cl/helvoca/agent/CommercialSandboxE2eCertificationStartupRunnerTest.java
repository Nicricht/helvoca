package cl.helvoca.agent;

import cl.helvoca.catalog.CatalogItemRepository;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.operations.BusinessOperation;
import cl.helvoca.operations.BusinessOperationRepository;
import cl.helvoca.operations.BusinessOrder;
import cl.helvoca.operations.CommercialOperationToolService;
import cl.helvoca.payment.BusinessPayment;
import cl.helvoca.payment.BusinessPaymentRepository;
import cl.helvoca.security.TenantDatabaseContext;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CommercialSandboxE2eCertificationStartupRunnerTest {

    @Test
    void fullJourneyCreatesOneLinkedCheckout() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID journeyId = UUID.randomUUID();
        UUID orderOperationId = UUID.randomUUID();
        UUID paymentOperationId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        BusinessOperation journey = journey(businessId, customerId, journeyId, itemId);
        AtomicReference<BusinessOperation> orderRef = new AtomicReference<>();
        AtomicReference<BusinessOperation> paymentOperationRef = new AtomicReference<>();
        AtomicReference<BusinessPayment> paymentRef = new AtomicReference<>();

        BusinessOperationRepository operations = mock(BusinessOperationRepository.class);
        BusinessPaymentRepository payments = mock(BusinessPaymentRepository.class);
        CommercialOperationToolService commercial = mock(CommercialOperationToolService.class);

        when(operations.findByIdAndBusinessId(any(UUID.class), eq(businessId)))
                .thenAnswer(invocation -> {
                    UUID id = invocation.getArgument(0);
                    if (journeyId.equals(id)) return Optional.of(journey);
                    if (orderOperationId.equals(id)) return Optional.ofNullable(orderRef.get());
                    if (paymentOperationId.equals(id)) return Optional.ofNullable(paymentOperationRef.get());
                    return Optional.empty();
                });
        when(payments.findByOperationIdAndBusinessId(any(UUID.class), eq(businessId)))
                .thenAnswer(invocation -> {
                    UUID id = invocation.getArgument(0);
                    BusinessPayment payment = paymentRef.get();
                    return payment != null && id.equals(payment.getOperationId())
                            ? Optional.of(payment)
                            : Optional.empty();
                });

        when(commercial.execute(
                eq(businessId),
                eq(customerId),
                isNull(),
                anyString(),
                eq(BusinessOrder.Source.WHATSAPP),
                anyString(),
                anyString()))
                .thenAnswer(invocation -> {
                    String tool = invocation.getArgument(5);
                    JSONObject args = new JSONObject((String) invocation.getArgument(6));

                    return switch (tool) {
                        case CommercialOperationToolService.SHOWCASE_SELECTION_TOOL -> {
                            journey.getMetadata().put("selectedCatalogItemId", itemId.toString());
                            journey.getMetadata().put("commercialStage", "PRODUCT_SELECTED");
                            yield success(new JSONObject()
                                    .put("operationId", journeyId.toString())
                                    .put("selectedCatalogItemId", itemId.toString()));
                        }
                        case CommercialOperationToolService.SHOWCASE_QUOTE_TOOL -> {
                            journey.getMetadata().put("quotedCatalogItemId", itemId.toString());
                            journey.getMetadata().put("quotedQuantity", 1);
                            journey.getMetadata().put("commercialStage", "QUOTE_PENDING");
                            yield success(new JSONObject()
                                    .put("operationId", journeyId.toString())
                                    .put("selectedCatalogItemId", itemId.toString())
                                    .put("total", 1000)
                                    .put("currency", "CLP"));
                        }
                        case CommercialOperationToolService.SHOWCASE_ORDER_TOOL -> {
                            BusinessOperation order = new BusinessOperation();
                            order.setId(orderOperationId);
                            order.setBusinessId(businessId);
                            order.setCustomerId(customerId);
                            order.setType(BusinessOperation.Type.ORDER);
                            order.setStatus(BusinessOperation.Status.AWAITING_CONFIRMATION);
                            order.setSource(BusinessOrder.Source.WHATSAPP);
                            order.setRevision(1);
                            order.setConfirmationToken(UUID.randomUUID());
                            order.setTotal(new BigDecimal("1000"));
                            order.setCurrency("CLP");
                            order.setMetadata(new LinkedHashMap<>(Map.of(
                                    "commercialJourneyOperationId", journeyId.toString())));
                            orderRef.set(order);
                            journey.getMetadata().put("orderOperationId", orderOperationId.toString());
                            yield success(new JSONObject()
                                    .put("operationId", orderOperationId.toString())
                                    .put("confirmationToken", order.getConfirmationToken().toString())
                                    .put("total", 1000)
                                    .put("currency", "CLP"));
                        }
                        case "create_order" -> {
                            BusinessOperation order = orderRef.get();
                            assertEquals(order.getConfirmationToken().toString(),
                                    args.getString("confirmationToken"));
                            order.setStatus(BusinessOperation.Status.CONFIRMED);
                            order.setConfirmationToken(null);
                            journey.getMetadata().put("commercialStage", "ORDER_CONFIRMED");
                            yield success(new JSONObject()
                                    .put("operationId", orderOperationId.toString())
                                    .put("orderId", UUID.randomUUID().toString())
                                    .put("status", "CONFIRMED")
                                    .put("total", 1000)
                                    .put("currency", "CLP"));
                        }
                        case "quote_payment" -> {
                            assertEquals(orderOperationId.toString(),
                                    args.getString("targetOperationId"));
                            BusinessOperation paymentOperation = new BusinessOperation();
                            paymentOperation.setId(paymentOperationId);
                            paymentOperation.setBusinessId(businessId);
                            paymentOperation.setCustomerId(customerId);
                            paymentOperation.setType(BusinessOperation.Type.PAYMENT);
                            paymentOperation.setStatus(BusinessOperation.Status.AWAITING_CONFIRMATION);
                            paymentOperation.setSource(BusinessOrder.Source.WHATSAPP);
                            paymentOperation.setRevision(1);
                            paymentOperation.setConfirmationToken(UUID.randomUUID());
                            paymentOperation.setTotal(new BigDecimal("1000"));
                            paymentOperation.setCurrency("CLP");
                            paymentOperation.setMetadata(new LinkedHashMap<>(Map.of(
                                    "commercialJourneyOperationId", journeyId.toString(),
                                    "targetOperationId", orderOperationId.toString())));
                            paymentOperationRef.set(paymentOperation);
                            journey.getMetadata().put("paymentOperationId", paymentOperationId.toString());
                            yield success(new JSONObject()
                                    .put("operationId", paymentOperationId.toString())
                                    .put("targetOperationId", orderOperationId.toString())
                                    .put("confirmationToken", paymentOperation.getConfirmationToken().toString())
                                    .put("amount", 1000)
                                    .put("currency", "CLP"));
                        }
                        case "create_payment" -> {
                            BusinessOperation paymentOperation = paymentOperationRef.get();
                            assertEquals(paymentOperation.getConfirmationToken().toString(),
                                    args.getString("confirmationToken"));
                            paymentOperation.setStatus(BusinessOperation.Status.CONFIRMED);
                            paymentOperation.setConfirmationToken(null);

                            BusinessPayment payment = new BusinessPayment();
                            payment.setId(paymentId);
                            payment.setOperationId(paymentOperationId);
                            payment.setBusinessId(businessId);
                            payment.setCustomerId(customerId);
                            payment.setTargetOperationId(orderOperationId);
                            payment.setProvider("mercadopago");
                            payment.setExternalId("ORDTST-E2E");
                            payment.setIdempotencyKey("payment-operation:" + paymentOperationId);
                            payment.setAmount(new BigDecimal("1000"));
                            payment.setCurrency("CLP");
                            payment.setStatus(BusinessPayment.Status.REQUIRES_ACTION);
                            payment.setCheckoutUrl("https://www.mercadopago.cl/checkout/test-e2e");
                            payment.setSource(BusinessOrder.Source.WHATSAPP);
                            paymentRef.set(payment);

                            journey.getMetadata().put("paymentId", paymentId.toString());
                            journey.getMetadata().put("paymentStatus", "REQUIRES_ACTION");
                            journey.getMetadata().put("commercialStage", "PAYMENT_LINK_SENT");
                            journey.getMetadata().put("checkoutUrl", payment.getCheckoutUrl());

                            yield success(new JSONObject()
                                    .put("operationId", paymentOperationId.toString())
                                    .put("paymentId", paymentId.toString())
                                    .put("status", "REQUIRES_ACTION")
                                    .put("checkoutUrl", payment.getCheckoutUrl()));
                        }
                        default -> throw new AssertionError("Unexpected tool: " + tool);
                    };
                });

        CommercialSandboxE2eCertificationStartupRunner runner = runner(
                operations, payments, commercial);

        var result = runner.certify(new CommercialSandboxE2eCertificationStartupRunner.Seed(
                businessId, "run-1", customerId, itemId, journeyId));

        assertEquals(journeyId, result.journeyOperationId());
        assertEquals(orderOperationId, result.orderOperationId());
        assertEquals(paymentOperationId, result.paymentOperationId());
        assertEquals(paymentId, result.paymentId());
        assertEquals("ORDTST-E2E", result.externalId());
        assertEquals(BusinessPayment.Status.REQUIRES_ACTION, result.paymentStatus());
        assertEquals("https://www.mercadopago.cl/checkout/test-e2e", result.checkoutUrl());

        verify(commercial).execute(
                eq(businessId), eq(customerId), isNull(), anyString(),
                eq(BusinessOrder.Source.WHATSAPP),
                eq(CommercialOperationToolService.SHOWCASE_SELECTION_TOOL), anyString());
        verify(commercial).execute(
                eq(businessId), eq(customerId), isNull(), anyString(),
                eq(BusinessOrder.Source.WHATSAPP),
                eq(CommercialOperationToolService.SHOWCASE_QUOTE_TOOL), anyString());
        verify(commercial).execute(
                eq(businessId), eq(customerId), isNull(), anyString(),
                eq(BusinessOrder.Source.WHATSAPP),
                eq(CommercialOperationToolService.SHOWCASE_ORDER_TOOL), anyString());
        verify(commercial).execute(
                eq(businessId), eq(customerId), isNull(), anyString(),
                eq(BusinessOrder.Source.WHATSAPP),
                eq("create_order"), anyString());
        verify(commercial).execute(
                eq(businessId), eq(customerId), isNull(), anyString(),
                eq(BusinessOrder.Source.WHATSAPP),
                eq("quote_payment"), anyString());
        verify(commercial).execute(
                eq(businessId), eq(customerId), isNull(), anyString(),
                eq(BusinessOrder.Source.WHATSAPP),
                eq("create_payment"), anyString());
    }

    @Test
    void restartReusesExistingPaymentWithoutCreatingAnotherCheckout() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID journeyId = UUID.randomUUID();
        UUID orderOperationId = UUID.randomUUID();
        UUID paymentOperationId = UUID.randomUUID();

        BusinessOperation journey = journey(businessId, customerId, journeyId, itemId);
        journey.getMetadata().put("orderOperationId", orderOperationId.toString());
        journey.getMetadata().put("paymentOperationId", paymentOperationId.toString());
        journey.getMetadata().put("commercialStage", "PAYMENT_LINK_SENT");

        BusinessPayment payment = new BusinessPayment();
        payment.setId(UUID.randomUUID());
        payment.setOperationId(paymentOperationId);
        payment.setBusinessId(businessId);
        payment.setCustomerId(customerId);
        payment.setTargetOperationId(orderOperationId);
        payment.setProvider("mercadopago");
        payment.setExternalId("ORDTST-EXISTING");
        payment.setIdempotencyKey("payment-operation:" + paymentOperationId);
        payment.setAmount(new BigDecimal("1000"));
        payment.setCurrency("CLP");
        payment.setStatus(BusinessPayment.Status.REQUIRES_ACTION);
        payment.setCheckoutUrl("https://www.mercadopago.cl/checkout/existing");
        payment.setSource(BusinessOrder.Source.WHATSAPP);

        BusinessOperationRepository operations = mock(BusinessOperationRepository.class);
        BusinessPaymentRepository payments = mock(BusinessPaymentRepository.class);
        CommercialOperationToolService commercial = mock(CommercialOperationToolService.class);

        when(operations.findByIdAndBusinessId(journeyId, businessId))
                .thenReturn(Optional.of(journey));
        when(payments.findByOperationIdAndBusinessId(paymentOperationId, businessId))
                .thenReturn(Optional.of(payment));

        CommercialSandboxE2eCertificationStartupRunner runner = runner(
                operations, payments, commercial);

        var result = runner.certify(new CommercialSandboxE2eCertificationStartupRunner.Seed(
                businessId, "run-1", customerId, itemId, journeyId));

        assertEquals(payment.getId(), result.paymentId());
        assertEquals("ORDTST-EXISTING", result.externalId());
        assertEquals(payment.getCheckoutUrl(), result.checkoutUrl());
        verifyNoInteractions(commercial);
    }

    private static CommercialSandboxE2eCertificationStartupRunner runner(
            BusinessOperationRepository operations,
            BusinessPaymentRepository payments,
            CommercialOperationToolService commercial) {
        return new CommercialSandboxE2eCertificationStartupRunner(
                false,
                "",
                "",
                "1000",
                mock(TenantDatabaseContext.class),
                mock(PlatformTransactionManager.class),
                mock(CommercialCheckoutCapabilityActivationStartupRunner.class),
                mock(CustomerRepository.class),
                mock(CatalogItemRepository.class),
                operations,
                payments,
                commercial);
    }

    private static BusinessOperation journey(UUID businessId,
                                             UUID customerId,
                                             UUID journeyId,
                                             UUID itemId) {
        BusinessOperation journey = new BusinessOperation();
        journey.setId(journeyId);
        journey.setBusinessId(businessId);
        journey.setCustomerId(customerId);
        journey.setType(BusinessOperation.Type.REQUEST);
        journey.setStatus(BusinessOperation.Status.CONFIRMED);
        journey.setSource(BusinessOrder.Source.WHATSAPP);
        journey.setRevision(1);
        journey.setMetadata(new LinkedHashMap<>(Map.of(
                "sandboxCertification", true,
                "certificationRunId", "run-1",
                "showcaseCatalogItemIds", java.util.List.of(itemId.toString()),
                "commercialStage", "MEDIA_QUEUED")));
        return journey;
    }

    private static String success(JSONObject data) {
        return new JSONObject()
                .put("success", true)
                .put("data", data)
                .put("error", JSONObject.NULL)
                .toString();
    }
}

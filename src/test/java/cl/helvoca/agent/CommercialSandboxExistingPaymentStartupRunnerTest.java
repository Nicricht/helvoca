package cl.helvoca.agent;

import cl.helvoca.operations.BusinessOperation;
import cl.helvoca.operations.BusinessOperationRepository;
import cl.helvoca.operations.BusinessOrder;
import cl.helvoca.operations.CommercialOperationToolService;
import cl.helvoca.payment.BusinessPayment;
import cl.helvoca.payment.BusinessPaymentRepository;
import cl.helvoca.security.TenantDatabaseContext;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CommercialSandboxExistingPaymentStartupRunnerTest {

    @Test
    void activatesOnlyTheExistingLinkedPaymentDraft() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID journeyId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID paymentOperationId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        BusinessOperation journey = operation(
                businessId, customerId, journeyId, BusinessOperation.Type.REQUEST,
                BusinessOperation.Status.CONFIRMED);
        journey.setMetadata(new LinkedHashMap<>(Map.of(
                "orderOperationId", orderId.toString(),
                "paymentOperationId", paymentOperationId.toString())));

        BusinessOperation order = operation(
                businessId, customerId, orderId, BusinessOperation.Type.ORDER,
                BusinessOperation.Status.CONFIRMED);

        BusinessOperation paymentOperation = operation(
                businessId, customerId, paymentOperationId, BusinessOperation.Type.PAYMENT,
                BusinessOperation.Status.AWAITING_CONFIRMATION);
        paymentOperation.setConfirmationToken(UUID.randomUUID());
        paymentOperation.setContactPhone("+56900009999");
        paymentOperation.setSource(BusinessOrder.Source.WHATSAPP);
        paymentOperation.setMetadata(new LinkedHashMap<>(Map.of(
                "targetOperationId", orderId.toString(),
                "commercialJourneyOperationId", journeyId.toString())));

        BusinessPayment payment = new BusinessPayment();
        payment.setId(paymentId);
        payment.setOperationId(paymentOperationId);
        payment.setBusinessId(businessId);
        payment.setCustomerId(customerId);
        payment.setTargetOperationId(orderId);
        payment.setProvider("mercadopago");
        payment.setExternalId("ORDTST-READY");
        payment.setIdempotencyKey("payment-operation:" + paymentOperationId);
        payment.setAmount(new BigDecimal("1000"));
        payment.setCurrency("CLP");
        payment.setStatus(BusinessPayment.Status.REQUIRES_ACTION);
        payment.setCheckoutUrl("https://www.mercadopago.cl/checkout/test-ready");
        payment.setSource(BusinessOrder.Source.WHATSAPP);

        BusinessOperationRepository operations = mock(BusinessOperationRepository.class);
        BusinessPaymentRepository payments = mock(BusinessPaymentRepository.class);
        CommercialOperationToolService commercial = mock(CommercialOperationToolService.class);

        when(operations.findByIdAndBusinessId(journeyId, businessId))
                .thenReturn(Optional.of(journey));
        when(operations.findByIdAndBusinessId(orderId, businessId))
                .thenReturn(Optional.of(order));
        when(operations.findByIdAndBusinessId(paymentOperationId, businessId))
                .thenReturn(Optional.of(paymentOperation));
        when(payments.findByOperationIdAndBusinessId(paymentOperationId, businessId))
                .thenReturn(Optional.empty(), Optional.of(payment));
        when(commercial.execute(
                eq(businessId),
                eq(customerId),
                isNull(),
                eq("+56900009999"),
                eq(BusinessOrder.Source.WHATSAPP),
                eq("create_payment"),
                anyString()))
                .thenAnswer(invocation -> {
                    JSONObject args = new JSONObject(invocation.getArgument(6, String.class));
                    assertEquals(paymentOperationId.toString(), args.getString("operationId"));
                    assertEquals(
                            paymentOperation.getConfirmationToken().toString(),
                            args.getString("confirmationToken"));
                    return new JSONObject()
                            .put("success", true)
                            .put("data", new JSONObject()
                                    .put("operationId", paymentOperationId.toString())
                                    .put("paymentId", paymentId.toString())
                                    .put("status", "REQUIRES_ACTION")
                                    .put("checkoutUrl", payment.getCheckoutUrl()))
                            .put("error", JSONObject.NULL)
                            .toString();
                });

        CommercialSandboxExistingPaymentStartupRunner runner = runner(
                operations, payments, commercial);

        var result = runner.activate(
                businessId, journeyId, orderId, paymentOperationId);

        assertEquals(journeyId, result.journeyOperationId());
        assertEquals(orderId, result.orderOperationId());
        assertEquals(paymentOperationId, result.paymentOperationId());
        assertEquals(paymentId, result.paymentId());
        assertEquals("ORDTST-READY", result.externalId());
        assertEquals(BusinessPayment.Status.REQUIRES_ACTION, result.paymentStatus());
        assertFalse(result.idempotentReplay());
        verify(commercial, times(1)).execute(
                eq(businessId), eq(customerId), isNull(), eq("+56900009999"),
                eq(BusinessOrder.Source.WHATSAPP), eq("create_payment"), anyString());
    }

    @Test
    void existingPaymentIsIdempotentAndDoesNotCallProviderAgain() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID journeyId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID paymentOperationId = UUID.randomUUID();

        BusinessOperation journey = operation(
                businessId, customerId, journeyId, BusinessOperation.Type.REQUEST,
                BusinessOperation.Status.CONFIRMED);
        journey.setMetadata(new LinkedHashMap<>(Map.of(
                "orderOperationId", orderId.toString(),
                "paymentOperationId", paymentOperationId.toString())));
        BusinessOperation order = operation(
                businessId, customerId, orderId, BusinessOperation.Type.ORDER,
                BusinessOperation.Status.CONFIRMED);
        BusinessOperation paymentOperation = operation(
                businessId, customerId, paymentOperationId, BusinessOperation.Type.PAYMENT,
                BusinessOperation.Status.CONFIRMED);
        paymentOperation.setMetadata(new LinkedHashMap<>(Map.of(
                "targetOperationId", orderId.toString(),
                "commercialJourneyOperationId", journeyId.toString())));

        BusinessPayment payment = new BusinessPayment();
        payment.setId(UUID.randomUUID());
        payment.setOperationId(paymentOperationId);
        payment.setBusinessId(businessId);
        payment.setCustomerId(customerId);
        payment.setTargetOperationId(orderId);
        payment.setProvider("mercadopago");
        payment.setExternalId("ORDTST-EXISTING");
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
        when(operations.findByIdAndBusinessId(orderId, businessId))
                .thenReturn(Optional.of(order));
        when(operations.findByIdAndBusinessId(paymentOperationId, businessId))
                .thenReturn(Optional.of(paymentOperation));
        when(payments.findByOperationIdAndBusinessId(paymentOperationId, businessId))
                .thenReturn(Optional.of(payment));

        var result = runner(operations, payments, commercial)
                .activate(businessId, journeyId, orderId, paymentOperationId);

        assertTrue(result.idempotentReplay());
        assertEquals("ORDTST-EXISTING", result.externalId());
        verifyNoInteractions(commercial);
    }

    @Test
    void linkageMismatchFailsBeforeCallingCreatePayment() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID journeyId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID wrongOrderId = UUID.randomUUID();
        UUID paymentOperationId = UUID.randomUUID();

        BusinessOperation journey = operation(
                businessId, customerId, journeyId, BusinessOperation.Type.REQUEST,
                BusinessOperation.Status.CONFIRMED);
        journey.setMetadata(new LinkedHashMap<>(Map.of(
                "orderOperationId", orderId.toString(),
                "paymentOperationId", paymentOperationId.toString())));
        BusinessOperation order = operation(
                businessId, customerId, orderId, BusinessOperation.Type.ORDER,
                BusinessOperation.Status.CONFIRMED);
        BusinessOperation paymentOperation = operation(
                businessId, customerId, paymentOperationId, BusinessOperation.Type.PAYMENT,
                BusinessOperation.Status.AWAITING_CONFIRMATION);
        paymentOperation.setConfirmationToken(UUID.randomUUID());
        paymentOperation.setMetadata(new LinkedHashMap<>(Map.of(
                "targetOperationId", wrongOrderId.toString(),
                "commercialJourneyOperationId", journeyId.toString())));

        BusinessOperationRepository operations = mock(BusinessOperationRepository.class);
        BusinessPaymentRepository payments = mock(BusinessPaymentRepository.class);
        CommercialOperationToolService commercial = mock(CommercialOperationToolService.class);

        when(operations.findByIdAndBusinessId(journeyId, businessId))
                .thenReturn(Optional.of(journey));
        when(operations.findByIdAndBusinessId(orderId, businessId))
                .thenReturn(Optional.of(order));
        when(operations.findByIdAndBusinessId(paymentOperationId, businessId))
                .thenReturn(Optional.of(paymentOperation));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> runner(operations, payments, commercial)
                        .activate(businessId, journeyId, orderId, paymentOperationId));

        assertTrue(error.getMessage().contains("targetOperationId"));
        verifyNoInteractions(commercial);
    }

    private static CommercialSandboxExistingPaymentStartupRunner runner(
            BusinessOperationRepository operations,
            BusinessPaymentRepository payments,
            CommercialOperationToolService commercial) {
        return new CommercialSandboxExistingPaymentStartupRunner(
                false, "", "", "", "",
                mock(TenantDatabaseContext.class),
                operations,
                payments,
                commercial);
    }

    private static BusinessOperation operation(UUID businessId,
                                               UUID customerId,
                                               UUID id,
                                               BusinessOperation.Type type,
                                               BusinessOperation.Status status) {
        BusinessOperation operation = new BusinessOperation();
        operation.setId(id);
        operation.setBusinessId(businessId);
        operation.setCustomerId(customerId);
        operation.setType(type);
        operation.setStatus(status);
        operation.setRevision(1);
        operation.setSource(BusinessOrder.Source.WHATSAPP);
        return operation;
    }
}

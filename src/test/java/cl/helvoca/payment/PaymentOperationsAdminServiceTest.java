package cl.helvoca.payment;

import cl.helvoca.operations.BusinessOperation;
import cl.helvoca.operations.BusinessOperationRepository;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentOperationsAdminServiceTest {
    @Mock BusinessOperationRepository operations;
    @Mock BusinessPaymentRepository payments;
    @Mock PaymentWorkflowService workflow;
    @Mock TenantProvider tenantProvider;

    private PaymentOperationsAdminService service;

    @BeforeEach
    void setUp() {
        service = new PaymentOperationsAdminService(
                operations, payments, workflow, tenantProvider);
    }

    @Test
    void diagnosesPaidJourneyAndExactPaymentProjection() {
        Fixture fixture = fixture(BusinessPayment.Status.SUCCEEDED, "PAID");
        stub(fixture);

        PaymentOperationsAdminService.Diagnostic result =
                service.diagnose(fixture.paymentOperationId);

        assertEquals(fixture.businessId, result.businessId());
        assertEquals(fixture.journeyId, result.journeyOperationId());
        assertEquals(fixture.orderId, result.orderOperationId());
        assertEquals(fixture.paymentOperationId, result.paymentOperationId());
        assertEquals(fixture.payment.getId(), result.paymentId());
        assertEquals("SUCCEEDED", result.paymentStatus());
        assertEquals("PAID", result.commercialStage());
        assertEquals("processed", result.remoteStatus());
        assertEquals("accredited", result.remoteStatusDetail());
        assertEquals(1, result.paymentProjectionsForOperation());
        assertEquals(1, result.paymentsForTargetOrder());
        assertTrue(result.linkageConsistent());
        assertTrue(result.journeyStateConsistent());
        assertTrue(result.terminal());
        assertEquals("NONE", result.recommendedAction());
    }

    @Test
    void reconcileRefreshesSamePaymentAndJourneyWithoutCreatingAnything() {
        Fixture fixture = fixture(BusinessPayment.Status.REQUIRES_ACTION, "PAYMENT_LINK_SENT");
        stub(fixture);

        when(workflow.reconcileOne(fixture.businessId, fixture.payment.getId()))
                .thenAnswer(invocation -> {
                    fixture.payment.setStatus(BusinessPayment.Status.SUCCEEDED);
                    fixture.payment.setMetadata(Map.of(
                            "remoteStatus", "processed",
                            "remoteStatusDetail", "accredited"));
                    Map<String, Object> journeyMetadata =
                            new LinkedHashMap<>(fixture.journey.getMetadata());
                    journeyMetadata.put("paymentStatus", "SUCCEEDED");
                    journeyMetadata.put("commercialStage", "PAID");
                    journeyMetadata.put("lastAction", "PAYMENT_STATUS_VERIFIED");
                    fixture.journey.setMetadata(journeyMetadata);
                    return fixture.payment;
                });

        PaymentOperationsAdminService.Diagnostic result =
                service.reconcile(fixture.paymentOperationId);

        assertEquals(fixture.payment.getId(), result.paymentId());
        assertEquals("SUCCEEDED", result.paymentStatus());
        assertEquals("PAID", result.commercialStage());
        assertTrue(result.journeyStateConsistent());
        assertEquals("NONE", result.recommendedAction());
        verify(workflow).reconcileOne(fixture.businessId, fixture.payment.getId());
        verifyNoMoreInteractions(workflow);
    }

    private void stub(Fixture fixture) {
        when(tenantProvider.requireBusinessId()).thenReturn(fixture.businessId);
        when(operations.findByIdAndBusinessId(fixture.paymentOperationId, fixture.businessId))
                .thenReturn(Optional.of(fixture.paymentOperation));
        when(operations.findByIdAndBusinessId(fixture.orderId, fixture.businessId))
                .thenReturn(Optional.of(fixture.order));
        when(operations.findByIdAndBusinessId(fixture.journeyId, fixture.businessId))
                .thenReturn(Optional.of(fixture.journey));
        when(payments.findByOperationIdAndBusinessId(
                fixture.paymentOperationId, fixture.businessId))
                .thenReturn(Optional.of(fixture.payment));
        when(payments.countByBusinessIdAndOperationId(
                fixture.businessId, fixture.paymentOperationId)).thenReturn(1L);
        when(payments.countByBusinessIdAndTargetOperationId(
                fixture.businessId, fixture.orderId)).thenReturn(1L);
    }

    private static Fixture fixture(BusinessPayment.Status paymentStatus,
                                   String commercialStage) {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID journeyId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID paymentOperationId = UUID.randomUUID();

        BusinessOperation journey = operation(
                journeyId, businessId, customerId,
                BusinessOperation.Type.REQUEST, BusinessOperation.Status.CONFIRMED);
        journey.setMetadata(new LinkedHashMap<>(Map.of(
                "orderOperationId", orderId.toString(),
                "paymentOperationId", paymentOperationId.toString(),
                "commercialStage", commercialStage)));

        BusinessOperation order = operation(
                orderId, businessId, customerId,
                BusinessOperation.Type.ORDER, BusinessOperation.Status.CONFIRMED);

        BusinessOperation paymentOperation = operation(
                paymentOperationId, businessId, customerId,
                BusinessOperation.Type.PAYMENT, BusinessOperation.Status.CONFIRMED);
        paymentOperation.setMetadata(new LinkedHashMap<>(Map.of(
                "targetOperationId", orderId.toString(),
                "commercialJourneyOperationId", journeyId.toString(),
                "paymentStatus", paymentStatus.name())));

        BusinessPayment payment = new BusinessPayment();
        payment.setId(UUID.randomUUID());
        payment.setOperationId(paymentOperationId);
        payment.setBusinessId(businessId);
        payment.setCustomerId(customerId);
        payment.setTargetOperationId(orderId);
        payment.setProvider("mercadopago");
        payment.setExternalId("ORDTST-123");
        payment.setIdempotencyKey(UUID.randomUUID().toString());
        payment.setAmount(new BigDecimal("1000"));
        payment.setCurrency("CLP");
        payment.setStatus(paymentStatus);
        payment.setCheckoutUrl("https://www.mercadopago.cl/checkout/test");
        payment.setMetadata(Map.of(
                "remoteStatus", paymentStatus == BusinessPayment.Status.SUCCEEDED
                        ? "processed" : "action_required",
                "remoteStatusDetail", paymentStatus == BusinessPayment.Status.SUCCEEDED
                        ? "accredited" : "waiting_payment"));

        return new Fixture(
                businessId, journeyId, orderId, paymentOperationId,
                journey, order, paymentOperation, payment);
    }

    private static BusinessOperation operation(UUID id,
                                               UUID businessId,
                                               UUID customerId,
                                               BusinessOperation.Type type,
                                               BusinessOperation.Status status) {
        BusinessOperation operation = new BusinessOperation();
        operation.setId(id);
        operation.setBusinessId(businessId);
        operation.setCustomerId(customerId);
        operation.setType(type);
        operation.setStatus(status);
        operation.setRevision(1);
        return operation;
    }

    private record Fixture(
            UUID businessId,
            UUID journeyId,
            UUID orderId,
            UUID paymentOperationId,
            BusinessOperation journey,
            BusinessOperation order,
            BusinessOperation paymentOperation,
            BusinessPayment payment) {}
}

package cl.helvoca.booking;

import cl.helvoca.operations.BusinessOperation;
import cl.helvoca.operations.BusinessOperationRepository;
import cl.helvoca.operations.BusinessOrder;
import cl.helvoca.operations.ConversationStateService;
import cl.helvoca.operations.OperationPolicyService;
import cl.helvoca.payment.BusinessPayment;
import cl.helvoca.payment.BusinessPaymentRepository;
import cl.helvoca.payment.PaymentProviderRegistry;
import cl.helvoca.payment.PaymentWorkflowService;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BookingRevenueTruthContractTest {

    @Test
    void bookingHasCompletedAndNoShowTerminalOutcomes() {
        assertDoesNotThrow(() -> BookingStatus.valueOf("COMPLETED"));
        assertDoesNotThrow(() -> BookingStatus.valueOf("NO_SHOW"));
    }

    @Test
    void paymentPersistsVerificationMethodPaymentMethodAndVerifiedTimestamp() {
        assertDoesNotThrow(() -> BusinessPayment.class.getMethod("getVerificationMethod"));
        assertDoesNotThrow(() -> BusinessPayment.class.getMethod("getPaymentMethod"));
        assertDoesNotThrow(() -> BusinessPayment.class.getMethod("getVerifiedAt"));
    }

    @Test
    void completedBookingCanStillBeQuotedForPaymentAfterService() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID bookingOperationId = UUID.randomUUID();

        BusinessOperationRepository operations = mock(BusinessOperationRepository.class);
        BusinessPaymentRepository payments = mock(BusinessPaymentRepository.class);
        PaymentProviderRegistry providers = mock(PaymentProviderRegistry.class);
        ConversationStateService conversation = mock(ConversationStateService.class);

        BusinessOperation booking = new BusinessOperation();
        booking.setId(bookingOperationId);
        booking.setBusinessId(businessId);
        booking.setCustomerId(customerId);
        booking.setType(BusinessOperation.Type.BOOKING);
        booking.setStatus(BusinessOperation.Status.COMPLETED);
        booking.setSource(BusinessOrder.Source.MANUAL);
        booking.setTotal(new BigDecimal("15000"));
        booking.setCurrency("CLP");

        when(operations.findByIdAndBusinessId(bookingOperationId, businessId))
                .thenReturn(Optional.of(booking));
        when(payments.findAllByBusinessIdAndTargetOperationIdOrderByCreatedAtAsc(
                businessId, bookingOperationId)).thenReturn(java.util.List.of());
        when(operations.saveAndFlush(any(BusinessOperation.class)))
                .thenAnswer(invocation -> {
                    BusinessOperation saved = invocation.getArgument(0);
                    if (saved.getId() == null) saved.setId(UUID.randomUUID());
                    return saved;
                });

        PaymentWorkflowService service = new PaymentWorkflowService(
                operations,
                payments,
                providers,
                new OperationPolicyService(),
                conversation);

        JSONObject result = service.quote(
                businessId,
                customerId,
                null,
                null,
                BusinessOrder.Source.MANUAL,
                new JSONObject().put("targetOperationId", bookingOperationId.toString()));

        assertTrue(result.getBoolean("success"), result.toString());
        assertEquals("15000", result.getJSONObject("data").get("amount").toString());
        assertEquals("CLP", result.getJSONObject("data").getString("currency"));
    }
}

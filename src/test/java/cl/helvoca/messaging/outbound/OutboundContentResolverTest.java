package cl.helvoca.messaging.outbound;

import cl.helvoca.operations.BusinessOperation;
import cl.helvoca.payment.BusinessPayment;
import cl.helvoca.payment.BusinessPaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OutboundContentResolverTest {
    private BusinessPaymentRepository payments;
    private OutboundContentResolver resolver;
    private UUID businessId;
    private UUID customerId;

    @BeforeEach
    void setUp() {
        payments = mock(BusinessPaymentRepository.class);
        resolver = new OutboundContentResolver(payments);
        businessId = UUID.randomUUID();
        customerId = UUID.randomUUID();
    }

    @Test
    void rejectsMissingPurposeOperationTenantAndCustomerMismatch() {
        BusinessOperation operation = operation(BusinessOperation.Type.ORDER);
        assertThrows(IllegalArgumentException.class,
                () -> resolver.render(businessId, customerId, null, operation));
        assertThrows(IllegalArgumentException.class,
                () -> resolver.render(businessId, customerId, OutboundMessage.Purpose.ORDER_STATUS, null));

        when(operation.getBusinessId()).thenReturn(UUID.randomUUID());
        assertThrows(IllegalArgumentException.class,
                () -> resolver.render(businessId, customerId, OutboundMessage.Purpose.ORDER_STATUS, operation));

        when(operation.getBusinessId()).thenReturn(businessId);
        when(operation.getCustomerId()).thenReturn(null);
        assertThrows(IllegalArgumentException.class,
                () -> resolver.render(businessId, customerId, OutboundMessage.Purpose.ORDER_STATUS, operation));

        when(operation.getCustomerId()).thenReturn(UUID.randomUUID());
        assertThrows(IllegalArgumentException.class,
                () -> resolver.render(businessId, customerId, OutboundMessage.Purpose.ORDER_STATUS, operation));
    }

    @Test
    void rendersBookingWithAndWithoutStartTime() {
        BusinessOperation operation = operation(BusinessOperation.Type.BOOKING);
        when(operation.getMetadata()).thenReturn(Map.of("startAt", "2026-10-01T10:00:00Z"));
        assertTrue(resolver.render(businessId, customerId, OutboundMessage.Purpose.BOOKING_CONFIRMATION, operation)
                .contains("2026-10-01T10:00:00Z"));

        when(operation.getMetadata()).thenReturn(null);
        assertEquals("Tu reserva está confirmada.",
                resolver.render(businessId, customerId, OutboundMessage.Purpose.BOOKING_CONFIRMATION, operation));

        when(operation.getType()).thenReturn(BusinessOperation.Type.ORDER);
        assertThrows(IllegalArgumentException.class,
                () -> resolver.render(businessId, customerId, OutboundMessage.Purpose.BOOKING_CONFIRMATION, operation));
    }

    @Test
    void meetingRequiresTrustedHttpsUrl() {
        BusinessOperation operation = operation(BusinessOperation.Type.BOOKING);
        when(operation.getMetadata()).thenReturn(Map.of("meetingUrl", " https://meet.example.com/r/abc "));
        assertTrue(resolver.render(businessId, customerId, OutboundMessage.Purpose.MEETING_LINK, operation)
                .contains("https://meet.example.com/r/abc"));

        when(operation.getMetadata()).thenReturn(Map.of());
        assertThrows(IllegalStateException.class,
                () -> resolver.render(businessId, customerId, OutboundMessage.Purpose.MEETING_LINK, operation));

        when(operation.getMetadata()).thenReturn(Map.of("meetingUrl", "http://meet.example.com/x"));
        assertThrows(IllegalStateException.class,
                () -> resolver.render(businessId, customerId, OutboundMessage.Purpose.MEETING_LINK, operation));

        when(operation.getMetadata()).thenReturn(Map.of("meetingUrl", "https://user:pass@meet.example.com/x"));
        assertThrows(IllegalStateException.class,
                () -> resolver.render(businessId, customerId, OutboundMessage.Purpose.MEETING_LINK, operation));

        when(operation.getMetadata()).thenReturn(Map.of("meetingUrl", "   "));
        assertThrows(IllegalStateException.class,
                () -> resolver.render(businessId, customerId, OutboundMessage.Purpose.MEETING_LINK, operation));
    }

    @Test
    void rendersOrderQuoteReminderAndDeliveryVariants() {
        BusinessOperation order = operation(BusinessOperation.Type.ORDER);
        when(order.getStatus()).thenReturn(BusinessOperation.Status.CONFIRMED);
        when(order.getTotal()).thenReturn(new BigDecimal("12990.00"));
        when(order.getCurrency()).thenReturn("clp");
        assertEquals("Estado de tu pedido: CONFIRMED. Total: CLP 12990.",
                resolver.render(businessId, customerId, OutboundMessage.Purpose.ORDER_STATUS, order));

        when(order.getTotal()).thenReturn(null);
        assertEquals("Estado de tu pedido: CONFIRMED.",
                resolver.render(businessId, customerId, OutboundMessage.Purpose.ORDER_STATUS, order));

        BusinessOperation quote = operation(BusinessOperation.Type.QUOTE);
        when(quote.getTotal()).thenReturn(null);
        assertEquals("Tu cotización está disponible. Total: sin total calculado.",
                resolver.render(businessId, customerId, OutboundMessage.Purpose.QUOTE, quote));
        when(quote.getTotal()).thenReturn(new BigDecimal("1500.50"));
        when(quote.getCurrency()).thenReturn(" ");
        assertEquals("Tu cotización está disponible. Total: 1500.5.",
                resolver.render(businessId, customerId, OutboundMessage.Purpose.QUOTE, quote));

        BusinessOperation reminder = operation(BusinessOperation.Type.LEAD);
        when(reminder.getStatus()).thenReturn(BusinessOperation.Status.DRAFT);
        assertEquals("Recordatorio de tu gestión LEAD: estado DRAFT.",
                resolver.render(businessId, customerId, OutboundMessage.Purpose.REMINDER, reminder));

        BusinessOperation delivery = operation(BusinessOperation.Type.DELIVERY);
        when(delivery.getStatus()).thenReturn(BusinessOperation.Status.CONFIRMED);
        when(delivery.getMetadata()).thenReturn(Map.of("trackingStatus", "EN_RUTA"));
        assertEquals("Estado de entrega: EN_RUTA.",
                resolver.render(businessId, customerId, OutboundMessage.Purpose.DELIVERY_STATUS, delivery));
        when(delivery.getMetadata()).thenReturn(Map.of("trackingStatus", " "));
        assertEquals("Estado de entrega: CONFIRMED.",
                resolver.render(businessId, customerId, OutboundMessage.Purpose.DELIVERY_STATUS, delivery));

        when(delivery.getType()).thenReturn(BusinessOperation.Type.ORDER);
        assertThrows(IllegalArgumentException.class,
                () -> resolver.render(businessId, customerId, OutboundMessage.Purpose.DELIVERY_STATUS, delivery));
    }

    @Test
    void rendersPendingPaymentLinkAndCoversSafetyFailures() {
        BusinessOperation operation = operation(BusinessOperation.Type.PAYMENT);
        BusinessPayment payment = mock(BusinessPayment.class);
        when(payments.findByOperationIdAndBusinessId(operation.getId(), businessId))
                .thenReturn(Optional.of(payment));
        when(payment.getCustomerId()).thenReturn(customerId);
        when(payment.getStatus()).thenReturn(BusinessPayment.Status.REQUIRES_ACTION);
        when(payment.getCheckoutUrl()).thenReturn("https://pay.example.com/checkout/1");
        when(payment.getAmount()).thenReturn(new BigDecimal("9990"));
        when(payment.getCurrency()).thenReturn("clp");

        String rendered = resolver.render(
                businessId, customerId, OutboundMessage.Purpose.PAYMENT_LINK, operation);
        assertTrue(rendered.contains("CLP 9990"));
        assertTrue(rendered.contains("https://pay.example.com/checkout/1"));

        when(payment.getStatus()).thenReturn(BusinessPayment.Status.PENDING);
        assertDoesNotThrow(() -> resolver.render(
                businessId, customerId, OutboundMessage.Purpose.PAYMENT_LINK, operation));

        when(payment.getAmount()).thenReturn(null);
        when(payment.getCurrency()).thenReturn(null);
        assertTrue(resolver.render(
                businessId, customerId, OutboundMessage.Purpose.PAYMENT_LINK, operation)
                .contains("monto pendiente"));

        when(payment.getCustomerId()).thenReturn(UUID.randomUUID());
        assertThrows(IllegalStateException.class, () -> resolver.render(
                businessId, customerId, OutboundMessage.Purpose.PAYMENT_LINK, operation));

        when(payment.getCustomerId()).thenReturn(customerId);
        when(payment.getStatus()).thenReturn(BusinessPayment.Status.SUCCEEDED);
        assertThrows(IllegalStateException.class, () -> resolver.render(
                businessId, customerId, OutboundMessage.Purpose.PAYMENT_LINK, operation));

        when(payment.getStatus()).thenReturn(BusinessPayment.Status.PENDING);
        when(payment.getCheckoutUrl()).thenReturn("javascript:alert(1)");
        assertThrows(IllegalStateException.class, () -> resolver.render(
                businessId, customerId, OutboundMessage.Purpose.PAYMENT_LINK, operation));
    }

    @Test
    void paymentLinkFailsWhenProjectionMissingAndWhenPurposeTypeMismatch() {
        BusinessOperation operation = operation(BusinessOperation.Type.PAYMENT);
        when(payments.findByOperationIdAndBusinessId(operation.getId(), businessId))
                .thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class, () -> resolver.render(
                businessId, customerId, OutboundMessage.Purpose.PAYMENT_LINK, operation));

        when(operation.getType()).thenReturn(BusinessOperation.Type.QUOTE);
        assertThrows(IllegalArgumentException.class, () -> resolver.render(
                businessId, customerId, OutboundMessage.Purpose.PAYMENT_LINK, operation));
    }

    @Test
    void paymentConfirmationRequiresVerifiedSuccess() {
        BusinessOperation operation = operation(BusinessOperation.Type.PAYMENT);
        BusinessPayment payment = mock(BusinessPayment.class);
        when(payments.findByOperationIdAndBusinessId(operation.getId(), businessId))
                .thenReturn(Optional.of(payment));
        when(payment.getCustomerId()).thenReturn(customerId);
        when(payment.getStatus()).thenReturn(BusinessPayment.Status.SUCCEEDED);
        when(payment.getAmount()).thenReturn(new BigDecimal("5000"));
        when(payment.getCurrency()).thenReturn("CLP");

        assertEquals("Pago confirmado por CLP 5000. El proveedor verificó el pago correctamente.",
                resolver.render(businessId, customerId, OutboundMessage.Purpose.PAYMENT_CONFIRMATION, operation));

        when(payment.getStatus()).thenReturn(BusinessPayment.Status.FAILED);
        assertThrows(IllegalStateException.class, () -> resolver.render(
                businessId, customerId, OutboundMessage.Purpose.PAYMENT_CONFIRMATION, operation));

        when(payment.getCustomerId()).thenReturn(null);
        assertThrows(IllegalStateException.class, () -> resolver.render(
                businessId, customerId, OutboundMessage.Purpose.PAYMENT_CONFIRMATION, operation));
    }

    @Test
    void rejectsPurposesThatRequireExternallyPreparedContent() {
        BusinessOperation operation = operation(BusinessOperation.Type.REQUEST);
        assertThrows(IllegalArgumentException.class, () -> resolver.render(
                businessId, customerId, OutboundMessage.Purpose.INCIDENT_NOTICE, operation));
        assertThrows(IllegalArgumentException.class, () -> resolver.render(
                businessId, customerId, OutboundMessage.Purpose.PRODUCT_SHOWCASE, operation));
    }

    private BusinessOperation operation(BusinessOperation.Type type) {
        BusinessOperation operation = mock(BusinessOperation.class);
        when(operation.getId()).thenReturn(UUID.randomUUID());
        when(operation.getBusinessId()).thenReturn(businessId);
        when(operation.getCustomerId()).thenReturn(customerId);
        when(operation.getType()).thenReturn(type);
        return operation;
    }
}

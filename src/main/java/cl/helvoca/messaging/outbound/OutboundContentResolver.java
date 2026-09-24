package cl.helvoca.messaging.outbound;

import cl.helvoca.operations.BusinessOperation;
import cl.helvoca.payment.BusinessPayment;
import cl.helvoca.payment.BusinessPaymentRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class OutboundContentResolver {
    private final BusinessPaymentRepository payments;

    public OutboundContentResolver(BusinessPaymentRepository payments) {
        this.payments = payments;
    }

    public String render(UUID businessId,
                         UUID customerId,
                         OutboundMessage.Purpose purpose,
                         BusinessOperation operation) {
        if (purpose == null || operation == null) throw new IllegalArgumentException("purpose and operation are required");
        if (!businessId.equals(operation.getBusinessId())) throw new IllegalArgumentException("Operation tenant mismatch");
        if (operation.getCustomerId() == null || !operation.getCustomerId().equals(customerId)) {
            throw new IllegalArgumentException("Operation does not belong to customer");
        }
        return switch (purpose) {
            case PAYMENT_LINK -> paymentLink(businessId, customerId, operation);
            case BOOKING_CONFIRMATION -> booking(operation);
            case MEETING_LINK -> meeting(operation);
            case ORDER_STATUS -> order(operation);
            case QUOTE -> quote(operation);
            case REMINDER -> reminder(operation);
            case DELIVERY_STATUS -> delivery(operation);
            case INCIDENT_NOTICE -> throw new IllegalArgumentException("Incident notices require prepared campaign content");
            case PRODUCT_SHOWCASE -> throw new IllegalArgumentException("Product showcase content must come from catalog media");
        };
    }

    private String paymentLink(UUID businessId, UUID customerId, BusinessOperation operation) {
        requireType(operation, BusinessOperation.Type.PAYMENT);
        BusinessPayment payment = payments.findByOperationIdAndBusinessId(operation.getId(), businessId)
                .orElseThrow(() -> new IllegalStateException("Payment record is missing"));
        if (payment.getCustomerId() == null || !payment.getCustomerId().equals(customerId)) {
            throw new IllegalStateException("Payment does not belong to customer");
        }
        if (payment.getStatus() != BusinessPayment.Status.REQUIRES_ACTION
                && payment.getStatus() != BusinessPayment.Status.PENDING) {
            throw new IllegalStateException("Payment is not awaiting customer action");
        }
        String checkout = requireHttps(payment.getCheckoutUrl(), "checkout URL");
        return "Tienes un pago pendiente por " + money(payment.getAmount(), payment.getCurrency())
                + ". Usa este enlace seguro generado por el proveedor: " + checkout;
    }

    private String booking(BusinessOperation operation) {
        requireType(operation, BusinessOperation.Type.BOOKING);
        String startAt = metadata(operation, "startAt");
        return "Tu reserva está confirmada" + (startAt == null ? "." : " para " + startAt + ".");
    }

    private String meeting(BusinessOperation operation) {
        String url = metadata(operation, "meetingUrl");
        return "Tu enlace de reunión es: " + requireHttps(url, "meeting URL");
    }

    private String order(BusinessOperation operation) {
        requireType(operation, BusinessOperation.Type.ORDER);
        String total = operation.getTotal() == null ? "" : " Total: " + money(operation.getTotal(), operation.getCurrency()) + ".";
        return "Estado de tu pedido: " + operation.getStatus().name() + "." + total;
    }

    private String quote(BusinessOperation operation) {
        requireType(operation, BusinessOperation.Type.QUOTE);
        String total = operation.getTotal() == null ? "sin total calculado" : money(operation.getTotal(), operation.getCurrency());
        return "Tu cotización está disponible. Total: " + total + ".";
    }

    private String reminder(BusinessOperation operation) {
        return "Recordatorio de tu gestión " + operation.getType().name() + ": estado " + operation.getStatus().name() + ".";
    }

    private String delivery(BusinessOperation operation) {
        requireType(operation, BusinessOperation.Type.DELIVERY);
        String tracking = metadata(operation, "trackingStatus");
        return "Estado de entrega: " + (tracking == null ? operation.getStatus().name() : tracking) + ".";
    }

    private static void requireType(BusinessOperation operation, BusinessOperation.Type expected) {
        if (operation.getType() != expected) throw new IllegalArgumentException("Purpose does not match operation type");
    }

    private static String metadata(BusinessOperation operation, String key) {
        Map<String, Object> metadata = operation.getMetadata();
        if (metadata == null) return null;
        Object value = metadata.get(key);
        if (value == null) return null;
        String text = Objects.toString(value, "").trim();
        return text.isBlank() ? null : text;
    }

    private static String requireHttps(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalStateException(label + " is missing");
        try {
            URI uri = URI.create(value.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
                throw new IllegalArgumentException();
            }
            return uri.toString();
        } catch (RuntimeException e) {
            throw new IllegalStateException(label + " is not a trusted HTTPS URL");
        }
    }

    private static String money(BigDecimal amount, String currency) {
        if (amount == null) return "monto pendiente";
        String safeCurrency = currency == null || currency.isBlank() ? "" : currency.trim().toUpperCase() + " ";
        return safeCurrency + amount.stripTrailingZeros().toPlainString();
    }
}

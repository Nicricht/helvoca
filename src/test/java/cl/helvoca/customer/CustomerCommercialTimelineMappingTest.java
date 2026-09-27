package cl.helvoca.customer;

import cl.helvoca.inventory.InventoryReservation;
import cl.helvoca.messaging.outbound.OutboundMessage;
import cl.helvoca.operations.BusinessOperation;
import cl.helvoca.operations.BusinessOrder;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerCommercialTimelineMappingTest {

    @Test
    void operationTitlesCoverEveryCommercialOperationTypeAndNull() throws Exception {
        Method method = privateMethod("operationTitle", BusinessOperation.Type.class);

        assertEquals("Actividad comercial", invoke(method, (Object) null));
        assertEquals("Pedido comercial", invoke(method, BusinessOperation.Type.ORDER));
        assertEquals("Cotización", invoke(method, BusinessOperation.Type.QUOTE));
        assertEquals("Pago comercial", invoke(method, BusinessOperation.Type.PAYMENT));
        assertEquals("Compra omnicanal", invoke(method, BusinessOperation.Type.REQUEST));
        assertEquals("Oportunidad", invoke(method, BusinessOperation.Type.LEAD));
        assertEquals("Despacho", invoke(method, BusinessOperation.Type.DELIVERY));
        assertEquals("Reserva comercial", invoke(method, BusinessOperation.Type.BOOKING));
    }

    @Test
    void inventoryTitlesAndDetailsCoverAllStatusesAndQuantityForms() throws Exception {
        Method title = privateMethod("inventoryTitle", InventoryReservation.Status.class);
        assertEquals("Inventario", invoke(title, (Object) null));
        assertEquals("Inventario reservado", invoke(title, InventoryReservation.Status.ACTIVE));
        assertEquals("Inventario consumido", invoke(title, InventoryReservation.Status.CONSUMED));
        assertEquals("Inventario liberado", invoke(title, InventoryReservation.Status.RELEASED));
        assertEquals("Reserva de inventario expirada", invoke(title, InventoryReservation.Status.EXPIRED));

        Method detail = privateMethod(
                "inventoryDetail", String.class, String.class, int.class, UUID.class);
        UUID variantId = UUID.randomUUID();

        assertEquals("1 unidad", invoke(detail, null, null, 1, null));
        assertEquals("Producto · 2 unidades", invoke(detail, "Producto", null, 2, null));
        assertEquals("Producto · Variante · 1 unidad",
                invoke(detail, "Producto", "Variante", 1, variantId));
        assertEquals("2 unidades", invoke(detail, " ", "Variante", 2, variantId));
        assertEquals("Producto · 2 unidades", invoke(detail, "Producto", " ", 2, variantId));
    }

    @Test
    void outboundTitlesCoverEveryPurposeIncludingNull() throws Exception {
        Method method = privateMethod("outboundTitle", OutboundMessage.Purpose.class);

        assertEquals("Mensaje saliente", invoke(method, (Object) null));
        assertEquals("Link de pago", invoke(method, OutboundMessage.Purpose.PAYMENT_LINK));
        assertEquals("Confirmación de pago", invoke(method, OutboundMessage.Purpose.PAYMENT_CONFIRMATION));
        assertEquals("Confirmación de reserva", invoke(method, OutboundMessage.Purpose.BOOKING_CONFIRMATION));
        assertEquals("Link de reunión", invoke(method, OutboundMessage.Purpose.MEETING_LINK));
        assertEquals("Estado del pedido", invoke(method, OutboundMessage.Purpose.ORDER_STATUS));
        assertEquals("Cotización enviada", invoke(method, OutboundMessage.Purpose.QUOTE));
        assertEquals("Recordatorio", invoke(method, OutboundMessage.Purpose.REMINDER));
        assertEquals("Estado del despacho", invoke(method, OutboundMessage.Purpose.DELIVERY_STATUS));
        assertEquals("Aviso de incidente", invoke(method, OutboundMessage.Purpose.INCIDENT_NOTICE));
        assertEquals("Productos enviados", invoke(method, OutboundMessage.Purpose.PRODUCT_SHOWCASE));
    }

    @Test
    void outboundDetailsCoverEveryDeliveryState() throws Exception {
        Method method = privateMethod("outboundDetail", OutboundMessage.class);

        assertDetail(method, OutboundMessage.Status.PREPARED, "Mensaje preparado");
        assertDetail(method, OutboundMessage.Status.QUEUED, "Mensaje en cola");
        assertDetail(method, OutboundMessage.Status.SENT, "Mensaje enviado");
        assertDetail(method, OutboundMessage.Status.FAILED, "Error de envío");
        assertDetail(method, OutboundMessage.Status.CANCELLED, "Mensaje cancelado");
        assertDetail(method, OutboundMessage.Status.BLOCKED, "Mensaje bloqueado");
    }

    @Test
    void utilityMappingsCoverNullBlankFallbackAndParsingBranches() throws Exception {
        Method money = privateMethod("money", BigDecimal.class, String.class);
        assertNull(invoke(money, null, "CLP"));
        assertEquals("1500 CLP", invoke(money, new BigDecimal("1500.00"), null));
        assertEquals("1500 USD", invoke(money, new BigDecimal("1500"), "USD"));

        Method source = privateMethod("source", BusinessOrder.Source.class);
        assertEquals("SYSTEM", invoke(source, (Object) null));
        assertEquals("VOICE", invoke(source, BusinessOrder.Source.VOICE));

        Method enumName = privateMethod("enumName", Enum.class);
        assertNull(invoke(enumName, (Object) null));
        assertEquals("CONFIRMED", invoke(enumName, BusinessOrder.Status.CONFIRMED));

        Method text = privateMethod("text", String.class, String.class);
        assertEquals("fallback", invoke(text, null, "fallback"));
        assertEquals("fallback", invoke(text, " ", "fallback"));
        assertEquals("value", invoke(text, "value", "fallback"));

        Method uuid = privateMethod("uuidOrNull", Object.class);
        UUID id = UUID.randomUUID();
        assertNull(invoke(uuid, (Object) null));
        assertNull(invoke(uuid, "bad-uuid"));
        assertEquals(id, invoke(uuid, id.toString()));
    }

    @Test
    void temporalHelpersCoverNullBeforeAfterAndEventFiltering() throws Exception {
        Method newer = privateMethod("newer", Instant.class, Instant.class);
        Instant now = Instant.parse("2026-09-27T10:00:00Z");
        assertFalse((Boolean) invoke(newer, null, now));
        assertTrue((Boolean) invoke(newer, now, null));
        assertTrue((Boolean) invoke(newer, now.plusSeconds(1), now));
        assertFalse((Boolean) invoke(newer, now.minusSeconds(1), now));

        Method add = privateMethod("add", List.class, CustomerCommercialTimelineService.TimelineEvent.class);
        List<CustomerCommercialTimelineService.TimelineEvent> events = new ArrayList<>();
        CustomerCommercialTimelineService.TimelineEvent noDate =
                new CustomerCommercialTimelineService.TimelineEvent(
                        null, "TEST", "SYSTEM", "No date", null, null, null);
        CustomerCommercialTimelineService.TimelineEvent dated =
                new CustomerCommercialTimelineService.TimelineEvent(
                        now, "TEST", "SYSTEM", "Dated", null, null, null);

        invoke(add, events, noDate);
        assertTrue(events.isEmpty());
        invoke(add, events, dated);
        assertEquals(List.of(dated), events);
    }

    private static void assertDetail(Method method,
                                     OutboundMessage.Status status,
                                     String expected) throws Exception {
        OutboundMessage message = mock(OutboundMessage.class);
        when(message.getStatus()).thenReturn(status);
        assertEquals(expected, invoke(method, message));
    }

    private static Method privateMethod(String name, Class<?>... parameterTypes) throws Exception {
        Method method = CustomerCommercialTimelineService.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method;
    }

    @SuppressWarnings("unchecked")
    private static <T> T invoke(Method method, Object... args) throws Exception {
        return (T) method.invoke(null, args);
    }
}

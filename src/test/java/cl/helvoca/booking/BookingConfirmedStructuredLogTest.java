package cl.helvoca.booking;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.operations.BusinessOperation;
import cl.helvoca.operations.BusinessOperationRepository;
import cl.helvoca.operations.BusinessOrder;
import cl.helvoca.operations.ConversationStateService;
import cl.helvoca.operations.UniversalConfirmationService;
import cl.helvoca.schedule.BusinessScheduleService;
import cl.helvoca.servicecatalog.ServiceItem;
import cl.helvoca.servicecatalog.ServiceItemRepository;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BookingConfirmedStructuredLogTest {

    @Test
    void emitsNonPiiBookingConfirmedLogOnlyAfterCommit() {
        BookingRepository bookings = mock(BookingRepository.class);
        BusinessOperationRepository operations = mock(BusinessOperationRepository.class);
        ServiceItemRepository services = mock(ServiceItemRepository.class);
        BusinessScheduleService schedule = mock(BusinessScheduleService.class);
        UniversalConfirmationService confirmations = mock(UniversalConfirmationService.class);
        ConversationStateService conversationState = mock(ConversationStateService.class);
        BusinessRepository businesses = mock(BusinessRepository.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);

        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID token = UUID.randomUUID();
        Instant startAt = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);

        BusinessOperation operation = new BusinessOperation();
        ReflectionTestUtils.setField(operation, "id", operationId);
        operation.setBusinessId(businessId);
        operation.setCustomerId(customerId);
        operation.setType(BusinessOperation.Type.BOOKING);
        operation.setStatus(BusinessOperation.Status.AWAITING_CONFIRMATION);
        operation.setSource(BusinessOrder.Source.WHATSAPP);
        operation.setRevision(1);
        operation.setConfirmationToken(token);
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("serviceId", serviceId.toString());
        metadata.put("startAt", startAt.toString());
        operation.setMetadata(metadata);

        ServiceItem service = new ServiceItem();
        ReflectionTestUtils.setField(service, "id", serviceId);
        service.setName("Peluquería");
        service.setDurationMinutes(30);
        service.setPrice(new BigDecimal("10000"));
        service.setActive(true);

        when(confirmations.authorize(any(), any(), any(), any(), any(), any()))
                .thenReturn(UniversalConfirmationService.Authorization.AUTHORIZED);
        when(operations.findByIdAndBusinessId(operationId, businessId)).thenReturn(Optional.of(operation));
        when(services.findByIdAndBusinessId(serviceId, businessId)).thenReturn(Optional.of(service));
        when(schedule.isWithinBusinessHours(any(), any(), any())).thenReturn(true);
        when(bookings.countOverlaps(any(), any(), any(), any(), any(), any())).thenReturn(0L);
        when(bookings.saveAndFlush(any(Booking.class))).thenAnswer(invocation -> {
            Booking booking = invocation.getArgument(0);
            ReflectionTestUtils.setField(booking, "id", bookingId);
            return booking;
        });
        when(operations.saveAndFlush(any(BusinessOperation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(businesses.findById(businessId)).thenReturn(Optional.empty());
        when(jdbc.execute(anyString())).thenReturn(null);

        BookingConfirmationWorkflowService workflow = new BookingConfirmationWorkflowService(
                bookings, operations, services, schedule, confirmations, conversationState, businesses, jdbc);

        Logger logger = (Logger) LoggerFactory.getLogger(BookingConfirmationWorkflowService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        TransactionSynchronizationManager.initSynchronization();

        try {
            JSONObject result = workflow.execute(
                    businessId,
                    customerId,
                    null,
                    "+56900000000",
                    BusinessOrder.Source.WHATSAPP,
                    BookingSource.AI_WHATSAPP,
                    new JSONObject()
                            .put("operationId", operationId.toString())
                            .put("confirmationToken", token.toString()));

            assertTrue(result.getBoolean("success"));
            assertFalse(messages(appender).contains("BOOKING_CONFIRMED"));

            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }

            String logs = messages(appender);
            assertTrue(logs.contains("BOOKING_CONFIRMED"));
            assertTrue(logs.contains("businessId=" + businessId));
            assertTrue(logs.contains("bookingId=" + bookingId));
            assertTrue(logs.contains("operationId=" + operationId));
            assertTrue(logs.contains("startAt=" + startAt));
            assertTrue(logs.contains("source=WHATSAPP"));
            assertFalse(logs.contains(customerId.toString()));
            assertFalse(logs.contains("+56900000000"));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private static String messages(ListAppender<ILoggingEvent> appender) {
        return appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (left, right) -> left + "\n" + right);
    }
}

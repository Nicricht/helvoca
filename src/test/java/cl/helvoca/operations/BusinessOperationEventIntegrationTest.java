package cl.helvoca.operations;

import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest
class BusinessOperationEventIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("app.seed.enabled", () -> "false");
    }

    @Autowired BusinessRepository businesses;
    @Autowired BusinessOperationRepository operations;
    @Autowired BusinessOperationEventRepository events;
    @Autowired JdbcTemplate jdbc;

    @Test
    void capturesSemanticOrderLifecycleWithoutCopyingArbitraryMetadata() {
        Business business = new Business();
        business.setName("Event Log Test");
        business = businesses.saveAndFlush(business);

        BusinessOperation operation = new BusinessOperation();
        operation.setBusinessId(business.getId());
        operation.setType(BusinessOperation.Type.ORDER);
        operation.setStatus(BusinessOperation.Status.AWAITING_CONFIRMATION);
        operation.setConfirmationToken(UUID.randomUUID());
        operation.setSource(BusinessOrder.Source.WHATSAPP);
        operation.setRevision(1);
        operation.setTotal(new BigDecimal("12500"));
        operation.setCurrency("CLP");
        operation.setMetadata(Map.of("secretLikeValue", "must-not-be-copied"));
        operation = operations.saveAndFlush(operation);

        operation.setRevision(2);
        operation.setTotal(new BigDecimal("13900"));
        operation = operations.saveAndFlush(operation);

        operation.setRevision(3);
        operation.setStatus(BusinessOperation.Status.CONFIRMED);
        operation.setConfirmationToken(null);
        operation = operations.saveAndFlush(operation);

        List<BusinessOperationEvent> history = events
                .findTop100ByBusinessIdAndOperationIdOrderBySequenceNoDesc(business.getId(), operation.getId());

        assertEquals(3, history.size());
        assertEquals("ORDER_CONFIRMED", history.get(0).getEventType());
        assertEquals("ORDER_UPDATED", history.get(1).getEventType());
        assertEquals("ORDER_QUOTED", history.get(2).getEventType());
        assertEquals(BusinessOperationEvent.ActorType.AI, history.get(2).getActorType());
        assertEquals(BusinessOperation.Status.AWAITING_CONFIRMATION, history.get(0).getPreviousStatus());
        assertEquals(0, new BigDecimal(history.get(0).getPayload().get("total").toString())
                .compareTo(new BigDecimal("13900")));
        assertFalse(history.stream().anyMatch(event -> event.getPayload().containsKey("secretLikeValue")));
    }

    @Test
    void eventRowsRejectUpdateAndDeleteAtDatabaseLevel() {
        Business business = new Business();
        business.setName("Immutable Event Test");
        business = businesses.saveAndFlush(business);

        BusinessOperation operation = new BusinessOperation();
        operation.setBusinessId(business.getId());
        operation.setType(BusinessOperation.Type.REQUEST);
        operation.setStatus(BusinessOperation.Status.CONFIRMED);
        operation.setSource(BusinessOrder.Source.API);
        operation = operations.saveAndFlush(operation);

        BusinessOperationEvent event = events
                .findTop100ByBusinessIdAndOperationIdOrderBySequenceNoDesc(business.getId(), operation.getId())
                .getFirst();

        assertThrows(DataAccessException.class, () -> jdbc.update(
                "UPDATE business_operation_event SET event_type = 'TAMPERED' WHERE id = ?",
                event.getId()));
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "DELETE FROM business_operation_event WHERE id = ?",
                event.getId()));
    }

    @Test
    void operationDeletionCreatesHistoricalEventWithoutBlockingCleanup() {
        Business business = new Business();
        business.setName("Delete Event Test");
        business = businesses.saveAndFlush(business);

        BusinessOperation operation = new BusinessOperation();
        operation.setBusinessId(business.getId());
        operation.setType(BusinessOperation.Type.LEAD);
        operation.setStatus(BusinessOperation.Status.CONFIRMED);
        operation.setSource(BusinessOrder.Source.MANUAL);
        operation = operations.saveAndFlush(operation);

        var operationId = operation.getId();
        var businessId = business.getId();
        operations.deleteById(operationId);
        operations.flush();

        List<BusinessOperationEvent> history = events
                .findTop100ByBusinessIdAndOperationIdOrderBySequenceNoDesc(businessId, operationId);
        assertEquals("LEAD_DELETED", history.getFirst().getEventType());
        assertEquals(BusinessOperationEvent.ActorType.HUMAN, history.getFirst().getActorType());
        assertFalse(operations.existsById(operationId));
    }

    @Test
    void paymentMaterializationIsRecordedAsPaymentConfirmed() {
        Business business = new Business();
        business.setName("Payment Confirmation Event Test");
        business = businesses.saveAndFlush(business);

        BusinessOperation operation = new BusinessOperation();
        operation.setBusinessId(business.getId());
        operation.setType(BusinessOperation.Type.PAYMENT);
        operation.setStatus(BusinessOperation.Status.AWAITING_CONFIRMATION);
        operation.setConfirmationToken(UUID.randomUUID());
        operation.setSource(BusinessOrder.Source.VOICE);
        operation.setRevision(1);
        operation.setTotal(new BigDecimal("5000"));
        operation.setCurrency("CLP");
        operation = operations.saveAndFlush(operation);

        operation.setStatus(BusinessOperation.Status.CONFIRMED);
        operation.setConfirmationToken(null);
        operation.setMetadata(Map.of("paymentStatus", "REQUIRES_ACTION"));
        operation = operations.saveAndFlush(operation);

        BusinessOperationEvent latest = events
                .findTop100ByBusinessIdAndOperationIdOrderBySequenceNoDesc(business.getId(), operation.getId())
                .getFirst();

        assertEquals("PAYMENT_CONFIRMED", latest.getEventType());
        assertEquals(BusinessOperationEvent.ActorType.AI, latest.getActorType());
        assertEquals("REQUIRES_ACTION", latest.getPayload().get("paymentStatusAfter"));
    }

    @Test
    void paymentStatusChangeIsAttributedToProvider() {
        Business business = new Business();
        business.setName("Provider Event Test");
        business = businesses.saveAndFlush(business);

        BusinessOperation operation = new BusinessOperation();
        operation.setBusinessId(business.getId());
        operation.setType(BusinessOperation.Type.PAYMENT);
        operation.setStatus(BusinessOperation.Status.CONFIRMED);
        operation.setSource(BusinessOrder.Source.VOICE);
        operation.setRevision(1);
        operation.setTotal(new BigDecimal("5000"));
        operation.setCurrency("CLP");
        operation.setMetadata(Map.of("paymentStatus", "PENDING"));
        operation = operations.saveAndFlush(operation);

        operation.setRevision(2);
        operation.setMetadata(Map.of("paymentStatus", "SUCCEEDED"));
        operation = operations.saveAndFlush(operation);

        BusinessOperationEvent latest = events
                .findTop100ByBusinessIdAndOperationIdOrderBySequenceNoDesc(business.getId(), operation.getId())
                .getFirst();

        assertEquals("PAYMENT_STATUS_CHANGED", latest.getEventType());
        assertEquals(BusinessOperationEvent.ActorType.PROVIDER, latest.getActorType());
        assertEquals("PENDING", latest.getPayload().get("paymentStatusBefore"));
        assertEquals("SUCCEEDED", latest.getPayload().get("paymentStatusAfter"));
    }
}

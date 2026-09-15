package cl.helvoca.operations;

import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.customer.Customer;
import cl.helvoca.customer.CustomerRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest
@Transactional
class UniversalConfirmationIntegrationTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("app.seed.enabled", () -> "false");
    }

    @Autowired BusinessRepository businesses;
    @Autowired CustomerRepository customers;
    @Autowired BusinessOperationRepository operations;
    @Autowired OperationConfirmationRepository confirmations;
    @Autowired BusinessOperationEventRepository events;
    @Autowired UniversalConfirmationService service;
    @Autowired EntityManager entityManager;

    @Test
    void confirmationIsDurableRevisionedAndCrossChannel() {
        Business business = new Business();
        business.setName("Confirmation tenant");
        business = businesses.saveAndFlush(business);
        Customer customer = new Customer();
        customer.setBusinessId(business.getId());
        customer.setName("Customer");
        customer = customers.saveAndFlush(customer);

        UUID voiceSource = UUID.randomUUID();
        UUID token1 = UUID.randomUUID();
        BusinessOperation op = new BusinessOperation();
        op.setBusinessId(business.getId());
        op.setCustomerId(customer.getId());
        op.setSourceReferenceId(voiceSource);
        op.setType(BusinessOperation.Type.ORDER);
        op.setStatus(BusinessOperation.Status.AWAITING_CONFIRMATION);
        op.setSource(BusinessOrder.Source.VOICE);
        op.setRevision(1);
        op.setConfirmationToken(token1);
        op = operations.saveAndFlush(op);

        OperationConfirmation first = confirmations
                .findByBusinessIdAndOperationIdAndOperationRevision(business.getId(), op.getId(), 1)
                .orElseThrow();
        assertEquals(OperationConfirmation.State.AWAITING, first.getState());
        assertEquals(UniversalConfirmationService.Authorization.AUTHORIZED,
                service.authorize(business.getId(), op.getId(), customer.getId(), UUID.randomUUID(), null,
                        token1));

        UUID token2 = UUID.randomUUID();
        op.setRevision(2);
        op.setConfirmationToken(token2);
        op.setStatus(BusinessOperation.Status.AWAITING_CONFIRMATION);
        operations.saveAndFlush(op);

        // The confirmation lifecycle is maintained by a PostgreSQL trigger. Refresh
        // the managed entity so this assertion observes the database-owned state.
        entityManager.refresh(first);
        assertEquals(OperationConfirmation.State.INVALIDATED, first.getState());
        assertEquals(UniversalConfirmationService.Authorization.STALE,
                service.authorize(business.getId(), op.getId(), customer.getId(), UUID.randomUUID(), null,
                        token1));
        assertEquals(UniversalConfirmationService.Authorization.AUTHORIZED,
                service.authorize(business.getId(), op.getId(), customer.getId(), UUID.randomUUID(), null,
                        token2));

        op.setStatus(BusinessOperation.Status.CONFIRMED);
        op.setConfirmationToken(null);
        operations.saveAndFlush(op);
        UUID whatsappSource = UUID.randomUUID();
        service.recordResolution(business.getId(), op.getId(), token2, BusinessOrder.Source.WHATSAPP, whatsappSource);
        OperationConfirmation consumed = confirmations.findByBusinessIdAndToken(business.getId(), token2).orElseThrow();
        entityManager.refresh(consumed);
        assertEquals(OperationConfirmation.State.CONSUMED, consumed.getState());
        assertEquals(BusinessOrder.Source.WHATSAPP, consumed.getResolvedChannel());
        assertEquals(whatsappSource, consumed.getResolvedSourceReferenceId());
        assertNotNull(consumed.getResolvedAt());

        assertEquals(UniversalConfirmationService.Authorization.IDEMPOTENT_REPLAY,
                service.authorize(business.getId(), op.getId(), customer.getId(), UUID.randomUUID(), null,
                        token2));

        // Consumed confirmation remains the idempotency key even if a downstream
        // workflow advances the operation revision after confirmation.
        op.setRevision(3);
        op.setStatus(BusinessOperation.Status.EXECUTING);
        operations.saveAndFlush(op);
        assertEquals(UniversalConfirmationService.Authorization.IDEMPOTENT_REPLAY,
                service.authorize(business.getId(), op.getId(), customer.getId(), UUID.randomUUID(), null,
                        token2));

        assertEquals(UniversalConfirmationService.Authorization.NOT_AWAITING,
                service.authorize(business.getId(), op.getId(), customer.getId(), UUID.randomUUID(), null,
                        null));
        assertEquals(UniversalConfirmationService.Authorization.NOT_AWAITING,
                service.authorize(business.getId(), op.getId(), customer.getId(), UUID.randomUUID(), null,
                        UUID.randomUUID()));
    }

    @Test
    void explicitCustomerBindingCannotFallBackToPhoneOrSourceReference() {
        Business business = new Business();
        business.setName("Ownership tenant");
        business = businesses.saveAndFlush(business);

        Customer owner = new Customer();
        owner.setBusinessId(business.getId());
        owner.setName("Owner");
        owner = customers.saveAndFlush(owner);
        Customer other = new Customer();
        other.setBusinessId(business.getId());
        other.setName("Other");
        other = customers.saveAndFlush(other);

        UUID sourceReference = UUID.randomUUID();
        UUID token = UUID.randomUUID();
        BusinessOperation op = new BusinessOperation();
        op.setBusinessId(business.getId());
        op.setCustomerId(owner.getId());
        op.setSourceReferenceId(sourceReference);
        op.setContactPhone("+56911111111");
        op.setType(BusinessOperation.Type.BOOKING);
        op.setStatus(BusinessOperation.Status.AWAITING_CONFIRMATION);
        op.setSource(BusinessOrder.Source.VOICE);
        op.setRevision(1);
        op.setConfirmationToken(token);
        op = operations.saveAndFlush(op);

        assertEquals(UniversalConfirmationService.Authorization.NOT_OWNED,
                service.authorize(business.getId(), op.getId(), other.getId(), sourceReference,
                        "+56911111111", token));
        assertEquals(UniversalConfirmationService.Authorization.NOT_OWNED,
                service.authorize(business.getId(), op.getId(), null, sourceReference,
                        "+56911111111", token));
        assertEquals(UniversalConfirmationService.Authorization.AUTHORIZED,
                service.authorize(business.getId(), op.getId(), owner.getId(), UUID.randomUUID(), null, token));
    }

    @Test
    void expiredAndWrongTenantConfirmationsFailClosed() {
        Business a = new Business(); a.setName("A"); a = businesses.saveAndFlush(a);
        Business b = new Business(); b.setName("B"); b = businesses.saveAndFlush(b);
        Customer customer = new Customer();
        customer.setBusinessId(a.getId()); customer.setName("Customer"); customer = customers.saveAndFlush(customer);
        UUID token = UUID.randomUUID();
        BusinessOperation op = new BusinessOperation();
        op.setBusinessId(a.getId()); op.setCustomerId(customer.getId()); op.setType(BusinessOperation.Type.PAYMENT);
        op.setStatus(BusinessOperation.Status.AWAITING_CONFIRMATION); op.setSource(BusinessOrder.Source.VOICE);
        op.setRevision(1); op.setConfirmationToken(token); op = operations.saveAndFlush(op);

        OperationConfirmation confirmation = confirmations.findByBusinessIdAndToken(a.getId(), token).orElseThrow();
        confirmation.setExpiresAt(Instant.now().minusSeconds(1));
        confirmations.saveAndFlush(confirmation);

        assertEquals(UniversalConfirmationService.Authorization.EXPIRED,
                service.authorize(a.getId(), op.getId(), customer.getId(), null, null, token));
        assertEquals(UniversalConfirmationService.Authorization.NOT_FOUND,
                service.authorize(b.getId(), op.getId(), customer.getId(), null, null, token));
    }

    @Test
    void expandedOperationLifecycleRemainsCompatibleWithImmutableEventLog() {
        Business business = new Business();
        business.setName("Lifecycle tenant");
        business = businesses.saveAndFlush(business);

        BusinessOperation op = new BusinessOperation();
        op.setBusinessId(business.getId());
        op.setType(BusinessOperation.Type.REQUEST);
        op.setStatus(BusinessOperation.Status.DRAFT);
        op.setSource(BusinessOrder.Source.API);
        op = operations.saveAndFlush(op);

        op.setStatus(BusinessOperation.Status.PROPOSED);
        op = operations.saveAndFlush(op);
        op.setStatus(BusinessOperation.Status.EXECUTING);
        op = operations.saveAndFlush(op);
        op.setStatus(BusinessOperation.Status.COMPLETED);
        operations.saveAndFlush(op);

        var history = events.findTop100ByBusinessIdAndOperationIdOrderBySequenceNoDesc(business.getId(), op.getId());
        assertTrue(history.stream().anyMatch(event -> event.getStatus() == BusinessOperation.Status.PROPOSED));
        assertTrue(history.stream().anyMatch(event -> event.getStatus() == BusinessOperation.Status.EXECUTING));
        assertTrue(history.stream().anyMatch(event -> event.getStatus() == BusinessOperation.Status.COMPLETED));
    }
}

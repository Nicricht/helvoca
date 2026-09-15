package cl.helvoca.operations;

import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.customer.Customer;
import cl.helvoca.customer.CustomerRepository;
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
    @Autowired UniversalConfirmationService service;

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

        assertEquals(OperationConfirmation.State.INVALIDATED,
                confirmations.findById(first.getId()).orElseThrow().getState());
        assertEquals(UniversalConfirmationService.Authorization.STALE,
                service.authorize(business.getId(), op.getId(), customer.getId(), UUID.randomUUID(), null,
                        token1));
        assertEquals(UniversalConfirmationService.Authorization.AUTHORIZED,
                service.authorize(business.getId(), op.getId(), customer.getId(), UUID.randomUUID(), null,
                        token2));

        op.setStatus(BusinessOperation.Status.CONFIRMED);
        op.setConfirmationToken(null);
        operations.saveAndFlush(op);
        service.recordResolution(business.getId(), op.getId(), token2, BusinessOrder.Source.WHATSAPP, UUID.randomUUID());
        OperationConfirmation consumed = confirmations.findByBusinessIdAndToken(business.getId(), token2).orElseThrow();
        assertEquals(OperationConfirmation.State.CONSUMED, consumed.getState());
        assertEquals(BusinessOrder.Source.WHATSAPP, consumed.getResolvedChannel());
        assertNotNull(consumed.getResolvedAt());
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
}

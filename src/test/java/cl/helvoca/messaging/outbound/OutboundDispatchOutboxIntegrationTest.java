package cl.helvoca.messaging.outbound;

import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.customer.Customer;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.jobs.PersistentJob;
import cl.helvoca.jobs.PersistentJobStore;
import cl.helvoca.omnichannel.CustomerIdentity;
import cl.helvoca.omnichannel.CustomerIdentityService;
import cl.helvoca.operations.BusinessOperation;
import cl.helvoca.operations.BusinessOperationRepository;
import cl.helvoca.operations.BusinessOrder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest
@Transactional
class OutboundDispatchOutboxIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("app.seed.enabled", () -> "false");
        registry.add("app.jobs.enabled", () -> "false");
        registry.add("app.outbound.delivery-enabled", () -> "false");
    }

    @Autowired BusinessRepository businesses;
    @Autowired CustomerRepository customers;
    @Autowired CustomerIdentityService identities;
    @Autowired BusinessOperationRepository operations;
    @Autowired OutboundMessagingService outbound;
    @Autowired OutboundMessageRepository messages;
    @Autowired OutboundDispatchOutboxService outbox;
    @Autowired PersistentJobStore jobs;

    @Test
    void queueTransitionAndDurableJobAreAtomicAndIdempotent() {
        Business business = new Business();
        business.setName("Outbound outbox tenant");
        business = businesses.saveAndFlush(business);

        Customer customer = new Customer();
        customer.setBusinessId(business.getId());
        customer.setName("Customer");
        customer.setPhone("+56940000001");
        customer = customers.saveAndFlush(customer);

        CustomerIdentity identity = identities.verifyPhone(
                business.getId(), customer.getId(), customer.getPhone(),
                CustomerIdentity.VerificationStatus.CUSTOMER_VERIFIED, "TEST");

        BusinessOperation quote = new BusinessOperation();
        quote.setBusinessId(business.getId());
        quote.setCustomerId(customer.getId());
        quote.setType(BusinessOperation.Type.QUOTE);
        quote.setStatus(BusinessOperation.Status.CONFIRMED);
        quote.setSource(BusinessOrder.Source.API);
        quote.setTotal(new BigDecimal("12500"));
        quote.setCurrency("CLP");
        quote = operations.saveAndFlush(quote);

        OutboundMessage prepared = outbound.prepare(
                business.getId(), customer.getId(), OutboundMessage.Channel.WHATSAPP,
                OutboundMessage.Purpose.QUOTE, quote.getId(), identity.getId());
        assertEquals(OutboundMessage.Status.PREPARED, prepared.getStatus());

        PersistentJob first = outbox.queue(business.getId(), prepared.getId());
        PersistentJob replay = outbox.queue(business.getId(), prepared.getId());

        assertEquals(first.id(), replay.id());
        assertEquals(PersistentJob.Status.PENDING, first.status());
        assertEquals(PersistentJob.Type.OUTBOUND_MESSAGE_DISPATCH, first.jobType());
        assertEquals(quote.getId(), first.operationId());
        assertEquals(OutboundMessage.Status.QUEUED,
                messages.findByIdAndBusinessId(prepared.getId(), business.getId()).orElseThrow().getStatus());
        assertEquals(first.id(), jobs.findByIdempotencyKey(
                business.getId(), "outbound-message-dispatch:" + prepared.getId()).orElseThrow().id());
        assertThrows(IllegalStateException.class,
                () -> outbound.dispatch(business.getId(), prepared.getId()));
        assertEquals(OutboundMessage.Status.QUEUED,
                messages.findByIdAndBusinessId(prepared.getId(), business.getId()).orElseThrow().getStatus());
    }
}

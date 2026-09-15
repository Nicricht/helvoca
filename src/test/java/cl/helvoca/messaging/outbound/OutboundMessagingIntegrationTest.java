package cl.helvoca.messaging.outbound;

import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.customer.Customer;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.omnichannel.CustomerIdentity;
import cl.helvoca.omnichannel.CustomerIdentityService;
import cl.helvoca.operations.BusinessOperation;
import cl.helvoca.operations.BusinessOperationRepository;
import cl.helvoca.operations.BusinessOrder;
import cl.helvoca.payment.BusinessPayment;
import cl.helvoca.payment.BusinessPaymentRepository;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest
@Transactional
class OutboundMessagingIntegrationTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("app.seed.enabled", () -> "false");
        registry.add("app.outbound.delivery-enabled", () -> "false");
    }

    @Autowired BusinessRepository businesses;
    @Autowired CustomerRepository customers;
    @Autowired CustomerIdentityService identities;
    @Autowired BusinessOperationRepository operations;
    @Autowired BusinessPaymentRepository payments;
    @Autowired OutboundMessagingService outbound;
    @Autowired OutboundMessageRepository messages;

    @Test
    void verifiedRecipientAndBackendPaymentLinkAreRequiredAndPreparationIsIdempotent() {
        Business business = business("Outbound tenant");
        Customer customer = customer(business, "+56911112222");
        BusinessOperation paymentOperation = operation(business, customer, BusinessOperation.Type.PAYMENT);
        payment(paymentOperation, customer, "https://sandbox.example.test/pay/abc");

        assertThrows(IllegalStateException.class, () -> outbound.prepare(
                business.getId(), customer.getId(), OutboundMessage.Channel.WHATSAPP,
                OutboundMessage.Purpose.PAYMENT_LINK, paymentOperation.getId(), null));

        CustomerIdentity identity = identities.verifyPhone(
                business.getId(), customer.getId(), customer.getPhone(),
                CustomerIdentity.VerificationStatus.CUSTOMER_VERIFIED, "TEST");

        OutboundMessage first = outbound.prepare(
                business.getId(), customer.getId(), OutboundMessage.Channel.WHATSAPP,
                OutboundMessage.Purpose.PAYMENT_LINK, paymentOperation.getId(), identity.getId());
        OutboundMessage second = outbound.prepare(
                business.getId(), customer.getId(), OutboundMessage.Channel.WHATSAPP,
                OutboundMessage.Purpose.PAYMENT_LINK, paymentOperation.getId(), identity.getId());

        assertEquals(first.getId(), second.getId());
        assertEquals(1L, messages.count());
        assertTrue(first.getContentText().contains("https://sandbox.example.test/pay/abc"));
        assertTrue(first.getContentText().contains("CLP 24990"));
        assertEquals("+56911112222", first.getRecipientAddress());
        assertEquals(OutboundMessage.Status.PREPARED, first.getStatus());
        assertThrows(IllegalStateException.class, () -> outbound.dispatch(business.getId(), first.getId()));
        assertEquals(OutboundMessage.Status.PREPARED,
                messages.findByIdAndBusinessId(first.getId(), business.getId()).orElseThrow().getStatus());
    }

    @Test
    void tenantAndCustomerMismatchesFailClosed() {
        Business tenantA = business("A");
        Business tenantB = business("B");
        Customer customerA = customer(tenantA, "+56933334444");
        Customer customerB = customer(tenantB, "+56933334444");
        BusinessOperation quoteA = operation(tenantA, customerA, BusinessOperation.Type.QUOTE);
        quoteA.setTotal(new BigDecimal("5000"));
        quoteA = operations.saveAndFlush(quoteA);
        CustomerIdentity identityB = identities.verifyPhone(
                tenantB.getId(), customerB.getId(), customerB.getPhone(),
                CustomerIdentity.VerificationStatus.MANUAL_VERIFIED, "TEST");

        BusinessOperation finalQuoteA = quoteA;
        assertThrows(IllegalArgumentException.class, () -> outbound.prepare(
                tenantA.getId(), customerA.getId(), OutboundMessage.Channel.WHATSAPP,
                OutboundMessage.Purpose.QUOTE, finalQuoteA.getId(), identityB.getId()));
        assertEquals(0L, messages.count());
    }

    private Business business(String name) {
        Business b = new Business();
        b.setName(name);
        return businesses.saveAndFlush(b);
    }

    private Customer customer(Business business, String phone) {
        Customer c = new Customer();
        c.setBusinessId(business.getId());
        c.setName("Customer");
        c.setPhone(phone);
        return customers.saveAndFlush(c);
    }

    private BusinessOperation operation(Business business, Customer customer, BusinessOperation.Type type) {
        BusinessOperation op = new BusinessOperation();
        op.setBusinessId(business.getId());
        op.setCustomerId(customer.getId());
        op.setType(type);
        op.setStatus(BusinessOperation.Status.CONFIRMED);
        op.setSource(BusinessOrder.Source.API);
        op.setCurrency("CLP");
        return operations.saveAndFlush(op);
    }

    private BusinessPayment payment(BusinessOperation operation, Customer customer, String checkoutUrl) {
        BusinessPayment p = new BusinessPayment();
        p.setOperationId(operation.getId());
        p.setBusinessId(operation.getBusinessId());
        p.setCustomerId(customer.getId());
        p.setTargetOperationId(UUID.randomUUID());
        p.setProvider("TEST_SANDBOX");
        p.setIdempotencyKey("test:" + operation.getId());
        p.setAmount(new BigDecimal("24990"));
        p.setCurrency("CLP");
        p.setStatus(BusinessPayment.Status.REQUIRES_ACTION);
        p.setCheckoutUrl(checkoutUrl);
        p.setSource(BusinessOrder.Source.API);
        return payments.saveAndFlush(p);
    }
}

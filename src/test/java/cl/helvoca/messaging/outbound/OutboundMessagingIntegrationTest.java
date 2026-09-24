package cl.helvoca.messaging.outbound;

import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.catalog.CatalogItem;
import cl.helvoca.catalog.CatalogItemRepository;
import cl.helvoca.catalog.CatalogMedia;
import cl.helvoca.catalog.CatalogMediaRepository;
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
    @Autowired CatalogItemRepository catalog;
    @Autowired CatalogMediaRepository catalogMedia;
    @Autowired OutboundMessagingService outbound;
    @Autowired OutboundMessageRepository messages;

    @Test
    void verifiedRecipientAndBackendPaymentLinkAreRequiredAndPreparationIsIdempotent() {
        Business business = business("Outbound tenant");
        Customer customer = customer(business, "+56911112222");
        BusinessOperation targetOperation = operation(business, customer, BusinessOperation.Type.ORDER);
        BusinessOperation paymentOperation = operation(business, customer, BusinessOperation.Type.PAYMENT);
        payment(paymentOperation, targetOperation, customer, "https://sandbox.example.test/pay/abc");

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
    void catalogMediaPreparationIsTenantSafeAndIdempotent() {
        Business business = business("Visual catalog tenant");
        Customer customer = customer(business, "+56911113333");
        BusinessOperation operation = operation(business, customer, BusinessOperation.Type.REQUEST);
        CustomerIdentity identity = identities.verifyPhone(
                business.getId(), customer.getId(), customer.getPhone(),
                CustomerIdentity.VerificationStatus.CUSTOMER_VERIFIED, "TEST");

        CatalogItem product = new CatalogItem();
        product.setBusinessId(business.getId());
        product.setKind(CatalogItem.Kind.PRODUCT);
        product.setName("Shampoo hidratante");
        product.setDescription("Para cabello seco");
        product.setPrice(new BigDecimal("12990"));
        product.setCurrency("CLP");
        product.setActive(true);
        product = catalog.saveAndFlush(product);

        CatalogMedia media = new CatalogMedia();
        media.setBusinessId(business.getId());
        media.setCatalogItemId(product.getId());
        media.setMediaType(CatalogMedia.Type.IMAGE);
        media.setMediaUrl("https://cdn.example.test/shampoo.jpg");
        media.setMimeType("image/jpeg");
        media.setCaption("Mira esta opción");
        media.setSortOrder(0);
        media.setActive(true);
        media = catalogMedia.saveAndFlush(media);

        OutboundMessage first = outbound.prepareCatalogMedia(
                business.getId(),
                customer.getId(),
                operation.getId(),
                identity.getId(),
                media.getId());
        OutboundMessage second = outbound.prepareCatalogMedia(
                business.getId(),
                customer.getId(),
                operation.getId(),
                identity.getId(),
                media.getId());

        assertEquals(first.getId(), second.getId());
        assertEquals(OutboundMessage.Purpose.PRODUCT_SHOWCASE, first.getPurpose());
        assertEquals(OutboundMessage.ContentType.IMAGE, first.getContentType());
        assertEquals(product.getId(), first.getCatalogItemId());
        assertEquals("https://cdn.example.test/shampoo.jpg", first.getMediaUrl());
        assertEquals("image/jpeg", first.getMediaMimeType());
        assertTrue(first.getMediaCaption().contains("Mira esta opción"));
        assertTrue(first.getMediaCaption().contains("CLP 12990"));
        assertEquals(OutboundMessage.Status.PREPARED, first.getStatus());
    }

    @Test
    void multipleVerifiedRecipientsRequireExplicitIdentity() {
        Business business = business("Multiple phones");
        Customer customer = customer(business, "+56910000001");
        BusinessOperation quote = operation(business, customer, BusinessOperation.Type.QUOTE);
        quote.setTotal(new BigDecimal("7000"));
        quote = operations.saveAndFlush(quote);
        CustomerIdentity first = identities.verifyPhone(
                business.getId(), customer.getId(), "+56910000001",
                CustomerIdentity.VerificationStatus.CUSTOMER_VERIFIED, "TEST");
        identities.verifyPhone(
                business.getId(), customer.getId(), "+56910000002",
                CustomerIdentity.VerificationStatus.CUSTOMER_VERIFIED, "TEST");

        BusinessOperation finalQuote = quote;
        assertThrows(IllegalStateException.class, () -> outbound.prepare(
                business.getId(), customer.getId(), OutboundMessage.Channel.WHATSAPP,
                OutboundMessage.Purpose.QUOTE, finalQuote.getId(), null));

        OutboundMessage prepared = outbound.prepare(
                business.getId(), customer.getId(), OutboundMessage.Channel.WHATSAPP,
                OutboundMessage.Purpose.QUOTE, finalQuote.getId(), first.getId());
        assertEquals(first.getNormalizedValue(), prepared.getRecipientAddress());
    }

    @Test
    void unsafeBackendUrlIsRejectedBeforeMessagePersistence() {
        Business business = business("Unsafe url");
        Customer customer = customer(business, "+56922223333");
        BusinessOperation target = operation(business, customer, BusinessOperation.Type.ORDER);
        BusinessOperation paymentOperation = operation(business, customer, BusinessOperation.Type.PAYMENT);
        payment(paymentOperation, target, customer, "http://not-secure.example.test/pay");
        CustomerIdentity identity = identities.verifyPhone(
                business.getId(), customer.getId(), customer.getPhone(),
                CustomerIdentity.VerificationStatus.CUSTOMER_VERIFIED, "TEST");

        assertThrows(IllegalStateException.class, () -> outbound.prepare(
                business.getId(), customer.getId(), OutboundMessage.Channel.WHATSAPP,
                OutboundMessage.Purpose.PAYMENT_LINK, paymentOperation.getId(), identity.getId()));
        assertEquals(0L, messages.count());
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

    private BusinessPayment payment(BusinessOperation operation,
                                    BusinessOperation targetOperation,
                                    Customer customer,
                                    String checkoutUrl) {
        BusinessPayment p = new BusinessPayment();
        p.setOperationId(operation.getId());
        p.setBusinessId(operation.getBusinessId());
        p.setCustomerId(customer.getId());
        p.setTargetOperationId(targetOperation.getId());
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

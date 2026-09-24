package cl.helvoca.operations;

import cl.helvoca.agent.AiAgent;
import cl.helvoca.agent.AiAgentRepository;
import cl.helvoca.agent.AiCapability;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.catalog.CatalogItem;
import cl.helvoca.catalog.CatalogItemRepository;
import cl.helvoca.customer.Customer;
import cl.helvoca.customer.CustomerRepository;
import org.json.JSONObject;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest
@Transactional
class ShowcaseProductSelectionPersistenceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

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
    @Autowired CatalogItemRepository catalog;
    @Autowired BusinessOperationRepository operations;
    @Autowired AiAgentRepository agents;
    @Autowired CommercialOperationToolService commercial;

    @Test
    void whatsappSecondChoicePersistsOnTheExactShowcaseOperationAndIsIdempotent() {
        Business business = new Business();
        business.setName("Showcase E2E tenant");
        business = businesses.saveAndFlush(business);

        Customer customer = new Customer();
        customer.setBusinessId(business.getId());
        customer.setName("Cliente WhatsApp");
        customer.setPhone("+56911112222");
        customer = customers.saveAndFlush(customer);

        AiAgent agent = new AiAgent();
        agent.setBusinessId(business.getId());
        agent.setName("RecepVoz");
        agent.setLanguage("es");
        agent.setGreeting("Hola");
        agent.setActive(true);
        agent.setCapabilities(Set.of(
                AiCapability.LIST_CATALOG,
                AiCapability.CREATE_QUOTE,
                AiCapability.QUOTE_ORDER,
                AiCapability.CREATE_ORDER,
                AiCapability.QUOTE_PAYMENT));
        agents.saveAndFlush(agent);

        CatalogItem first = product(business.getId(), "Producto A", "9900");
        CatalogItem second = product(business.getId(), "Producto B", "12990");
        CatalogItem third = product(business.getId(), "Producto C", "15990");

        BusinessOperation operation = new BusinessOperation();
        operation.setBusinessId(business.getId());
        operation.setCustomerId(customer.getId());
        operation.setType(BusinessOperation.Type.REQUEST);
        operation.setStatus(BusinessOperation.Status.CONFIRMED);
        operation.setSource(BusinessOrder.Source.WHATSAPP);
        operation.setCurrency("CLP");

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("handoffChannel", "WHATSAPP");
        metadata.put("commercialStage", "MEDIA_QUEUED");
        metadata.put("showcaseCatalogItemIds", List.of(
                first.getId().toString(),
                second.getId().toString(),
                third.getId().toString()));
        metadata.put("showcaseMessageCount", 3);
        metadata.put("lastAction", "PRODUCT_SHOWCASE_QUEUED");
        metadata.put("contextMarker", "preserve-me");
        operation.setMetadata(metadata);
        operation = operations.saveAndFlush(operation);

        long operationCountBefore = operations.count();
        UUID operationId = operation.getId();
        UUID sourceReferenceId = UUID.randomUUID();

        JSONObject firstResult = new JSONObject(commercial.execute(
                business.getId(),
                customer.getId(),
                sourceReferenceId,
                customer.getPhone(),
                BusinessOrder.Source.WHATSAPP,
                CommercialOperationToolService.SHOWCASE_SELECTION_TOOL,
                new JSONObject()
                        .put("operationId", operationId.toString())
                        .put("selectionIndex", 2)
                        .toString()));

        assertTrue(firstResult.getBoolean("success"), firstResult::toString);
        JSONObject selected = firstResult.getJSONObject("data");
        assertEquals(operationId.toString(), selected.getString("operationId"));
        assertEquals(2, selected.getInt("selectionIndex"));
        assertEquals(second.getId().toString(), selected.getString("selectedCatalogItemId"));
        assertEquals("Producto B", selected.getJSONObject("product").getString("name"));
        assertEquals("PRODUCT_SELECTED", selected.getString("commercialStage"));
        assertFalse(selected.getBoolean("idempotent"));

        BusinessOperation persisted = operations.findByIdAndBusinessId(
                operationId, business.getId()).orElseThrow();
        assertEquals(operationId, persisted.getId());
        assertEquals(second.getId().toString(),
                persisted.getMetadata().get("selectedCatalogItemId"));
        assertEquals("PRODUCT_SELECTED",
                persisted.getMetadata().get("commercialStage"));
        assertEquals("PRODUCT_SELECTED",
                persisted.getMetadata().get("lastAction"));
        assertEquals("preserve-me",
                persisted.getMetadata().get("contextMarker"));
        assertEquals(List.of(
                        first.getId().toString(),
                        second.getId().toString(),
                        third.getId().toString()),
                persisted.getMetadata().get("showcaseCatalogItemIds"));
        assertEquals(operationCountBefore, operations.count(),
                "Selecting a shown product must never create another BusinessOperation");

        JSONObject quote = new JSONObject(commercial.execute(
                business.getId(),
                customer.getId(),
                sourceReferenceId,
                customer.getPhone(),
                BusinessOrder.Source.WHATSAPP,
                CommercialOperationToolService.SHOWCASE_QUOTE_TOOL,
                new JSONObject()
                        .put("operationId", operationId.toString())
                        .put("quantity", 2)
                        .toString()));

        assertTrue(quote.getBoolean("success"), quote::toString);
        JSONObject quoted = quote.getJSONObject("data");
        assertEquals(operationId.toString(), quoted.getString("operationId"));
        assertEquals(second.getId().toString(), quoted.getString("selectedCatalogItemId"));
        assertEquals(2, quoted.getInt("quantity"));
        assertEquals(0, new BigDecimal(String.valueOf(quoted.get("unitPrice")))
                .compareTo(new BigDecimal("12990")));
        assertEquals(0, new BigDecimal(String.valueOf(quoted.get("total")))
                .compareTo(new BigDecimal("25980")));
        assertEquals("CLP", quoted.getString("currency"));
        assertEquals("QUOTE_PENDING", quoted.getString("commercialStage"));
        assertFalse(quoted.getBoolean("idempotent"));

        BusinessOperation quotedOperation = operations.findByIdAndBusinessId(
                operationId, business.getId()).orElseThrow();
        assertEquals(operationId, quotedOperation.getId());
        assertEquals(0, quotedOperation.getTotal().compareTo(new BigDecimal("25980")));
        assertEquals(0, quotedOperation.getSubtotal().compareTo(new BigDecimal("25980")));
        assertEquals(0, quotedOperation.getDeliveryFee().compareTo(BigDecimal.ZERO));
        assertEquals("CLP", quotedOperation.getCurrency());
        assertEquals("QUOTE_PENDING", quotedOperation.getMetadata().get("commercialStage"));
        assertEquals("SELECTED_PRODUCT_QUOTED", quotedOperation.getMetadata().get("lastAction"));
        assertEquals(second.getId().toString(),
                quotedOperation.getMetadata().get("quotedCatalogItemId"));
        assertEquals("preserve-me", quotedOperation.getMetadata().get("contextMarker"));
        assertEquals(operationCountBefore, operations.count(),
                "Quoting the selected product must preserve the same BusinessOperation");

        int revisionAfterQuote = quotedOperation.getRevision();
        JSONObject quoteReplay = new JSONObject(commercial.execute(
                business.getId(),
                customer.getId(),
                sourceReferenceId,
                customer.getPhone(),
                BusinessOrder.Source.WHATSAPP,
                CommercialOperationToolService.SHOWCASE_QUOTE_TOOL,
                new JSONObject()
                        .put("operationId", operationId.toString())
                        .put("quantity", 2)
                        .toString()));

        assertTrue(quoteReplay.getBoolean("success"), quoteReplay::toString);
        assertTrue(quoteReplay.getJSONObject("data").getBoolean("idempotent"));
        BusinessOperation afterQuoteReplay = operations.findByIdAndBusinessId(
                operationId, business.getId()).orElseThrow();
        assertEquals(revisionAfterQuote, afterQuoteReplay.getRevision());
        assertEquals(operationCountBefore, operations.count());

        JSONObject orderQuote = new JSONObject(commercial.execute(
                business.getId(),
                customer.getId(),
                sourceReferenceId,
                customer.getPhone(),
                BusinessOrder.Source.WHATSAPP,
                CommercialOperationToolService.SHOWCASE_ORDER_TOOL,
                new JSONObject()
                        .put("operationId", operationId.toString())
                        .put("fulfillmentType", "PICKUP")
                        .toString()));

        assertTrue(orderQuote.getBoolean("success"), orderQuote::toString);
        JSONObject orderQuoted = orderQuote.getJSONObject("data");
        UUID orderOperationId = UUID.fromString(orderQuoted.getString("orderOperationId"));
        assertEquals(operationId.toString(),
                orderQuoted.getString("commercialJourneyOperationId"));
        assertEquals(0, new BigDecimal(String.valueOf(orderQuoted.get("total")))
                .compareTo(new BigDecimal("25980")));

        BusinessOperation rootAfterOrderQuote = operations.findByIdAndBusinessId(
                operationId, business.getId()).orElseThrow();
        assertEquals(orderOperationId.toString(),
                rootAfterOrderQuote.getMetadata().get("orderOperationId"));
        assertEquals("PURCHASE_PENDING",
                rootAfterOrderQuote.getMetadata().get("commercialStage"));

        JSONObject confirmedOrder = new JSONObject(commercial.execute(
                business.getId(),
                customer.getId(),
                sourceReferenceId,
                customer.getPhone(),
                BusinessOrder.Source.WHATSAPP,
                "create_order",
                new JSONObject()
                        .put("operationId", orderOperationId.toString())
                        .put("confirmationToken", orderQuoted.getString("confirmationToken"))
                        .toString()));

        assertTrue(confirmedOrder.getBoolean("success"), confirmedOrder::toString);
        assertNotNull(confirmedOrder.getJSONObject("data").getString("orderId"));

        BusinessOperation rootAfterOrder = operations.findByIdAndBusinessId(
                operationId, business.getId()).orElseThrow();
        assertEquals("ORDER_CONFIRMED",
                rootAfterOrder.getMetadata().get("commercialStage"));
        assertEquals(orderOperationId.toString(),
                rootAfterOrder.getMetadata().get("orderOperationId"));

        JSONObject paymentQuote = new JSONObject(commercial.execute(
                business.getId(),
                customer.getId(),
                sourceReferenceId,
                customer.getPhone(),
                BusinessOrder.Source.WHATSAPP,
                "quote_payment",
                new JSONObject()
                        .put("targetOperationId", orderOperationId.toString())
                        .toString()));

        assertTrue(paymentQuote.getBoolean("success"), paymentQuote::toString);
        JSONObject paymentQuoted = paymentQuote.getJSONObject("data");
        UUID paymentOperationId = UUID.fromString(paymentQuoted.getString("operationId"));
        assertEquals(orderOperationId.toString(),
                paymentQuoted.getString("targetOperationId"));
        assertEquals(0, new BigDecimal(String.valueOf(paymentQuoted.get("amount")))
                .compareTo(new BigDecimal("25980")));

        BusinessOperation paymentOperation = operations.findByIdAndBusinessId(
                paymentOperationId, business.getId()).orElseThrow();
        assertEquals(operationId.toString(),
                paymentOperation.getMetadata().get("commercialJourneyOperationId"));

        BusinessOperation rootAfterPaymentQuote = operations.findByIdAndBusinessId(
                operationId, business.getId()).orElseThrow();
        assertEquals(paymentOperationId.toString(),
                rootAfterPaymentQuote.getMetadata().get("paymentOperationId"));
        assertEquals("PAYMENT_PENDING",
                rootAfterPaymentQuote.getMetadata().get("commercialStage"));
        assertEquals(operationCountBefore + 2, operations.count(),
                "The journey may add one ORDER and one PAYMENT operation, but no duplicate commercial roots");

        JSONObject replay = new JSONObject(commercial.execute(
                business.getId(),
                customer.getId(),
                sourceReferenceId,
                customer.getPhone(),
                BusinessOrder.Source.WHATSAPP,
                CommercialOperationToolService.SHOWCASE_SELECTION_TOOL,
                new JSONObject()
                        .put("operationId", operationId.toString())
                        .put("selectionIndex", 2)
                        .toString()));

        assertTrue(replay.getBoolean("success"), replay::toString);
        assertTrue(replay.getJSONObject("data").getBoolean("idempotent"));
        assertEquals(operationId.toString(),
                replay.getJSONObject("data").getString("operationId"));
        assertEquals(second.getId().toString(),
                replay.getJSONObject("data").getString("selectedCatalogItemId"));
        assertEquals("PAYMENT_PENDING",
                replay.getJSONObject("data").getString("commercialStage"),
                "Replaying the selection must not regress a later commercial stage");
        assertEquals(operationCountBefore + 2, operations.count());
    }

    private CatalogItem product(UUID businessId, String name, String price) {
        CatalogItem item = new CatalogItem();
        item.setBusinessId(businessId);
        item.setKind(CatalogItem.Kind.PRODUCT);
        item.setName(name);
        item.setPrice(new BigDecimal(price));
        item.setCurrency("CLP");
        item.setActive(true);
        return catalog.saveAndFlush(item);
    }
}

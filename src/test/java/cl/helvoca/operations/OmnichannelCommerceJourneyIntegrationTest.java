package cl.helvoca.operations;

import cl.helvoca.agent.AiAgent;
import cl.helvoca.agent.AiAgentRepository;
import cl.helvoca.agent.AiCapability;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.call.CallDirection;
import cl.helvoca.call.CallSession;
import cl.helvoca.call.CallSessionRepository;
import cl.helvoca.call.CallStatus;
import cl.helvoca.catalog.CatalogItem;
import cl.helvoca.catalog.CatalogItemRepository;
import cl.helvoca.catalog.CatalogMedia;
import cl.helvoca.catalog.CatalogMediaRepository;
import cl.helvoca.customer.Customer;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.messaging.MessagingConversation;
import cl.helvoca.messaging.MessagingConversationRepository;
import cl.helvoca.messaging.outbound.OutboundMessage;
import cl.helvoca.messaging.outbound.OutboundMessageRepository;
import cl.helvoca.inventory.InventoryReservation;
import cl.helvoca.inventory.InventoryReservationRepository;
import cl.helvoca.inventory.InventoryStock;
import cl.helvoca.inventory.InventoryStockRepository;
import cl.helvoca.omnichannel.CustomerIdentity;
import cl.helvoca.omnichannel.CustomerIdentityService;
import cl.helvoca.payment.BusinessPayment;
import cl.helvoca.payment.BusinessPaymentRepository;
import cl.helvoca.payment.PaymentProviderAdapter;
import cl.helvoca.payment.PaymentWebhookService;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest
@Transactional
@Import(OmnichannelCommerceJourneyIntegrationTest.FakePaymentConfiguration.class)
class OmnichannelCommerceJourneyIntegrationTest {

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
        registry.add("app.outbound.provider", () -> "NONE");
    }

    @Autowired BusinessRepository businesses;
    @Autowired CustomerRepository customers;
    @Autowired AiAgentRepository agents;
    @Autowired CatalogItemRepository catalog;
    @Autowired CatalogMediaRepository media;
    @Autowired CallSessionRepository calls;
    @Autowired MessagingConversationRepository conversations;
    @Autowired CustomerIdentityService identities;
    @Autowired BusinessOperationRepository operations;
    @Autowired ConversationStateService conversationState;
    @Autowired CommercialOperationToolService commercial;
    @Autowired BusinessPaymentRepository payments;
    @Autowired PaymentWebhookService paymentWebhooks;
    @Autowired OutboundMessageRepository outboundMessages;
    @Autowired InventoryStockRepository inventoryStocks;
    @Autowired InventoryReservationRepository inventoryReservations;

    @Test
    void voiceShowcaseContinuesOnWhatsappThroughVerifiedPaymentAndSharedContext() {
        Business business = new Business();
        business.setName("Omnichannel commerce E2E");
        business = businesses.saveAndFlush(business);
        UUID tenantId = business.getId();

        Customer customer = new Customer();
        customer.setBusinessId(business.getId());
        customer.setName("Cliente E2E");
        customer.setPhone("+56911112222");
        customer = customers.saveAndFlush(customer);

        identities.verifyPhone(
                business.getId(),
                customer.getId(),
                customer.getPhone(),
                CustomerIdentity.VerificationStatus.CUSTOMER_VERIFIED,
                "OMNICHANNEL_E2E");

        AiAgent agent = new AiAgent();
        agent.setBusinessId(business.getId());
        agent.setName("RecepVoz");
        agent.setLanguage("es");
        agent.setGreeting("Hola");
        agent.setActive(true);
        agent.setCapabilities(Set.of(
                AiCapability.SEND_WHATSAPP_OPERATION,
                AiCapability.LIST_CATALOG,
                AiCapability.CREATE_QUOTE,
                AiCapability.QUOTE_ORDER,
                AiCapability.CREATE_ORDER,
                AiCapability.QUOTE_PAYMENT,
                AiCapability.CREATE_PAYMENT));
        agents.saveAndFlush(agent);

        CatalogItem product = new CatalogItem();
        product.setBusinessId(business.getId());
        product.setKind(CatalogItem.Kind.PRODUCT);
        product.setName("Producto E2E");
        product.setDescription("Producto para certificar continuidad omnicanal");
        product.setPrice(new BigDecimal("12990"));
        product.setCurrency("CLP");
        product.setActive(true);
        product = catalog.saveAndFlush(product);

        InventoryStock stock = new InventoryStock();
        stock.setBusinessId(business.getId());
        stock.setCatalogItemId(product.getId());
        stock.setSku("OMNI-E2E-001");
        stock.setTrackingEnabled(true);
        stock.setOnHand(2);
        stock.setReserved(0);
        stock.setReorderThreshold(0);
        inventoryStocks.saveAndFlush(stock);

        CatalogMedia image = new CatalogMedia();
        image.setBusinessId(business.getId());
        image.setCatalogItemId(product.getId());
        image.setMediaType(CatalogMedia.Type.IMAGE);
        image.setMediaUrl("https://cdn.example.test/omnichannel-product.jpg");
        image.setMimeType("image/jpeg");
        image.setCaption("Producto destacado");
        image.setSortOrder(0);
        image.setActive(true);
        media.saveAndFlush(image);

        CallSession call = new CallSession();
        call.setBusinessId(business.getId());
        call.setCustomerId(customer.getId());
        call.setTelephonyProvider("TEST");
        call.setProviderCallId("call-" + UUID.randomUUID());
        call.setCallerNumber(customer.getPhone());
        call.setDestinationNumber("+56999990000");
        call.setDirection(CallDirection.INBOUND);
        call.setStatus(CallStatus.IN_PROGRESS);
        call.setStartedAt(Instant.now());
        call = calls.saveAndFlush(call);

        MessagingConversation whatsapp = new MessagingConversation();
        whatsapp.setBusinessId(business.getId());
        whatsapp.setCustomerId(customer.getId());
        whatsapp.setChannel("whatsapp");
        whatsapp.setSender(customer.getPhone());
        whatsapp.setRecipient("+56999990000");
        whatsapp = conversations.saveAndFlush(whatsapp);

        BusinessOperation journey = new BusinessOperation();
        journey.setBusinessId(business.getId());
        journey.setCustomerId(customer.getId());
        journey.setSourceReferenceId(call.getId());
        journey.setType(BusinessOperation.Type.REQUEST);
        journey.setStatus(BusinessOperation.Status.CONFIRMED);
        journey.setSource(BusinessOrder.Source.VOICE);
        journey.setCurrency("CLP");
        journey.setMetadata(new java.util.LinkedHashMap<>(Map.of(
                "commercialStage", "STARTED",
                "lastAction", "VOICE_PRODUCT_REQUEST")));
        journey = operations.saveAndFlush(journey);

        conversationState.apply(
                business.getId(),
                call.getId(),
                BusinessOrder.Source.VOICE,
                journey.getId(),
                Map.of(
                        "intent", "PRODUCT_SHOWCASE",
                        "operationId", journey.getId().toString(),
                        "channelHandoffPending", true));

        JSONObject showcase = new JSONObject(commercial.execute(
                business.getId(),
                customer.getId(),
                call.getId(),
                customer.getPhone(),
                BusinessOrder.Source.VOICE,
                CrossChannelMessagingToolService.TOOL_NAME,
                new JSONObject()
                        .put("operationId", journey.getId().toString())
                        .put("purpose", "PRODUCT_SHOWCASE")
                        .put("catalogItemIds", new org.json.JSONArray().put(product.getId().toString()))
                        .toString()));

        assertFalse(showcase.getBoolean("success"),
                "Real delivery is disabled in certification and must remain disabled");
        assertEquals("WHATSAPP_DELIVERY_DISABLED",
                showcase.getJSONObject("error").getString("code"));
        assertTrue(showcase.getJSONObject("data").getBoolean("prepared"));
        assertFalse(showcase.getJSONObject("data").getBoolean("sent"));

        List<OutboundMessage> afterShowcase =
                outboundMessages.findTop100ByBusinessIdOrderByCreatedAtDesc(business.getId());
        assertEquals(1, afterShowcase.stream()
                .filter(message -> message.getPurpose() == OutboundMessage.Purpose.PRODUCT_SHOWCASE)
                .count());

        ConversationOperationState shared = conversationState.find(
                business.getId(),
                whatsapp.getId(),
                BusinessOrder.Source.WHATSAPP);
        assertNotNull(shared);
        assertEquals(journey.getId(), shared.getActiveOperationId(),
                "Voice and WhatsApp must resolve the same active commercial operation");

        JSONObject selection = success(commercial.execute(
                business.getId(),
                customer.getId(),
                whatsapp.getId(),
                customer.getPhone(),
                BusinessOrder.Source.WHATSAPP,
                CommercialOperationToolService.SHOWCASE_SELECTION_TOOL,
                new JSONObject()
                        .put("operationId", journey.getId().toString())
                        .put("selectionIndex", 1)
                        .toString()));
        assertEquals(product.getId().toString(),
                selection.getString("selectedCatalogItemId"));

        JSONObject quote = success(commercial.execute(
                business.getId(),
                customer.getId(),
                whatsapp.getId(),
                customer.getPhone(),
                BusinessOrder.Source.WHATSAPP,
                CommercialOperationToolService.SHOWCASE_QUOTE_TOOL,
                new JSONObject()
                        .put("operationId", journey.getId().toString())
                        .put("quantity", 1)
                        .toString()));
        assertEquals(0, new BigDecimal(String.valueOf(quote.get("total")))
                .compareTo(new BigDecimal("12990")));

        JSONObject orderQuote = success(commercial.execute(
                business.getId(),
                customer.getId(),
                whatsapp.getId(),
                customer.getPhone(),
                BusinessOrder.Source.WHATSAPP,
                CommercialOperationToolService.SHOWCASE_ORDER_TOOL,
                new JSONObject()
                        .put("operationId", journey.getId().toString())
                        .put("quantity", 1)
                        .put("fulfillmentType", "PICKUP")
                        .toString()));

        UUID orderOperationId = UUID.fromString(orderQuote.getString("orderOperationId"));
        String orderConfirmationToken = orderQuote.getString("confirmationToken");

        JSONObject confirmedOrder = success(commercial.execute(
                business.getId(),
                customer.getId(),
                whatsapp.getId(),
                customer.getPhone(),
                BusinessOrder.Source.WHATSAPP,
                "create_order",
                new JSONObject()
                        .put("operationId", orderOperationId.toString())
                        .put("confirmationToken", orderConfirmationToken)
                        .toString()));
        assertNotNull(confirmedOrder.getString("orderId"));

        InventoryStock reservedStock = inventoryStocks
                .findByBusinessIdAndCatalogItemId(business.getId(), product.getId())
                .orElseThrow();
        assertEquals(2, reservedStock.getOnHand());
        assertEquals(1, reservedStock.getReserved());
        assertEquals(1, reservedStock.available());

        List<InventoryReservation> activeReservations = inventoryReservations.findAll().stream()
                .filter(reservation -> tenantId.equals(reservation.getBusinessId()))
                .filter(reservation -> orderOperationId.equals(reservation.getReferenceId()))
                .filter(reservation -> reservation.getStatus() == InventoryReservation.Status.ACTIVE)
                .toList();
        assertEquals(1, activeReservations.size(),
                "Order confirmation must create exactly one active stock reservation");

        JSONObject paymentQuote = success(commercial.execute(
                business.getId(),
                customer.getId(),
                whatsapp.getId(),
                customer.getPhone(),
                BusinessOrder.Source.WHATSAPP,
                "quote_payment",
                new JSONObject()
                        .put("targetOperationId", orderOperationId.toString())
                        .toString()));

        UUID paymentOperationId = UUID.fromString(paymentQuote.getString("operationId"));

        JSONObject paymentCreated = success(commercial.execute(
                business.getId(),
                customer.getId(),
                whatsapp.getId(),
                customer.getPhone(),
                BusinessOrder.Source.WHATSAPP,
                "create_payment",
                new JSONObject()
                        .put("operationId", paymentOperationId.toString())
                        .put("confirmationToken", paymentQuote.getString("confirmationToken"))
                        .toString()));

        assertEquals("REQUIRES_ACTION", paymentCreated.getString("status"));
        assertTrue(paymentCreated.getString("checkoutUrl").startsWith("https://sandbox.example.test/"));

        BusinessPayment payment = payments
                .findByOperationIdAndBusinessId(paymentOperationId, business.getId())
                .orElseThrow();

        assertEquals(PaymentWebhookService.Result.PROCESSED,
                paymentWebhooks.processVerified(
                        business.getId(),
                        "sandbox-e2e",
                        "evt-" + UUID.randomUUID(),
                        payment.getExternalId(),
                        paymentOperationId.toString(),
                        "{\"status\":\"approved\"}"));

        BusinessPayment paid = payments
                .findByOperationIdAndBusinessId(paymentOperationId, business.getId())
                .orElseThrow();
        assertEquals(BusinessPayment.Status.SUCCEEDED, paid.getStatus());

        InventoryStock consumedStock = inventoryStocks
                .findByBusinessIdAndCatalogItemId(business.getId(), product.getId())
                .orElseThrow();
        assertEquals(1, consumedStock.getOnHand());
        assertEquals(0, consumedStock.getReserved());
        assertEquals(1, consumedStock.available());

        List<InventoryReservation> consumedReservations = inventoryReservations.findAll().stream()
                .filter(reservation -> tenantId.equals(reservation.getBusinessId()))
                .filter(reservation -> orderOperationId.equals(reservation.getReferenceId()))
                .filter(reservation -> reservation.getStatus() == InventoryReservation.Status.CONSUMED)
                .toList();
        assertEquals(1, consumedReservations.size(),
                "Verified successful payment must consume the exact order reservation once");

        BusinessOperation paidJourney = operations
                .findByIdAndBusinessId(journey.getId(), business.getId())
                .orElseThrow();
        assertEquals("PAID", paidJourney.getMetadata().get("commercialStage"));
        assertEquals("SUCCEEDED", paidJourney.getMetadata().get("paymentStatus"));

        List<OutboundMessage> finalMessages =
                outboundMessages.findTop100ByBusinessIdOrderByCreatedAtDesc(business.getId());
        List<OutboundMessage> confirmations = finalMessages.stream()
                .filter(message -> message.getPurpose() == OutboundMessage.Purpose.PAYMENT_CONFIRMATION)
                .toList();
        assertEquals(1, confirmations.size());
        assertEquals(OutboundMessage.Status.PREPARED, confirmations.get(0).getStatus());
        assertTrue(confirmations.get(0).getContentText().contains("Pago confirmado"));
        assertFalse(confirmations.get(0).getContentText().contains("sandbox.example.test"));

        ConversationOperationState finalShared = conversationState.find(
                business.getId(),
                call.getId(),
                BusinessOrder.Source.VOICE);
        assertNotNull(finalShared);
        assertEquals(paymentOperationId, finalShared.getActiveOperationId());
        assertEquals("SUCCEEDED", String.valueOf(finalShared.getState().get("paymentStatus")));

        assertEquals(2, finalMessages.size(),
                "Certification must create one product showcase and one payment confirmation only");
        assertTrue(finalMessages.stream().noneMatch(message ->
                message.getStatus() == OutboundMessage.Status.SENT
                        || message.getStatus() == OutboundMessage.Status.QUEUED),
                "Certification must never queue or send real WhatsApp traffic");
    }

    private static JSONObject success(String raw) {
        JSONObject result = new JSONObject(raw);
        assertTrue(result.optBoolean("success", false), result::toString);
        JSONObject data = result.optJSONObject("data");
        assertNotNull(data, result::toString);
        return data;
    }

    @TestConfiguration
    static class FakePaymentConfiguration {
        @Bean
        PaymentProviderAdapter omnichannelE2ePaymentProvider() {
            return new PaymentProviderAdapter() {
                @Override
                public String providerCode() {
                    return "sandbox-e2e";
                }

                @Override
                public boolean supports(UUID businessId) {
                    return businessId != null;
                }

                @Override
                public CreateResult create(CreateCommand command) {
                    return new CreateResult(
                            "sandbox-" + command.paymentOperationId(),
                            "https://sandbox.example.test/pay/" + command.paymentOperationId(),
                            BusinessPayment.Status.REQUIRES_ACTION,
                            Map.of("sandbox", true));
                }

                @Override
                public StatusResult getStatus(StatusCommand command) {
                    return new StatusResult(
                            BusinessPayment.Status.SUCCEEDED,
                            Map.of("sandbox", true, "verified", true));
                }

                @Override
                public CancelResult cancel(CancelCommand command) {
                    return new CancelResult(
                            BusinessPayment.Status.CANCELLED,
                            Map.of("sandbox", true));
                }
            };
        }
    }
}

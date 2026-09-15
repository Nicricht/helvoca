package cl.helvoca.operations;

import cl.helvoca.catalog.CatalogItem;
import cl.helvoca.catalog.CatalogItemRepository;
import cl.helvoca.request.BusinessRequest;
import cl.helvoca.request.BusinessRequestRepository;
import cl.helvoca.request.RequestPriority;
import cl.helvoca.request.RequestSource;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UniversalOperationWorkflowServiceTest {
    @Mock CatalogItemRepository catalog;
    @Mock BusinessOperationRepository operations;
    @Mock BusinessOperationItemRepository operationItems;
    @Mock BusinessQuoteRepository quotes;
    @Mock BusinessLeadRepository leads;
    @Mock BusinessRequestRepository requests;
    @Mock ConversationStateService conversationState;

    private UniversalOperationWorkflowService service;

    @BeforeEach
    void setUp() {
        service = new UniversalOperationWorkflowService(
                catalog,
                operations,
                operationItems,
                quotes,
                leads,
                requests,
                new OperationPolicyService(),
                conversationState);

        lenient().when(operations.saveAndFlush(any(BusinessOperation.class))).thenAnswer(invocation -> {
            BusinessOperation operation = invocation.getArgument(0);
            if (operation.getId() == null) operation.setId(UUID.randomUUID());
            return operation;
        });
        lenient().when(operationItems.save(any(BusinessOperationItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(quotes.saveAndFlush(any(BusinessQuote.class))).thenAnswer(invocation -> {
            BusinessQuote quote = invocation.getArgument(0);
            if (quote.getId() == null) quote.setId(UUID.randomUUID());
            return quote;
        });
        lenient().when(leads.saveAndFlush(any(BusinessLead.class))).thenAnswer(invocation -> {
            BusinessLead lead = invocation.getArgument(0);
            if (lead.getId() == null) lead.setId(UUID.randomUUID());
            return lead;
        });
        lenient().when(requests.saveAndFlush(any(BusinessRequest.class))).thenAnswer(invocation -> {
            BusinessRequest request = invocation.getArgument(0);
            if (request.getId() == null) ReflectionTestUtils.setField(request, "id", UUID.randomUUID());
            return request;
        });
    }

    @Test
    void quoteUsesTenantCatalogPriceAndCreatesUniversalOperationBeforeProjection() {
        UUID businessId = UUID.randomUUID();
        UUID sourceReferenceId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        CatalogItem item = catalogItem(itemId, businessId, "Instalación", "2500");
        when(catalog.findByIdAndBusinessId(itemId, businessId)).thenReturn(Optional.of(item));

        JSONObject args = new JSONObject()
                .put("title", "Cotizar instalación")
                .put("items", new JSONArray().put(new JSONObject()
                        .put("catalogItemId", itemId.toString())
                        .put("quantity", 2)
                        .put("modifiers", new JSONObject().put("urgency", "normal"))));

        JSONObject result = service.createQuote(
                businessId, null, sourceReferenceId, "+56911111111",
                BusinessOrder.Source.WHATSAPP, args);

        assertTrue(result.getBoolean("success"));
        assertEquals(new BigDecimal("5000"),
                new BigDecimal(String.valueOf(result.getJSONObject("data").get("amount"))));

        ArgumentCaptor<BusinessOperation> operationCaptor = ArgumentCaptor.forClass(BusinessOperation.class);
        verify(operations).saveAndFlush(operationCaptor.capture());
        BusinessOperation operation = operationCaptor.getValue();
        assertEquals(BusinessOperation.Type.QUOTE, operation.getType());
        assertEquals(BusinessOperation.Status.CONFIRMED, operation.getStatus());
        assertEquals(new BigDecimal("5000"), operation.getTotal());
        assertEquals("CLP", operation.getCurrency());
        assertEquals(businessId, operation.getBusinessId());

        ArgumentCaptor<BusinessQuote> quoteCaptor = ArgumentCaptor.forClass(BusinessQuote.class);
        verify(quotes).saveAndFlush(quoteCaptor.capture());
        assertEquals(operation.getId(), quoteCaptor.getValue().getOperationId());
        assertEquals(new BigDecimal("5000"), quoteCaptor.getValue().getAmount());

        ArgumentCaptor<BusinessOperationItem> lineCaptor = ArgumentCaptor.forClass(BusinessOperationItem.class);
        verify(operationItems).save(lineCaptor.capture());
        assertEquals(operation.getId(), lineCaptor.getValue().getOperationId());
        assertEquals(2, lineCaptor.getValue().getQuantity());
        assertEquals(new BigDecimal("2500"), lineCaptor.getValue().getUnitPrice());
        assertEquals("normal", lineCaptor.getValue().getModifiers().get("urgency"));

        verify(catalog).findByIdAndBusinessId(itemId, businessId);
        verify(conversationState).apply(eq(businessId), eq(sourceReferenceId),
                eq(BusinessOrder.Source.WHATSAPP), eq(operation.getId()), anyMap());
    }

    @Test
    void foreignTenantCatalogItemCannotEnterQuoteOperation() {
        UUID businessId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        when(catalog.findByIdAndBusinessId(itemId, businessId)).thenReturn(Optional.empty());

        JSONObject args = new JSONObject()
                .put("title", "Cotización inválida")
                .put("items", new JSONArray().put(new JSONObject()
                        .put("catalogItemId", itemId.toString())
                        .put("quantity", 1)));

        assertThrows(IllegalArgumentException.class, () -> service.createQuote(
                businessId, null, UUID.randomUUID(), "+56911111111",
                BusinessOrder.Source.VOICE, args));

        verifyNoInteractions(operations, quotes, conversationState);
    }

    @Test
    void leadCreatesUniversalOperationAndTypedProjection() {
        UUID businessId = UUID.randomUUID();
        UUID sourceReferenceId = UUID.randomUUID();
        JSONObject result = service.createLead(
                businessId, null, sourceReferenceId, "+56922222222",
                BusinessOrder.Source.VOICE,
                new JSONObject()
                        .put("name", "Ana")
                        .put("interest", "Plan empresa")
                        .put("budget", 150000));

        assertTrue(result.getBoolean("success"));
        ArgumentCaptor<BusinessOperation> operationCaptor = ArgumentCaptor.forClass(BusinessOperation.class);
        verify(operations).saveAndFlush(operationCaptor.capture());
        BusinessOperation operation = operationCaptor.getValue();
        assertEquals(BusinessOperation.Type.LEAD, operation.getType());
        assertEquals(BusinessOperation.Status.CONFIRMED, operation.getStatus());
        assertEquals("Plan empresa", operation.getMetadata().get("interest"));

        ArgumentCaptor<BusinessLead> leadCaptor = ArgumentCaptor.forClass(BusinessLead.class);
        verify(leads).saveAndFlush(leadCaptor.capture());
        assertEquals(operation.getId(), leadCaptor.getValue().getOperationId());
        assertEquals(new BigDecimal("150000"), leadCaptor.getValue().getBudget());
    }

    @Test
    void requestUsesSameEnvelopeAndMapsVoiceSource() {
        UUID businessId = UUID.randomUUID();
        UUID sourceReferenceId = UUID.randomUUID();
        BusinessRequest request = service.createRequest(
                businessId,
                null,
                sourceReferenceId,
                "soporte",
                "Necesito ayuda",
                "No inicia el equipo",
                "Nico",
                "+56933333333",
                RequestPriority.HIGH,
                "{\"device\":\"notebook\"}",
                RequestSource.AI_CALL);

        ArgumentCaptor<BusinessOperation> operationCaptor = ArgumentCaptor.forClass(BusinessOperation.class);
        verify(operations).saveAndFlush(operationCaptor.capture());
        BusinessOperation operation = operationCaptor.getValue();
        assertEquals(BusinessOperation.Type.REQUEST, operation.getType());
        assertEquals(BusinessOrder.Source.VOICE, operation.getSource());
        assertEquals(BusinessOperation.Status.CONFIRMED, operation.getStatus());
        assertEquals(operation.getId(), request.getOperationId());
        assertEquals(RequestSource.AI_CALL, request.getSource());
        verify(conversationState).apply(eq(businessId), eq(sourceReferenceId),
                eq(BusinessOrder.Source.VOICE), eq(operation.getId()), anyMap());
    }

    private static CatalogItem catalogItem(UUID id, UUID businessId, String name, String price) {
        CatalogItem item = new CatalogItem();
        item.setId(id);
        item.setBusinessId(businessId);
        item.setKind(CatalogItem.Kind.SERVICE);
        item.setName(name);
        item.setPrice(new BigDecimal(price));
        item.setCurrency("CLP");
        item.setActive(true);
        return item;
    }
}

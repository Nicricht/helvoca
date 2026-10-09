package cl.helvoca.request;

import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.messaging.MessagingConversation;
import cl.helvoca.messaging.MessagingConversationRepository;
import cl.helvoca.messaging.MessagingMessage;
import cl.helvoca.messaging.MessagingMessageRepository;
import cl.helvoca.operations.BusinessOperation;
import cl.helvoca.operations.HumanAttentionService;
import cl.helvoca.operations.HumanHandoffService;
import cl.helvoca.testsupport.ExplicitSystemDatabaseScopeSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest
class RequestLifecycleAttentionIntegrationTest extends ExplicitSystemDatabaseScopeSupport {
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
    @Autowired BusinessRequestService requests;
    @Autowired RequestLifecycleEventService events;
    @Autowired HumanAttentionService attention;
    @Autowired HumanHandoffService handoffs;
    @Autowired JdbcTemplate jdbc;
    @Autowired MessagingConversationRepository messagingConversations;
    @Autowired MessagingMessageRepository messagingMessages;
    @Autowired RequestReplyCorrelationService replyCorrelations;
    @Autowired RequestReplyDeliveryEvidenceService replyReceipts;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void humanStateChangesAreLockedAuditedAndTerminalStatusesCannotReopen() {
        Business business = business("Request state history");
        authenticate(business.getId());
        var request = requests.create(new BusinessRequestDtos.Create(
                "GENERAL", "Consulta especial", null, null, null, RequestPriority.NORMAL, null));

        assertEquals(1, attention.pending().stream().filter(x ->
                x.kind() == HumanAttentionService.Kind.REQUEST && x.id().equals(request.id())).count());

        requests.setStatus(request.id(), RequestStatus.IN_PROGRESS);
        requests.setStatus(request.id(), RequestStatus.IN_PROGRESS); // no duplicate event
        requests.setStatus(request.id(), RequestStatus.RESOLVED);
        requests.setStatus(request.id(), RequestStatus.RESOLVED); // idempotent
        var history = events.history(request.id());
        assertEquals(2, history.size());
        assertEquals(List.of(RequestStatus.IN_PROGRESS, RequestStatus.RESOLVED),
                history.stream().map(RequestLifecycleEventService.Transition::status).toList());
        assertTrue(history.stream().allMatch(event ->
                event.actorType().equals("BUSINESS_USER") && event.evidenceEventId() == null));

        assertThrows(IllegalArgumentException.class,
                () -> requests.setStatus(request.id(), RequestStatus.OPEN));
        assertTrue(attention.pending().stream().noneMatch(x -> request.id().equals(x.id())));
        assertEquals(2, events.history(request.id()).size());
    }

    @Test
    void activeHandoffReplacesOpenRequestInsteadOfCreatingTwoInboxItems() {
        Business business = business("One attention item");
        authenticate(business.getId());
        var request = requests.create(new BusinessRequestDtos.Create(
                "GENERAL", "Necesita un humano", null, null, null, RequestPriority.HIGH, null));
        UUID operationId = jdbc.queryForObject(
                "SELECT operation_id FROM business_request WHERE id = ? AND business_id = ?",
                UUID.class, request.id(), business.getId());

        var first = handoffs.createForUnresolvable(
                business.getId(), BusinessOperation.Type.REQUEST, null, operationId,
                "create_request", "CUSTOMER_NEEDS_SUPPORT", 0);
        var repeated = handoffs.createForUnresolvable(
                business.getId(), BusinessOperation.Type.REQUEST, null, operationId,
                "create_request", "CUSTOMER_NEEDS_SUPPORT", 0);
        assertEquals(first.handoffId(), repeated.handoffId());
        assertFalse(repeated.created());

        var entries = attention.pending().stream()
                .filter(x -> operationId.equals(x.operationId())).toList();
        assertEquals(1, entries.size());
        assertEquals(HumanAttentionService.Kind.HANDOFF, entries.getFirst().kind());

        handoffs.resolve(first.handoffId(), "tester");
        // A resolved handoff alone is not proof of request resolution.
        var afterHandoff = attention.pending().stream()
                .filter(x -> operationId.equals(x.operationId())).toList();
        assertEquals(1, afterHandoff.size());
        assertEquals(HumanAttentionService.Kind.REQUEST, afterHandoff.getFirst().kind());
    }

    @Test
    void anotherTenantCannotSeeOrChangeRequestOrItsHistory() {
        Business a = business("Scoped request A");
        Business b = business("Scoped request B");
        authenticate(a.getId());
        var request = requests.create(new BusinessRequestDtos.Create(
                "GENERAL", "Solo negocio A", null, null, null, RequestPriority.NORMAL, null));
        requests.setStatus(request.id(), RequestStatus.IN_PROGRESS);
        assertEquals(1, events.history(request.id()).size());

        authenticate(b.getId());
        assertThrows(cl.helvoca.common.NotFoundException.class,
                () -> requests.setStatus(request.id(), RequestStatus.RESOLVED));
        assertTrue(events.history(request.id()).isEmpty());
        assertTrue(attention.pending().stream()
                .noneMatch(item -> item.id().equals(request.id())));
    }


    @Test
    void databaseRejectsDirectSqlReopeningEvenOutsideTheJavaLifecycleService() {
        Business business = business("DB terminal lifecycle");
        authenticate(business.getId());
        var request = requests.create(new BusinessRequestDtos.Create(
                "GENERAL", "Solicitud terminal", null, null, null, RequestPriority.NORMAL, null));
        requests.setStatus(request.id(), RequestStatus.RESOLVED);

        assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.update(
                "UPDATE public.business_request SET status = 'OPEN' WHERE business_id = ? AND id = ?",
                business.getId(), request.id()));
        assertEquals("RESOLVED", jdbc.queryForObject(
                "SELECT status FROM public.business_request WHERE business_id = ? AND id = ?",
                String.class, business.getId(), request.id()));
    }

    @Test
    void databaseRejectsAutomationAuditWithoutSpecificEvidenceEvent() {
        Business business = business("DB automatic evidence gate");
        authenticate(business.getId());
        var request = requests.create(new BusinessRequestDtos.Create(
                "GENERAL", "No synthetic closure", null, null, null, RequestPriority.NORMAL, null));
        UUID operationId = jdbc.queryForObject(
                "SELECT operation_id FROM public.business_request WHERE business_id = ? AND id = ?",
                UUID.class, business.getId(), request.id());

        assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.update("""
                INSERT INTO public.business_request_transition_event(
                    business_id, request_id, operation_id, previous_status, status,
                    actor_type, actor_reference, reason_code, evidence_event_id)
                VALUES (?, ?, ?, 'OPEN', 'RESOLVED',
                    'AUTOMATION', NULL, 'UNVERIFIED_TOOL_SUCCESS', NULL)
                """, business.getId(), request.id(), operationId));
        assertEquals(0, jdbc.queryForObject("""
                SELECT COUNT(*) FROM public.business_request_transition_event
                WHERE business_id = ? AND request_id = ?
                """, Integer.class, business.getId(), request.id()));
    }


    @Test
    @org.springframework.transaction.annotation.Transactional
    void aiCreatedWhatsAppRequestHasIdempotentExactlyScopedReplyCorrelation() {
        Business business = business("Reply correlation tenant");
        authenticate(business.getId());

        MessagingConversation conversation = new MessagingConversation();
        conversation.setBusinessId(business.getId());
        conversation.setChannel("whatsapp");
        conversation.setSender("+56912345678");
        conversation.setRecipient("+56987654321");
        conversation = messagingConversations.saveAndFlush(conversation);

        MessagingMessage inbound = new MessagingMessage();
        inbound.setConversationId(conversation.getId());
        inbound.setExternalMessageId("test-correlation-" + UUID.randomUUID());
        inbound.setDirection("INBOUND");
        inbound.setRole("USER");
        inbound.setContent("Necesito hablar con alguien");
        inbound.setReplyText("Tu solicitud fue registrada para seguimiento.");
        inbound = messagingMessages.saveAndFlush(inbound);

        BusinessRequest request = requests.createFromAi(
                business.getId(), null, conversation.getId(), "GENERAL",
                "Seguimiento requerido", "El cliente requiere atencion", null,
                conversation.getSender(), RequestPriority.NORMAL, null, RequestSource.AI_WHATSAPP);
        var ids = new RequestReplyCorrelationService.CreatedRequest(request.getId(), request.getOperationId());

        replyCorrelations.capture(business.getId(), conversation.getId(), inbound.getId(), List.of(ids, ids));
        replyCorrelations.capture(business.getId(), conversation.getId(), inbound.getId(), List.of(ids));
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM public.business_request_reply_correlation
                 WHERE business_id = ? AND request_id = ? AND operation_id = ?
                       AND conversation_id = ? AND inbound_message_id = ?
                """, Integer.class, business.getId(), request.getId(), request.getOperationId(),
                conversation.getId(), inbound.getId()));

        // Even an ID from another tenant or another conversation is never accepted.
        replyCorrelations.capture(UUID.randomUUID(), conversation.getId(), inbound.getId(), List.of(ids));
        replyCorrelations.capture(business.getId(), UUID.randomUUID(), inbound.getId(), List.of(ids));
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM public.business_request_reply_correlation WHERE request_id = ?",
                Integer.class, request.getId()));
        // A queued or sent reply is not a confirmed customer receipt.
        inbound.setProvider("META_WHATSAPP_CLOUD");
        inbound.setProviderMessageId("wamid." + UUID.randomUUID());
        inbound.setProviderDeliveryStatus("SENT");
        messagingMessages.saveAndFlush(inbound);
        replyReceipts.recordMetaReceipt(business.getId(), inbound.getId(),
                inbound.getProviderMessageId(), "DELIVERED");
        assertEquals(0, receiptCount(business.getId(), request.getId()));

        // The authenticated Meta path persists the provider status first,
        // then appends the correlated immutable receipt in the same transaction.
        inbound.setProviderDeliveryStatus("DELIVERED");
        inbound.setDeliveredAt(java.time.Instant.now());
        messagingMessages.saveAndFlush(inbound);
        replyReceipts.recordMetaReceipt(business.getId(), inbound.getId(),
                inbound.getProviderMessageId(), "DELIVERED");
        replyReceipts.recordMetaReceipt(business.getId(), inbound.getId(),
                inbound.getProviderMessageId(), "DELIVERED");
        replyReceipts.recordMetaReceipt(business.getId(), inbound.getId(),
                "wamid.WRONG", "DELIVERED");
        replyReceipts.recordMetaReceipt(UUID.randomUUID(), inbound.getId(),
                inbound.getProviderMessageId(), "DELIVERED");
        assertEquals(1, receiptCount(business.getId(), request.getId()));

        // A new READ update is a new receipt kind, not resolution.
        inbound.setProviderDeliveryStatus("READ");
        inbound.setReadAt(java.time.Instant.now());
        messagingMessages.saveAndFlush(inbound);
        replyReceipts.recordMetaReceipt(business.getId(), inbound.getId(),
                inbound.getProviderMessageId(), "READ");
        replyReceipts.recordMetaReceipt(business.getId(), inbound.getId(),
                inbound.getProviderMessageId(), "READ");
        assertEquals(2, receiptCount(business.getId(), request.getId()));
        assertEquals("OPEN", jdbc.queryForObject(
                "SELECT status FROM public.business_request WHERE id = ?",
                String.class, request.getId()));
    }

    private int receiptCount(UUID businessId, UUID requestId) {
        return jdbc.queryForObject("""
                SELECT count(*)
                FROM public.business_request_reply_delivery_event receipt
                JOIN public.business_request_reply_correlation corr
                  ON corr.id = receipt.correlation_id AND corr.business_id = receipt.business_id
                WHERE receipt.business_id = ? AND corr.request_id = ?
                """, Integer.class, businessId, requestId);
    }

    private Business business(String name) {
        Business business = new Business();
        business.setName(name);
        return businesses.saveAndFlush(business);
    }

    private static void authenticate(UUID businessId) {
        Jwt jwt = Jwt.withTokenValue("request-test")
                .header("alg", "none")
                .subject(UUID.randomUUID().toString())
                .claim("business_id", businessId.toString())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwt, List.of(new SimpleGrantedAuthority("ROLE_BUSINESS_ADMIN"))));
    }
}

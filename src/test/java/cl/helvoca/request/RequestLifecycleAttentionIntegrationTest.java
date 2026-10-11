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
    @Autowired cl.helvoca.operations.OperationsDashboardService homeDashboard;
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

    @Test
    void realPostgresRejectsForgedCorrelationAndUnsignedProviderReceiptsAtTheDatabaseBoundary() {
        Business business = business("Reply receipt SQL guards");
        authenticate(business.getId());

        MessagingConversation conversation = new MessagingConversation();
        conversation.setBusinessId(business.getId());
        conversation.setChannel("whatsapp");
        conversation.setSender("+56912345678");
        conversation.setRecipient("+56987654321");
        conversation = messagingConversations.saveAndFlush(conversation);

        MessagingMessage inbound = new MessagingMessage();
        inbound.setConversationId(conversation.getId());
        inbound.setExternalMessageId("sql-guard-" + UUID.randomUUID());
        inbound.setDirection("INBOUND");
        inbound.setRole("USER");
        inbound.setContent("Necesito información sobre mi solicitud");
        inbound = messagingMessages.saveAndFlush(inbound);

        BusinessRequest request = requests.createFromAi(
                business.getId(), null, conversation.getId(), "GENERAL",
                "Solicitud verificable", "Requiere respuesta", null,
                conversation.getSender(), RequestPriority.NORMAL, null, RequestSource.AI_WHATSAPP);

        UUID finalConversation = conversation.getId();
        UUID finalMessage = inbound.getId();
        // Missing persisted assistant reply must be rejected by the V96 trigger.
        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> jdbc.update("""
                        INSERT INTO public.business_request_reply_correlation
                            (business_id, request_id, operation_id, conversation_id, inbound_message_id)
                        VALUES (?, ?, ?, ?, ?)
                        """, business.getId(), request.getId(), request.getOperationId(),
                        finalConversation, finalMessage));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.business_request_reply_correlation WHERE request_id = ?",
                Integer.class, request.getId()));

        inbound.setReplyText("Registré tu solicitud para seguimiento.");
        inbound = messagingMessages.saveAndFlush(inbound);

        // Valid trusted evidence may be inserted; foreign tenant references may not.
        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> jdbc.update("""
                        INSERT INTO public.business_request_reply_correlation
                            (business_id, request_id, operation_id, conversation_id, inbound_message_id)
                        VALUES (?, ?, ?, ?, ?)
                        """, UUID.randomUUID(), request.getId(), request.getOperationId(),
                        finalConversation, finalMessage));
        jdbc.update("""
                INSERT INTO public.business_request_reply_correlation
                    (business_id, request_id, operation_id, conversation_id, inbound_message_id)
                VALUES (?, ?, ?, ?, ?)
                """, business.getId(), request.getId(), request.getOperationId(),
                finalConversation, finalMessage);
        UUID correlationId = jdbc.queryForObject("""
                SELECT id FROM public.business_request_reply_correlation
                WHERE business_id = ? AND request_id = ?
                """, UUID.class, business.getId(), request.getId());
        assertNotNull(correlationId);

        // No signed delivery callback has persisted DELIVERED, so V97 must reject
        // a fabricated provider receipt, even if the IDs match real rows.
        UUID finalCorrelation = correlationId;
        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> jdbc.update("""
                        INSERT INTO public.business_request_reply_delivery_event
                            (business_id, correlation_id, inbound_message_id, provider,
                             provider_message_id, receipt_status, provider_recorded_at)
                        VALUES (?, ?, ?, 'META_WHATSAPP_CLOUD',
                                'wamid.forged', 'DELIVERED', CURRENT_TIMESTAMP)
                        """, business.getId(), finalCorrelation, finalMessage));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.business_request_reply_delivery_event WHERE correlation_id = ?",
                Integer.class, finalCorrelation));
        assertEquals("OPEN", jdbc.queryForObject(
                "SELECT status FROM public.business_request WHERE id = ?", String.class, request.getId()));
    }


    @Test
    void homeInboxRealPostgresFiltersTenantStatusesAndKeepsRequestAfterHandoffCloses() {
        Business tenantA = business("Home scoped attention A");
        Business tenantB = business("Home scoped attention B");
        authenticate(tenantA.getId());

        var request = requests.create(new BusinessRequestDtos.Create(
                "GENERAL", "Tenant A needs a person", null, null, null,
                RequestPriority.HIGH, null));
        UUID operationId = jdbc.queryForObject(
                "SELECT operation_id FROM business_request WHERE business_id = ? AND id = ?",
                UUID.class, tenantA.getId(), request.id());
        var active = handoffs.createForUnresolvable(
                tenantA.getId(), BusinessOperation.Type.REQUEST, null, operationId,
                "create_request", "HOME_TEST_SUPPORT", 0);

        authenticate(tenantB.getId());
        var foreign = requests.create(new BusinessRequestDtos.Create(
                "GENERAL", "Tenant B private request", null, null, null,
                RequestPriority.NORMAL, null));
        var foreignActive = handoffs.createForUnresolvable(
                tenantB.getId(), BusinessOperation.Type.REQUEST, null, UUID.randomUUID(),
                "create_request", "HOME_TEST_FOREIGN", 0);

        authenticate(tenantA.getId());
        var tenantAEntries = databaseContext.callAsTenant(tenantA.getId(), attention::pending);
        assertEquals(1, tenantAEntries.stream()
                .filter(item -> operationId.equals(item.operationId())).count(),
                "active handoff and underlying request must never count twice");
        assertTrue(tenantAEntries.stream().anyMatch(item ->
                item.id().equals(active.handoffId())
                && item.kind() == HumanAttentionService.Kind.HANDOFF));
        assertTrue(tenantAEntries.stream().noneMatch(item ->
                item.id().equals(foreign.id()) || item.id().equals(foreignActive.handoffId())));
        // Independent database RLS assertions, not just service WHERE business_id filters.
        assertEquals(0L, databaseContext.callAsTenant(tenantA.getId(), () ->
                jdbc.queryForObject("SELECT count(*) FROM public.human_handoff WHERE id = ?",
                        Long.class, foreignActive.handoffId())));
        assertEquals(0L, databaseContext.callAsTenant(tenantA.getId(), () ->
                jdbc.queryForObject("SELECT count(*) FROM public.business_request WHERE id = ?",
                        Long.class, foreign.id())));
        assertEquals(1L, databaseContext.callAsTenant(tenantB.getId(), () ->
                jdbc.queryForObject("SELECT count(*) FROM public.human_handoff WHERE id = ?",
                        Long.class, foreignActive.handoffId())));

        assertThrows(IllegalArgumentException.class,
                () -> databaseContext.callAsTenant(tenantA.getId(),
                        () -> handoffs.acknowledge(foreignActive.handoffId(), "forged tenant")));
        assertEquals("OPEN", jdbc.queryForObject(
                "SELECT status FROM public.human_handoff WHERE id = ?",
                String.class, foreignActive.handoffId()));

        handoffs.resolve(active.handoffId(), "test operator");
        var afterHandoff = databaseContext.callAsTenant(tenantA.getId(), attention::pending);
        assertEquals(1, afterHandoff.stream()
                .filter(item -> operationId.equals(item.operationId())).count());
        assertTrue(afterHandoff.stream().anyMatch(item ->
                item.id().equals(request.id())
                && item.kind() == HumanAttentionService.Kind.REQUEST));
        requests.setStatus(request.id(), RequestStatus.RESOLVED);
        assertTrue(databaseContext.callAsTenant(tenantA.getId(), attention::pending)
                .stream().noneMatch(item -> operationId.equals(item.operationId())));
        assertTrue(databaseContext.callAsTenant(tenantB.getId(), () -> {
            authenticate(tenantB.getId());
            return attention.pending();
        }).stream().anyMatch(item -> item.id().equals(foreignActive.handoffId())));
    }

    @Test
    void homeInboxRealPostgresOrdersNewestFirstAndNeverReturnsMoreThanHundred() {
        Business tenantA = business("Home attention limit A");
        Business tenantB = business("Home attention limit B");
        authenticate(tenantA.getId());

        java.time.Instant base = java.time.Instant.parse("2026-10-01T00:00:00Z");
        List<UUID> inserted = new java.util.ArrayList<>();
        for (int i = 0; i < 105; i++) {
            UUID id = UUID.randomUUID();
            inserted.add(id);
            jdbc.update("""
                    INSERT INTO human_handoff(
                        id, business_id, operation_id, operation_type,
                        tool_name, reason_code, failure_class, priority, status,
                        safe_summary, created_at)
                    VALUES (?, ?, ?, 'REQUEST', 'home_qa', 'HOME_INBOX_LIMIT',
                            'UNRESOLVABLE', 'NORMAL', 'OPEN', ?, ?)
                    """, id, tenantA.getId(), UUID.randomUUID(),
                    "Caso número " + i, java.sql.Timestamp.from(base.plusSeconds(i)));
        }
        UUID foreignId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO human_handoff(
                    id, business_id, operation_id, operation_type,
                    tool_name, reason_code, failure_class, status, created_at)
                VALUES (?, ?, ?, 'REQUEST', 'home_qa', 'HOME_FOREIGN_LIMIT',
                        'UNRESOLVABLE', 'OPEN', ?)
                """, foreignId, tenantB.getId(), UUID.randomUUID(),
                java.sql.Timestamp.from(base.plusSeconds(9999)));

        var items = databaseContext.callAsTenant(tenantA.getId(), attention::pending);
        assertEquals(100, items.size(), "backend must cap the actionable inbox at 100");
        assertEquals(inserted.get(104), items.getFirst().id(),
                "highest created_at must be shown first");
        assertEquals(inserted.get(5), items.getLast().id(),
                "the five oldest rows must be excluded");
        assertFalse(items.stream().anyMatch(item -> item.id().equals(foreignId)));
        assertEquals(100, items.stream().map(HumanAttentionService.AttentionItem::id).distinct().count());

        // The literal limit is the maximum number of rows, not a global history total.
        assertEquals(105L, jdbc.queryForObject(
                "SELECT count(*) FROM human_handoff WHERE business_id = ?",
                Long.class, tenantA.getId()));
    }

    @Test
    void concurrentRequestProgressInRealPostgresProducesOneDurableTransition() throws Exception {
        Business business = business("Home request concurrent users");
        authenticate(business.getId());
        var request = requests.create(new BusinessRequestDtos.Create(
                "GENERAL", "Two simultaneous operators", null, null, null,
                RequestPriority.NORMAL, null));

        java.util.concurrent.CountDownLatch ready = new java.util.concurrent.CountDownLatch(2);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        try (java.util.concurrent.ExecutorService executor =
                     java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.List<java.util.concurrent.Future<RequestStatus>> futures =
                    new java.util.ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(executor.submit(() -> {
                    authenticate(business.getId());
                    try {
                        return databaseContext.callAsTenant(business.getId(), () -> {
                            ready.countDown();
                            try {
                                if (!start.await(20, java.util.concurrent.TimeUnit.SECONDS)) {
                                    throw new IllegalStateException("Concurrent test start timed out");
                                }
                            } catch (InterruptedException interrupted) {
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException(interrupted);
                            }
                            return requests.setStatus(request.id(), RequestStatus.IN_PROGRESS).status();
                        });
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                }));
            }
            assertTrue(ready.await(20, java.util.concurrent.TimeUnit.SECONDS),
                    "both operators must enter before mutations start");
            start.countDown();
            for (var future : futures) {
                assertEquals(RequestStatus.IN_PROGRESS,
                        future.get(30, java.util.concurrent.TimeUnit.SECONDS));
            }
        }

        authenticate(business.getId());
        assertEquals(List.of(RequestStatus.IN_PROGRESS),
                events.history(request.id()).stream()
                        .map(RequestLifecycleEventService.Transition::status).toList(),
                "one real DB transition/event despite simultaneous API-equivalent writes");
        assertEquals("IN_PROGRESS", jdbc.queryForObject(
                "SELECT status FROM business_request WHERE business_id = ? AND id = ?",
                String.class, business.getId(), request.id()));
        assertEquals(1, databaseContext.callAsTenant(business.getId(), attention::pending)
                .stream().filter(item -> item.id().equals(request.id())).count());
    }


    @Test
    void homeDashboardRealPostgresIsTenantScopedAndUsesHalfOpenLocalDayWithNoSimulatorCost() {
        Business tenantA = new Business();
        tenantA.setName("Home Dashboard A");
        tenantA.setTimezone("Pacific/Auckland");
        tenantA = businesses.saveAndFlush(tenantA);
        Business tenantB = new Business();
        tenantB.setName("Home Dashboard B");
        tenantB.setTimezone("UTC");
        tenantB = businesses.saveAndFlush(tenantB);

        UUID a = tenantA.getId();
        UUID b = tenantB.getId();
        UUID phoneA = UUID.randomUUID();
        UUID phoneB = UUID.randomUUID();
        jdbc.update("INSERT INTO phone_number(id, business_id, phone_number) VALUES (?, ?, ?)",
                phoneA, a, syntheticPhoneNumber());
        jdbc.update("INSERT INTO phone_number(id, business_id, phone_number) VALUES (?, ?, ?)",
                phoneB, b, syntheticPhoneNumber());
        java.time.ZoneId zone = java.time.ZoneId.of("Pacific/Auckland");
        java.time.LocalDate today = java.time.LocalDate.now(zone);
        java.time.Instant dayStart = today.atStartOfDay(zone).toInstant();
        java.time.Instant nextDayStart = today.plusDays(1).atStartOfDay(zone).toInstant();
        insertHomeSyntheticCall(a, phoneA, dayStart, "twilio", false, "COMPLETED", 60, "0.60");
        insertHomeSyntheticCall(a, phoneA, dayStart.plusSeconds(2), "twilio", false, "FAILED", 30, "0.20");
        insertHomeSyntheticCall(a, phoneA, dayStart.plusSeconds(3), "simulator", false, "COMPLETED", 999, "100.00");
        insertHomeSyntheticCall(a, phoneA, dayStart.plusSeconds(4), "twilio", true, "COMPLETED", 999, "100.00");
        insertHomeSyntheticCall(a, phoneA, nextDayStart, "twilio", false, "COMPLETED", 300, "5.00");
        insertHomeSyntheticCall(a, phoneA, dayStart.minusSeconds(1), "twilio", false, "COMPLETED", 300, "5.00");
        insertHomeSyntheticCall(b, phoneB, java.time.Instant.now(), "twilio", false, "COMPLETED", 999, "100.00");

        authenticate(a);
        var requestA = requests.create(new BusinessRequestDtos.Create(
                "GENERAL", "Only dashboard A", null, null, null, RequestPriority.NORMAL, null));
        requests.create(new BusinessRequestDtos.Create(
                "GENERAL", "Second A request", null, null, null, RequestPriority.HIGH, null));
        authenticate(b);
        var requestB = requests.create(new BusinessRequestDtos.Create(
                "GENERAL", "Only dashboard B", null, null, null, RequestPriority.NORMAL, null));

        authenticate(a);
        var result = databaseContext.callAsTenant(a, homeDashboard::dashboard);
        assertEquals("Home Dashboard A", result.businessName());
        assertEquals("Pacific/Auckland", result.timezone());
        assertEquals(today, java.time.OffsetDateTime.parse(result.localNow()).toLocalDate());
        assertEquals(2, result.callsToday(), "beginning of day included, next day excluded");
        assertEquals(1, result.callFailuresToday(), "only production failure in local day");
        assertEquals(90, result.callDurationSecondsToday(), "simulator/certification excluded");
        assertEquals(0, new java.math.BigDecimal("0.80")
                .compareTo(result.estimatedCallCostTodayUsd()), "simulator/certification cost excluded");
        assertEquals(2, result.openRequests(), "only tenant A's OPEN requests");
        assertEquals(0, result.bookingsToday());
        assertEquals(0, result.newCustomersToday());
        assertTrue(result.recentRequests().stream().anyMatch(x -> x.id().equals(requestA.id())));
        assertFalse(result.recentRequests().stream().anyMatch(x -> x.id().equals(requestB.id())));
        assertTrue(result.recentCalls().stream().allMatch(x ->
                x.durationSeconds() == 30 || x.durationSeconds() == 60 || x.durationSeconds() == 300));
        assertFalse(result.recentCalls().stream().anyMatch(x -> x.durationSeconds() == 999));

        requests.setStatus(requestA.id(), RequestStatus.RESOLVED);
        assertEquals(1, databaseContext.callAsTenant(a, homeDashboard::dashboard).openRequests());
        authenticate(b);
        var foreignResult = databaseContext.callAsTenant(b, homeDashboard::dashboard);
        assertEquals("Home Dashboard B", foreignResult.businessName());
        assertEquals(1, foreignResult.callsToday());
        assertEquals(1, foreignResult.openRequests());
        assertFalse(foreignResult.recentRequests().stream()
                .anyMatch(x -> x.id().equals(requestA.id())));
    }

    private static String syntheticPhoneNumber() {
        int suffix = (int) Math.floorMod(UUID.randomUUID().getLeastSignificantBits(), 100_000_000L);
        return String.format(java.util.Locale.ROOT, "+569%08d", suffix);
    }

    private void insertHomeSyntheticCall(UUID businessId, UUID phoneNumberId,
                                         java.time.Instant startedAt, String provider,
                                         boolean certification, String status,
                                         int durationSeconds, String totalCost) {
        jdbc.update("""
                INSERT INTO call_session(
                    id, business_id, phone_number_id, telephony_provider, provider_call_id,
                    destination_number, direction, status, started_at, duration_seconds,
                    estimated_total_cost_usd, certification, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, '+56900000000', 'INBOUND', ?, ?, ?, ?, ?, NOW(), NOW())
                """, UUID.randomUUID(), businessId, phoneNumberId, provider,
                "home-qa-" + UUID.randomUUID(), status, java.sql.Timestamp.from(startedAt),
                durationSeconds, new java.math.BigDecimal(totalCost), certification);
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

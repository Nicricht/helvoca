package cl.helvoca.retention;

import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DataRetentionInventoryServiceTest {

    @Test
    void policyBuildsExpectedProductCutoffs() {
        Instant now = Instant.parse("2026-09-22T12:00:00Z");

        DataRetentionPolicy.Cutoffs cutoffs = DataRetentionPolicy.cutoffs(now);

        assertEquals(Instant.parse("2026-06-24T12:00:00Z"), cutoffs.callContentBefore());
        assertEquals(Instant.parse("2026-03-26T12:00:00Z"), cutoffs.callSessionsBefore());
        assertEquals(Instant.parse("2026-06-24T12:00:00Z"), cutoffs.messageContentBefore());
        assertEquals(Instant.parse("2026-03-26T12:00:00Z"), cutoffs.conversationsBefore());
        assertEquals(Instant.parse("2024-09-22T12:00:00Z"), cutoffs.customerReviewBefore());
        assertEquals(Instant.parse("2024-09-22T12:00:00Z"), cutoffs.operationsBefore());
        assertEquals(Instant.parse("2024-09-22T12:00:00Z"), cutoffs.auditBefore());
    }

    @Test
    void dryRunIsTenantScopedCountsOnlyAndNeverEnablesDeletion() {
        UUID businessId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-22T12:00:00Z");
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(), any()))
                .thenReturn(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L);

        DataRetentionInventoryService service = new DataRetentionInventoryService(
                jdbc,
                tenantProvider,
                Clock.fixed(now, ZoneOffset.UTC));

        DataRetentionInventoryService.Inventory inventory = service.dryRun();

        assertEquals(now, inventory.generatedAt());
        assertFalse(inventory.destructiveActionsEnabled());
        assertEquals(1L, inventory.callTranscriptsEligible());
        assertEquals(2L, inventory.callSummariesEligible());
        assertEquals(3L, inventory.callActionsEligible());
        assertEquals(4L, inventory.callSessionsEligible());
        assertEquals(5L, inventory.messagingMessagesEligible());
        assertEquals(6L, inventory.messagingConversationsEligible());
        assertEquals(7L, inventory.outboundMessageContentEligible());
        assertEquals(8L, inventory.customerInactivityReviewCandidates());
        assertEquals(9L, inventory.nonFinancialOperationsEligible());
        assertEquals(10L, inventory.financialOperationsHeldFromAutomation());
        assertEquals(11L, inventory.auditLogsEligible());

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> tenant = ArgumentCaptor.forClass(Object.class);
        verify(jdbc, times(11)).queryForObject(sql.capture(), eq(Long.class), tenant.capture(), any());

        List<String> statements = sql.getAllValues();
        assertEquals(11, statements.size());
        assertTrue(statements.stream().allMatch(statement -> statement.contains("business_id = ?")));
        assertTrue(tenant.getAllValues().stream().allMatch(businessId::equals));
        assertTrue(statements.stream().allMatch(statement -> statement.stripLeading().toUpperCase().startsWith("SELECT")));

        for (String callContentInventory : statements.subList(0, 3)) {
            assertTrue(callContentInventory.contains("retention_legal_hold"));
            assertTrue(callContentInventory.contains("h.business_id = c.business_id"));
            assertTrue(callContentInventory.contains("h.target_type = 'CALL_SESSION'"));
            assertTrue(callContentInventory.contains("h.target_id = c.id"));
            assertTrue(callContentInventory.contains("h.released_at IS NULL"));
        }

        String callSessionInventory = statements.get(3);
        assertTrue(callSessionInventory.contains("retention_legal_hold"));
        assertTrue(callSessionInventory.contains("h.business_id = c.business_id"));
        assertTrue(callSessionInventory.contains("h.target_type = 'CALL_SESSION'"));
        assertTrue(callSessionInventory.contains("h.target_id = c.id"));
        assertTrue(callSessionInventory.contains("h.released_at IS NULL"));

        String messagingMessageInventory = statements.get(4);
        String messagingConversationInventory = statements.get(5);
        for (String statement : List.of(messagingMessageInventory, messagingConversationInventory)) {
            assertTrue(statement.contains("retention_legal_hold"));
            assertTrue(statement.contains("h.business_id = c.business_id"));
            assertTrue(statement.contains("h.target_type = 'MESSAGING_CONVERSATION'"));
            assertTrue(statement.contains("h.target_id = c.id"));
            assertTrue(statement.contains("h.released_at IS NULL"));
        }

        String outboundMessageInventory = statements.get(6);
        assertTrue(outboundMessageInventory.contains("retention_legal_hold"));
        assertTrue(outboundMessageInventory.contains("h.business_id = om.business_id"));
        assertTrue(outboundMessageInventory.contains("h.target_type = 'OUTBOUND_MESSAGE'"));
        assertTrue(outboundMessageInventory.contains("h.target_id = om.id"));
        assertTrue(outboundMessageInventory.contains("h.released_at IS NULL"));

        String customerInventory = statements.get(7);
        assertTrue(customerInventory.contains("retention_legal_hold"));
        assertTrue(customerInventory.contains("h.business_id = c.business_id"));
        assertTrue(customerInventory.contains("h.target_type = 'CUSTOMER'"));
        assertTrue(customerInventory.contains("h.target_id = c.id"));
        assertTrue(customerInventory.contains("h.released_at IS NULL"));
        assertTrue(customerInventory.contains("business_operation"));
        assertTrue(customerInventory.contains("o.business_id = c.business_id"));
        assertTrue(customerInventory.contains("o.customer_id = c.id"));
        assertTrue(customerInventory.contains("'DRAFT'"));
        assertTrue(customerInventory.contains("'AWAITING_CONFIRMATION'"));
        assertTrue(customerInventory.contains("'EXECUTING'"));

        String nonFinancialOperationInventory = statements.get(8);
        assertTrue(nonFinancialOperationInventory.contains("retention_legal_hold"));
        assertTrue(nonFinancialOperationInventory.contains("h.business_id = o.business_id"));
        assertTrue(nonFinancialOperationInventory.contains("h.target_type = 'BUSINESS_OPERATION'"));
        assertTrue(nonFinancialOperationInventory.contains("h.target_id = o.id"));
        assertTrue(nonFinancialOperationInventory.contains("h.released_at IS NULL"));

        String auditInventory = statements.get(10);
        assertTrue(auditInventory.contains("retention_legal_hold"));
        assertTrue(auditInventory.contains("h.business_id = a.business_id"));
        assertTrue(auditInventory.contains("h.target_type = 'AUDIT_LOG'"));
        assertTrue(auditInventory.contains("h.target_id = a.id"));
        assertTrue(auditInventory.contains("h.released_at IS NULL"));
    }
}

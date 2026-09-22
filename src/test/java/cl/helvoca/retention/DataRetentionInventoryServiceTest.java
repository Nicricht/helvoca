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
                .thenReturn(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);

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
        assertEquals(7L, inventory.customerInactivityReviewCandidates());
        assertEquals(8L, inventory.nonFinancialOperationsEligible());
        assertEquals(9L, inventory.financialOperationsHeldFromAutomation());
        assertEquals(10L, inventory.auditLogsEligible());

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> tenant = ArgumentCaptor.forClass(Object.class);
        verify(jdbc, times(10)).queryForObject(sql.capture(), eq(Long.class), tenant.capture(), any());

        List<String> statements = sql.getAllValues();
        assertEquals(10, statements.size());
        assertTrue(statements.stream().allMatch(statement -> statement.contains("business_id = ?")));
        assertTrue(tenant.getAllValues().stream().allMatch(businessId::equals));
        assertTrue(statements.stream().noneMatch(statement -> statement.toUpperCase().contains("DELETE")));
        assertTrue(statements.stream().noneMatch(statement -> statement.toUpperCase().contains("UPDATE")));
        assertTrue(statements.stream().noneMatch(statement -> statement.toUpperCase().contains("INSERT")));
    }
}

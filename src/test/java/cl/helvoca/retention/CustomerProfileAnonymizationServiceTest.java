package cl.helvoca.retention;

import cl.helvoca.audit.AuditService;
import cl.helvoca.common.ConflictException;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CustomerProfileAnonymizationServiceTest {

    @Test
    void anonymizesOnlyCurrentTenantProfileAndDeletesItsIdentities() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        AuditService auditService = mock(AuditService.class);

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(jdbc.update(startsWith("UPDATE customer"), any(Object[].class))).thenReturn(1);
        when(jdbc.update(startsWith("UPDATE call_session"), any(Object[].class))).thenReturn(3);
        when(jdbc.update(startsWith("UPDATE messaging_conversation"), any(Object[].class))).thenReturn(4);
        when(jdbc.update(startsWith("UPDATE booking"), any(Object[].class))).thenReturn(5);
        when(jdbc.update(argThat(sql -> sql != null
                && sql.stripLeading().startsWith("UPDATE business_operation")
                && sql.contains("o.type = 'BOOKING'")), any(Object[].class))).thenReturn(6);
        when(jdbc.update(startsWith("UPDATE business_request"), any(Object[].class))).thenReturn(7);
        when(jdbc.update(argThat(sql -> sql != null
                && sql.stripLeading().startsWith("UPDATE business_operation")
                && sql.contains("o.type = 'REQUEST'")), any(Object[].class))).thenReturn(8);
        when(jdbc.update(startsWith("UPDATE business_lead"), any(Object[].class))).thenReturn(9);
        when(jdbc.update(argThat(sql -> sql != null
                && sql.stripLeading().startsWith("UPDATE business_operation")
                && sql.contains("o.type = 'LEAD'")), any(Object[].class))).thenReturn(10);
        when(jdbc.update(startsWith("DELETE FROM customer_identity"), any(Object[].class))).thenReturn(2);

        CustomerProfileAnonymizationService service =
                new CustomerProfileAnonymizationService(jdbc, tenantProvider, auditService);

        CustomerProfileAnonymizationService.Result result =
                service.anonymizeCurrentTenantCustomer(customerId);

        assertEquals(customerId, result.customerId());
        assertEquals(3, result.callSessionsScrubbed());
        assertEquals(4, result.messagingConversationsScrubbed());
        assertEquals(5, result.bookingNotesScrubbed());
        assertEquals(6, result.bookingOperationMetadataScrubbed());
        assertEquals(7, result.businessRequestsScrubbed());
        assertEquals(8, result.requestOperationPiiScrubbed());
        assertEquals(9, result.businessLeadsScrubbed());
        assertEquals(10, result.leadOperationPiiScrubbed());
        assertEquals(2, result.identitiesDeleted());

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc, times(10)).update(sql.capture(), args.capture());

        assertTrue(sql.getAllValues().get(0).contains("SET name = NULL"));
        assertTrue(sql.getAllValues().get(0).contains("phone = NULL"));
        assertTrue(sql.getAllValues().get(0).contains("email = NULL"));
        assertTrue(sql.getAllValues().get(0).contains("notes = NULL"));
        assertTrue(sql.getAllValues().get(0).contains("WHERE c.id = ?"));
        assertTrue(sql.getAllValues().get(0).contains("c.business_id = ?"));
        assertTrue(sql.getAllValues().get(0).contains("retention_legal_hold"));
        assertTrue(sql.getAllValues().get(0).contains("h.business_id = c.business_id"));
        assertTrue(sql.getAllValues().get(0).contains("h.target_type = 'CUSTOMER'"));
        assertTrue(sql.getAllValues().get(0).contains("h.target_id = c.id"));
        assertTrue(sql.getAllValues().get(0).contains("h.released_at IS NULL"));
        assertTrue(sql.getAllValues().get(0).contains("business_operation"));
        assertTrue(sql.getAllValues().get(0).contains("o.business_id = c.business_id"));
        assertTrue(sql.getAllValues().get(0).contains("o.customer_id = c.id"));
        assertTrue(sql.getAllValues().get(0).contains("'AWAITING_CONFIRMATION'"));
        assertTrue(sql.getAllValues().get(0).contains("'EXECUTING'"));
        assertTrue(sql.getAllValues().get(0).contains("call_session"));
        assertTrue(sql.getAllValues().get(0).contains("cs.business_id = c.business_id"));
        assertTrue(sql.getAllValues().get(0).contains("cs.customer_id = c.id"));
        assertTrue(sql.getAllValues().get(0).contains("cs.ended_at IS NULL"));
        assertTrue(sql.getAllValues().get(0).contains("messaging_conversation mc"));
        assertTrue(sql.getAllValues().get(0).contains("mc.business_id = c.business_id"));
        assertTrue(sql.getAllValues().get(0).contains("mc.customer_id = c.id"));
        assertTrue(sql.getAllValues().get(0).contains("mc.last_message_at >= ?"));
        assertEquals(customerId, args.getAllValues().get(0)[0]);
        assertEquals(businessId, args.getAllValues().get(0)[1]);
        assertInstanceOf(Instant.class, args.getAllValues().get(0)[2]);

        assertTrue(sql.getAllValues().get(1).contains("UPDATE call_session"));
        assertTrue(sql.getAllValues().get(1).contains("caller_number = NULL"));
        assertTrue(sql.getAllValues().get(1).contains("cs.business_id = ?"));
        assertTrue(sql.getAllValues().get(1).contains("cs.customer_id = ?"));
        assertTrue(sql.getAllValues().get(1).contains("cs.ended_at IS NOT NULL"));
        assertTrue(sql.getAllValues().get(1).contains("cs.caller_number IS NOT NULL"));
        assertTrue(sql.getAllValues().get(1).contains("retention_legal_hold"));
        assertTrue(sql.getAllValues().get(1).contains("h.business_id = cs.business_id"));
        assertTrue(sql.getAllValues().get(1).contains("h.target_type = 'CALL_SESSION'"));
        assertTrue(sql.getAllValues().get(1).contains("h.target_id = cs.id"));
        assertTrue(sql.getAllValues().get(1).contains("h.released_at IS NULL"));
        assertArrayEquals(new Object[]{businessId, customerId}, args.getAllValues().get(1));

        assertTrue(sql.getAllValues().get(2).contains("UPDATE messaging_conversation"));
        assertTrue(sql.getAllValues().get(2).contains("sender = '[redacted]'"));
        assertTrue(sql.getAllValues().get(2).contains("mc.business_id = ?"));
        assertTrue(sql.getAllValues().get(2).contains("mc.customer_id = ?"));
        assertTrue(sql.getAllValues().get(2).contains("mc.last_message_at < ?"));
        assertTrue(sql.getAllValues().get(2).contains("retention_legal_hold"));
        assertTrue(sql.getAllValues().get(2).contains("h.business_id = mc.business_id"));
        assertTrue(sql.getAllValues().get(2).contains("h.target_type = 'MESSAGING_CONVERSATION'"));
        assertTrue(sql.getAllValues().get(2).contains("h.target_id = mc.id"));
        assertTrue(sql.getAllValues().get(2).contains("h.released_at IS NULL"));
        assertEquals(businessId, args.getAllValues().get(2)[0]);
        assertEquals(customerId, args.getAllValues().get(2)[1]);
        assertInstanceOf(Instant.class, args.getAllValues().get(2)[2]);

        assertTrue(sql.getAllValues().get(3).contains("UPDATE booking"));
        assertTrue(sql.getAllValues().get(3).contains("notes = NULL"));
        assertTrue(sql.getAllValues().get(3).contains("b.business_id = ?"));
        assertTrue(sql.getAllValues().get(3).contains("b.customer_id = ?"));
        assertTrue(sql.getAllValues().get(3).contains("business_operation o"));
        assertTrue(sql.getAllValues().get(3).contains("o.id = b.operation_id"));
        assertTrue(sql.getAllValues().get(3).contains("o.type = 'BOOKING'"));
        assertTrue(sql.getAllValues().get(3).contains("o.status IN ('COMPLETED', 'CANCELLED', 'EXPIRED', 'FAILED')"));
        assertTrue(sql.getAllValues().get(3).contains("h.target_type = 'BUSINESS_OPERATION'"));
        assertTrue(sql.getAllValues().get(3).contains("h.target_id = o.id"));
        assertTrue(sql.getAllValues().get(3).contains("h.released_at IS NULL"));
        assertArrayEquals(new Object[]{businessId, customerId}, args.getAllValues().get(3));

        assertTrue(sql.getAllValues().get(4).contains("UPDATE business_operation"));
        assertTrue(sql.getAllValues().get(4).contains("metadata_json = o.metadata_json - 'notes'"));
        assertTrue(sql.getAllValues().get(4).contains("o.business_id = ?"));
        assertTrue(sql.getAllValues().get(4).contains("o.customer_id = ?"));
        assertTrue(sql.getAllValues().get(4).contains("o.type = 'BOOKING'"));
        assertTrue(sql.getAllValues().get(4).contains("jsonb_exists(o.metadata_json, 'notes')"));
        assertTrue(sql.getAllValues().get(4).contains("h.target_type = 'BUSINESS_OPERATION'"));
        assertTrue(sql.getAllValues().get(4).contains("h.target_id = o.id"));
        assertTrue(sql.getAllValues().get(4).contains("h.released_at IS NULL"));
        assertFalse(sql.getAllValues().get(4).contains("updated_at ="));
        assertArrayEquals(new Object[]{businessId, customerId}, args.getAllValues().get(4));

        assertTrue(sql.getAllValues().get(5).contains("UPDATE business_request"));
        assertTrue(sql.getAllValues().get(5).contains("title = '[redacted]'"));
        assertTrue(sql.getAllValues().get(5).contains("description = NULL"));
        assertTrue(sql.getAllValues().get(5).contains("contact_name = NULL"));
        assertTrue(sql.getAllValues().get(5).contains("contact_phone = NULL"));
        assertTrue(sql.getAllValues().get(5).contains("details_json = NULL"));
        assertTrue(sql.getAllValues().get(5).contains("o.type = 'REQUEST'"));
        assertTrue(sql.getAllValues().get(5).contains("o.status IN ('COMPLETED', 'CANCELLED', 'EXPIRED', 'FAILED')"));
        assertTrue(sql.getAllValues().get(5).contains("h.target_type = 'BUSINESS_OPERATION'"));
        assertTrue(sql.getAllValues().get(5).contains("h.target_id = o.id"));
        assertTrue(sql.getAllValues().get(5).contains("h.released_at IS NULL"));
        assertArrayEquals(new Object[]{businessId, customerId}, args.getAllValues().get(5));

        assertTrue(sql.getAllValues().get(6).contains("UPDATE business_operation"));
        assertTrue(sql.getAllValues().get(6).contains("contact_name = NULL"));
        assertTrue(sql.getAllValues().get(6).contains("contact_phone = NULL"));
        assertTrue(sql.getAllValues().get(6).contains("metadata_json = o.metadata_json - 'title' - 'description' - 'details'"));
        assertTrue(sql.getAllValues().get(6).contains("o.type = 'REQUEST'"));
        assertTrue(sql.getAllValues().get(6).contains("o.status IN ('COMPLETED', 'CANCELLED', 'EXPIRED', 'FAILED')"));
        assertTrue(sql.getAllValues().get(6).contains("jsonb_exists(o.metadata_json, 'title')"));
        assertTrue(sql.getAllValues().get(6).contains("jsonb_exists(o.metadata_json, 'description')"));
        assertTrue(sql.getAllValues().get(6).contains("jsonb_exists(o.metadata_json, 'details')"));
        assertTrue(sql.getAllValues().get(6).contains("h.target_type = 'BUSINESS_OPERATION'"));
        assertTrue(sql.getAllValues().get(6).contains("h.target_id = o.id"));
        assertTrue(sql.getAllValues().get(6).contains("h.released_at IS NULL"));
        assertFalse(sql.getAllValues().get(6).contains("updated_at ="));
        assertArrayEquals(new Object[]{businessId, customerId}, args.getAllValues().get(6));

        assertTrue(sql.getAllValues().get(7).contains("UPDATE business_lead"));
        assertTrue(sql.getAllValues().get(7).contains("name = '[redacted]'"));
        assertTrue(sql.getAllValues().get(7).contains("phone = NULL"));
        assertTrue(sql.getAllValues().get(7).contains("email = NULL"));
        assertTrue(sql.getAllValues().get(7).contains("notes = NULL"));
        assertTrue(sql.getAllValues().get(7).contains("o.type = 'LEAD'"));
        assertTrue(sql.getAllValues().get(7).contains("o.status IN ('COMPLETED', 'CANCELLED', 'EXPIRED', 'FAILED')"));
        assertTrue(sql.getAllValues().get(7).contains("h.target_type = 'BUSINESS_OPERATION'"));
        assertTrue(sql.getAllValues().get(7).contains("h.target_id = o.id"));
        assertTrue(sql.getAllValues().get(7).contains("h.released_at IS NULL"));
        assertArrayEquals(new Object[]{businessId, customerId}, args.getAllValues().get(7));

        assertTrue(sql.getAllValues().get(8).contains("UPDATE business_operation"));
        assertTrue(sql.getAllValues().get(8).contains("contact_name = NULL"));
        assertTrue(sql.getAllValues().get(8).contains("contact_phone = NULL"));
        assertTrue(sql.getAllValues().get(8).contains("metadata_json = o.metadata_json - 'name' - 'email' - 'notes'"));
        assertTrue(sql.getAllValues().get(8).contains("o.type = 'LEAD'"));
        assertTrue(sql.getAllValues().get(8).contains("o.status IN ('COMPLETED', 'CANCELLED', 'EXPIRED', 'FAILED')"));
        assertTrue(sql.getAllValues().get(8).contains("jsonb_exists(o.metadata_json, 'name')"));
        assertTrue(sql.getAllValues().get(8).contains("jsonb_exists(o.metadata_json, 'email')"));
        assertTrue(sql.getAllValues().get(8).contains("jsonb_exists(o.metadata_json, 'notes')"));
        assertTrue(sql.getAllValues().get(8).contains("h.target_type = 'BUSINESS_OPERATION'"));
        assertTrue(sql.getAllValues().get(8).contains("h.target_id = o.id"));
        assertTrue(sql.getAllValues().get(8).contains("h.released_at IS NULL"));
        assertFalse(sql.getAllValues().get(8).contains("updated_at ="));
        assertArrayEquals(new Object[]{businessId, customerId}, args.getAllValues().get(8));

        assertTrue(sql.getAllValues().get(9).contains("DELETE FROM customer_identity"));
        assertTrue(sql.getAllValues().get(9).contains("business_id = ?"));
        assertTrue(sql.getAllValues().get(9).contains("customer_id = ?"));
        assertArrayEquals(new Object[]{businessId, customerId}, args.getAllValues().get(9));

        verify(auditService).humanSuccess(
                businessId,
                "CUSTOMER_PROFILE_ANONYMIZE",
                "CUSTOMER",
                customerId);
    }

    @Test
    void refusesAnonymizationBeforeMutationWhenCustomerHasActiveLegalHold() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        AuditService auditService = mock(AuditService.class);

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(businessId), eq(customerId)))
                .thenReturn(true);

        CustomerProfileAnonymizationService service =
                new CustomerProfileAnonymizationService(jdbc, tenantProvider, auditService);

        assertThrows(
                ConflictException.class,
                () -> service.anonymizeCurrentTenantCustomer(customerId));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForObject(
                sql.capture(),
                eq(Boolean.class),
                eq(businessId),
                eq(customerId));
        assertTrue(sql.getValue().contains("retention_legal_hold"));
        assertTrue(sql.getValue().contains("h.business_id = ?"));
        assertTrue(sql.getValue().contains("h.target_type = 'CUSTOMER'"));
        assertTrue(sql.getValue().contains("h.target_id = ?"));
        assertTrue(sql.getValue().contains("h.released_at IS NULL"));

        verify(jdbc, never()).update(anyString(), any(Object[].class));
        verifyNoInteractions(auditService);
    }

    @Test
    void refusesAnonymizationBeforeMutationWhenCustomerHasActiveOperation() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        AuditService auditService = mock(AuditService.class);

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(jdbc.queryForObject(
                contains("retention_legal_hold"),
                eq(Boolean.class),
                eq(businessId),
                eq(customerId)))
                .thenReturn(false);
        when(jdbc.queryForObject(
                contains("business_operation"),
                eq(Boolean.class),
                eq(businessId),
                eq(customerId)))
                .thenReturn(true);

        CustomerProfileAnonymizationService service =
                new CustomerProfileAnonymizationService(jdbc, tenantProvider, auditService);

        assertThrows(
                ConflictException.class,
                () -> service.anonymizeCurrentTenantCustomer(customerId));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(2)).queryForObject(
                sql.capture(),
                eq(Boolean.class),
                eq(businessId),
                eq(customerId));

        String activeOperationSql = sql.getAllValues().get(1);
        assertTrue(activeOperationSql.contains("business_operation"));
        assertTrue(activeOperationSql.contains("o.business_id = ?"));
        assertTrue(activeOperationSql.contains("o.customer_id = ?"));
        assertTrue(activeOperationSql.contains("'DRAFT'"));
        assertTrue(activeOperationSql.contains("'PROPOSED'"));
        assertTrue(activeOperationSql.contains("'AWAITING_CONFIRMATION'"));
        assertTrue(activeOperationSql.contains("'CONFIRMED'"));
        assertTrue(activeOperationSql.contains("'EXECUTING'"));

        verify(jdbc, never()).update(anyString(), any(Object[].class));
        verifyNoInteractions(auditService);
    }

    @Test
    void refusesAnonymizationBeforeMutationWhenCustomerHasActiveCall() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        AuditService auditService = mock(AuditService.class);

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(jdbc.queryForObject(
                contains("retention_legal_hold"),
                eq(Boolean.class),
                eq(businessId),
                eq(customerId)))
                .thenReturn(false);
        when(jdbc.queryForObject(
                contains("business_operation"),
                eq(Boolean.class),
                eq(businessId),
                eq(customerId)))
                .thenReturn(false);
        when(jdbc.queryForObject(
                contains("call_session cs"),
                eq(Boolean.class),
                eq(businessId),
                eq(customerId)))
                .thenReturn(true);

        CustomerProfileAnonymizationService service =
                new CustomerProfileAnonymizationService(jdbc, tenantProvider, auditService);

        assertThrows(
                ConflictException.class,
                () -> service.anonymizeCurrentTenantCustomer(customerId));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(3)).queryForObject(
                sql.capture(),
                eq(Boolean.class),
                eq(businessId),
                eq(customerId));

        String activeCallSql = sql.getAllValues().get(2);
        assertTrue(activeCallSql.contains("call_session cs"));
        assertTrue(activeCallSql.contains("cs.business_id = ?"));
        assertTrue(activeCallSql.contains("cs.customer_id = ?"));
        assertTrue(activeCallSql.contains("cs.ended_at IS NULL"));

        verify(jdbc, never()).update(anyString(), any(Object[].class));
        verifyNoInteractions(auditService);
    }

    @Test
    void refusesAnonymizationBeforeMutationWhenCustomerHasRecentMessagingConversation() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        AuditService auditService = mock(AuditService.class);

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(jdbc.queryForObject(
                contains("retention_legal_hold"),
                eq(Boolean.class),
                eq(businessId),
                eq(customerId)))
                .thenReturn(false);
        when(jdbc.queryForObject(
                contains("business_operation"),
                eq(Boolean.class),
                eq(businessId),
                eq(customerId)))
                .thenReturn(false);
        when(jdbc.queryForObject(
                contains("call_session cs"),
                eq(Boolean.class),
                eq(businessId),
                eq(customerId)))
                .thenReturn(false);
        when(jdbc.queryForObject(
                contains("messaging_conversation mc"),
                eq(Boolean.class),
                eq(businessId),
                eq(customerId),
                any(Instant.class)))
                .thenReturn(true);

        CustomerProfileAnonymizationService service =
                new CustomerProfileAnonymizationService(jdbc, tenantProvider, auditService);

        assertThrows(
                ConflictException.class,
                () -> service.anonymizeCurrentTenantCustomer(customerId));

        verify(jdbc).queryForObject(
                contains("messaging_conversation mc"),
                eq(Boolean.class),
                eq(businessId),
                eq(customerId),
                any(Instant.class));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
        verifyNoInteractions(auditService);
    }

    @Test
    void doesNotDeleteIdentitiesOrAuditWhenCustomerIsOutsideTenant() {
        UUID businessId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        AuditService auditService = mock(AuditService.class);

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(jdbc.update(startsWith("UPDATE customer"), any(Object[].class))).thenReturn(0);

        CustomerProfileAnonymizationService service =
                new CustomerProfileAnonymizationService(jdbc, tenantProvider, auditService);

        assertThrows(NotFoundException.class, () -> service.anonymizeCurrentTenantCustomer(customerId));

        verify(jdbc, times(3)).queryForObject(
                anyString(),
                eq(Boolean.class),
                eq(businessId),
                eq(customerId));
        verify(jdbc).queryForObject(
                contains("messaging_conversation mc"),
                eq(Boolean.class),
                eq(businessId),
                eq(customerId),
                any(Instant.class));
        verify(jdbc, times(1)).update(startsWith("UPDATE customer"), any(Object[].class));
        verifyNoMoreInteractions(jdbc);
        verifyNoInteractions(auditService);
    }

    @Test
    void failsClosedBeforeDatabaseAccessWhenTenantCannotBeResolved() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        AuditService auditService = mock(AuditService.class);

        when(tenantProvider.requireBusinessId()).thenThrow(new IllegalStateException("missing tenant"));

        CustomerProfileAnonymizationService service =
                new CustomerProfileAnonymizationService(jdbc, tenantProvider, auditService);

        assertThrows(
                IllegalStateException.class,
                () -> service.anonymizeCurrentTenantCustomer(UUID.randomUUID()));

        verifyNoInteractions(jdbc, auditService);
    }

    @Test
    void rejectsNullCustomerBeforeResolvingTenant() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        AuditService auditService = mock(AuditService.class);

        CustomerProfileAnonymizationService service =
                new CustomerProfileAnonymizationService(jdbc, tenantProvider, auditService);

        assertThrows(IllegalArgumentException.class, () -> service.anonymizeCurrentTenantCustomer(null));

        verifyNoInteractions(jdbc, tenantProvider, auditService);
    }
}

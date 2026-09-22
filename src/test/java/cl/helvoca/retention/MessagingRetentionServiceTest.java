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

class MessagingRetentionServiceTest {

    @Test
    void purgesExpiredMessagesThenOnlyInactiveConversationsForCurrentTenant() {
        UUID businessId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-22T12:00:00Z");
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);

        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(7, 2);

        MessagingRetentionService service = new MessagingRetentionService(
                jdbc,
                tenantProvider,
                Clock.fixed(now, ZoneOffset.UTC));

        MessagingRetentionService.Result result = service.purgeExpiredMessagingForCurrentTenant();

        assertEquals(Instant.parse("2026-06-24T12:00:00Z"), result.messageCutoff());
        assertEquals(Instant.parse("2026-03-26T12:00:00Z"), result.conversationCutoff());
        assertEquals(7, result.messagesDeleted());
        assertEquals(2, result.conversationsDeleted());

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc, times(2)).update(sql.capture(), args.capture());

        List<String> statements = sql.getAllValues();
        assertTrue(statements.stream().allMatch(value -> value.stripLeading().toUpperCase().startsWith("DELETE")));
        assertTrue(statements.stream().allMatch(value -> value.contains("business_id = ?")));

        Object[] messageArgs = args.getAllValues().get(0);
        assertArrayEquals(new Object[]{
                businessId,
                Instant.parse("2026-06-24T12:00:00Z")
        }, messageArgs);

        Object[] conversationArgs = args.getAllValues().get(1);
        assertArrayEquals(new Object[]{
                businessId,
                Instant.parse("2026-03-26T12:00:00Z"),
                Instant.parse("2026-03-26T12:00:00Z")
        }, conversationArgs);

        assertTrue(statements.get(1).contains("NOT EXISTS"));
        assertTrue(statements.get(1).contains("m.created_at >= ?"));
    }

    @Test
    void failsClosedBeforeDeletionWhenTenantCannotBeResolved() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        when(tenantProvider.requireBusinessId()).thenThrow(new IllegalStateException("missing tenant"));

        MessagingRetentionService service = new MessagingRetentionService(
                jdbc,
                tenantProvider,
                Clock.fixed(Instant.parse("2026-09-22T12:00:00Z"), ZoneOffset.UTC));

        assertThrows(IllegalStateException.class, service::purgeExpiredMessagingForCurrentTenant);
        verifyNoInteractions(jdbc);
    }
}

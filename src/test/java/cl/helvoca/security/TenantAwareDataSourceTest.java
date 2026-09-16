package cl.helvoca.security;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class TenantAwareDataSourceTest {

    @Test
    void scrubFailureEvictsConnectionInsteadOfReturningContaminatedSessionToPool() throws Exception {
        HikariDataSource pool = mock(HikariDataSource.class);
        Connection physical = mock(Connection.class);
        Statement statement = mock(Statement.class);
        PreparedStatement checkoutScrub = mock(PreparedStatement.class);
        PreparedStatement installTenant = mock(PreparedStatement.class);
        PreparedStatement closeScrub = mock(PreparedStatement.class);

        when(pool.getConnection()).thenReturn(physical);
        when(physical.isClosed()).thenReturn(false);
        when(physical.getAutoCommit()).thenReturn(true);
        when(physical.createStatement()).thenReturn(statement);
        when(physical.prepareStatement(anyString()))
                .thenReturn(checkoutScrub, installTenant, closeScrub);
        doThrow(new SQLException("scrub failed")).when(closeScrub).execute();

        TenantDatabaseContext context = new TenantDatabaseContext();
        TenantAwareDataSource dataSource = new TenantAwareDataSource(pool, context);

        try (TenantDatabaseContext.Scope ignored = context.useTenant(UUID.randomUUID())) {
            Connection wrapped = dataSource.getConnection();
            assertThrows(SQLException.class, wrapped::close);
        }

        verify(pool).evictConnection(physical);
        verify(physical, never()).close();
    }
}

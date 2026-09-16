package cl.helvoca.security;

import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Applies PostgreSQL runtime role + tenant context on every pooled connection
 * checkout and scrubs both before the connection is returned to Hikari.
 */
public final class TenantAwareDataSource implements DataSource {
    static final String TENANT_ROLE = "helvoca_runtime";
    static final String SYSTEM_ROLE = "helvoca_system";

    private final DataSource delegate;
    private final TenantDatabaseContext context;

    public TenantAwareDataSource(DataSource delegate, TenantDatabaseContext context) {
        this.delegate = Objects.requireNonNull(delegate);
        this.context = Objects.requireNonNull(context);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return prepare(delegate.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return prepare(delegate.getConnection(username, password));
    }

    private Connection prepare(Connection connection) throws SQLException {
        TenantDatabaseContext.Access access = context.currentOrInternalSystem();
        try {
            scrubOutsideTransaction(connection);
            String role = access.mode() == TenantDatabaseContext.Mode.SYSTEM ? SYSTEM_ROLE : TENANT_ROLE;
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET ROLE " + role);
            }
            String tenantId = access.mode() == TenantDatabaseContext.Mode.TENANT
                    ? access.businessId().toString()
                    : "";
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT set_config('app.tenant_id', ?, false)")) {
                statement.setString(1, tenantId);
                statement.execute();
            }
            return wrap(connection);
        } catch (SQLException | RuntimeException e) {
            discardConnection(connection, e);
            throw e;
        }
    }

    private Connection wrap(Connection connection) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                new ConnectionHandler(connection));
    }

    /**
     * Session state must be reset outside a transaction. If RESET ROLE or
     * set_config were executed inside a transaction and Hikari later rolled it
     * back, PostgreSQL could restore the previous tenant/role on the pooled
     * physical connection.
     */
    private static void scrubOutsideTransaction(Connection connection) throws SQLException {
        if (connection == null || connection.isClosed()) return;

        SQLException first = null;
        try {
            if (!connection.getAutoCommit()) {
                try {
                    connection.rollback();
                } catch (SQLException e) {
                    first = e;
                }
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException e) {
                    if (first == null) first = e;
                    else first.addSuppressed(e);
                }
            }
        } catch (SQLException e) {
            first = e;
        }

        if (first == null) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT set_config('app.tenant_id', '', false)")) {
                statement.execute();
            } catch (SQLException e) {
                first = e;
            }
            try (Statement statement = connection.createStatement()) {
                statement.execute("RESET ROLE");
            } catch (SQLException e) {
                if (first == null) first = e;
                else first.addSuppressed(e);
            }
        }
        if (first != null) throw first;
    }

    /**
     * A connection whose tenant/role scrub failed must never be returned to the
     * pool. Hikari eviction is the primary path; generic DataSource delegates
     * fall back to JDBC abort before close.
     */
    private void discardConnection(Connection connection, Throwable primary) {
        if (connection == null) return;

        if (delegate instanceof HikariDataSource hikari) {
            try {
                hikari.evictConnection(connection);
                return;
            } catch (RuntimeException e) {
                primary.addSuppressed(e);
            }
        }

        try {
            connection.abort(Runnable::run);
        } catch (SQLException | RuntimeException e) {
            primary.addSuppressed(e);
        }
        try {
            connection.close();
        } catch (SQLException | RuntimeException e) {
            primary.addSuppressed(e);
        }
    }

    private final class ConnectionHandler implements java.lang.reflect.InvocationHandler {
        private final Connection physical;
        private boolean closed;

        private ConnectionHandler(Connection physical) {
            this.physical = physical;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if ("close".equals(name) && method.getParameterCount() == 0) {
                if (closed) return null;
                closed = true;
                try {
                    scrubOutsideTransaction(physical);
                } catch (SQLException e) {
                    discardConnection(physical, e);
                    throw e;
                }
                physical.close();
                return null;
            }
            if ("isClosed".equals(name) && method.getParameterCount() == 0 && closed) return true;
            if ("unwrap".equals(name) && args != null && args.length == 1 && args[0] instanceof Class<?> type) {
                if (type.isInstance(proxy)) return proxy;
            }
            if ("isWrapperFor".equals(name) && args != null && args.length == 1 && args[0] instanceof Class<?> type) {
                if (type.isInstance(proxy)) return true;
            }
            try {
                return method.invoke(physical, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }

    @Override public PrintWriter getLogWriter() throws SQLException { return delegate.getLogWriter(); }
    @Override public void setLogWriter(PrintWriter out) throws SQLException { delegate.setLogWriter(out); }
    @Override public void setLoginTimeout(int seconds) throws SQLException { delegate.setLoginTimeout(seconds); }
    @Override public int getLoginTimeout() throws SQLException { return delegate.getLoginTimeout(); }
    @Override public Logger getParentLogger() throws SQLFeatureNotSupportedException { return delegate.getParentLogger(); }
    @Override public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) return iface.cast(this);
        return delegate.unwrap(iface);
    }
    @Override public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isInstance(this) || delegate.isWrapperFor(iface);
    }
}

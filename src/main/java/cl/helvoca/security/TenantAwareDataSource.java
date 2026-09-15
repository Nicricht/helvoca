package cl.helvoca.security;

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
import java.util.concurrent.Executor;
import java.util.logging.Logger;

/**
 * Applies PostgreSQL runtime role + tenant context on every pooled connection
 * checkout and scrubs both before the connection is returned to Hikari.
 */
public final class TenantAwareDataSource implements DataSource {
    static final String TENANT_ROLE = "helvoca_runtime";
    static final String SYSTEM_ROLE = "helvoca_system";
    private static final Executor DIRECT_EXECUTOR = Runnable::run;

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
            reset(connection);
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
        } catch (SQLException e) {
            discard(connection, e);
            throw e;
        }
    }

    private Connection wrap(Connection connection) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                new ConnectionHandler(connection));
    }

    private static void reset(Connection connection) throws SQLException {
        if (connection == null || connection.isClosed()) return;
        SQLException first = null;
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
        if (first != null) throw first;
    }

    /** A connection that could not be scrubbed must never re-enter the pool. */
    private static void discard(Connection connection, SQLException cause) {
        if (connection == null) return;
        try {
            connection.abort(DIRECT_EXECUTOR);
        } catch (SQLException abortFailure) {
            cause.addSuppressed(abortFailure);
            try { connection.close(); } catch (SQLException closeFailure) { cause.addSuppressed(closeFailure); }
        }
    }

    private static final class ConnectionHandler implements java.lang.reflect.InvocationHandler {
        private final Connection delegate;
        private boolean closed;

        private ConnectionHandler(Connection delegate) {
            this.delegate = delegate;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if ("close".equals(name) && method.getParameterCount() == 0) {
                if (closed) return null;
                closed = true;
                try {
                    reset(delegate);
                } catch (SQLException resetFailure) {
                    discard(delegate, resetFailure);
                    throw resetFailure;
                }
                delegate.close();
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
                return method.invoke(delegate, args);
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

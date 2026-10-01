package cl.helvoca.testsupport;

import cl.helvoca.security.TenantDatabaseContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.context.transaction.BeforeTransaction;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Opt-in support for integration tests that intentionally prepare cross-tenant
 * or owner-level fixtures.
 *
 * Production code never inherits this behavior. Tests must extend this class
 * explicitly, which keeps the application default fail-closed while making
 * privileged fixture setup visible in the test type.
 */
public abstract class ExplicitSystemDatabaseScopeSupport {

    @Autowired
    protected TenantDatabaseContext databaseContext;

    private TenantDatabaseContext.Scope systemScope;

    @BeforeTransaction
    void enterBeforeTransactionalTest() {
        enter();
    }

    @BeforeEach
    void enterBeforeTest() {
        enter();
    }

    @AfterEach
    void leaveAfterNonTransactionalTest() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            close();
        }
    }

    @AfterTransaction
    void leaveAfterTransactionalTest() {
        close();
    }

    private void enter() {
        if (systemScope == null) {
            systemScope = databaseContext.useSystem();
        }
    }

    private void close() {
        if (systemScope == null) return;
        systemScope.close();
        systemScope = null;
    }
}

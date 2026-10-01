package cl.helvoca.security;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Installs tenant identity before a database transaction starts.
 *
 * This is required for non-HTTP entry points such as realtime voice sessions,
 * where there is no authenticated servlet filter to establish tenant context.
 */
@Component
public class TenantTransactionExecutor {
    private final TenantDatabaseContext databaseContext;
    private final PlatformTransactionManager transactionManager;

    public TenantTransactionExecutor(TenantDatabaseContext databaseContext,
                                     PlatformTransactionManager transactionManager) {
        this.databaseContext = databaseContext;
        this.transactionManager = transactionManager;
    }

    public <T> T read(UUID businessId, Supplier<T> work) {
        return execute(businessId, true, TransactionDefinition.ISOLATION_DEFAULT, work);
    }

    public <T> T write(UUID businessId, int isolationLevel, Supplier<T> work) {
        return execute(businessId, false, isolationLevel, work);
    }

    private <T> T execute(UUID businessId,
                          boolean readOnly,
                          int isolationLevel,
                          Supplier<T> work) {
        if (businessId == null) throw new IllegalArgumentException("businessId is required");
        if (work == null) throw new IllegalArgumentException("work is required");

        return databaseContext.callAsTenant(businessId, () -> {
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            transaction.setReadOnly(readOnly);
            transaction.setIsolationLevel(isolationLevel);
            return transaction.execute(status -> work.get());
        });
    }
}

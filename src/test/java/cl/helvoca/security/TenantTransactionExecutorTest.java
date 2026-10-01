package cl.helvoca.security;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class TenantTransactionExecutorTest {

    @Test
    void rejectsMissingBusinessOrWorkBeforeOpeningTransaction() {
        TenantDatabaseContext context = new TenantDatabaseContext();
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        TenantTransactionExecutor executor = new TenantTransactionExecutor(context, transactionManager);
        UUID businessId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> executor.read(null, () -> "never"));
        assertThrows(IllegalArgumentException.class, () -> executor.read(businessId, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> executor.write(null, TransactionDefinition.ISOLATION_SERIALIZABLE, () -> "never"));
        assertThrows(
                IllegalArgumentException.class,
                () -> executor.write(businessId, TransactionDefinition.ISOLATION_SERIALIZABLE, null));

        verifyNoInteractions(transactionManager);
        assertEquals(TenantDatabaseContext.Mode.DENIED, context.currentOrDenied().mode());
    }

    @Test
    void readAndWriteInstallTenantBeforeTransactionAndRestoreDeniedAfterward() {
        TenantDatabaseContext context = new TenantDatabaseContext();
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        TransactionStatus status = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(status);

        TenantTransactionExecutor executor = new TenantTransactionExecutor(context, transactionManager);
        UUID businessId = UUID.randomUUID();

        String readResult = executor.read(businessId, () -> {
            assertEquals(TenantDatabaseContext.Mode.TENANT, context.currentOrDenied().mode());
            assertEquals(businessId, context.currentOrDenied().businessId());
            return "read-ok";
        });

        String writeResult = executor.write(
                businessId,
                TransactionDefinition.ISOLATION_SERIALIZABLE,
                () -> {
                    assertEquals(TenantDatabaseContext.Mode.TENANT, context.currentOrDenied().mode());
                    assertEquals(businessId, context.currentOrDenied().businessId());
                    return "write-ok";
                });

        assertEquals("read-ok", readResult);
        assertEquals("write-ok", writeResult);
        assertEquals(TenantDatabaseContext.Mode.DENIED, context.currentOrDenied().mode());

        verify(transactionManager).getTransaction(argThat(definition ->
                definition.isReadOnly()
                        && definition.getIsolationLevel() == TransactionDefinition.ISOLATION_DEFAULT));
        verify(transactionManager).getTransaction(argThat(definition ->
                !definition.isReadOnly()
                        && definition.getIsolationLevel() == TransactionDefinition.ISOLATION_SERIALIZABLE));
        verify(transactionManager, times(2)).commit(status);
        verify(transactionManager, never()).rollback(any());
    }
}

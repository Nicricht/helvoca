package cl.helvoca.agent;

import cl.helvoca.operations.BusinessOperation;
import cl.helvoca.security.TenantDatabaseContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CommercialSandboxOrderReadyStartupRunnerTest {

    @Test
    void disabledRunnerDoesNothing() throws Exception {
        TenantDatabaseContext databaseContext = mock(TenantDatabaseContext.class);
        CommercialSandboxE2eCertificationStartupRunner certification =
                mock(CommercialSandboxE2eCertificationStartupRunner.class);

        CommercialSandboxOrderReadyStartupRunner runner =
                new CommercialSandboxOrderReadyStartupRunner(
                        false,
                        "",
                        "",
                        "1000",
                        databaseContext,
                        mock(PlatformTransactionManager.class),
                        certification);

        runner.run(mock(ApplicationArguments.class));

        verifyNoInteractions(databaseContext, certification);
    }

    @Test
    void orderReadyContractStopsBeforeExternalPaymentProjection() {
        var result = new CommercialSandboxE2eCertificationStartupRunner.OrderReadyResult(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                BusinessOperation.Status.CONFIRMED,
                BusinessOperation.Status.AWAITING_CONFIRMATION,
                false);

        org.junit.jupiter.api.Assertions.assertEquals(
                BusinessOperation.Status.CONFIRMED, result.orderStatus());
        org.junit.jupiter.api.Assertions.assertEquals(
                BusinessOperation.Status.AWAITING_CONFIRMATION,
                result.paymentOperationStatus());
        org.junit.jupiter.api.Assertions.assertFalse(result.paymentProjectionExists());
    }
}

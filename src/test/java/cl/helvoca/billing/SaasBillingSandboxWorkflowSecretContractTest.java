package cl.helvoca.billing;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaasBillingSandboxWorkflowSecretContractTest {
    @Test
    void manualProviderRunnerReceivesOwnTestSigningSecretBeforeProviderEgress() throws Exception {
        String workflow = Files.readString(Path.of(".github/workflows/saas-billing-sandbox.yml"));
        assertTrue(workflow.contains("workflow_dispatch:"),
                "An external provider checkout must be a manual, explicit action");
        assertTrue(workflow.contains("MERCADOPAGO_TEST_WEBHOOK_SECRET: " + "$"
                + "{{ secrets.MERCADOPAGO_TEST_WEBHOOK_SECRET }}"),
                "TEST signing secret must come from its separate private Actions secret");
        assertTrue(workflow.contains("if [[ -z \"" + "$"
                + "{MERCADOPAGO_TEST_WEBHOOK_SECRET:-}\" ]]; then"),
                "Missing TEST signing secret must fail before the provider call");
        assertTrue(workflow.contains("Missing repository secret MERCADOPAGO_TEST_WEBHOOK_SECRET"),
                "Missing secret diagnostics must not reveal its value");
        assertFalse(workflow.contains("secrets.MERCADOPAGO_WEBHOOK_SECRET }}"),
                "Never use the production signing secret in the TEST workflow");
        int preflight = workflow.indexOf("name: Fail closed unless this is an explicit TEST run");
        int providerRun = workflow.indexOf("name: Run real Mercado Pago sandbox provider certification");
        assertTrue(preflight >= 0 && providerRun > preflight,
                "Manual and secret preflight must precede external provider calls");
    }
}

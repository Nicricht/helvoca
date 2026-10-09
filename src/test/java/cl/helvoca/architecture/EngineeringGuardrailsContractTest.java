package cl.helvoca.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class EngineeringGuardrailsContractTest {

    private static String read(String relativePath) throws IOException {
        return Files.readString(Path.of(relativePath));
    }

    @Test
    void repositoryGuardrailsRequireFirstPassEngineeringAndFreshEvidence() throws Exception {
        String agents = read("AGENTS.md");
        assertTrue(agents.contains("First-Pass Engineering"));
        assertTrue(agents.contains("software-factory/skill/first-pass-engineering/SKILL.md"));
        assertTrue(agents.contains("100% differential line, branch and method coverage"));
        assertTrue(agents.contains("100% mapped interaction/state-transition coverage"));
        assertTrue(agents.contains("100% mapped real-PostgreSQL invariant coverage"));
        assertTrue(agents.toLowerCase().contains("final commit"));
        assertTrue(agents.contains("Never develop directly on `main`"));
        assertTrue(agents.toLowerCase().contains("regression tests for every bug fixed"));
    }

    @Test
    void fastGateRunsSoftwareFactoryContractTestsWhenFactoryChanges() throws Exception {
        String fastGate = read("scripts/ci/fast-gate.sh");
        assertTrue(fastGate.contains("python3 -m unittest discover -s software-factory/tests -p 'test_*.py' -v"));
    }

    @Test
    void projectInvariantsProtectCriticalCommercialAndTenantTruths() throws Exception {
        String invariants = read("docs/engineering/invariants.md").toLowerCase();
        assertTrue(invariants.contains("tenant"));
        assertTrue(invariants.contains("simulator"));
        assertTrue(invariants.contains("external side effect"));
        assertTrue(invariants.contains("payment"));
        assertTrue(invariants.contains("duplicate"));
        assertTrue(invariants.contains("order"));
    }
}

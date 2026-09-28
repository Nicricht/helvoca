package cl.helvoca.governance;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineeringOperatingSystemContractTest {

    @Test
    void repositoryDefinesMandatoryEngineeringOperatingSystemForFutureAgents() throws IOException {
        String agents = Files.readString(Path.of("AGENTS.md"));
        Path playbookPath = Path.of("docs", "RECEPVOZ_ENGINEERING_OPERATING_SYSTEM.md");

        assertTrue(Files.exists(playbookPath),
                "The RecepVoz engineering operating system must exist for future agents.");

        String playbook = Files.readString(playbookPath);

        assertTrue(agents.contains("RECEPVOZ_ENGINEERING_OPERATING_SYSTEM.md"),
                "AGENTS.md must direct every implementation agent to the engineering operating system.");
        assertTrue(agents.contains("developer + QA"),
                "AGENTS.md must require the agent to act as developer and QA.");

        assertTrue(playbook.contains("Product / Functional Analysis"));
        assertTrue(playbook.contains("Architecture / Backend / Data"));
        assertTrue(playbook.contains("UX / UI / Accessibility"));
        assertTrue(playbook.contains("AI / Voice / Telephony"));
        assertTrue(playbook.contains("Security / Privacy / Multi-tenant"));
        assertTrue(playbook.contains("QA / SDET / Adversarial Testing"));
        assertTrue(playbook.contains("DevOps / SRE / Release"));

        assertTrue(playbook.contains("RED -> GREEN -> REFACTOR"));
        assertTrue(playbook.contains("Fast Gate"));
        assertTrue(playbook.contains("JaCoCo"));
        assertTrue(playbook.contains("80%"));
        assertTrue(playbook.contains("70%"));
        assertTrue(playbook.contains("Playwright"));
        assertTrue(playbook.contains("Golden Journey"));
        assertTrue(playbook.contains("final commit"));
        assertTrue(playbook.contains("No completion claim without fresh evidence"));
        assertTrue(playbook.contains("Never develop directly on main"));
        assertTrue(playbook.contains("No merge or deploy without explicit authorization"));
    }
}

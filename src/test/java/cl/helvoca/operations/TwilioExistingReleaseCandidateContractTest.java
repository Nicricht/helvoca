package cl.helvoca.operations;

import cl.helvoca.phone.TwilioExistingEnrollmentAdminController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TwilioExistingReleaseCandidateContractTest {

    @Test
    void releaseCandidateIsDocumentedAsNoGoUntilDistinctProductionApproval() throws Exception {
        String plan = Files.readString(Path.of(
                "docs/telephony/2026-10-12-legacy-twilio-reconciliation-phase10-release-candidate.md"));
        for (String invariant : new String[]{
                "PRODUCTION / COMMERCIAL: NO-GO",
                "PR #790",
                "PR #791",
                "DIVERGED_FROM_MAIN",
                "VERIFIED_EXISTING",
                "MANAGED_PURCHASE",
                "TWILIO_CERTIFICATION_CALL_ON_STARTUP",
                "GEMINI_LIVE_PROBE_ON_STARTUP",
                "HELVOCA_CONTROLLED_PILOT_EXTERNAL_EFFECTS_ENABLED",
                "NOT_PILOT_MANAGED",
                "read-only carrier",
                "rollback",
                "maximum authorized cost",
                "explicit approval",
                "7/7"
        }) {
            assertTrue(plan.contains(invariant), "Release gate missing: " + invariant);
        }
        assertFalse(plan.contains("PRODUCTION / COMMERCIAL: GO"),
                "Code CI must never self-issue production approval");
    }

    @Test
    void allNetworkCapableDefaultsRemainOffWithoutExplicitOperatorAction() throws Exception {
        var source = new YamlPropertySourceLoader().load(
                "phase10-safe-defaults", new ClassPathResource("application.yml")).getFirst();
        assertEquals("${TWILIO_PROVISIONING_ENABLED:false}",
                source.getProperty("app.twilio.provisioning-enabled"));
        assertEquals("${TWILIO_CERTIFICATION_INGRESS_ENABLED:false}",
                source.getProperty("app.twilio.certification-ingress-enabled"));
        assertEquals("${GEMINI_LIVE_ENABLED:false}",
                source.getProperty("app.gemini.live.enabled"));
        assertEquals("${GEMINI_LIVE_MODEL:gemini-3.8-live}",
                source.getProperty("app.gemini.live.model"));

        String twilioRunner = Files.readString(Path.of(
                "src/main/java/cl/helvoca/telephony/twilio/TwilioCertificationStartupRunGate.java"));
        String geminiProbe = Files.readString(Path.of(
                "src/main/java/cl/helvoca/ai/gemini/GeminiLiveStartupProbe.java"));
        assertTrue(twilioRunner.contains("TWILIO_CERTIFICATION_CALL_ON_STARTUP:false"));
        assertTrue(geminiProbe.contains("GEMINI_LIVE_PROBE_ON_STARTUP:false"));
    }

    @Test
    void importedPhoneOnlyEligibleBehindNewIndependentFailClosedVoiceGate() throws Exception {
        String binding = Files.readString(Path.of(
                "src/main/java/cl/helvoca/telephony/twilio/TwilioCallBindingGuard.java"));
        String guide = Files.readString(Path.of(
                "src/main/java/cl/helvoca/onboarding/VoiceSetupReadinessService.java"));
        assertTrue(binding.contains("\"MANAGED_PURCHASE\".equals(phone.getTwilioBindingSource())"));
        assertTrue(binding.contains("\"VERIFIED_EXISTING\""));
        assertTrue(binding.contains("existingRoute.test(phone)"));
        String route = Files.readString(Path.of(
                "src/main/java/cl/helvoca/telephony/twilio/TwilioVerifiedExistingVoiceRouteGate.java"));
        assertTrue(route.contains("existing-voice-route-enabled:false"));
        assertTrue(route.contains("r.state = 'APPLIED'"));
        assertTrue(route.contains("c.requester_id <> c.approver_id"));
        assertTrue(route.contains("NOT_PILOT_MANAGED"));
        assertTrue(route.contains("a.action = 'EXECUTED'"));
        String application = Files.readString(Path.of("src/main/resources/application.yml"));
        assertTrue(application.contains("HELVOCA_TWILIO_EXISTING_VOICE_ROUTE_ENABLED:false"));
        assertTrue(guide.contains("\"MANAGED_PURCHASE\".equals(phone.getTwilioBindingSource())"));
        assertTrue(guide.contains("new Step(\"LIVE_CALL\", \"Prueba de llamada real autorizada\", false"));

        ConditionalOnProperty flag = TwilioExistingEnrollmentAdminController.class
                .getAnnotation(ConditionalOnProperty.class);
        assertNotNull(flag);
        assertEquals("true", flag.havingValue());
        assertFalse(flag.matchIfMissing());
        assertArrayEquals(new String[]{
                "app.telephony.twilio.existing-enrollment-admin-enabled"
        }, flag.name());
    }

    @Test
    void globalKillSwitchIsNotMisrepresentedAsProtectingAnUnregisteredTenant() {
        PilotLaunchControlRepository controls = mock(PilotLaunchControlRepository.class);
        UUID missingTenant = UUID.randomUUID();
        when(controls.findById(missingTenant)).thenReturn(Optional.empty());
        var guard = new ControlledPilotExternalEffectGuard(controls, false);
        var result = guard.evaluate(missingTenant, ControlledPilotExternalEffectGuard.Effect.VOICE);
        assertTrue(result.allowed(), "Existing behavior allows non-pilot tenants");
        assertFalse(result.pilotManaged());
        assertEquals("NOT_PILOT_MANAGED", result.code());
        verify(controls).findById(missingTenant);
    }

    @Test
    void registeredButPausedPilotNeverEnablesLiveVoice() {
        PilotLaunchControlRepository controls = mock(PilotLaunchControlRepository.class);
        UUID tenant = UUID.randomUUID();
        PilotLaunchControl record = new PilotLaunchControl();
        record.setBusinessId(tenant);
        record.setStatus(PilotLaunchControl.Status.PAUSED);
        when(controls.findById(tenant)).thenReturn(Optional.of(record));
        var guard = new ControlledPilotExternalEffectGuard(controls, true);
        var result = guard.evaluate(tenant, ControlledPilotExternalEffectGuard.Effect.VOICE);
        assertFalse(result.allowed());
        assertTrue(result.pilotManaged());
        assertEquals("PILOT_NOT_RUNNING", result.code());
    }
}

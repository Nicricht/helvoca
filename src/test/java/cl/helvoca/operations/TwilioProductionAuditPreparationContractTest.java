package cl.helvoca.operations;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 3: strictly offline source-contract checks.
 * Reads only checked-in files; makes zero network, carrier, DB or credential calls.
 * These tests do not establish production ownership or permission to call.
 */
class TwilioProductionAuditPreparationContractTest {
    private static final String PLAN =
            "docs/telephony/2026-10-12-phase3-twilio-railway-read-only-audit-plan.md";

    private static String source(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    @Test
    void planMakesProductionAndCommercialNoGoExplicitAndRequiresDistinctApprovals() throws Exception {
        String plan = source(PLAN);
        for (String requirement : new String[] {
                "PRODUCTION / COMMERCIAL: NO-GO",
                "A0: offline repository",
                "A1-T: current Twilio ownership",
                "A1-D: current Railway PostgreSQL",
                "A1-C: deployed non-secret",
                "A2: enroll/reconcile",
                "A3: a real telephone call",
                "NOT AUTHORIZED",
                "No Twilio, Railway, PostgreSQL, Gemini or external provider request is authorized",
                "Do not begin Part 4 automatically.",
                "Full exact-HEAD 7/7 certification remains **PENDING**"
        }) {
            assertTrue(plan.contains(requirement), "Missing explicit approval gate: " + requirement);
        }
        assertFalse(plan.contains("PRODUCTION / COMMERCIAL: GO"));
    }

    @Test
    void readOnlyDatabaseOutlineCannotAccidentallyBeTreatedAsMutation() throws Exception {
        String plan = source(PLAN);
        String[] segments = plan.split("```sql", -1);
        assertEquals(2, segments.length, "Audit must include one explicitly reviewed SQL outline");
        String sql = segments[1].split("```", 2)[0];
        assertTrue(sql.contains("BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY"));
        assertTrue(sql.contains("SHOW transaction_read_only"));
        assertTrue(sql.contains("FROM public.phone_number"));
        assertTrue(sql.contains("FROM public.pilot_launch_control"));
        assertTrue(sql.contains("business_id = $1::uuid AND phone_number = $2::text"));
        assertTrue(sql.contains("ROLLBACK"));
        assertFalse(Pattern.compile("\\b(INSERT|UPDATE|DELETE|ALTER|TRUNCATE|DROP|CREATE|GRANT|REVOKE|COMMIT|COPY)\\b",
                Pattern.CASE_INSENSITIVE).matcher(sql).find(),
                "Even the future authorized SQL outline must not be write-capable");
        assertTrue(plan.contains("do not auto-run Flyway in production"));
        assertTrue(plan.contains("does not itself grant or guarantee RLS isolation"));
    }

    @Test
    void carrierEvidenceRequirementsAreGroundedInExistingGetOnlyVerifier() throws Exception {
        String verifier = source(
                "src/main/java/cl/helvoca/telephony/twilio/TwilioExistingNumberOwnershipVerifier.java");
        String plan = source(PLAN);
        assertTrue(verifier.contains("client.get()"));
        assertFalse(verifier.contains("client.post("));
        assertFalse(verifier.contains("client.put("));
        assertFalse(verifier.contains("client.delete("));
        for (String required : new String[] {
                "next_page_uri", "incoming_phone_numbers", "voice_receive_mode",
                "voice_url", "voice_method", "status_callback", "status_callback_method",
                "voice_application_sid", "trunk_sid", "owner_account_sid"
        }) {
            assertTrue(verifier.contains(required), "Missing verifier field: " + required);
        }
        assertTrue(plan.contains("both list and detail"));
        assertTrue(plan.contains("PHONE_IDENTITY_MISMATCH"));
        assertTrue(plan.contains("WEBHOOK_MISMATCH"));
        assertTrue(plan.contains("not a record that a real carrier or database audit occurred"));
    }

    @Test
    void importedNumberAndUnregisteredPilotRemainFailClosedForAuditDecision() throws Exception {
        String guard = source("src/main/java/cl/helvoca/telephony/twilio/TwilioCallBindingGuard.java");
        String pilot = source("src/main/java/cl/helvoca/operations/ControlledPilotExternalEffectGuard.java");
        String plan = source(PLAN);
        assertTrue(guard.contains("\"MANAGED_PURCHASE\".equals(phone.getTwilioBindingSource())"));
        assertTrue(guard.contains("\"VERIFIED_EXISTING\""));
        assertTrue(guard.contains("existingRoute.test(phone)"));
        String route = source("src/main/java/cl/helvoca/telephony/twilio/TwilioVerifiedExistingVoiceRouteGate.java");
        assertTrue(route.contains("existing-voice-route-enabled:false"));
        assertTrue(route.contains("r.state = 'APPLIED'"));
        assertTrue(route.contains("c.requester_id <> c.approver_id"));
        assertTrue(route.contains("NOT_PILOT_MANAGED"));
        assertTrue(pilot.contains("new Decision(true, false, \"NOT_PILOT_MANAGED\""));
        assertTrue(plan.contains("NOT_PILOT_MANAGED"));
        assertTrue(plan.contains("PAUSED"));
        assertTrue(plan.contains("LIVE_EVIDENCE_PENDING"));
        assertTrue(plan.contains("never buy a replacement"));
        String migration = source(
                "src/main/resources/db/migration/V101__twilio_verified_existing_binding_provenance.sql");
        assertTrue(migration.contains("VERIFIED_EXISTING"));
        assertFalse(migration.toUpperCase(Locale.ROOT).contains("UPDATE PHONE_NUMBER"));
    }
}

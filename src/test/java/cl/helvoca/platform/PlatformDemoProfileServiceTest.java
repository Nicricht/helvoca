package cl.helvoca.platform;

import cl.helvoca.audit.AuditService;
import cl.helvoca.common.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlatformDemoProfileServiceTest {

    @Test
    void createsReusableDemoProfileWithoutProviderCredentials() {
        DemoProfileRepository profiles = mock(DemoProfileRepository.class);
        AuditService audit = mock(AuditService.class);
        PlatformDemoProfileService service = new PlatformDemoProfileService(profiles, audit);

        UUID id = UUID.randomUUID();
        when(profiles.saveAndFlush(any(DemoProfile.class))).thenAnswer(invocation -> {
            DemoProfile profile = invocation.getArgument(0);
            ReflectionTestUtils.setField(profile, "id", id);
            ReflectionTestUtils.setField(profile, "createdAt", Instant.parse("2026-10-01T05:00:00Z"));
            ReflectionTestUtils.setField(profile, "updatedAt", Instant.parse("2026-10-01T05:00:00Z"));
            return profile;
        });

        PlatformDemoProfileResponse created = service.create(request(
                " Sushi Akira ",
                " Sushi Akira ",
                "ES",
                List.of("order", "DELIVERY", "order", " ")));

        assertEquals(id, created.id());
        assertEquals("Sushi Akira", created.displayName());
        assertEquals("Sushi Akira", created.businessName());
        assertEquals("es", created.language());
        assertEquals("America/Santiago", created.timezone());
        assertEquals(List.of("ORDER", "DELIVERY"), created.capabilities());
        assertNotNull(created.catalog());
        verify(audit).platformHumanSuccess(
                isNull(),
                eq("DEMO_PROFILE_CREATE"),
                eq("DEMO_PROFILE"),
                eq(id),
                isNull(),
                argThat(after -> "Sushi Akira".equals(after.get("displayName"))
                        && "Sushi Akira".equals(after.get("businessName"))));
    }

    @Test
    void listsUpdatesAndDeletesProfilesWithAuditedPlatformMutations() {
        DemoProfileRepository profiles = mock(DemoProfileRepository.class);
        AuditService audit = mock(AuditService.class);
        PlatformDemoProfileService service = new PlatformDemoProfileService(profiles, audit);
        DemoProfile profile = profile("Demo antigua", "Negocio antiguo");

        when(profiles.findAllByOrderByUpdatedAtDesc()).thenReturn(List.of(profile));
        when(profiles.findById(profile.getId())).thenReturn(java.util.Optional.of(profile));
        when(profiles.saveAndFlush(profile)).thenAnswer(invocation -> invocation.getArgument(0));

        List<PlatformDemoProfileResponse> listed = service.list();
        assertEquals(1, listed.size());
        assertEquals("Demo antigua", listed.getFirst().displayName());

        PlatformDemoProfileResponse updated = service.update(
                profile.getId(),
                request("Demo nueva", "Negocio nuevo", "es", List.of("booking")));

        assertEquals("Demo nueva", updated.displayName());
        assertEquals("Negocio nuevo", updated.businessName());
        assertEquals(List.of("BOOKING"), updated.capabilities());
        verify(audit).platformHumanSuccess(
                isNull(),
                eq("DEMO_PROFILE_UPDATE"),
                eq("DEMO_PROFILE"),
                eq(profile.getId()),
                argThat(before -> "Demo antigua".equals(before.get("displayName"))),
                argThat(after -> "Demo nueva".equals(after.get("displayName"))));

        service.delete(profile.getId());
        verify(profiles).delete(profile);
        verify(audit).platformHumanSuccess(
                isNull(),
                eq("DEMO_PROFILE_DELETE"),
                eq("DEMO_PROFILE"),
                eq(profile.getId()),
                argThat(before -> "Demo nueva".equals(before.get("displayName"))),
                isNull());
    }

    @Test
    void rejectsInvalidTimezoneAndProviderSecretsBeforePersistence() {
        DemoProfileRepository profiles = mock(DemoProfileRepository.class);
        AuditService audit = mock(AuditService.class);
        PlatformDemoProfileService service = new PlatformDemoProfileService(profiles, audit);

        PlatformDemoProfileRequest invalidTimezone = new PlatformDemoProfileRequest(
                "Demo", "Demo", "Mars/Olympus", "es",
                Map.of(), Map.of(), Map.of(), "Hola", null,
                List.of("REQUEST"), null, Map.of());

        assertThrows(IllegalArgumentException.class, () -> service.create(invalidTimezone));

        PlatformDemoProfileRequest secret = new PlatformDemoProfileRequest(
                "Demo", "Demo", "America/Santiago", "es",
                Map.of("provider", List.of(
                        Map.of("api_key", "never-store-me"),
                        Map.of("providerToken", "also-never-store-me"))),
                Map.of(), Map.of(), "Hola", null,
                List.of("REQUEST"), null, Map.of());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.create(secret));
        assertTrue(error.getMessage().contains("provider credentials"));
        verifyNoInteractions(profiles, audit);
    }

    @Test
    void missingProfileFailsClosedForUpdateAndDelete() {
        DemoProfileRepository profiles = mock(DemoProfileRepository.class);
        AuditService audit = mock(AuditService.class);
        PlatformDemoProfileService service = new PlatformDemoProfileService(profiles, audit);
        UUID missing = UUID.randomUUID();
        when(profiles.findById(missing)).thenReturn(java.util.Optional.empty());

        assertThrows(NotFoundException.class, () ->
                service.update(missing, request("Demo", "Demo", "es", List.of())));
        assertThrows(NotFoundException.class, () -> service.delete(missing));
        verify(audit, never()).platformHumanSuccess(any(), any(), any(), any(), any(), any());
    }

    private static PlatformDemoProfileRequest request(
            String displayName,
            String businessName,
            String language,
            List<String> capabilities) {
        return new PlatformDemoProfileRequest(
                displayName,
                businessName,
                "America/Santiago",
                language,
                Map.of("items", List.of(Map.of("name", "Sake", "price", 4990))),
                Map.of("monday", "12:00-22:00"),
                Map.of("faq", List.of("Despacho disponible")),
                "Hola, soy la asistente.",
                "No inventes productos ni precios.",
                capabilities,
                "Haz que el prospecto llame desde su teléfono.",
                Map.of("source", "manual"));
    }

    private static DemoProfile profile(String displayName, String businessName) {
        DemoProfile profile = new DemoProfile();
        UUID id = UUID.randomUUID();
        ReflectionTestUtils.setField(profile, "id", id);
        ReflectionTestUtils.setField(profile, "createdAt", Instant.parse("2026-10-01T04:00:00Z"));
        ReflectionTestUtils.setField(profile, "updatedAt", Instant.parse("2026-10-01T04:00:00Z"));
        profile.setDisplayName(displayName);
        profile.setBusinessName(businessName);
        profile.setTimezone("America/Santiago");
        profile.setLanguage("es");
        profile.setCatalog(Map.of());
        profile.setHours(Map.of());
        profile.setKnowledge(Map.of());
        profile.setGreeting("Hola");
        profile.setCapabilities(List.of("ORDER"));
        profile.setSourceMetadata(Map.of("source", "template"));
        return profile;
    }
}

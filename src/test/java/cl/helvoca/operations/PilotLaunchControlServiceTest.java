package cl.helvoca.operations;

import cl.helvoca.onboarding.PilotActivationChecklistService;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PilotLaunchControlServiceTest {
    @Mock PilotLaunchControlRepository controls;
    @Mock PilotReadinessService readiness;
    @Mock PilotActivationChecklistService activationChecklist;
    @Mock TenantProvider tenantProvider;

    private PilotLaunchControlService service;
    private UUID businessId;

    @BeforeEach
    void setUp() {
        businessId = UUID.randomUUID();
        when(tenantProvider.requireBusinessId()).thenReturn(businessId);
        lenient().when(activationChecklist.current()).thenReturn(activation(true));
        service = new PilotLaunchControlService(controls, readiness, activationChecklist, tenantProvider);
    }

    @Test
    void configureBecomesReadyOnlyWhenTechnicalReadinessAndRequiredFieldsAreComplete() {
        when(controls.findById(businessId)).thenReturn(Optional.empty());
        when(readiness.readiness()).thenReturn(new PilotReadinessService.Readiness(
                true, 5, 5, List.of(), List.of(), "gemini", "mercadopago", "SANDBOX"));
        when(controls.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PilotLaunchControlService.View result = service.configure(
                new PilotLaunchControlService.ConfigureRequest(
                        "Carla Pérez",
                        "+56911112222",
                        "Reducir llamadas perdidas y convertir reservas",
                        Instant.parse("2026-10-10T03:00:00Z")));

        assertEquals("READY", result.status());
        assertEquals("GO", result.launchDecision());
        assertTrue(result.canStart());
        assertTrue(result.technicalReady());
        assertTrue(result.configurationComplete());
        verify(controls).save(argThat(control ->
                control.getBusinessId().equals(businessId)
                        && control.getStatus() == PilotLaunchControl.Status.READY
                        && "Carla Pérez".equals(control.getResponsibleName())));
    }

    @Test
    void startFailsClosedWhenPilotReadinessHasBlockers() {
        PilotLaunchControl control = configured(PilotLaunchControl.Status.READY);
        when(controls.findById(businessId)).thenReturn(Optional.of(control));
        when(readiness.readiness()).thenReturn(new PilotReadinessService.Readiness(
                false, 4, 5, List.of(), List.of("Mercado Pago"), "gemini", "mercadopago", "SANDBOX"));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                service::start);

        assertEquals(409, error.getStatusCode().value());
        assertEquals(PilotLaunchControl.Status.READY, control.getStatus());
        verify(controls, never()).save(any());
    }

    @Test
    void startFailsClosedWhenExternalPilotActivationChecklistIsIncomplete() {
        PilotLaunchControl control = configured(PilotLaunchControl.Status.READY);
        when(controls.findById(businessId)).thenReturn(Optional.of(control));
        when(readiness.readiness()).thenReturn(new PilotReadinessService.Readiness(
                true, 5, 5, List.of(), List.of(), "gemini", "mercadopago", "SANDBOX"));
        when(activationChecklist.current()).thenReturn(activation(false));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                service::start);

        assertEquals(409, error.getStatusCode().value());
        assertEquals(PilotLaunchControl.Status.READY, control.getStatus());
        verify(controls, never()).save(any());
    }

    @Test
    void currentHidesStartAndReportsActivationBlockerUntilChecklistIsComplete() {
        PilotLaunchControl control = configured(PilotLaunchControl.Status.READY);
        when(controls.findById(businessId)).thenReturn(Optional.of(control));
        when(readiness.readiness()).thenReturn(new PilotReadinessService.Readiness(
                true, 5, 5, List.of(), List.of(), "gemini", "mercadopago", "SANDBOX"));
        when(activationChecklist.current()).thenReturn(activation(false));

        PilotLaunchControlService.View result = service.current();

        assertEquals("NO_GO", result.launchDecision());
        assertFalse(result.canStart());
        assertTrue(result.blockers().contains("Checklist de activación del piloto"));
    }

    @Test
    void resumeFailsClosedWhenActivationChecklistIsNoLongerComplete() {
        PilotLaunchControl control = configured(PilotLaunchControl.Status.PAUSED);
        when(controls.findById(businessId)).thenReturn(Optional.of(control));
        when(readiness.readiness()).thenReturn(new PilotReadinessService.Readiness(
                true, 5, 5, List.of(), List.of(), "gemini", "mercadopago", "SANDBOX"));
        when(activationChecklist.current()).thenReturn(activation(false));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                service::resume);

        assertEquals(409, error.getStatusCode().value());
        assertEquals(PilotLaunchControl.Status.PAUSED, control.getStatus());
        verify(controls, never()).save(any());
    }

    @Test
    void startPauseResumeAndCompletePreserveSinglePilotRecord() {
        PilotLaunchControl control = configured(PilotLaunchControl.Status.READY);
        when(controls.findById(businessId)).thenReturn(Optional.of(control));
        when(readiness.readiness()).thenReturn(new PilotReadinessService.Readiness(
                true, 5, 5, List.of(), List.of(), "gemini", "mercadopago", "SANDBOX"));
        when(controls.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PilotLaunchControlService.View running = service.start();
        assertEquals("RUNNING", running.status());
        assertNotNull(control.getStartedAt());

        PilotLaunchControlService.View paused = service.pause();
        assertEquals("PAUSED", paused.status());

        PilotLaunchControlService.View resumed = service.resume();
        assertEquals("RUNNING", resumed.status());

        PilotLaunchControlService.View completed = service.complete();
        assertEquals("COMPLETED", completed.status());
        assertNotNull(control.getCompletedAt());
        assertFalse(completed.canStart());
        verify(controls, times(4)).save(same(control));
    }

    private static PilotActivationChecklistService.View activation(boolean ready) {
        return new PilotActivationChecklistService.View(
                ready,
                ready ? 10 : 9,
                10,
                ready ? 100 : 90,
                ready ? List.of() : List.of("CONVERSATION_TEST_REQUIRED"),
                List.of());
    }

    private PilotLaunchControl configured(PilotLaunchControl.Status status) {
        PilotLaunchControl control = new PilotLaunchControl();
        control.setBusinessId(businessId);
        control.setStatus(status);
        control.setResponsibleName("Carla Pérez");
        control.setResponsibleContact("+56911112222");
        control.setGoal("Reducir llamadas perdidas y convertir reservas");
        control.setPlannedEndAt(Instant.parse("2026-10-10T03:00:00Z"));
        return control;
    }
}

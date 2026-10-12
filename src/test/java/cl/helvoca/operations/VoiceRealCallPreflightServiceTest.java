package cl.helvoca.operations;

import cl.helvoca.phone.PhoneNumber;
import cl.helvoca.phone.PhoneNumberRepository;
import cl.helvoca.security.TenantProvider;
import cl.helvoca.telephony.twilio.TwilioLiveCallAdmissionReadiness;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VoiceRealCallPreflightServiceTest {
    private static final String PHONE = "+14355652512";
    private static final String PN = "PN62601c6420599e74bc940f42de2dd1e4";

    private final UUID tenantId = UUID.randomUUID();
    private TenantProvider tenants;
    private PhoneNumberRepository numbers;
    private ChannelRuntimeReadinessService runtime;
    private ControlledPilotExternalEffectGuard pilot;
    private VoiceRealCallPreflightService service;
    private TwilioLiveCallAdmissionReadiness carrierAdmission;

    @BeforeEach
    void init() {
        tenants = mock(TenantProvider.class);
        numbers = mock(PhoneNumberRepository.class);
        runtime = mock(ChannelRuntimeReadinessService.class);
        pilot = mock(ControlledPilotExternalEffectGuard.class);
        carrierAdmission = mock(TwilioLiveCallAdmissionReadiness.class);
        when(carrierAdmission.locallyAllowed(any(PhoneNumber.class))).thenReturn(true);
        when(tenants.requireBusinessId()).thenReturn(tenantId);
        when(numbers.findAllByBusinessIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(phone()));
        when(runtime.snapshot()).thenReturn(readiness(true, true, true, "gemini", true, true));
        when(pilot.evaluate(tenantId, ControlledPilotExternalEffectGuard.Effect.VOICE))
                .thenReturn(decision(true, true));
        service = new VoiceRealCallPreflightService(tenants, numbers, runtime, pilot, carrierAdmission);
    }

    @Test
    void successfulLocalPreflightIsNeverLiveCertificationOrCommercialGo() throws Exception {
        var result = service.inspect(PHONE);
        assertEquals("LOCAL_PREFLIGHT_PASSED", result.state());
        assertTrue(result.localPreflightPassed());
        assertTrue(result.blockers().isEmpty());
        assertFalse(result.liveCallCertified());
        assertFalse(result.commercialGo());
        assertTrue(VoiceRealCallPreflightService.class
                .getMethod("inspect", String.class).isAnnotationPresent(Transactional.class));
        assertTrue(VoiceRealCallPreflightService.class
                .getMethod("inspect", String.class).getAnnotation(Transactional.class).readOnly());
        verify(numbers).findAllByBusinessIdOrderByCreatedAtDesc(tenantId);
    }

    @Test
    void malformedOrMissingPhoneCannotPassEvenWhenEverythingElseIsReady() {
        for (String invalid : new String[]{"14355652512", "+123", "+99999999999999999", ""}) {
            assertBlocked(service.inspect(invalid), "INVALID_DESTINATION_E164", "TENANT_TWILIO_PHONE_NOT_ACTIVE",
                    "TWILIO_PHONE_SID_NOT_BOUND");
        }
        assertBlocked(service.inspect(null), "INVALID_DESTINATION_E164");
        verify(numbers, never()).save(any());
    }

    @Test
    void neverAcceptsAnotherTenantsNumber() {
        when(numbers.findAllByBusinessIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of());
        assertBlocked(service.inspect(PHONE), "TENANT_TWILIO_PHONE_NOT_ACTIVE", "TWILIO_PHONE_SID_NOT_BOUND");
    }

    @Test
    void rejectsInactiveNumberAndDifferentProvider() {
        PhoneNumber inactive = phone();
        inactive.setActive(false);
        when(numbers.findAllByBusinessIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(inactive));
        assertBlocked(service.inspect(PHONE), "TENANT_TWILIO_PHONE_NOT_ACTIVE");
        PhoneNumber wrongProvider = phone();
        wrongProvider.setProvider("TWILIO_WHATSAPP");
        when(numbers.findAllByBusinessIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(wrongProvider));
        assertBlocked(service.inspect(PHONE), "TENANT_TWILIO_PHONE_NOT_ACTIVE");
    }

    @Test
    void rejectsMissingAndMalformedPhoneSid() {
        PhoneNumber number = phone();
        number.setExternalId(null);
        when(numbers.findAllByBusinessIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(number));
        assertBlocked(service.inspect(PHONE), "TWILIO_PHONE_SID_NOT_BOUND");
        number.setExternalId("TWILIO_TRIAL");
        assertBlocked(service.inspect(PHONE), "TWILIO_PHONE_SID_NOT_BOUND");
        number.setExternalId("PN62601c6420599e74bc940f42de2dd1e4");
        assertEquals("LOCAL_PREFLIGHT_PASSED", service.inspect(PHONE).state());
    }

    @Test
    void failsWhenTwilioCredentialHttpsOrMediaStreamIsNotReady() {
        when(runtime.snapshot()).thenReturn(readiness(false, true, true, "gemini", true, true));
        assertBlocked(service.inspect(PHONE), "TWILIO_RUNTIME_NOT_READY");
        when(runtime.snapshot()).thenReturn(readiness(true, false, true, "gemini", true, true));
        assertBlocked(service.inspect(PHONE), "TWILIO_RUNTIME_NOT_READY");
        when(runtime.snapshot()).thenReturn(readiness(true, true, false, "gemini", true, true));
        assertBlocked(service.inspect(PHONE), "TWILIO_RUNTIME_NOT_READY");
    }

    @Test
    void geminiMustBeTheActuallyAvailableSelectedMediaProvider() {
        when(runtime.snapshot()).thenReturn(readiness(true, true, true, "openai-live", true, true));
        assertBlocked(service.inspect(PHONE), "GEMINI_MEDIA_PROVIDER_NOT_READY");
        when(runtime.snapshot()).thenReturn(readiness(true, true, true, "gemini", false, true));
        assertBlocked(service.inspect(PHONE), "GEMINI_MEDIA_PROVIDER_NOT_READY");
        when(runtime.snapshot()).thenReturn(readiness(true, true, true, "gemini", true, false));
        assertBlocked(service.inspect(PHONE), "GEMINI_MEDIA_PROVIDER_NOT_READY");
        var missing = readiness(true, true, true, "gemini", true, true);
        when(runtime.snapshot()).thenReturn(new ChannelRuntimeReadinessService.ChannelRuntimeReadiness(
                missing.twilio(),
                new ChannelRuntimeReadinessService.VoiceRuntimeReadiness(true, "gemini", "READY", List.of()),
                missing.whatsApp()));
        assertBlocked(service.inspect(PHONE), "GEMINI_MEDIA_PROVIDER_NOT_READY");
    }

    @Test
    void unavailableVoiceAndUnmanagedPilotFailClosedEvenWhenCredentialsExist() {
        var ready = readiness(true, true, true, "gemini", true, true);
        when(runtime.snapshot()).thenReturn(new ChannelRuntimeReadinessService.ChannelRuntimeReadiness(
                ready.twilio(),
                new ChannelRuntimeReadinessService.VoiceRuntimeReadiness(false, "gemini", "NO_AVAILABLE_PROVIDER",
                        ready.voice().providers()),
                ready.whatsApp()));
        when(pilot.evaluate(tenantId, ControlledPilotExternalEffectGuard.Effect.VOICE))
                .thenReturn(decision(false, true));
        assertBlocked(service.inspect(PHONE), "GEMINI_MEDIA_PROVIDER_NOT_READY", "TENANT_NOT_PILOT_ENROLLED");
    }

    @Test
    void validPhoneSidWithoutCertifiedCarrierBindingMustFailClosed() {
        when(carrierAdmission.locallyAllowed(any(PhoneNumber.class))).thenReturn(false);
        assertBlocked(service.inspect(PHONE), "TWILIO_VOICE_BINDING_NOT_CERTIFIED");
        verify(carrierAdmission).locallyAllowed(any(PhoneNumber.class));
    }

    @Test
    void disabledOrPausedPilotsBlockCarrierRouting() {
        when(pilot.evaluate(tenantId, ControlledPilotExternalEffectGuard.Effect.VOICE))
                .thenReturn(decision(true, false));
        assertBlocked(service.inspect(PHONE), "PILOT_VOICE_EFFECT_BLOCKED");
        when(pilot.evaluate(tenantId, ControlledPilotExternalEffectGuard.Effect.VOICE))
                .thenReturn(decision(false, false));
        assertBlocked(service.inspect(PHONE), "TENANT_NOT_PILOT_ENROLLED", "PILOT_VOICE_EFFECT_BLOCKED");
    }

    private static void assertBlocked(VoiceRealCallPreflightService.Result result, String... expected) {
        assertEquals("BLOCKED", result.state());
        assertFalse(result.localPreflightPassed());
        assertFalse(result.liveCallCertified());
        assertFalse(result.commercialGo());
        assertTrue(result.blockers().containsAll(List.of(expected)), () -> result.blockers().toString());
    }

    private PhoneNumber phone() {
        PhoneNumber phone = new PhoneNumber();
        phone.setPhoneNumber(PHONE);
        phone.setBusinessId(tenantId);
        phone.setExternalId(PN);
        phone.setProvider("TWILIO");
        phone.setActive(true);
        return phone;
    }

    private static ControlledPilotExternalEffectGuard.Decision decision(boolean managed, boolean allowed) {
        return new ControlledPilotExternalEffectGuard.Decision(allowed, managed,
                allowed ? "ALLOWED" : "PILOT_NOT_RUNNING", "RUNNING",
                ControlledPilotExternalEffectGuard.Effect.VOICE);
    }

    private static ChannelRuntimeReadinessService.ChannelRuntimeReadiness readiness(
            boolean credentials, boolean https, boolean stream, String selected,
            boolean configured, boolean available) {
        return new ChannelRuntimeReadinessService.ChannelRuntimeReadiness(
                new ChannelRuntimeReadinessService.TwilioRuntimeReadiness(
                        credentials, https, stream, credentials && https, "READY"),
                new ChannelRuntimeReadinessService.VoiceRuntimeReadiness(
                        true, selected, "READY", List.of(
                                new ChannelRuntimeReadinessService.ProviderRuntimeStatus(
                                        "gemini", "MEDIA_STREAM", configured, available, "CLOSED"))),
                new ChannelRuntimeReadinessService.WhatsAppRuntimeReadiness(false, false, false, "DISABLED"));
    }
}

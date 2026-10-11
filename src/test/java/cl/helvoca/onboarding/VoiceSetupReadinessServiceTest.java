package cl.helvoca.onboarding;

import cl.helvoca.agent.AiAgent;
import cl.helvoca.agent.AiAgentService;
import cl.helvoca.business.BusinessProfileResponse;
import cl.helvoca.business.BusinessProfileService;
import cl.helvoca.phone.PhoneNumber;
import cl.helvoca.phone.PhoneNumberRepository;
import cl.helvoca.security.TenantProvider;
import cl.helvoca.telephony.twilio.TwilioPhoneProvisioningClient;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VoiceSetupReadinessServiceTest {
    private static final String ACCOUNT_SID = "AC" + "a".repeat(32);
    private static final String PHONE_SID = "PN" + "b".repeat(32);

    private final UUID businessId = UUID.randomUUID();
    private final TenantProvider tenant = mock(TenantProvider.class);
    private final OnboardingService onboarding = mock(OnboardingService.class);
    private final BusinessProfileService profiles = mock(BusinessProfileService.class);
    private final AiAgentService agents = mock(AiAgentService.class);
    private final AiAgent agent = mock(AiAgent.class);
    private final PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
    private final TwilioPhoneProvisioningClient twilio = mock(TwilioPhoneProvisioningClient.class);
    private final VoiceSetupReadinessService service = new VoiceSetupReadinessService(
            tenant, onboarding, profiles, agents, phones, twilio);

    private void setupComplete() {
        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(onboarding.status()).thenReturn(new OnboardingStatusResponse(
                true, true, true, false, false, true, true, "READY"));
        when(profiles.current()).thenReturn(profile(true, true));
        when(agents.configuredCurrent()).thenReturn(true);
        when(agents.current()).thenReturn(agent);
        when(agent.isActive()).thenReturn(true);
        when(twilio.configured()).thenReturn(true);
        when(twilio.managedAccountSid()).thenReturn(ACCOUNT_SID);
        when(phones.findAllByBusinessIdOrderByCreatedAtDesc(businessId)).thenReturn(List.of(managed()));
    }

    @Test
    void configuredLocallyStillRequiresARealCallAndNeverClaimsProductionCertification() {
        setupComplete();
        var guide = service.current();

        assertEquals("AWAITING_LIVE_CERTIFICATION", guide.status());
        assertTrue(guide.configurationReady());
        assertFalse(guide.liveCertified());
        assertFalse(guide.readyForCommercialCalls());
        assertEquals("+14355652512", guide.activePhoneNumber());
        Map<String, Boolean> checks = guide.steps().stream().collect(Collectors.toMap(
                VoiceSetupReadinessService.Step::code, VoiceSetupReadinessService.Step::complete));
        assertTrue(checks.get("BUSINESS"));
        assertTrue(checks.get("SERVICES"));
        assertTrue(checks.get("SCHEDULE"));
        assertTrue(checks.get("AGENT"));
        assertTrue(checks.get("CARRIER"));
        assertTrue(checks.get("PHONE"));
        assertFalse(checks.get("LIVE_CALL"));
        verify(phones).findAllByBusinessIdOrderByCreatedAtDesc(businessId);
        verify(twilio, never()).verifyManagedPhone(anyString());
    }

    @Test
    void noPhoneOrProviderNeverMeansReadyAndNeverLeaksNumbers() {
        setupComplete();
        when(phones.findAllByBusinessIdOrderByCreatedAtDesc(businessId)).thenReturn(List.of());
        when(twilio.configured()).thenReturn(false);
        var guide = service.current();

        assertEquals("CONFIGURATION_INCOMPLETE", guide.status());
        assertFalse(guide.configurationReady());
        assertNull(guide.activePhoneNumber());
        assertFalse(step(guide, "CARRIER").complete());
        assertFalse(step(guide, "PHONE").complete());
        verify(twilio, never()).managedAccountSid();
    }

    @Test
    void businessWithoutServicesOrAppointmentsMaySkipTheirOptionalSteps() {
        setupComplete();
        when(profiles.current()).thenReturn(profile(false, false));
        when(onboarding.status()).thenReturn(new OnboardingStatusResponse(
                true, false, false, false, false, true, true, "READY"));
        var guide = service.current();

        assertTrue(guide.configurationReady());
        assertTrue(step(guide, "SERVICES").complete());
        assertTrue(step(guide, "SCHEDULE").complete());
    }

    @Test
    void defaultAndUnspecifiedBusinessProfilesRequireCatalogAndHours() {
        setupComplete();
        when(profiles.current()).thenReturn(null);
        when(onboarding.status()).thenReturn(new OnboardingStatusResponse(
                true, false, false, false, false, true, false, "ADD_SERVICE"));
        var unknown = service.current();
        assertFalse(step(unknown, "SERVICES").complete());
        assertFalse(step(unknown, "SCHEDULE").complete());

        when(profiles.current()).thenReturn(profile(null, null));
        var unspecified = service.current();
        assertFalse(step(unspecified, "SERVICES").complete());
        assertFalse(step(unspecified, "SCHEDULE").complete());
    }

    @Test
    void servicesCanBeRequiredWithoutAppointmentSchedules() {
        setupComplete();
        when(profiles.current()).thenReturn(profile(true, false));
        when(onboarding.status()).thenReturn(new OnboardingStatusResponse(
                true, false, false, false, false, true, false, "ADD_SERVICE"));
        var guide = service.current();

        assertFalse(step(guide, "SERVICES").complete());
        assertTrue(step(guide, "SCHEDULE").complete());
        assertFalse(guide.configurationReady());
    }

    @Test
    void reservationsRequireServicesEvenWhenServicesFlagIsFalse() {
        setupComplete();
        when(profiles.current()).thenReturn(profile(false, true));
        when(onboarding.status()).thenReturn(new OnboardingStatusResponse(
                true, false, false, false, false, true, false, "ADD_SERVICE"));
        var guide = service.current();

        assertFalse(step(guide, "SERVICES").complete());
        assertFalse(step(guide, "SCHEDULE").complete());
    }

    @Test
    void missingBusinessDataOrDisabledAgentBlockLocalConfiguration() {
        setupComplete();
        when(onboarding.status()).thenReturn(new OnboardingStatusResponse(
                false, true, true, false, false, true, false, "CONFIGURE_BUSINESS"));
        when(agent.isActive()).thenReturn(false);
        var guide = service.current();
        assertFalse(step(guide, "BUSINESS").complete());
        assertFalse(step(guide, "AGENT").complete());
        assertFalse(guide.configurationReady());
    }

    @Test
    void defaultAgentThatWasNeverSavedDoesNotPassTheAgentGate() {
        setupComplete();
        when(agents.configuredCurrent()).thenReturn(false);
        var guide = service.current();
        assertFalse(step(guide, "AGENT").complete());
        assertFalse(guide.configurationReady());
    }

    @Test
    void phoneFromAnotherTenantNeverAppearsEvenIfItIsManaged() {
        setupComplete();
        PhoneNumber foreign = managed();
        foreign.setBusinessId(UUID.randomUUID());
        when(phones.findAllByBusinessIdOrderByCreatedAtDesc(businessId)).thenReturn(List.of(foreign));
        var guide = service.current();

        assertNull(guide.activePhoneNumber());
        assertFalse(step(guide, "PHONE").complete());
        assertFalse(guide.configurationReady());
    }

    @Test
    void activeHistoricalPhoneCannotPassOwnershipProof() {
        setupComplete();
        PhoneNumber legacy = managed();
        legacy.setTwilioBindingSource(null);
        when(phones.findAllByBusinessIdOrderByCreatedAtDesc(businessId)).thenReturn(List.of(legacy));
        var guide = service.current();

        assertEquals("+14355652512", guide.activePhoneNumber());
        assertFalse(step(guide, "PHONE").complete());
        assertFalse(guide.configurationReady());
    }

    @Test
    void disabledAndWrongProviderPhoneRecordsCannotPass() {
        setupComplete();
        PhoneNumber inactive = managed();
        inactive.setActive(false);
        when(phones.findAllByBusinessIdOrderByCreatedAtDesc(businessId)).thenReturn(List.of(inactive));
        assertFalse(step(service.current(), "PHONE").complete());
        assertNull(service.current().activePhoneNumber());

        PhoneNumber otherProvider = managed();
        otherProvider.setProvider("OTHER");
        when(phones.findAllByBusinessIdOrderByCreatedAtDesc(businessId)).thenReturn(List.of(otherProvider));
        assertFalse(step(service.current(), "PHONE").complete());
    }

    @Test
    void wrongOrMissingAccountSidBlocksActivation() {
        setupComplete();
        PhoneNumber number = managed();
        number.setTwilioAccountSid(null);
        when(phones.findAllByBusinessIdOrderByCreatedAtDesc(businessId)).thenReturn(List.of(number));
        assertFalse(step(service.current(), "PHONE").complete());

        number.setTwilioAccountSid("AC" + "c".repeat(32));
        assertFalse(step(service.current(), "PHONE").complete());
    }

    @Test
    void missingMalformedOrUnverifiedSidBlocksActivation() {
        setupComplete();
        PhoneNumber number = managed();
        when(phones.findAllByBusinessIdOrderByCreatedAtDesc(businessId)).thenReturn(List.of(number));
        number.setExternalId(null);
        assertFalse(step(service.current(), "PHONE").complete());

        number.setExternalId("PN-not-real");
        assertFalse(step(service.current(), "PHONE").complete());

        number.setExternalId(PHONE_SID);
        number.setVoiceVerifiedAt(null);
        assertFalse(step(service.current(), "PHONE").complete());
    }

    @Test
    void absentCarrierConfigurationBlocksEvenARecordedHistoricalVerification() {
        setupComplete();
        when(twilio.configured()).thenReturn(false);
        assertFalse(step(service.current(), "PHONE").complete());
        assertFalse(step(service.current(), "CARRIER").complete());
    }

    @Test
    void aValidNumberCanFollowAnUnverifiedRecordForTheSameTenant() {
        setupComplete();
        PhoneNumber legacy = managed();
        legacy.setTwilioBindingSource(null);
        when(phones.findAllByBusinessIdOrderByCreatedAtDesc(businessId))
                .thenReturn(List.of(legacy, managed()));
        assertTrue(step(service.current(), "PHONE").complete());
    }

    private static VoiceSetupReadinessService.Step step(VoiceSetupReadinessService.Guide guide, String code) {
        return guide.steps().stream().filter(item -> code.equals(item.code())).findFirst().orElseThrow();
    }

    private BusinessProfileResponse profile(Boolean sellsServices, Boolean reservations) {
        return new BusinessProfileResponse(
                businessId, null, null, null, null, null, null, null, null, null,
                "CL", "CLP", false, sellsServices, reservations);
    }

    private PhoneNumber managed() {
        PhoneNumber number = new PhoneNumber();
        number.setBusinessId(businessId);
        number.setPhoneNumber("+14355652512");
        number.setActive(true);
        number.setProvider("TWILIO");
        number.setExternalId(PHONE_SID);
        number.setTwilioBindingSource("MANAGED_PURCHASE");
        number.setTwilioAccountSid(ACCOUNT_SID);
        number.setVoiceVerifiedAt(Instant.now());
        return number;
    }
}

package cl.helvoca.phone;

import cl.helvoca.security.TenantProvider;
import cl.helvoca.telephony.twilio.TwilioPhoneProvisioningClient;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PhoneVoiceReadinessResponseTest {
    private static final String ACCOUNT = "AC" + "1".repeat(32);
    private static final String SID = "PN" + "a".repeat(32);

    private static PhoneNumber phone() {
        PhoneNumber p = new PhoneNumber();
        p.setBusinessId(UUID.randomUUID());
        p.setPhoneNumber("+12025550111");
        p.setProvider("TWILIO");
        p.setTwilioBindingSource("MANAGED_PURCHASE");
        p.setTwilioAccountSid(ACCOUNT);
        p.setExternalId(SID);
        p.setVoiceVerifiedAt(Instant.parse("2026-10-11T00:00:00Z"));
        p.setActive(true);
        return p;
    }

    private static PhoneVoiceReadinessResponse state(PhoneNumber p, String account,
                                                       String expected, boolean activation) {
        PhoneVoiceReadinessResponse r = PhoneVoiceReadinessResponse.from(p, account);
        assertEquals(expected, r.state());
        assertEquals(activation, r.canAttemptActivation());
        assertEquals(p.getId(), r.phoneId());
        assertEquals(p.getPhoneNumber(), r.phoneNumber());
        assertEquals(p.getVoiceVerifiedAt(), r.lastVerifiedAt());
        assertFalse(r.message().isBlank());
        return r;
    }

    @Test
    void activeWithHistoricalVerificationIsNeverAdvertisedAsLiveCertified() {
        PhoneVoiceReadinessResponse r = state(phone(), ACCOUNT, "PREVIOUSLY_VERIFIED", false);
        assertTrue(r.message().contains("No confirma"));
    }

    @Test
    void unsupportedProviderIsRejectedBeforeInspectingProviderEvidence() {
        PhoneNumber p = phone();
        p.setProvider("OTHER");
        state(p, ACCOUNT, "UNSUPPORTED_PROVIDER", false);
        p.setProvider(null);
        state(p, ACCOUNT, "UNSUPPORTED_PROVIDER", false);
    }

    @Test
    void invalidLegacyBindingEvidenceCannotBeMarkedAsVerified() {
        PhoneNumber p = phone();
        p.setTwilioBindingSource("LEGACY_IMPORT");
        state(p, ACCOUNT, "UNVERIFIED_BINDING", false);
        p.setTwilioBindingSource("MANAGED_PURCHASE");

        p.setTwilioAccountSid(null);
        state(p, ACCOUNT, "UNVERIFIED_BINDING", false);
        p.setTwilioAccountSid(" ");
        state(p, ACCOUNT, "UNVERIFIED_BINDING", false);
        p.setTwilioAccountSid(ACCOUNT);

        p.setExternalId(null);
        state(p, ACCOUNT, "UNVERIFIED_BINDING", false);
        p.setExternalId("PN-invented");
        state(p, ACCOUNT, "UNVERIFIED_BINDING", false);
        p.setExternalId(SID);
        state(p, ACCOUNT, "PREVIOUSLY_VERIFIED", false);
    }

    @Test
    void currentAccountMismatchAndMissingConfigurationAlwaysFailClosed() {
        PhoneNumber p = phone();
        state(p, null, "ACCOUNT_MISMATCH", false);
        state(p, "AC" + "2".repeat(32), "ACCOUNT_MISMATCH", false);
        state(p, ACCOUNT, "PREVIOUSLY_VERIFIED", false);
    }

    @Test
    void missingAttestationRequiresExplicitActivationBeforeAnyLiveReadinessClaim() {
        PhoneNumber p = phone();
        p.setVoiceVerifiedAt(null);
        state(p, ACCOUNT, "NEEDS_VERIFICATION", true);
        p.setVoiceVerifiedAt(Instant.parse("2026-10-11T00:00:00Z"));
        p.setActive(false);
        state(p, ACCOUNT, "ACTIVATION_REQUIRED", true);
        p.setActive(true);
        state(p, ACCOUNT, "PREVIOUSLY_VERIFIED", false);
    }

    @Test
    void tenantReadinessListsOnlyTenantRowsWithoutCarrierCallsOrWrites() {
        UUID tenantId = UUID.randomUUID();
        PhoneNumberRepository repository = mock(PhoneNumberRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);
        TwilioPhoneProvisioningClient twilio = mock(TwilioPhoneProvisioningClient.class);
        when(tenant.requireBusinessId()).thenReturn(tenantId);
        when(twilio.managedAccountSid()).thenReturn(ACCOUNT);

        PhoneNumber owned = phone();
        owned.setBusinessId(tenantId);
        when(repository.findAllByBusinessIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(owned));

        PhoneProvisioningService service = new PhoneProvisioningService(repository, tenant, twilio);
        var responses = service.readiness();
        assertEquals(1, responses.size());
        assertEquals("PREVIOUSLY_VERIFIED", responses.get(0).state());
        verify(repository).findAllByBusinessIdOrderByCreatedAtDesc(tenantId);
        verify(repository, never()).saveAndFlush(any());
        verify(twilio, never()).verifyManagedPhone(any());
        verify(twilio, never()).provision(any());
        verify(twilio, never()).releaseQuietly(any());
    }

    @Test
    void missingTenantRejectsReadinessBeforeAnyRepositoryOrProviderAccess() {
        PhoneNumberRepository repository = mock(PhoneNumberRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);
        TwilioPhoneProvisioningClient twilio = mock(TwilioPhoneProvisioningClient.class);
        when(tenant.requireBusinessId()).thenThrow(new IllegalStateException("tenant missing"));

        PhoneProvisioningService service = new PhoneProvisioningService(repository, tenant, twilio);
        assertThrows(IllegalStateException.class, service::readiness);
        verifyNoInteractions(repository, twilio);
    }

    @Test
    void readinessControllerDelegatesOnlyToReadOnlyProvisioningService() {
        PhoneProvisioningService provisioning = mock(PhoneProvisioningService.class);
        PhoneNumberService legacy = mock(PhoneNumberService.class);
        when(provisioning.readiness()).thenReturn(List.of());
        PhoneNumberController controller = new PhoneNumberController(legacy, provisioning);
        assertTrue(controller.voiceReadiness().isEmpty());
        verify(provisioning).readiness();
        verifyNoInteractions(legacy);
    }
}

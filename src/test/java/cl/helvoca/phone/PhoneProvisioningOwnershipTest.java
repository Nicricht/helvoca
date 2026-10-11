package cl.helvoca.phone;

import cl.helvoca.common.ConflictException;
import cl.helvoca.security.TenantProvider;
import cl.helvoca.telephony.twilio.TwilioPhoneProvisioningClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PhoneProvisioningOwnershipTest {
    private static final String PHONE = "+12025550123";
    private static final String SID = "PN" + "a".repeat(32);
    private static final String ACCOUNT = "AC" + "c".repeat(32);
    private PhoneNumberRepository repository;
    private TenantProvider tenant;
    private TwilioPhoneProvisioningClient twilio;
    private PhoneProvisioningService service;
    private UUID owner;

    @BeforeEach
    void setup() {
        repository = mock(PhoneNumberRepository.class);
        tenant = mock(TenantProvider.class);
        twilio = mock(TwilioPhoneProvisioningClient.class);
        owner = UUID.randomUUID();
        when(tenant.requireBusinessId()).thenReturn(owner);
        when(twilio.managedAccountSid()).thenReturn(ACCOUNT);
        when(repository.saveAndFlush(any(PhoneNumber.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new PhoneProvisioningService(repository, tenant, twilio);
    }

    private PhoneNumber alreadyAssigned(UUID businessId, String sid, String provider) {
        PhoneNumber phone = new PhoneNumber();
        phone.setBusinessId(businessId);
        phone.setPhoneNumber(PHONE);
        phone.setProvider(provider);
        phone.setExternalId(sid);
        phone.setTwilioBindingSource("MANAGED_PURCHASE");
        phone.setTwilioAccountSid(ACCOUNT);
        phone.setActive(false);
        when(repository.findByPhoneNumber(PHONE)).thenReturn(Optional.of(phone));
        return phone;
    }

    private void attested() {
        when(twilio.verifyManagedPhone(PHONE))
                .thenReturn(new TwilioPhoneProvisioningClient.VerifiedPhoneNumber(SID, PHONE));
    }

    @Test
    void refusesClientSuppliedCarrierSidBeforeCarrierAccess() {
        assertThrows(ConflictException.class, () -> service.connectExisting(new PhoneNumberRequest(PHONE, SID, true)));
        verify(twilio, never()).verifyManagedPhone(any());
    }

    @Test
    void blankClientSidCannotRepairUntrustedLegacyBinding() {
        alreadyAssigned(owner, null, "TWILIO");
        assertThrows(ConflictException.class,
                () -> service.connectExisting(new PhoneNumberRequest(PHONE, "  ", true)));
        verify(twilio, never()).verifyManagedPhone(any());
    }

    @Test
    void refusesDisableThroughConnectionEndpoint() {
        assertThrows(ConflictException.class, () -> service.connectExisting(new PhoneNumberRequest(PHONE, null, false)));
        verify(twilio, never()).verifyManagedPhone(any());
    }

    @Test
    void refusesUnassignedNumberWithoutAClaim() {
        when(repository.findByPhoneNumber(PHONE)).thenReturn(Optional.empty());
        assertThrows(ConflictException.class, () -> service.connectExisting(new PhoneNumberRequest(PHONE, null, true)));
        verify(twilio, never()).verifyManagedPhone(any());
    }

    @Test
    void refusesNumberOfDifferentTenantBeforeCarrierAccess() {
        alreadyAssigned(UUID.randomUUID(), SID, "TWILIO");
        assertThrows(ConflictException.class, () -> service.connectExisting(new PhoneNumberRequest(PHONE, null, true)));
        verify(twilio, never()).verifyManagedPhone(any());
    }

    @Test
    void refusesNumberBoundToNonTwilioProvider() {
        alreadyAssigned(owner, SID, "OTHER");
        assertThrows(ConflictException.class, () -> service.connectExisting(new PhoneNumberRequest(PHONE, null, true)));
        verify(twilio, never()).verifyManagedPhone(any());
    }

    @Test
    void refusesProviderSidConflictWithoutMutation() {
        PhoneNumber phone = alreadyAssigned(owner, "PN" + "b".repeat(32), "TWILIO");
        attested();
        assertThrows(ConflictException.class, () -> service.connectExisting(new PhoneNumberRequest(PHONE, null, true)));
        assertFalse(phone.isActive());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void reconnectsOnlyAfterTwilioAttestsTheAssignedPhone() {
        PhoneNumber phone = alreadyAssigned(owner, SID, "TWILIO");
        attested();
        PhoneNumberResponse result = service.connectExisting(new PhoneNumberRequest(PHONE, null, null));
        assertTrue(result.active());
        assertEquals(SID, result.externalId());
        assertSame(phone, verifySave());
    }

    @Test
    void rejectsUnprovenLegacyNullSidRatherThanClaimingCarrierOwnership() {
        PhoneNumber phone = alreadyAssigned(owner, null, "TWILIO");
        assertThrows(ConflictException.class,
                () -> service.connectExisting(new PhoneNumberRequest(PHONE, null, true)));
        assertFalse(phone.isActive());
        verify(twilio, never()).verifyManagedPhone(any());
    }

    @Test
    void rejectsUnprovenLegacyBlankSidBeforeCarrierLookup() {
        alreadyAssigned(owner, "", "TWILIO");
        assertThrows(ConflictException.class,
                () -> service.connectExisting(new PhoneNumberRequest(PHONE, null, true)));
        verify(twilio, never()).verifyManagedPhone(any());
    }

    @Test
    void carrierOutageDoesNotActivatePhone() {
        PhoneNumber phone = alreadyAssigned(owner, null, "TWILIO");
        when(twilio.verifyManagedPhone(PHONE)).thenThrow(new IllegalStateException("offline"));
        assertThrows(IllegalStateException.class, () -> service.connectExisting(new PhoneNumberRequest(PHONE, null, true)));
        assertFalse(phone.isActive());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void repeatPurchaseRejectsForeignTenantAndOtherProvider() {
        alreadyAssigned(UUID.randomUUID(), SID, "TWILIO");
        assertThrows(ConflictException.class, () -> service.provision(new ProvisionPhoneNumberRequest(PHONE, true)));
        alreadyAssigned(owner, SID, "OTHER");
        assertThrows(ConflictException.class, () -> service.provision(new ProvisionPhoneNumberRequest(PHONE, true)));
        verify(twilio, never()).verifyManagedPhone(any());
        verify(twilio, never()).provision(any());
    }

    @Test
    void repeatPurchaseRejectsOutdatedCarrierSidWithoutNewPurchase() {
        PhoneNumber phone = alreadyAssigned(owner, "PN" + "b".repeat(32), "TWILIO");
        attested();
        assertThrows(ConflictException.class, () -> service.provision(new ProvisionPhoneNumberRequest(PHONE, true)));
        assertFalse(phone.isActive());
        verify(twilio, never()).provision(any());
    }

    @Test
    void repeatPurchaseRechecksCarrierAndReactivatesValidAssignment() {
        PhoneNumber phone = alreadyAssigned(owner, SID, "TWILIO");
        attested();
        PhoneNumberResponse response = service.provision(new ProvisionPhoneNumberRequest(PHONE, true));
        assertTrue(response.active());
        assertEquals(SID, response.externalId());
        assertSame(phone, verifySave());
        verify(twilio, never()).provision(any());
    }

    @Test
    void repeatPurchaseCannotClaimLegacyBlankSid() {
        alreadyAssigned(owner, "", "TWILIO");
        assertThrows(ConflictException.class,
                () -> service.provision(new ProvisionPhoneNumberRequest(PHONE, true)));
        verify(twilio, never()).verifyManagedPhone(any());
    }

    @Test
    void repeatPurchaseCannotClaimLegacyNullSid() {
        alreadyAssigned(owner, null, "TWILIO");
        assertThrows(ConflictException.class,
                () -> service.provision(new ProvisionPhoneNumberRequest(PHONE, true)));
        verify(twilio, never()).verifyManagedPhone(any());
    }

    @Test
    void publicCreateControllerUsesVerifiedFlowNotLegacyUncheckedCreate() {
        PhoneNumberService legacy = mock(PhoneNumberService.class);
        PhoneProvisioningService verified = mock(PhoneProvisioningService.class);
        PhoneNumberRequest request = new PhoneNumberRequest(PHONE, null, true);
        PhoneNumberController controller = new PhoneNumberController(legacy, verified);
        controller.create(request);
        verify(verified).connectExisting(request);
        verifyNoInteractions(legacy);
    }

    @Test
    void activateVerifiedRejectsUnknownOrForeignTenantPhoneId() {
        UUID missing = UUID.randomUUID();
        assertThrows(ConflictException.class, () -> service.activateVerified(missing));
        verify(twilio, never()).verifyManagedPhone(any());
    }

    @Test
    void activateVerifiedRejectsWrongProvider() {
        UUID id = UUID.randomUUID();
        PhoneNumber phone = alreadyAssigned(owner, SID, "OTHER");
        when(repository.findByIdAndBusinessId(id, owner)).thenReturn(Optional.of(phone));
        assertThrows(ConflictException.class, () -> service.activateVerified(id));
        verify(twilio, never()).verifyManagedPhone(any());
    }

    @Test
    void activateVerifiedRejectsCarrierSidChangedAndKeepsInactive() {
        UUID id = UUID.randomUUID();
        PhoneNumber phone = alreadyAssigned(owner, "PN" + "f".repeat(32), "TWILIO");
        when(repository.findByIdAndBusinessId(id, owner)).thenReturn(Optional.of(phone));
        attested();
        assertThrows(ConflictException.class, () -> service.activateVerified(id));
        assertFalse(phone.isActive());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void activateVerifiedRejectsUnprovenLegacySid() {
        UUID id = UUID.randomUUID();
        PhoneNumber phone = alreadyAssigned(owner, null, "TWILIO");
        when(repository.findByIdAndBusinessId(id, owner)).thenReturn(Optional.of(phone));
        assertThrows(ConflictException.class, () -> service.activateVerified(id));
        assertFalse(phone.isActive());
    }

    @Test
    void activateVerifiedRechecksMatchingAndBlankSid() {
        UUID id = UUID.randomUUID();
        PhoneNumber phone = alreadyAssigned(owner, SID, "TWILIO");
        when(repository.findByIdAndBusinessId(id, owner)).thenReturn(Optional.of(phone));
        attested();
        assertTrue(service.activateVerified(id).active());
        phone.setExternalId("   ");
        assertThrows(ConflictException.class, () -> service.activateVerified(id));
        assertEquals("   ", phone.getExternalId());
    }

    @Test
    void activationToggleDelegatesToCarrierAttestationButDeactivationDoesNot() {
        PhoneNumberService legacy = mock(PhoneNumberService.class);
        PhoneProvisioningService verified = mock(PhoneProvisioningService.class);
        PhoneNumberController controller = new PhoneNumberController(legacy, verified);
        UUID id = UUID.randomUUID();
        controller.setActive(id, new PhoneNumberActiveRequest(true));
        verify(verified).activateVerified(id);
        verifyNoInteractions(legacy);
        controller.setActive(id, new PhoneNumberActiveRequest(false));
        verify(legacy).setActive(id, false);
    }

    @Test
    void rejectsUntrustedManualBindingEvenWhenCarrierAccountCanSeeNumber() {
        PhoneNumber phone = alreadyAssigned(owner, SID, "TWILIO");
        phone.setTwilioBindingSource(null);
        assertThrows(ConflictException.class,
                () -> service.connectExisting(new PhoneNumberRequest(PHONE, null, true)));
        verify(twilio, never()).verifyManagedPhone(any());
    }

    @Test
    void rejectsWrongCarrierAccountFromProvenance() {
        PhoneNumber phone = alreadyAssigned(owner, SID, "TWILIO");
        phone.setTwilioAccountSid("AC" + "d".repeat(32));
        assertThrows(ConflictException.class,
                () -> service.connectExisting(new PhoneNumberRequest(PHONE, null, true)));
    }

    @Test
    void rejectsEmptyAndMissingConfiguredCarrierAccount() {
        alreadyAssigned(owner, SID, "TWILIO");
        when(twilio.managedAccountSid()).thenReturn("");
        assertThrows(ConflictException.class,
                () -> service.connectExisting(new PhoneNumberRequest(PHONE, null, true)));
        when(twilio.managedAccountSid()).thenReturn(null);
        assertThrows(ConflictException.class,
                () -> service.connectExisting(new PhoneNumberRequest(PHONE, null, true)));
    }

    @Test
    void successfulCarrierAttestationRecordsVerificationTime() {
        alreadyAssigned(owner, SID, "TWILIO");
        attested();
        service.connectExisting(new PhoneNumberRequest(PHONE, null, true));
        assertNotNull(verifySave().getVoiceVerifiedAt());
    }

    private PhoneNumber verifySave() {
        org.mockito.ArgumentCaptor<PhoneNumber> captor = org.mockito.ArgumentCaptor.forClass(PhoneNumber.class);
        verify(repository).saveAndFlush(captor.capture());
        return captor.getValue();
    }
}

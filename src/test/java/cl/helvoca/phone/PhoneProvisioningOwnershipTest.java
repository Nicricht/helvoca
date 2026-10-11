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
        when(repository.saveAndFlush(any(PhoneNumber.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new PhoneProvisioningService(repository, tenant, twilio);
    }

    private PhoneNumber alreadyAssigned(UUID businessId, String sid, String provider) {
        PhoneNumber phone = new PhoneNumber();
        phone.setBusinessId(businessId);
        phone.setPhoneNumber(PHONE);
        phone.setProvider(provider);
        phone.setExternalId(sid);
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
    void acceptsBlankClientSidButNeverTreatsItAsProof() {
        alreadyAssigned(owner, null, "TWILIO");
        attested();
        assertTrue(service.connectExisting(new PhoneNumberRequest(PHONE, "  ", true)).active());
        verify(twilio).verifyManagedPhone(PHONE);
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
    void reconnectingAPlaceholderRequiresCarrierProofAndPersistsAttestedSid() {
        PhoneNumber phone = alreadyAssigned(owner, null, "TWILIO");
        attested();
        PhoneNumberResponse response = service.connectExisting(new PhoneNumberRequest(PHONE, null, true));
        assertEquals(SID, phone.getExternalId());
        assertEquals(SID, response.externalId());
        assertTrue(phone.isActive());
    }

    @Test
    void reconnectingBlankStoredSidRequiresCarrierProof() {
        PhoneNumber phone = alreadyAssigned(owner, "", "TWILIO");
        attested();
        assertTrue(service.connectExisting(new PhoneNumberRequest(PHONE, null, true)).active());
        assertEquals(SID, phone.getExternalId());
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
    void repeatPurchaseCanReconcileBlankSidOnlyForPreviouslyAssignedTenant() {
        PhoneNumber phone = alreadyAssigned(owner, "", "TWILIO");
        attested();
        PhoneNumberResponse response = service.provision(new ProvisionPhoneNumberRequest(PHONE, true));
        assertEquals(SID, phone.getExternalId());
        assertTrue(response.active());
    }

    @Test
    void repeatPurchaseCanReconcileNullSidOnlyForPreviouslyAssignedTenant() {
        PhoneNumber phone = alreadyAssigned(owner, null, "TWILIO");
        attested();
        assertEquals(SID, service.provision(new ProvisionPhoneNumberRequest(PHONE, true)).externalId());
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

    private PhoneNumber verifySave() {
        org.mockito.ArgumentCaptor<PhoneNumber> captor = org.mockito.ArgumentCaptor.forClass(PhoneNumber.class);
        verify(repository).saveAndFlush(captor.capture());
        return captor.getValue();
    }
}

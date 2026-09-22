package cl.helvoca.phone;

import cl.helvoca.audit.AuditService;
import cl.helvoca.common.ConflictException;
import cl.helvoca.common.NotFoundException;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PhoneNumberServiceTest {

    @Test
    void reconnectingNumberAlreadyOwnedBySameTenantIsIdempotent() {
        PhoneNumberRepository repository = mock(PhoneNumberRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        UUID businessId = UUID.randomUUID();
        when(tenantProvider.requireBusinessId()).thenReturn(businessId);

        PhoneNumber phone = new PhoneNumber();
        phone.setBusinessId(businessId);
        phone.setProvider("TWILIO");
        phone.setPhoneNumber("+14355652512");
        phone.setActive(false);
        when(repository.findByPhoneNumber("+14355652512")).thenReturn(Optional.of(phone));
        when(repository.save(phone)).thenReturn(phone);

        PhoneNumberService service = new PhoneNumberService(repository, tenantProvider);
        PhoneNumberResponse response = service.create(new PhoneNumberRequest("+14355652512", null, true));

        assertTrue(response.active());
        verify(repository).save(phone);
    }

    @Test
    void updatingExistingPhoneToInactiveAuditsClearedCertification() {
        PhoneNumberRepository repository = mock(PhoneNumberRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        AuditService audit = mock(AuditService.class);
        UUID businessId = UUID.randomUUID();
        when(tenantProvider.requireBusinessId()).thenReturn(businessId);

        PhoneNumber phone = new PhoneNumber();
        phone.setBusinessId(businessId);
        phone.setProvider("TWILIO");
        phone.setPhoneNumber("+14355652512");
        phone.setActive(true);
        phone.setWhatsappEnabled(true);
        phone.setWhatsappCertifiedAt(Instant.now());
        when(repository.findByPhoneNumber("+14355652512")).thenReturn(Optional.of(phone));
        when(repository.save(phone)).thenReturn(phone);

        PhoneNumberService service = new PhoneNumberService(repository, tenantProvider, audit);
        PhoneNumberResponse response = service.create(
                new PhoneNumberRequest("+14355652512", null, false));

        assertFalse(response.active());
        assertFalse(response.whatsappEnabled());
        assertNull(response.whatsappCertifiedAt());
        verify(audit).humanSuccess(
                eq(businessId),
                eq("WHATSAPP_CERTIFICATION_CLEARED"),
                eq("WHATSAPP_SENDER"),
                eq(businessId),
                eq(Map.of(
                        "whatsappEnabled", true,
                        "certified", true)),
                eq(Map.of(
                        "whatsappEnabled", false,
                        "certified", false)));
    }

    @Test
    void updatingExistingEnabledPhoneToInactiveAuditsSenderDisable() {
        PhoneNumberRepository repository = mock(PhoneNumberRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        AuditService audit = mock(AuditService.class);
        UUID businessId = UUID.randomUUID();
        when(tenantProvider.requireBusinessId()).thenReturn(businessId);

        PhoneNumber phone = new PhoneNumber();
        phone.setBusinessId(businessId);
        phone.setProvider("TWILIO");
        phone.setPhoneNumber("+14355652512");
        phone.setActive(true);
        phone.setWhatsappEnabled(true);
        when(repository.findByPhoneNumber("+14355652512")).thenReturn(Optional.of(phone));
        when(repository.save(phone)).thenReturn(phone);

        PhoneNumberService service = new PhoneNumberService(repository, tenantProvider, audit);
        PhoneNumberResponse response = service.create(
                new PhoneNumberRequest("+14355652512", null, false));

        assertFalse(response.active());
        assertFalse(response.whatsappEnabled());
        verify(audit).humanSuccess(
                eq(businessId),
                eq("WHATSAPP_SENDER_DISABLED"),
                eq("WHATSAPP_SENDER"),
                eq(businessId),
                eq(Map.of(
                        "whatsappEnabled", true,
                        "certified", false)),
                eq(Map.of(
                        "whatsappEnabled", false,
                        "certified", false)));
    }

    @Test
    void cannotBindNumberOwnedByAnotherTenant() {
        PhoneNumberRepository repository = mock(PhoneNumberRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        UUID currentBusiness = UUID.randomUUID();
        UUID otherBusiness = UUID.randomUUID();
        when(tenantProvider.requireBusinessId()).thenReturn(currentBusiness);

        PhoneNumber phone = new PhoneNumber();
        phone.setBusinessId(otherBusiness);
        phone.setProvider("TWILIO");
        phone.setPhoneNumber("+14355652512");
        phone.setActive(true);
        when(repository.findByPhoneNumber("+14355652512")).thenReturn(Optional.of(phone));

        PhoneNumberService service = new PhoneNumberService(repository, tenantProvider);
        ConflictException error = assertThrows(ConflictException.class,
                () -> service.create(new PhoneNumberRequest("+14355652512", null, true)));

        assertEquals("Este número ya está conectado a otro negocio", error.getMessage());
        verify(repository, never()).save(any());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void deactivatingWhatsappEnabledPhoneAuditsSenderDisable() {
        PhoneNumberRepository repository = mock(PhoneNumberRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        AuditService audit = mock(AuditService.class);
        UUID businessId = UUID.randomUUID();
        UUID phoneId = UUID.randomUUID();
        when(tenantProvider.requireBusinessId()).thenReturn(businessId);

        PhoneNumber phone = new PhoneNumber();
        phone.setBusinessId(businessId);
        phone.setActive(true);
        phone.setWhatsappEnabled(true);
        when(repository.findByIdAndBusinessId(phoneId, businessId)).thenReturn(Optional.of(phone));

        PhoneNumberService service = new PhoneNumberService(repository, tenantProvider, audit);
        PhoneNumberResponse response = service.setActive(phoneId, false);

        assertFalse(response.active());
        assertFalse(response.whatsappEnabled());
        verify(audit).humanSuccess(
                eq(businessId),
                eq("WHATSAPP_SENDER_DISABLED"),
                eq("WHATSAPP_SENDER"),
                eq(businessId),
                eq(Map.of(
                        "whatsappEnabled", true,
                        "certified", false)),
                eq(Map.of(
                        "whatsappEnabled", false,
                        "certified", false)));
    }

    @Test
    void deactivatingPhoneAuditsClearedCertification() {
        PhoneNumberRepository repository = mock(PhoneNumberRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        AuditService audit = mock(AuditService.class);
        UUID businessId = UUID.randomUUID();
        UUID phoneId = UUID.randomUUID();
        when(tenantProvider.requireBusinessId()).thenReturn(businessId);

        PhoneNumber phone = new PhoneNumber();
        phone.setBusinessId(businessId);
        phone.setActive(true);
        phone.setWhatsappEnabled(true);
        phone.setWhatsappCertifiedAt(Instant.now());
        when(repository.findByIdAndBusinessId(phoneId, businessId)).thenReturn(Optional.of(phone));

        PhoneNumberService service = new PhoneNumberService(repository, tenantProvider, audit);
        PhoneNumberResponse response = service.setActive(phoneId, false);

        assertFalse(response.active());
        assertFalse(response.whatsappEnabled());
        assertNull(response.whatsappCertifiedAt());
        verify(audit).humanSuccess(
                eq(businessId),
                eq("WHATSAPP_CERTIFICATION_CLEARED"),
                eq("WHATSAPP_SENDER"),
                eq(businessId),
                eq(Map.of(
                        "whatsappEnabled", true,
                        "certified", true)),
                eq(Map.of(
                        "whatsappEnabled", false,
                        "certified", false)));
    }

    @Test
    void enablingWhatsappAuditsSenderEnable() {
        PhoneNumberRepository repository = mock(PhoneNumberRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        AuditService audit = mock(AuditService.class);
        UUID businessId = UUID.randomUUID();
        UUID phoneId = UUID.randomUUID();
        when(tenantProvider.requireBusinessId()).thenReturn(businessId);

        PhoneNumber phone = new PhoneNumber();
        phone.setBusinessId(businessId);
        phone.setActive(true);
        phone.setWhatsappEnabled(false);
        when(repository.findByIdAndBusinessId(phoneId, businessId)).thenReturn(Optional.of(phone));
        when(repository.findAllByBusinessIdAndActiveTrueAndWhatsappEnabledTrueOrderByCreatedAtDesc(businessId))
                .thenReturn(java.util.List.of());
        when(repository.save(phone)).thenReturn(phone);

        PhoneNumberService service = new PhoneNumberService(repository, tenantProvider, audit);
        PhoneNumberResponse response = service.setWhatsappEnabled(phoneId, true);

        assertTrue(response.whatsappEnabled());
        verify(audit).humanSuccess(
                eq(businessId),
                eq("WHATSAPP_SENDER_ENABLED"),
                eq("WHATSAPP_SENDER"),
                eq(businessId),
                eq(Map.of(
                        "whatsappEnabled", false,
                        "certified", false)),
                eq(Map.of(
                        "whatsappEnabled", true,
                        "certified", false)));
    }

    @Test
    void enablingAlreadyEnabledWhatsappDoesNotDuplicateSenderEnableAudit() {
        PhoneNumberRepository repository = mock(PhoneNumberRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        AuditService audit = mock(AuditService.class);
        UUID businessId = UUID.randomUUID();
        UUID phoneId = UUID.randomUUID();
        when(tenantProvider.requireBusinessId()).thenReturn(businessId);

        PhoneNumber phone = new PhoneNumber();
        phone.setBusinessId(businessId);
        phone.setActive(true);
        phone.setWhatsappEnabled(true);
        when(repository.findByIdAndBusinessId(phoneId, businessId)).thenReturn(Optional.of(phone));
        when(repository.findAllByBusinessIdAndActiveTrueAndWhatsappEnabledTrueOrderByCreatedAtDesc(businessId))
                .thenReturn(java.util.List.of());
        when(repository.save(phone)).thenReturn(phone);

        PhoneNumberService service = new PhoneNumberService(repository, tenantProvider, audit);
        PhoneNumberResponse response = service.setWhatsappEnabled(phoneId, true);

        assertTrue(response.whatsappEnabled());
        verify(audit, never()).humanSuccess(
                eq(businessId),
                eq("WHATSAPP_SENDER_ENABLED"),
                anyString(),
                any(),
                any(),
                any());
    }

    @Test
    void disablingWhatsappAuditsSenderDisable() {
        PhoneNumberRepository repository = mock(PhoneNumberRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        AuditService audit = mock(AuditService.class);
        UUID businessId = UUID.randomUUID();
        UUID phoneId = UUID.randomUUID();
        when(tenantProvider.requireBusinessId()).thenReturn(businessId);

        PhoneNumber phone = new PhoneNumber();
        phone.setBusinessId(businessId);
        phone.setActive(true);
        phone.setWhatsappEnabled(true);
        when(repository.findByIdAndBusinessId(phoneId, businessId)).thenReturn(Optional.of(phone));
        when(repository.save(phone)).thenReturn(phone);

        PhoneNumberService service = new PhoneNumberService(repository, tenantProvider, audit);
        PhoneNumberResponse response = service.setWhatsappEnabled(phoneId, false);

        assertFalse(response.whatsappEnabled());
        verify(audit).humanSuccess(
                eq(businessId),
                eq("WHATSAPP_SENDER_DISABLED"),
                eq("WHATSAPP_SENDER"),
                eq(businessId),
                eq(Map.of(
                        "whatsappEnabled", true,
                        "certified", false)),
                eq(Map.of(
                        "whatsappEnabled", false,
                        "certified", false)));
    }

    @Test
    void disablingAlreadyDisabledWhatsappDoesNotDuplicateSenderDisableAudit() {
        PhoneNumberRepository repository = mock(PhoneNumberRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        AuditService audit = mock(AuditService.class);
        UUID businessId = UUID.randomUUID();
        UUID phoneId = UUID.randomUUID();
        when(tenantProvider.requireBusinessId()).thenReturn(businessId);

        PhoneNumber phone = new PhoneNumber();
        phone.setBusinessId(businessId);
        phone.setActive(true);
        phone.setWhatsappEnabled(false);
        when(repository.findByIdAndBusinessId(phoneId, businessId)).thenReturn(Optional.of(phone));
        when(repository.save(phone)).thenReturn(phone);

        PhoneNumberService service = new PhoneNumberService(repository, tenantProvider, audit);
        PhoneNumberResponse response = service.setWhatsappEnabled(phoneId, false);

        assertFalse(response.whatsappEnabled());
        verify(audit, never()).humanSuccess(
                eq(businessId),
                eq("WHATSAPP_SENDER_DISABLED"),
                anyString(),
                any(),
                any(),
                any());
    }

    @Test
    void disablingWhatsappClearsPreviousCertification() {
        PhoneNumberRepository repository = mock(PhoneNumberRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        AuditService audit = mock(AuditService.class);
        UUID businessId = UUID.randomUUID();
        UUID phoneId = UUID.randomUUID();
        when(tenantProvider.requireBusinessId()).thenReturn(businessId);

        PhoneNumber phone = new PhoneNumber();
        phone.setBusinessId(businessId);
        phone.setActive(true);
        phone.setWhatsappEnabled(true);
        phone.setWhatsappCertifiedAt(Instant.now());
        when(repository.findByIdAndBusinessId(phoneId, businessId)).thenReturn(Optional.of(phone));
        when(repository.save(phone)).thenReturn(phone);

        PhoneNumberService service = new PhoneNumberService(repository, tenantProvider, audit);
        PhoneNumberResponse response = service.setWhatsappEnabled(phoneId, false);

        assertFalse(response.whatsappEnabled());
        assertNull(response.whatsappCertifiedAt());
        verify(repository).save(phone);
        verify(audit).humanSuccess(
                eq(businessId),
                eq("WHATSAPP_CERTIFICATION_CLEARED"),
                eq("WHATSAPP_SENDER"),
                eq(businessId),
                eq(Map.of(
                        "whatsappEnabled", true,
                        "certified", true)),
                eq(Map.of(
                        "whatsappEnabled", false,
                        "certified", false)));
    }

    @Test
    void detachDeletesPhoneOwnedByCurrentTenant() {
        PhoneNumberRepository repository = mock(PhoneNumberRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        UUID businessId = UUID.randomUUID();
        UUID phoneId = UUID.randomUUID();
        when(tenantProvider.requireBusinessId()).thenReturn(businessId);

        PhoneNumber phone = new PhoneNumber();
        phone.setBusinessId(businessId);
        when(repository.findByIdAndBusinessId(phoneId, businessId)).thenReturn(Optional.of(phone));

        PhoneNumberService service = new PhoneNumberService(repository, tenantProvider);
        service.detach(phoneId);

        verify(repository).delete(phone);
    }

    @Test
    void detachingCertifiedPhoneAuditsClearedCertification() {
        PhoneNumberRepository repository = mock(PhoneNumberRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        AuditService audit = mock(AuditService.class);
        UUID businessId = UUID.randomUUID();
        UUID phoneId = UUID.randomUUID();
        when(tenantProvider.requireBusinessId()).thenReturn(businessId);

        PhoneNumber phone = new PhoneNumber();
        phone.setBusinessId(businessId);
        phone.setWhatsappEnabled(true);
        phone.setWhatsappCertifiedAt(Instant.now());
        when(repository.findByIdAndBusinessId(phoneId, businessId)).thenReturn(Optional.of(phone));

        PhoneNumberService service = new PhoneNumberService(repository, tenantProvider, audit);
        service.detach(phoneId);

        verify(repository).delete(phone);
        verify(audit).humanSuccess(
                eq(businessId),
                eq("WHATSAPP_CERTIFICATION_CLEARED"),
                eq("WHATSAPP_SENDER"),
                eq(businessId),
                eq(Map.of(
                        "whatsappEnabled", true,
                        "certified", true)),
                eq(Map.of(
                        "whatsappEnabled", false,
                        "certified", false)));
    }

    @Test
    void detachCannotDeletePhoneOwnedByAnotherTenant() {
        PhoneNumberRepository repository = mock(PhoneNumberRepository.class);
        TenantProvider tenantProvider = mock(TenantProvider.class);
        UUID currentBusiness = UUID.randomUUID();
        UUID phoneId = UUID.randomUUID();
        when(tenantProvider.requireBusinessId()).thenReturn(currentBusiness);
        when(repository.findByIdAndBusinessId(phoneId, currentBusiness)).thenReturn(Optional.empty());

        PhoneNumberService service = new PhoneNumberService(repository, tenantProvider);

        assertThrows(NotFoundException.class, () -> service.detach(phoneId));
        verify(repository, never()).delete(any());
    }
}

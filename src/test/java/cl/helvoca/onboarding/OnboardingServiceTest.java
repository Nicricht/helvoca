package cl.helvoca.onboarding;

import cl.helvoca.audit.AuditService;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.business.BusinessProfileResponse;
import cl.helvoca.business.BusinessProfileService;
import cl.helvoca.knowledge.KnowledgeItemRepository;
import cl.helvoca.phone.PhoneNumber;
import cl.helvoca.phone.PhoneNumberRepository;
import cl.helvoca.schedule.BusinessHoursAdminService;
import cl.helvoca.schedule.BusinessHourResponse;
import cl.helvoca.security.TenantProvider;
import cl.helvoca.servicecatalog.ServiceItem;
import cl.helvoca.servicecatalog.ServiceItemRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OnboardingServiceTest {
    @Test
    void readyForCallsRequiresActivePhoneButHumanTransferAndKnowledgeRemainOptional() {
        BusinessRepository businesses = mock(BusinessRepository.class);
        ServiceItemRepository services = mock(ServiceItemRepository.class);
        KnowledgeItemRepository knowledge = mock(KnowledgeItemRepository.class);
        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        BusinessHoursAdminService hours = mock(BusinessHoursAdminService.class);
        BusinessProfileService profiles = mock(BusinessProfileService.class);
        TenantProvider tenant = mock(TenantProvider.class);
        UUID businessId = UUID.randomUUID();
        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(profiles.current()).thenReturn(profile(businessId, null, null, null));

        Business business = new Business();
        business.setName("Clínica Norte");
        business.setTimezone("America/Santiago");
        business.setLanguage("es");
        when(businesses.findById(businessId)).thenReturn(Optional.of(business));

        ServiceItem activeService = new ServiceItem();
        activeService.setBusinessId(businessId);
        activeService.setName("Consulta");
        activeService.setDurationMinutes(30);
        activeService.setActive(true);
        when(services.findAllByBusinessIdOrderByNameAsc(businessId)).thenReturn(List.of(activeService));
        when(hours.list()).thenReturn(List.of(new BusinessHourResponse(1, LocalTime.of(9, 0), LocalTime.of(18, 0))));
        when(knowledge.findAllByBusinessIdAndActiveTrueOrderByTitleAsc(businessId)).thenReturn(List.of());

        OnboardingService service = new OnboardingService(
                businesses, services, knowledge, phones, hours, profiles, tenant, mock(AuditService.class));

        OnboardingStatusResponse withoutPhone = service.status();
        assertFalse(withoutPhone.readyForCalls());
        assertEquals("CONNECT_PHONE_NUMBER", withoutPhone.nextStep());

        PhoneNumber phone = new PhoneNumber();
        phone.setBusinessId(businessId);
        phone.setPhoneNumber("+17372508034");
        phone.setActive(true);
        when(phones.findAllByBusinessIdOrderByCreatedAtDesc(businessId)).thenReturn(List.of(phone));

        OnboardingStatusResponse ready = service.status();
        assertTrue(ready.readyForCalls());
        assertFalse(ready.humanTransferConfigured());
        assertFalse(ready.knowledgeConfigured());
        assertEquals("OPTIONAL_HUMAN_TRANSFER", ready.nextStep());
    }

    @Test
    void explicitNonServiceBusinessDoesNotRequireServicesOrReservationSchedule() {
        BusinessRepository businesses = mock(BusinessRepository.class);
        ServiceItemRepository services = mock(ServiceItemRepository.class);
        KnowledgeItemRepository knowledge = mock(KnowledgeItemRepository.class);
        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        BusinessHoursAdminService hours = mock(BusinessHoursAdminService.class);
        BusinessProfileService profiles = mock(BusinessProfileService.class);
        TenantProvider tenant = mock(TenantProvider.class);
        UUID businessId = UUID.randomUUID();
        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(profiles.current()).thenReturn(profile(businessId, true, false, false));

        Business business = new Business();
        business.setName("Tienda Norte");
        business.setTimezone("America/Santiago");
        business.setLanguage("es");
        when(businesses.findById(businessId)).thenReturn(Optional.of(business));
        when(services.findAllByBusinessIdOrderByNameAsc(businessId)).thenReturn(List.of());
        when(hours.list()).thenReturn(List.of());
        when(knowledge.findAllByBusinessIdAndActiveTrueOrderByTitleAsc(businessId)).thenReturn(List.of());

        PhoneNumber phone = new PhoneNumber();
        phone.setBusinessId(businessId);
        phone.setPhoneNumber("+17372508034");
        phone.setActive(true);
        when(phones.findAllByBusinessIdOrderByCreatedAtDesc(businessId)).thenReturn(List.of(phone));

        OnboardingService service = new OnboardingService(
                businesses, services, knowledge, phones, hours, profiles, tenant, mock(AuditService.class));

        OnboardingStatusResponse status = service.status();

        assertTrue(status.readyForCalls());
        assertFalse(status.servicesConfigured());
        assertFalse(status.scheduleConfigured());
        assertEquals("OPTIONAL_HUMAN_TRANSFER", status.nextStep());
    }

    private static BusinessProfileResponse profile(UUID businessId,
                                                   Boolean sellsProducts,
                                                   Boolean sellsServices,
                                                   Boolean usesReservations) {
        return new BusinessProfileResponse(
                businessId, "store", null, null, null, null,
                null, null, null, null, null, "CLP",
                sellsProducts, sellsServices, usesReservations);
    }
}

package cl.helvoca.onboarding;

import cl.helvoca.audit.AuditService;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.knowledge.KnowledgeItem;
import cl.helvoca.knowledge.KnowledgeItemRepository;
import cl.helvoca.phone.PhoneNumberRepository;
import cl.helvoca.schedule.BusinessHourResponse;
import cl.helvoca.schedule.BusinessHoursAdminService;
import cl.helvoca.security.TenantProvider;
import cl.helvoca.servicecatalog.ServiceItem;
import cl.helvoca.servicecatalog.ServiceItemRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OnboardingSynchronizationTest {

    @Test
    void setupRenamesExistingItemsByIdAndDeactivatesRemovedItems() {
        BusinessRepository businesses = mock(BusinessRepository.class);
        ServiceItemRepository services = mock(ServiceItemRepository.class);
        KnowledgeItemRepository knowledge = mock(KnowledgeItemRepository.class);
        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        BusinessHoursAdminService hours = mock(BusinessHoursAdminService.class);
        TenantProvider tenant = mock(TenantProvider.class);
        AuditService audit = mock(AuditService.class);

        UUID businessId = UUID.randomUUID();
        UUID keptServiceId = UUID.randomUUID();
        UUID removedServiceId = UUID.randomUUID();
        UUID removedKnowledgeId = UUID.randomUUID();
        when(tenant.requireBusinessId()).thenReturn(businessId);

        Business business = new Business();
        business.setName("Restaurante");
        business.setTimezone("America/Santiago");
        business.setLanguage("es");
        when(businesses.findById(businessId)).thenReturn(Optional.of(business));

        ServiceItem kept = mock(ServiceItem.class);
        when(kept.getId()).thenReturn(keptServiceId);
        when(kept.getName()).thenReturn("Reserva mesa");
        when(kept.isActive()).thenReturn(true);

        ServiceItem removed = mock(ServiceItem.class);
        when(removed.getId()).thenReturn(removedServiceId);
        when(removed.getName()).thenReturn("Evento privado");
        when(removed.isActive()).thenReturn(true);

        when(services.findAllByBusinessIdOrderByNameAsc(businessId)).thenReturn(List.of(kept, removed));
        when(services.save(any(ServiceItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        KnowledgeItem removedKnowledge = mock(KnowledgeItem.class);
        when(removedKnowledge.getId()).thenReturn(removedKnowledgeId);
        when(removedKnowledge.getTitle()).thenReturn("Estacionamiento");
        when(removedKnowledge.isActive()).thenReturn(true);
        when(knowledge.findAllByBusinessIdOrderByTitleAsc(businessId)).thenReturn(List.of(removedKnowledge));
        when(knowledge.findAllByBusinessIdAndActiveTrueOrderByTitleAsc(businessId)).thenReturn(List.of());
        when(knowledge.save(any(KnowledgeItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(hours.list()).thenReturn(List.of(new BusinessHourResponse(1, LocalTime.of(9, 0), LocalTime.of(18, 0))));
        when(phones.findAllByBusinessIdOrderByCreatedAtDesc(businessId)).thenReturn(List.of());

        OnboardingService service = new OnboardingService(
                businesses, services, knowledge, phones, hours, tenant, audit);

        OnboardingSetupRequest request = new OnboardingSetupRequest(
                "Restaurante", "America/Santiago", "es", null,
                List.of(new OnboardingSetupRequest.ServiceInput(
                        keptServiceId, "Reserva de mesa", "Mesa estándar", 60, null)),
                List.of(new OnboardingSetupRequest.HourInput(
                        1, LocalTime.of(9, 0), LocalTime.of(18, 0))),
                List.of());

        service.setup(request);

        verify(kept).setName("Reserva de mesa");
        verify(kept).setActive(true);
        verify(removed).setActive(false);
        verify(services).save(removed);
        verify(removedKnowledge).setActive(false);
        verify(knowledge).save(removedKnowledge);
    }

    @Test
    void setupRejectsItemIdsOutsideAuthenticatedTenant() {
        BusinessRepository businesses = mock(BusinessRepository.class);
        ServiceItemRepository services = mock(ServiceItemRepository.class);
        KnowledgeItemRepository knowledge = mock(KnowledgeItemRepository.class);
        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        BusinessHoursAdminService hours = mock(BusinessHoursAdminService.class);
        TenantProvider tenant = mock(TenantProvider.class);

        UUID businessId = UUID.randomUUID();
        when(tenant.requireBusinessId()).thenReturn(businessId);

        Business business = new Business();
        business.setName("Restaurante");
        business.setTimezone("America/Santiago");
        business.setLanguage("es");
        when(businesses.findById(businessId)).thenReturn(Optional.of(business));
        when(services.findAllByBusinessIdOrderByNameAsc(businessId)).thenReturn(List.of());

        OnboardingService service = new OnboardingService(
                businesses, services, knowledge, phones, hours, tenant, mock(AuditService.class));

        OnboardingSetupRequest request = new OnboardingSetupRequest(
                "Restaurante", "America/Santiago", "es", null,
                List.of(new OnboardingSetupRequest.ServiceInput(
                        UUID.randomUUID(), "Reserva", null, 30, null)),
                List.of(),
                List.of());

        assertThrows(cl.helvoca.common.NotFoundException.class, () -> service.setup(request));
    }
}

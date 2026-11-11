package cl.helvoca.ai.deepgram;

import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessProfile;
import cl.helvoca.business.BusinessProfileRepository;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.schedule.BusinessHour;
import cl.helvoca.schedule.BusinessHourRepository;
import cl.helvoca.security.TenantTransactionExecutor;
import cl.helvoca.servicecatalog.ServiceItem;
import cl.helvoca.servicecatalog.ServiceItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class EconomicVoiceFactsServiceTest {
    private final UUID businessId = UUID.randomUUID();
    private final RealtimeCallContext context = new RealtimeCallContext(
            UUID.randomUUID(), businessId, null, "+56911112222", "+56220000000", "stream");
    private BusinessRepository businesses;
    private BusinessProfileRepository profiles;
    private BusinessHourRepository hours;
    private ServiceItemRepository services;
    private TenantTransactionExecutor transactions;
    private EconomicVoiceFactsService facts;

    @BeforeEach
    void setUp() {
        businesses = mock(BusinessRepository.class);
        profiles = mock(BusinessProfileRepository.class);
        hours = mock(BusinessHourRepository.class);
        services = mock(ServiceItemRepository.class);
        transactions = mock(TenantTransactionExecutor.class);
        when(transactions.read(eq(businessId), any())).thenAnswer(invocation ->
                ((Supplier<?>) invocation.getArgument(1)).get());
        facts = new EconomicVoiceFactsService(
                businesses, profiles, hours, services, transactions);
    }

    @Test
    void returnsOnlyConfiguredTenantWeeklyHours() {
        BusinessHour monday = new BusinessHour();
        monday.setBusinessId(businessId);
        monday.setDayOfWeek(1);
        monday.setOpenTime(LocalTime.of(8, 0));
        monday.setCloseTime(LocalTime.of(20, 0));
        when(hours.findAllByBusinessIdOrderByDayOfWeekAscOpenTimeAsc(businessId))
                .thenReturn(List.of(monday));

        String answer = facts.answer(context, "¿Cuál es el horario de atención?").orElseThrow();

        assertTrue(answer.contains("lunes de 08:00 a 20:00"));
        assertTrue(answer.contains("domingo cerrado"));
        verify(transactions).read(eq(businessId), any());
    }

    @Test
    void returnsAddressOnlyWhenTheTenantHasAnOfficialPublicAddress() {
        BusinessProfile profile = new BusinessProfile();
        profile.setBusinessId(businessId);
        profile.setAddressLine("Av. Providencia 123");
        profile.setCommune("Providencia");
        profile.setCity("Santiago");
        profile.setRegion("Región Metropolitana");
        profile.setCountryCode("CL");
        when(profiles.findById(businessId)).thenReturn(Optional.of(profile));

        String answer = facts.answer(context, "¿Dónde quedan?").orElseThrow();

        assertEquals("Estamos en Av. Providencia 123, Providencia, Santiago, Región Metropolitana, Chile.",
                answer);
    }

    @Test
    void returnsPriceOnlyForOneExactActiveServiceAndKnownCurrency() {
        BusinessProfile profile = new BusinessProfile();
        profile.setBusinessId(businessId);
        profile.setDefaultCurrency("CLP");
        when(profiles.findById(businessId)).thenReturn(Optional.of(profile));
        ServiceItem service = new ServiceItem();
        service.setBusinessId(businessId);
        service.setName("Medicina General");
        service.setActive(true);
        service.setPrice(new BigDecimal("25000"));
        when(services.findAllByBusinessIdOrderByNameAsc(businessId)).thenReturn(List.of(service));

        String answer = facts.answer(context, "¿Cuánto cuesta medicina general?").orElseThrow();

        assertTrue(answer.contains("Medicina General"));
        assertTrue(answer.contains("25.000"));
        assertTrue(answer.contains("cuesta"));
    }

    @Test
    void refusesUnknownPriceOrAmbiguousServiceMatches() {
        BusinessProfile profile = new BusinessProfile();
        profile.setBusinessId(businessId);
        profile.setDefaultCurrency("CLP");
        when(profiles.findById(businessId)).thenReturn(Optional.of(profile));
        ServiceItem first = new ServiceItem();
        first.setName("Medicina");
        first.setActive(true);
        first.setPrice(new BigDecimal("35000"));
        ServiceItem second = new ServiceItem();
        second.setName("Medicina General");
        second.setActive(true);
        second.setPrice(new BigDecimal("40000"));
        when(services.findAllByBusinessIdOrderByNameAsc(businessId)).thenReturn(List.of(first, second));

        assertTrue(facts.answer(context, "¿Cuánto cuesta medicina general?").isEmpty());
        assertTrue(facts.answer(context, "¿Cuánto cuesta pediatría?").isEmpty());
    }

    @Test
    void neverAnswersNonPublicOrComplexRequestsFromTheRulesPath() {
        assertTrue(facts.answer(context, "Quiero reservar mañana y cuánto cuesta medicina general").isEmpty());
        verifyNoInteractions(hours, profiles, services);
    }
}

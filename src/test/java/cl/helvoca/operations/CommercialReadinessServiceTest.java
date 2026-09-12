package cl.helvoca.operations;

import cl.helvoca.ai.live.OpenAiLiveProperties;
import cl.helvoca.ai.realtime.OpenAiRealtimeProperties;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.phone.PhoneNumber;
import cl.helvoca.phone.PhoneNumberRepository;
import cl.helvoca.schedule.BusinessHourRepository;
import cl.helvoca.security.TenantProvider;
import cl.helvoca.servicecatalog.ServiceItem;
import cl.helvoca.servicecatalog.ServiceItemRepository;
import cl.helvoca.telephony.twilio.TwilioProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommercialReadinessServiceTest {

    @Test
    void readyCoreExposesAllConfiguredCapabilities() {
        Fixture f = fixture();
        f.business.setHumanTransferPhone("+56911111111");
        ServiceItem service = mock(ServiceItem.class);
        when(service.isActive()).thenReturn(true);
        when(f.services.findAllByBusinessIdOrderByNameAsc(f.businessId)).thenReturn(List.of(service));
        when(f.hours.countByBusinessId(f.businessId)).thenReturn(3L);

        CommercialReadinessService.Readiness result = f.service.readiness();

        assertTrue(result.ready());
        assertEquals(6, result.requiredPassed());
        assertEquals(6, result.requiredTotal());
        assertTrue(result.capabilities().get("VOICE_ASSISTANT"));
        assertTrue(result.capabilities().get("INFORMATION"));
        assertTrue(result.capabilities().get("GENERIC_REQUESTS"));
        assertTrue(result.capabilities().get("BOOKINGS"));
        assertTrue(result.capabilities().get("HUMAN_TRANSFER"));
        assertTrue(result.warnings().isEmpty());
        assertTrue(result.checks().stream().anyMatch(c -> c.code().equals("OPENAI_GPT_LIVE_SIP") && c.ready()));
    }

    @Test
    void coreVoiceCanBeReadyWithoutBookingOrTransferConfiguration() {
        Fixture f = fixture();
        when(f.services.findAllByBusinessIdOrderByNameAsc(f.businessId)).thenReturn(List.of());
        when(f.hours.countByBusinessId(f.businessId)).thenReturn(0L);

        CommercialReadinessService.Readiness result = f.service.readiness();

        assertTrue(result.ready());
        assertTrue(result.capabilities().get("VOICE_ASSISTANT"));
        assertTrue(result.capabilities().get("INFORMATION"));
        assertTrue(result.capabilities().get("GENERIC_REQUESTS"));
        assertFalse(result.capabilities().get("BOOKINGS"));
        assertFalse(result.capabilities().get("HUMAN_TRANSFER"));
        assertEquals(2, result.warnings().size());
    }

    @Test
    void invalidLiveConfigurationBlocksCommercialReadiness() {
        Fixture f = fixture();
        when(f.live.isEnabled()).thenReturn(false);
        when(f.openAi.hasApiKey()).thenReturn(false);

        CommercialReadinessService.Readiness result = f.service.readiness();

        assertFalse(result.ready());
        assertFalse(result.capabilities().get("VOICE_ASSISTANT"));
        assertTrue(result.checks().stream().anyMatch(c -> c.code().equals("OPENAI_GPT_LIVE_SIP") && !c.ready()));
        assertTrue(result.checks().stream().anyMatch(c -> c.code().equals("OPENAI_API") && !c.ready()));
        assertTrue(result.checks().stream().noneMatch(c -> c.code().equals("TWILIO_MEDIA_STREAM")));
        assertTrue(result.checks().stream().noneMatch(c -> c.code().equals("OPENAI_REALTIME")));
    }

    private static Fixture fixture() {
        UUID businessId = UUID.randomUUID();
        BusinessRepository businesses = mock(BusinessRepository.class);
        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        ServiceItemRepository services = mock(ServiceItemRepository.class);
        BusinessHourRepository hours = mock(BusinessHourRepository.class);
        TenantProvider tenant = mock(TenantProvider.class);
        TwilioProperties twilio = mock(TwilioProperties.class);
        OpenAiRealtimeProperties openAi = mock(OpenAiRealtimeProperties.class);
        OpenAiLiveProperties live = mock(OpenAiLiveProperties.class);

        Business business = new Business();
        business.setName("Negocio horizontal");
        business.setLanguage("es");
        business.setTimezone("America/Santiago");

        PhoneNumber phone = mock(PhoneNumber.class);
        when(phone.isActive()).thenReturn(true);
        when(phone.getPhoneNumber()).thenReturn("+56922222222");

        when(tenant.requireBusinessId()).thenReturn(businessId);
        when(businesses.findById(businessId)).thenReturn(Optional.of(business));
        when(phones.findAllByBusinessIdOrderByCreatedAtDesc(businessId)).thenReturn(List.of(phone));
        when(services.findAllByBusinessIdOrderByNameAsc(businessId)).thenReturn(List.of());
        when(hours.countByBusinessId(businessId)).thenReturn(0L);
        when(twilio.hasAuthToken()).thenReturn(true);
        when(twilio.getPublicBaseUrl()).thenReturn("https://helvoca.example.com");
        when(openAi.hasApiKey()).thenReturn(true);
        when(live.isEnabled()).thenReturn(true);
        when(live.hasProjectId()).thenReturn(true);
        when(live.hasWebhookSecret()).thenReturn(true);
        when(live.getModel()).thenReturn("gpt-live-1");
        when(live.getBackendModel()).thenReturn("gpt-5.6-luna");
        when(live.getVoice()).thenReturn("marin");
        when(live.getApiBaseUrl()).thenReturn("https://api.openai.com/v1");
        when(live.getSidebandBaseUrl()).thenReturn("wss://api.openai.com/v1");

        CommercialReadinessService service = new CommercialReadinessService(
                businesses, phones, services, hours, tenant, twilio, openAi, live);
        return new Fixture(businessId, business, services, hours, twilio, openAi, live, service);
    }

    private record Fixture(
            UUID businessId,
            Business business,
            ServiceItemRepository services,
            BusinessHourRepository hours,
            TwilioProperties twilio,
            OpenAiRealtimeProperties openAi,
            OpenAiLiveProperties live,
            CommercialReadinessService service
    ) {}
}

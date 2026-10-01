package cl.helvoca.platform;

import cl.helvoca.agent.AiAgent;
import cl.helvoca.agent.AiAgentRepository;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessMode;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.business.BusinessStatus;
import cl.helvoca.phone.PhoneNumber;
import cl.helvoca.phone.PhoneNumberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlatformDemoReadinessServiceTest {

    @Test
    void missingServerOwnedRuntimeFailsClosedWithoutTouchingTenantData() {
        DemoRuntimeProperties properties = new DemoRuntimeProperties();
        BusinessRepository businesses = mock(BusinessRepository.class);
        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        AiAgentRepository agents = mock(AiAgentRepository.class);
        PlatformDemoReadinessService service =
                new PlatformDemoReadinessService(properties, businesses, phones, agents);

        PlatformDemoReadinessResponse result = service.readiness();

        assertFalse(result.runtimeConfigured());
        assertNull(result.runtimeBusinessId());
        assertEquals("NOT_CONFIGURED", result.runtime().state());
        assertEquals("NOT_CONFIGURED", result.voiceNumber().state());
        assertEquals("NOT_CONFIGURED", result.voiceAi().state());
        assertEquals("NOT_CONFIGURED", result.businessData().state());
        assertEquals("NOT_CONFIGURED", result.operations().state());
        assertEquals("NOT_CONFIGURED", result.whatsapp().state());
        assertEquals("SANDBOX_ONLY", result.payment().state());
        assertEquals("DISARMED", result.externalEffects().state());
        verifyNoInteractions(businesses, phones, agents);
    }

    @Test
    void customerTenantCanNeverMasqueradeAsLiveDemoRuntime() {
        UUID businessId = UUID.randomUUID();
        DemoRuntimeProperties properties = properties(businessId);
        BusinessRepository businesses = mock(BusinessRepository.class);
        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        AiAgentRepository agents = mock(AiAgentRepository.class);
        Business business = business(businessId, BusinessMode.CUSTOMER, BusinessStatus.ACTIVE);
        when(businesses.findById(businessId)).thenReturn(Optional.of(business));

        PlatformDemoReadinessResponse result =
                new PlatformDemoReadinessService(properties, businesses, phones, agents).readiness();

        assertTrue(result.runtimeConfigured());
        assertEquals(businessId, result.runtimeBusinessId());
        assertEquals("FAILED", result.runtime().state());
        assertEquals("FAILED", result.voiceNumber().state());
        assertEquals("FAILED", result.voiceAi().state());
        assertEquals("FAILED", result.operations().state());
        verifyNoInteractions(phones, agents);
    }

    @Test
    void activeDemoRuntimeReportsOnlyReadinessProvenByStoredConfiguration() {
        UUID businessId = UUID.randomUUID();
        DemoRuntimeProperties properties = properties(businessId);
        BusinessRepository businesses = mock(BusinessRepository.class);
        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        AiAgentRepository agents = mock(AiAgentRepository.class);
        Business business = business(businessId, BusinessMode.DEMO, BusinessStatus.ACTIVE);
        when(businesses.findById(businessId)).thenReturn(Optional.of(business));

        PhoneNumber phone = new PhoneNumber();
        phone.setBusinessId(businessId);
        phone.setPhoneNumber("+56911112222");
        phone.setActive(true);
        phone.setWhatsappEnabled(true);
        phone.setWhatsappCertifiedAt(Instant.parse("2026-10-01T05:00:00Z"));
        when(phones.findAllByBusinessIdOrderByCreatedAtDesc(businessId)).thenReturn(List.of(phone));

        AiAgent agent = new AiAgent();
        agent.setBusinessId(businessId);
        agent.setName("Demo AI");
        agent.setLanguage("es");
        agent.setGreeting("Hola");
        agent.setActive(true);
        when(agents.findByBusinessId(businessId)).thenReturn(Optional.of(agent));

        PlatformDemoReadinessResponse result =
                new PlatformDemoReadinessService(properties, businesses, phones, agents).readiness();

        assertEquals("READY", result.runtime().state());
        assertEquals("READY", result.voiceNumber().state());
        assertEquals("+56911112222", result.voiceNumber().detail());
        assertEquals("READY", result.voiceAi().state());
        assertEquals("NOT_CONFIGURED", result.businessData().state());
        assertEquals("READY", result.operations().state());
        assertEquals("READY", result.whatsapp().state());
        assertEquals("SANDBOX_ONLY", result.payment().state());
        assertEquals("DISARMED", result.externalEffects().state());
    }

    @Test
    void suspendedDemoRuntimeIsUnavailableEvenWithConfiguration() {
        UUID businessId = UUID.randomUUID();
        DemoRuntimeProperties properties = properties(businessId);
        BusinessRepository businesses = mock(BusinessRepository.class);
        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        AiAgentRepository agents = mock(AiAgentRepository.class);
        when(businesses.findById(businessId))
                .thenReturn(Optional.of(business(businessId, BusinessMode.DEMO, BusinessStatus.SUSPENDED)));

        PlatformDemoReadinessResponse result =
                new PlatformDemoReadinessService(properties, businesses, phones, agents).readiness();

        assertEquals("UNAVAILABLE", result.runtime().state());
        assertEquals("UNAVAILABLE", result.voiceNumber().state());
        assertEquals("UNAVAILABLE", result.voiceAi().state());
        assertEquals("UNAVAILABLE", result.operations().state());
        verifyNoInteractions(phones, agents);
    }

    private static DemoRuntimeProperties properties(UUID businessId) {
        DemoRuntimeProperties properties = new DemoRuntimeProperties();
        properties.setRuntimeBusinessId(businessId.toString());
        return properties;
    }

    private static Business business(UUID id, BusinessMode mode, BusinessStatus status) {
        Business business = new Business();
        ReflectionTestUtils.setField(business, "id", id);
        business.setName("Live Demo Runtime");
        business.setMode(mode);
        business.setStatus(status);
        return business;
    }
}

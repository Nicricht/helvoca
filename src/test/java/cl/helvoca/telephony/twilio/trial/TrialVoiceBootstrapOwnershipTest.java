package cl.helvoca.telephony.twilio.trial;

import cl.helvoca.ai.realtime.OpenAiRealtimeProperties;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.knowledge.KnowledgeItemRepository;
import cl.helvoca.phone.PhoneNumber;
import cl.helvoca.phone.PhoneNumberRepository;
import cl.helvoca.schedule.BusinessHourRepository;
import cl.helvoca.servicecatalog.ServiceItemRepository;
import cl.helvoca.user.AppUser;
import cl.helvoca.user.AppUserRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;

class TrialVoiceBootstrapOwnershipTest {

    @Test
    void doesNotSeedDemoDataWhenTrialNumberBelongsToARealTenant() {
        TrialVoiceProperties properties = new TrialVoiceProperties();
        properties.setEnabled(true);
        properties.setPhoneNumber("+17372508034");

        OpenAiRealtimeProperties openAi = new OpenAiRealtimeProperties();
        BusinessRepository businesses = mock(BusinessRepository.class);
        PhoneNumberRepository phones = mock(PhoneNumberRepository.class);
        ServiceItemRepository services = mock(ServiceItemRepository.class);
        KnowledgeItemRepository knowledge = mock(KnowledgeItemRepository.class);
        BusinessHourRepository hours = mock(BusinessHourRepository.class);
        AppUserRepository users = mock(AppUserRepository.class);

        UUID businessId = UUID.randomUUID();
        PhoneNumber phone = new PhoneNumber();
        phone.setBusinessId(businessId);
        phone.setProvider("TWILIO_TRIAL");
        phone.setExternalId("TWILIO_TRIAL");
        phone.setPhoneNumber("+17372508034");
        phone.setActive(true);

        when(phones.findByPhoneNumber("+17372508034")).thenReturn(Optional.of(phone));
        when(users.findAllByBusinessIdOrderByName(businessId)).thenReturn(List.of(mock(AppUser.class)));

        TrialVoiceBootstrap bootstrap = new TrialVoiceBootstrap(
                properties, openAi, businesses, phones, services, knowledge, hours, users);

        bootstrap.run();

        verifyNoInteractions(businesses, services, knowledge, hours);
        verify(phones, never()).save(any());
        verify(phones, never()).saveAndFlush(any());
    }
}

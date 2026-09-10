package cl.helvoca.telephony.twilio.trial;

import cl.helvoca.ai.realtime.OpenAiRealtimeProperties;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.knowledge.KnowledgeItem;
import cl.helvoca.knowledge.KnowledgeItemRepository;
import cl.helvoca.phone.PhoneNumber;
import cl.helvoca.phone.PhoneNumberRepository;
import cl.helvoca.schedule.BusinessHour;
import cl.helvoca.schedule.BusinessHourRepository;
import cl.helvoca.servicecatalog.ServiceItem;
import cl.helvoca.servicecatalog.ServiceItemRepository;
import cl.helvoca.user.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.UUID;

@Component
public class TrialVoiceBootstrap implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(TrialVoiceBootstrap.class);

    private final TrialVoiceProperties properties;
    private final OpenAiRealtimeProperties openAi;
    private final BusinessRepository businesses;
    private final PhoneNumberRepository phoneNumbers;
    private final ServiceItemRepository services;
    private final KnowledgeItemRepository knowledge;
    private final BusinessHourRepository hours;
    private final AppUserRepository users;

    public TrialVoiceBootstrap(TrialVoiceProperties properties,
                               OpenAiRealtimeProperties openAi,
                               BusinessRepository businesses,
                               PhoneNumberRepository phoneNumbers,
                               ServiceItemRepository services,
                               KnowledgeItemRepository knowledge,
                               BusinessHourRepository hours,
                               AppUserRepository users) {
        this.properties = properties;
        this.openAi = openAi;
        this.businesses = businesses;
        this.phoneNumbers = phoneNumbers;
        this.services = services;
        this.knowledge = knowledge;
        this.hours = hours;
        this.users = users;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!properties.isEnabled()) return;
        if (!properties.hasPhoneNumber()) {
            log.warn("Twilio trial voice mode is enabled but TWILIO_TRIAL_PHONE_NUMBER is missing or invalid");
            return;
        }

        PhoneNumber phone = phoneNumbers.findByPhoneNumber(properties.getPhoneNumber()).orElse(null);
        UUID businessId;
        boolean createdDemoBusiness = false;
        if (phone == null) {
            Business business = new Business();
            business.setName(properties.getBusinessName());
            business.setLanguage("es");
            business.setTimezone("America/Santiago");
            business = businesses.saveAndFlush(business);
            businessId = business.getId();
            createdDemoBusiness = true;

            phone = new PhoneNumber();
            phone.setBusinessId(businessId);
            phone.setProvider("TWILIO_TRIAL");
            phone.setExternalId("TWILIO_TRIAL");
            phone.setPhoneNumber(properties.getPhoneNumber());
            phone.setActive(true);
            phoneNumbers.saveAndFlush(phone);
        } else {
            businessId = phone.getBusinessId();
            if (!phone.isActive()) {
                phone.setActive(true);
                phoneNumbers.save(phone);
            }
        }

        boolean realTenantOwnsTrialNumber = !users.findAllByBusinessIdOrderByName(businessId).isEmpty();
        if (realTenantOwnsTrialNumber && !createdDemoBusiness) {
            log.info("Twilio trial voice mode ready for {} using configured tenant business {} (OpenAI configured: {})",
                    properties.getPhoneNumber(), businessId, openAi.hasApiKey());
            return;
        }

        if (!services.existsByBusinessIdAndNameIgnoreCase(businessId, "Reserva de mesa")) {
            ServiceItem service = new ServiceItem();
            service.setBusinessId(businessId);
            service.setName("Reserva de mesa");
            service.setDescription("Reserva de mesa del restaurante demo de Helvoca.");
            service.setDurationMinutes(120);
            service.setActive(true);
            services.save(service);
        }

        // Seed defaults only for the synthetic bootstrap business. A real tenant that owns
        // the shared Trial number must keep its own services, hours and knowledge untouched.
        if (hours.countByBusinessId(businessId) == 0) {
            for (int day = 1; day <= 7; day++) {
                BusinessHour hour = new BusinessHour();
                hour.setBusinessId(businessId);
                hour.setDayOfWeek(day);
                hour.setOpenTime(LocalTime.of(12, 0));
                hour.setCloseTime(LocalTime.of(22, 0));
                hours.save(hour);
            }
        }

        boolean hasDemoKnowledge = knowledge.findAllByBusinessIdOrderByTitleAsc(businessId).stream()
                .anyMatch(item -> "Información demo".equalsIgnoreCase(item.getTitle()));
        if (!hasDemoKnowledge) {
            KnowledgeItem item = new KnowledgeItem();
            item.setBusinessId(businessId);
            item.setTitle("Información demo");
            item.setCategory("demo");
            item.setContent("Este es un restaurante de demostración de Helvoca. Puede informar sobre el servicio de reserva de mesa y crear reservas reales en el entorno demo. No debe inventar dirección, menú o precios que no estén configurados.");
            item.setActive(true);
            knowledge.save(item);
        }

        log.info("Twilio trial voice mode ready for {} (OpenAI configured: {}, schedule configured: {})",
                properties.getPhoneNumber(), openAi.hasApiKey(), hours.countByBusinessId(businessId) > 0);
    }
}

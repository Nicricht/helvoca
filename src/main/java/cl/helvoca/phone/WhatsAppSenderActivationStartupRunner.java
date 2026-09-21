package cl.helvoca.phone;

import cl.helvoca.audit.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.regex.Pattern;

@Component
public class WhatsAppSenderActivationStartupRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(WhatsAppSenderActivationStartupRunner.class);
    private static final Pattern E164 = Pattern.compile("^\\+[1-9][0-9]{7,14}$");
    private static final String TWILIO_WHATSAPP_PROVIDER = "TWILIO_WHATSAPP";

    private final boolean enabled;
    private final String senderE164;
    private final PhoneNumberRepository repository;
    private final AuditService auditService;

    @Autowired
    public WhatsAppSenderActivationStartupRunner(
            @Value("${HELVOCA_WHATSAPP_SENDER_ACTIVATE_ON_STARTUP:false}") boolean enabled,
            @Value("${HELVOCA_WHATSAPP_SENDER_E164:}") String senderE164,
            PhoneNumberRepository repository,
            AuditService auditService) {
        this.enabled = enabled;
        this.senderE164 = senderE164 == null ? "" : senderE164.trim();
        this.repository = repository;
        this.auditService = auditService;
    }

    WhatsAppSenderActivationStartupRunner(
            boolean enabled,
            String senderE164,
            PhoneNumberRepository repository) {
        this(enabled, senderE164, repository, null);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) return;
        if (!E164.matcher(senderE164).matches()) {
            throw new IllegalStateException("HELVOCA_WHATSAPP_SENDER_E164 must use E.164 format");
        }

        PhoneNumber phone = repository.findByPhoneNumber(senderE164)
                .orElseThrow(() -> new IllegalStateException("Configured WhatsApp sender is not registered in Helvoca"));
        if (!phone.isActive()) {
            throw new IllegalStateException("Configured WhatsApp sender must be active");
        }
        if (!TWILIO_WHATSAPP_PROVIDER.equalsIgnoreCase(phone.getWhatsappProvider())) {
            throw new IllegalStateException("Configured WhatsApp sender must use TWILIO_WHATSAPP provider");
        }

        boolean anotherSender = repository
                .findAllByBusinessIdAndActiveTrueAndWhatsappEnabledTrueOrderByCreatedAtDesc(phone.getBusinessId())
                .stream()
                .anyMatch(existing -> !Objects.equals(existing.getId(), phone.getId()));
        if (anotherSender) {
            throw new IllegalStateException("Tenant already has another active WhatsApp sender");
        }

        if (!phone.isWhatsappEnabled()) {
            phone.setWhatsappEnabled(true);
            repository.save(phone);
            if (auditService != null) {
                auditService.success(
                        phone.getBusinessId(),
                        "TWILIO_WHATSAPP_SENDER_ACTIVATED_ON_STARTUP",
                        "WHATSAPP_SENDER",
                        phone.getBusinessId());
            }
            log.info("WHATSAPP_SENDER_ACTIVATION enabled configured tenant sender ending={}", lastFour(senderE164));
        } else {
            log.info("WHATSAPP_SENDER_ACTIVATION sender already enabled ending={}", lastFour(senderE164));
        }
    }

    private static String lastFour(String value) {
        return value.length() <= 4 ? value : value.substring(value.length() - 4);
    }
}

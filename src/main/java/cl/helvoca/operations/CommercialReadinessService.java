package cl.helvoca.operations;

import cl.helvoca.ai.realtime.OpenAiRealtimeProperties;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.phone.PhoneNumberRepository;
import cl.helvoca.schedule.BusinessHourRepository;
import cl.helvoca.security.TenantProvider;
import cl.helvoca.servicecatalog.ServiceItem;
import cl.helvoca.servicecatalog.ServiceItemRepository;
import cl.helvoca.telephony.twilio.TwilioProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CommercialReadinessService {
    private final BusinessRepository businesses;
    private final PhoneNumberRepository phones;
    private final ServiceItemRepository services;
    private final BusinessHourRepository hours;
    private final TenantProvider tenantProvider;
    private final TwilioProperties twilio;
    private final OpenAiRealtimeProperties openAi;

    public CommercialReadinessService(BusinessRepository businesses,
                                      PhoneNumberRepository phones,
                                      ServiceItemRepository services,
                                      BusinessHourRepository hours,
                                      TenantProvider tenantProvider,
                                      TwilioProperties twilio,
                                      OpenAiRealtimeProperties openAi) {
        this.businesses = businesses;
        this.phones = phones;
        this.services = services;
        this.hours = hours;
        this.tenantProvider = tenantProvider;
        this.twilio = twilio;
        this.openAi = openAi;
    }

    @Transactional(readOnly = true)
    public Readiness readiness() {
        UUID businessId = tenantProvider.requireBusinessId();
        Business business = businesses.findById(businessId).orElseThrow();

        boolean profileReady = notBlank(business.getName())
                && notBlank(business.getLanguage())
                && validZone(business.getTimezone());
        boolean activePhone = phones.findAllByBusinessIdOrderByCreatedAtDesc(businessId).stream()
                .anyMatch(phone -> phone.isActive() && notBlank(phone.getPhoneNumber()));
        boolean twilioAuth = twilio.hasAuthToken();
        boolean mediaStream = validWss(twilio.getMediaStreamUrl());
        boolean publicWebhook = validHttps(twilio.getPublicBaseUrl());
        boolean openAiKey = openAi.hasApiKey();
        boolean openAiRealtime = validWss(openAi.getRealtimeUrl()) && notBlank(openAi.getRealtimeModel());

        List<Check> core = List.of(
                new Check("BUSINESS_PROFILE", "Perfil del negocio", profileReady, true,
                        profileReady ? "Nombre, idioma y zona horaria válidos." : "Completa nombre, idioma y zona horaria."),
                new Check("ACTIVE_PHONE", "Número activo", activePhone, true,
                        activePhone ? "Hay al menos un número activo asociado al tenant." : "Conecta o activa un número telefónico."),
                new Check("TWILIO_AUTH", "Autenticación Twilio", twilioAuth, true,
                        twilioAuth ? "Credencial de firma/webhook disponible." : "Falta TWILIO_AUTH_TOKEN."),
                new Check("TWILIO_PUBLIC_WEBHOOK", "Webhook público Twilio", publicWebhook, true,
                        publicWebhook ? "URL pública HTTPS configurada." : "TWILIO_PUBLIC_BASE_URL debe ser HTTPS público."),
                new Check("TWILIO_MEDIA_STREAM", "Media Streams", mediaStream, true,
                        mediaStream ? "WebSocket WSS de audio configurado." : "TWILIO_MEDIA_STREAM_URL debe usar wss://."),
                new Check("OPENAI_API", "OpenAI", openAiKey, true,
                        openAiKey ? "API key disponible." : "Falta OPENAI_API_KEY."),
                new Check("OPENAI_REALTIME", "OpenAI Realtime", openAiRealtime, true,
                        openAiRealtime ? "Realtime URL y modelo configurados." : "Configura URL WSS y modelo Realtime.")
        );

        boolean coreReady = core.stream().allMatch(Check::ready);
        boolean bookingCapability = services.findAllByBusinessIdOrderByNameAsc(businessId).stream().anyMatch(ServiceItem::isActive)
                && hours.countByBusinessId(businessId) > 0;
        boolean humanTransferCapability = notBlank(business.getHumanTransferPhone());

        Map<String, Boolean> capabilities = new LinkedHashMap<>();
        capabilities.put("VOICE_ASSISTANT", coreReady);
        capabilities.put("INFORMATION", coreReady);
        capabilities.put("GENERIC_REQUESTS", coreReady);
        capabilities.put("BOOKINGS", coreReady && bookingCapability);
        capabilities.put("HUMAN_TRANSFER", coreReady && humanTransferCapability);

        List<String> warnings = new ArrayList<>();
        if (!bookingCapability) {
            warnings.add("Reservas deshabilitadas: configura al menos un servicio activo y horarios de atención.");
        }
        if (!humanTransferCapability) {
            warnings.add("Transferencia humana deshabilitada: configura un teléfono de transferencia.");
        }

        return new Readiness(
                coreReady,
                core.stream().filter(Check::ready).count(),
                core.size(),
                core,
                capabilities,
                warnings);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean validZone(String value) {
        if (!notBlank(value)) return false;
        try {
            ZoneId.of(value);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean validHttps(String value) {
        return notBlank(value) && value.trim().startsWith("https://");
    }

    private static boolean validWss(String value) {
        return notBlank(value) && value.trim().startsWith("wss://");
    }

    public record Check(String code, String label, boolean ready, boolean required, String detail) {}

    public record Readiness(
            boolean ready,
            long requiredPassed,
            long requiredTotal,
            List<Check> checks,
            Map<String, Boolean> capabilities,
            List<String> warnings
    ) {}
}

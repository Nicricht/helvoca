package cl.helvoca.operations;

import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.phone.PhoneNumberRepository;
import cl.helvoca.schedule.BusinessHourRepository;
import cl.helvoca.security.TenantProvider;
import cl.helvoca.servicecatalog.ServiceItem;
import cl.helvoca.servicecatalog.ServiceItemRepository;
import cl.helvoca.telephony.twilio.TwilioProperties;
import cl.helvoca.voice.VoiceCallRouter;
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
    private final VoiceCallRouter voiceRouter;

    public CommercialReadinessService(BusinessRepository businesses,
                                      PhoneNumberRepository phones,
                                      ServiceItemRepository services,
                                      BusinessHourRepository hours,
                                      TenantProvider tenantProvider,
                                      TwilioProperties twilio,
                                      VoiceCallRouter voiceRouter) {
        this.businesses = businesses;
        this.phones = phones;
        this.services = services;
        this.hours = hours;
        this.tenantProvider = tenantProvider;
        this.twilio = twilio;
        this.voiceRouter = voiceRouter;
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
        boolean publicWebhook = twilio.hasSecurePublicBaseUrl();
        VoiceCallRouter.VoiceReadiness voice = voiceRouter.readiness();

        List<Check> checks = new ArrayList<>();
        checks.add(new Check("BUSINESS_PROFILE", "Perfil del negocio", profileReady, true,
                profileReady ? "Nombre, idioma y zona horaria válidos." : "Completa nombre, idioma y zona horaria."));
        checks.add(new Check("ACTIVE_PHONE", "Número activo", activePhone, true,
                activePhone ? "Hay al menos un número activo asociado al tenant." : "Conecta o activa un número telefónico."));
        checks.add(new Check("TWILIO_AUTH", "Autenticación Twilio", twilioAuth, true,
                twilioAuth ? "Credencial de firma/webhook disponible." : "Falta TWILIO_AUTH_TOKEN."));
        checks.add(new Check("TWILIO_PUBLIC_WEBHOOK", "Webhook público Twilio", publicWebhook, true,
                publicWebhook ? "URL pública HTTPS configurada para webhooks y Media Streams." : "TWILIO_PUBLIC_BASE_URL debe ser HTTPS público."));
        checks.add(new Check("VOICE_PROVIDER", "Proveedor de voz en tiempo real", voice.ready(), true,
                voice.ready()
                        ? "Proveedor seleccionado: " + voice.selectedProvider() + "."
                        : "No hay ningún proveedor de voz configurado y saludable."));

        for (VoiceCallRouter.ProviderStatus provider : voice.providers()) {
            checks.add(new Check(
                    "VOICE_PROVIDER_" + provider.providerId().toUpperCase().replace('-', '_'),
                    "Proveedor " + provider.providerId(),
                    provider.available(),
                    false,
                    provider.mode() + " / " + provider.state() + " / " + provider.detail()));
        }

        boolean coreReady = checks.stream().filter(Check::required).allMatch(Check::ready);
        boolean bookingCapability = services.findAllByBusinessIdOrderByNameAsc(businessId).stream()
                .anyMatch(ServiceItem::isActive)
                && hours.countByBusinessId(businessId) > 0;
        boolean humanTransferCapability = notBlank(business.getHumanTransferPhone());

        Map<String, Boolean> capabilities = new LinkedHashMap<>();
        capabilities.put("VOICE_ASSISTANT", coreReady);
        capabilities.put("INFORMATION", coreReady);
        capabilities.put("GENERIC_REQUESTS", coreReady);
        capabilities.put("BOOKINGS", coreReady && bookingCapability);
        capabilities.put("HUMAN_TRANSFER", coreReady && humanTransferCapability);

        List<String> warnings = new ArrayList<>();
        voice.providers().stream()
                .filter(provider -> !provider.available())
                .forEach(provider -> warnings.add(
                        "Proveedor " + provider.providerId() + " no disponible: " + provider.detail()));
        if (!bookingCapability) {
            warnings.add("Reservas deshabilitadas: configura al menos un servicio activo y horarios de atención.");
        }
        if (!humanTransferCapability) {
            warnings.add("Transferencia humana deshabilitada: configura un teléfono de transferencia.");
        }

        long requiredTotal = checks.stream().filter(Check::required).count();
        long requiredPassed = checks.stream().filter(Check::required).filter(Check::ready).count();
        return new Readiness(
                coreReady,
                requiredPassed,
                requiredTotal,
                List.copyOf(checks),
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

package cl.helvoca.onboarding;

import cl.helvoca.agent.AiAgentService;
import cl.helvoca.business.BusinessProfileResponse;
import cl.helvoca.business.BusinessProfileService;
import cl.helvoca.phone.PhoneNumber;
import cl.helvoca.phone.PhoneNumberRepository;
import cl.helvoca.security.TenantProvider;
import cl.helvoca.telephony.twilio.TwilioPhoneProvisioningClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A read-only tenant-scoped setup guide, NOT a live carrier certification.
 * Historical active numbers do not qualify without managed ownership evidence.
 */
@Service
public class VoiceSetupReadinessService {
    private static final Pattern PHONE_SID = Pattern.compile("^PN[0-9a-fA-F]{32}$");
    private final TenantProvider tenantProvider;
    private final OnboardingService onboarding;
    private final BusinessProfileService profiles;
    private final AiAgentService agents;
    private final PhoneNumberRepository phones;
    private final TwilioPhoneProvisioningClient twilio;

    public VoiceSetupReadinessService(TenantProvider tenantProvider,
                                      OnboardingService onboarding,
                                      BusinessProfileService profiles,
                                      AiAgentService agents,
                                      PhoneNumberRepository phones,
                                      TwilioPhoneProvisioningClient twilio) {
        this.tenantProvider = tenantProvider;
        this.onboarding = onboarding;
        this.profiles = profiles;
        this.agents = agents;
        this.phones = phones;
        this.twilio = twilio;
    }

    public Guide current() {
        UUID businessId = tenantProvider.requireBusinessId();
        OnboardingStatusResponse setup = onboarding.status();
        BusinessProfileResponse profile = profiles.current();
        boolean reservations = profile == null || profile.usesReservations() == null || profile.usesReservations();
        boolean services = reservations || profile == null || profile.sellsServices() == null || profile.sellsServices();
        boolean providerConfigured = twilio.configured();
        String managedAccount = providerConfigured ? twilio.managedAccountSid() : "";

        List<PhoneNumber> assigned = phones.findAllByBusinessIdOrderByCreatedAtDesc(businessId);
        PhoneNumber active = assigned.stream()
                .filter(phone -> businessId.equals(phone.getBusinessId()) && phone.isActive())
                .findFirst().orElse(null);
        boolean verifiedPhone = assigned.stream().anyMatch(phone ->
                businessId.equals(phone.getBusinessId())
                        && phone.isActive()
                        && "TWILIO".equalsIgnoreCase(phone.getProvider())
                        && "MANAGED_PURCHASE".equals(phone.getTwilioBindingSource())
                        && phone.getTwilioAccountSid() != null
                        && phone.getTwilioAccountSid().equals(managedAccount)
                        && phone.getExternalId() != null
                        && PHONE_SID.matcher(phone.getExternalId()).matches()
                        && phone.getVoiceVerifiedAt() != null
                        && providerConfigured);
        boolean agentEnabled = agents.configuredCurrent() && agents.current().isActive();

        List<Step> steps = List.of(
                new Step("BUSINESS", "Datos del negocio", setup.businessProfileConfigured(),
                        "Completa nombre, idioma y zona horaria.", "/app/settings?section=business"),
                new Step("SERVICES", "Catálogo de servicios", !services || setup.servicesConfigured(),
                        "Añade al menos un servicio activo si tu negocio lo necesita.", "/app/settings?section=services"),
                new Step("SCHEDULE", "Horarios de atención", !reservations || setup.scheduleConfigured(),
                        "Configura los horarios cuando trabajas con reservas.", "/app/settings?section=hours"),
                new Step("AGENT", "Recepcionista IA", agentEnabled,
                        "Activa la recepcionista y revisa su saludo y capacidades.", "/app/settings?section=receptionist"),
                new Step("CARRIER", "Conexión segura con Twilio", providerConfigured,
                        "El administrador debe configurar credenciales y webhooks HTTPS del servidor.", null),
                new Step("PHONE", "Número Twilio certificado para este negocio", verifiedPhone,
                        "El número debe estar activo y tener SID, cuenta administrada y verificación de voz coincidentes. Un número histórico no basta.", "/app/settings?section=channels"),
                new Step("LIVE_CALL", "Prueba de llamada real autorizada", false,
                        "Pendiente: certificar audio y acciones con el número real, solo con autorización explícita.", null));
        boolean configurationReady = steps.stream()
                .filter(step -> !"LIVE_CALL".equals(step.code()))
                .allMatch(Step::complete);

        return new Guide(
                configurationReady ? "AWAITING_LIVE_CERTIFICATION" : "CONFIGURATION_INCOMPLETE",
                configurationReady,
                false,
                false,
                active == null ? null : active.getPhoneNumber(),
                steps);
    }

    public record Step(String code, String label, boolean complete, String guidance, String href) {}

    public record Guide(String status,
                        boolean configurationReady,
                        boolean liveCertified,
                        boolean readyForCommercialCalls,
                        String activePhoneNumber,
                        List<Step> steps) {}
}

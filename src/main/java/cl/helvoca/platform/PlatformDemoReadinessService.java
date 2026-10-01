package cl.helvoca.platform;

import cl.helvoca.agent.AiAgent;
import cl.helvoca.agent.AiAgentRepository;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessMode;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.business.BusinessStatus;
import cl.helvoca.operations.ChannelRuntimeReadinessService;
import cl.helvoca.phone.PhoneNumber;
import cl.helvoca.phone.PhoneNumberRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class PlatformDemoReadinessService {
    private static final PlatformDemoReadinessResponse.ReadinessItem PAYMENT =
            item("SANDBOX_ONLY", "Merchant payment LIVE remains disabled.");
    private static final PlatformDemoReadinessResponse.ReadinessItem EFFECTS =
            item("DISARMED", "Outbound external effects are not armed.");

    private final DemoRuntimeProperties properties;
    private final BusinessRepository businesses;
    private final PhoneNumberRepository phones;
    private final AiAgentRepository agents;
    private final ChannelRuntimeReadinessService channels;
    private final DemoSessionRepository sessions;

    public PlatformDemoReadinessService(DemoRuntimeProperties properties,
                                        BusinessRepository businesses,
                                        PhoneNumberRepository phones,
                                        AiAgentRepository agents,
                                        ChannelRuntimeReadinessService channels) {
        this(properties, businesses, phones, agents, channels, null);
    }

    @Autowired
    public PlatformDemoReadinessService(DemoRuntimeProperties properties,
                                        BusinessRepository businesses,
                                        PhoneNumberRepository phones,
                                        AiAgentRepository agents,
                                        ChannelRuntimeReadinessService channels,
                                        DemoSessionRepository sessions) {
        this.properties = properties;
        this.businesses = businesses;
        this.phones = phones;
        this.agents = agents;
        this.channels = channels;
        this.sessions = sessions;
    }

    @Transactional(readOnly = true)
    public PlatformDemoReadinessResponse readiness() {
        UUID runtimeId = properties.runtimeBusinessUuid();
        if (runtimeId == null) {
            PlatformDemoReadinessResponse.ReadinessItem missing =
                    item("NOT_CONFIGURED", "No dedicated DEMO runtime is configured.");
            return response(false, null, missing, missing, missing, missing, missing, missing);
        }

        Business runtime = businesses.findById(runtimeId).orElse(null);
        if (runtime == null) {
            PlatformDemoReadinessResponse.ReadinessItem failed =
                    item("FAILED", "Configured DEMO runtime business does not exist.");
            return response(true, runtimeId, failed, failed, failed, failed, failed, failed);
        }

        if (runtime.getMode() != BusinessMode.DEMO) {
            PlatformDemoReadinessResponse.ReadinessItem failed =
                    item("FAILED", "Configured runtime is not in DEMO mode.");
            return response(true, runtimeId, failed, failed, failed, failed, failed, failed);
        }

        if (runtime.getStatus() != BusinessStatus.ACTIVE) {
            PlatformDemoReadinessResponse.ReadinessItem unavailable =
                    item("UNAVAILABLE", "DEMO runtime is not active.");
            return response(true, runtimeId, unavailable, unavailable, unavailable,
                    unavailable, unavailable, unavailable);
        }

        ChannelRuntimeReadinessService.ChannelRuntimeReadiness channel = channels.snapshot();
        List<PhoneNumber> configuredPhones = phones.findAllByBusinessIdOrderByCreatedAtDesc(runtimeId);
        PhoneNumber voice = configuredPhones.stream()
                .filter(PhoneNumber::isActive)
                .filter(value -> value.getPhoneNumber() != null && !value.getPhoneNumber().isBlank())
                .findFirst()
                .orElse(null);

        AiAgent agent = agents.findByBusinessId(runtimeId)
                .filter(AiAgent::isActive)
                .orElse(null);

        PhoneNumber whatsapp = configuredPhones.stream()
                .filter(PhoneNumber::isActive)
                .filter(PhoneNumber::isWhatsappEnabled)
                .filter(value -> value.getWhatsappCertifiedAt() != null)
                .findFirst()
                .orElse(null);

        PlatformDemoReadinessResponse.ReadinessItem runtimeReady =
                item("READY", runtime.getName());

        PlatformDemoReadinessResponse.ReadinessItem voiceNumber;
        if (voice == null) {
            voiceNumber = item("NOT_CONFIGURED", "No active demo voice number is registered.");
        } else if (!channel.twilio().webhookReady()) {
            voiceNumber = item(
                    "UNAVAILABLE",
                    "Demo number is registered, but telephony ingress is not ready: " + channel.twilio().code());
        } else {
            voiceNumber = item("READY", voice.getPhoneNumber());
        }

        PlatformDemoReadinessResponse.ReadinessItem voiceAi;
        if (agent == null) {
            voiceAi = item("NOT_CONFIGURED", "No active demo AI agent is configured.");
        } else if (!channel.voice().ready()) {
            voiceAi = item(
                    "UNAVAILABLE",
                    "AI agent exists, but voice runtime is not ready: " + channel.voice().code());
        } else {
            String selected = channel.voice().selectedProvider();
            voiceAi = item(
                    "READY",
                    selected == null || selected.isBlank()
                            ? agent.getName()
                            : agent.getName() + " · " + selected);
        }

        PlatformDemoReadinessResponse.ReadinessItem businessData =
                item("NOT_CONFIGURED", "No approved demo profile has been prepared into the runtime yet.");
        if (sessions != null) {
            DemoSession prepared = sessions.findPreparedForRuntime(runtimeId).orElse(null);
            if (prepared != null && prepared.getStagedAt() != null) {
                businessData = item(
                        "READY",
                        "Profile " + prepared.getDemoProfileId()
                                + " staged as revision " + prepared.getConfigurationRevision()
                                + " for session " + prepared.getCorrelationId() + ".");
            }
        }
        PlatformDemoReadinessResponse.ReadinessItem operations =
                item("READY", "DEMO operations are isolated from PILOT/CUSTOMER tenants.");

        PlatformDemoReadinessResponse.ReadinessItem whatsappState;
        if (whatsapp == null) {
            whatsappState = item("NOT_CONFIGURED", "No certified demo WhatsApp channel is configured.");
        } else if (!channel.whatsApp().ready()) {
            whatsappState = item(
                    "UNAVAILABLE",
                    "Demo WhatsApp identity is certified, but runtime delivery is not ready: "
                            + channel.whatsApp().code());
        } else {
            whatsappState = item("READY", "Certified demo WhatsApp channel is available.");
        }

        return response(true, runtimeId, runtimeReady, voiceNumber, voiceAi,
                businessData, operations, whatsappState);
    }

    private static PlatformDemoReadinessResponse response(
            boolean configured,
            UUID runtimeId,
            PlatformDemoReadinessResponse.ReadinessItem runtime,
            PlatformDemoReadinessResponse.ReadinessItem voiceNumber,
            PlatformDemoReadinessResponse.ReadinessItem voiceAi,
            PlatformDemoReadinessResponse.ReadinessItem businessData,
            PlatformDemoReadinessResponse.ReadinessItem operations,
            PlatformDemoReadinessResponse.ReadinessItem whatsapp) {
        return new PlatformDemoReadinessResponse(
                configured,
                runtimeId,
                runtime,
                voiceNumber,
                voiceAi,
                businessData,
                operations,
                whatsapp,
                PAYMENT,
                EFFECTS);
    }

    private static PlatformDemoReadinessResponse.ReadinessItem item(String state, String detail) {
        return PlatformDemoReadinessResponse.ReadinessItem.of(state, detail);
    }
}

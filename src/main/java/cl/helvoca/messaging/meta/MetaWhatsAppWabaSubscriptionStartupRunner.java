package cl.helvoca.messaging.meta;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.function.Function;

@Component
public class MetaWhatsAppWabaSubscriptionStartupRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(MetaWhatsAppWabaSubscriptionStartupRunner.class);

    private final boolean enabled;
    private final String wabaId;
    private final String credentialRef;
    private final MetaWhatsAppCloudClient client;
    private final Function<String, String> environment;

    @Autowired
    public MetaWhatsAppWabaSubscriptionStartupRunner(
            @Value("${HELVOCA_META_WHATSAPP_WABA_SUBSCRIBE_ON_STARTUP:false}") boolean enabled,
            @Value("${HELVOCA_META_WHATSAPP_TEST_WABA_ID:}") String wabaId,
            @Value("${HELVOCA_META_WHATSAPP_TEST_CREDENTIAL_REF:PILOT_01}") String credentialRef,
            MetaWhatsAppCloudClient client) {
        this(enabled, wabaId, credentialRef, client, System::getenv);
    }

    MetaWhatsAppWabaSubscriptionStartupRunner(
            boolean enabled,
            String wabaId,
            String credentialRef,
            MetaWhatsAppCloudClient client,
            Function<String, String> environment) {
        this.enabled = enabled;
        this.wabaId = clean(wabaId);
        this.credentialRef = clean(credentialRef).toUpperCase(Locale.ROOT);
        this.client = client;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) return;

        if (!wabaId.matches("^[0-9]{5,30}$")) {
            throw new IllegalStateException("HELVOCA_META_WHATSAPP_TEST_WABA_ID is invalid");
        }
        if (!credentialRef.matches("^[A-Z0-9_]{2,80}$")) {
            throw new IllegalStateException("HELVOCA_META_WHATSAPP_TEST_CREDENTIAL_REF is invalid");
        }

        String variable = "HELVOCA_META_WHATSAPP_" + credentialRef + "_ACCESS_TOKEN";
        String accessToken = clean(environment.apply(variable));
        if (accessToken.isBlank()) {
            throw new IllegalStateException("Meta WhatsApp WABA subscription access token is missing");
        }

        client.subscribeWaba(wabaId, accessToken);
        log.info(
                "META_WHATSAPP_WABA_SUBSCRIBED wabaIdEnding={} subscriptionReady=true",
                lastFour(wabaId));
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String lastFour(String value) {
        return value.length() <= 4 ? value : value.substring(value.length() - 4);
    }
}

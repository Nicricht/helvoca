package cl.helvoca.messaging.meta;

import cl.helvoca.messaging.outbound.MetaWhatsAppMessagingProvider;
import cl.helvoca.phone.PhoneNumber;
import cl.helvoca.phone.PhoneNumberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class MetaWhatsAppTestBootstrapRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(MetaWhatsAppTestBootstrapRunner.class);

    private final boolean enabled;
    private final String anchorPhone;
    private final String providerPhoneNumberId;
    private final String wabaId;
    private final String credentialRef;
    private final PhoneNumberRepository phones;
    private final MetaWhatsAppTenantConfigRepository configs;

    public MetaWhatsAppTestBootstrapRunner(
            @Value("${HELVOCA_META_WHATSAPP_TEST_BOOTSTRAP_ON_STARTUP:false}") boolean enabled,
            @Value("${HELVOCA_WHATSAPP_SANDBOX_TENANT_PHONE_E164:}") String anchorPhone,
            @Value("${HELVOCA_META_WHATSAPP_TEST_PHONE_NUMBER_ID:}") String providerPhoneNumberId,
            @Value("${HELVOCA_META_WHATSAPP_TEST_WABA_ID:}") String wabaId,
            @Value("${HELVOCA_META_WHATSAPP_TEST_CREDENTIAL_REF:PILOT_01}") String credentialRef,
            PhoneNumberRepository phones,
            MetaWhatsAppTenantConfigRepository configs) {
        this.enabled = enabled;
        this.anchorPhone = clean(anchorPhone);
        this.providerPhoneNumberId = clean(providerPhoneNumberId);
        this.wabaId = clean(wabaId);
        this.credentialRef = clean(credentialRef).toUpperCase();
        this.phones = phones;
        this.configs = configs;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) return;

        validate();

        PhoneNumber phone = phones.findByPhoneNumberAndActiveTrue(anchorPhone)
                .orElseThrow(() -> new IllegalStateException(
                        "Meta WhatsApp test bootstrap anchor phone is not mapped to an active tenant"));

        phone.setWhatsappProvider(MetaWhatsAppMessagingProvider.ID);
        phone.setWhatsappExternalId(providerPhoneNumberId);
        phone.setWhatsappEnabled(true);
        phone.setWhatsappCertifiedAt(null);
        phones.save(phone);

        MetaWhatsAppTenantConfig config = configs.findById(phone.getBusinessId())
                .orElseGet(MetaWhatsAppTenantConfig::new);
        config.setBusinessId(phone.getBusinessId());
        config.setCredentialRef(credentialRef);
        config.setWabaId(wabaId);
        config.setEnabled(true);
        configs.save(config);

        log.info(
                "META_WHATSAPP_TEST_BOOTSTRAPPED phoneNumberIdEnding={} wabaIdEnding={} tenantReady=true",
                lastFour(providerPhoneNumberId),
                lastFour(wabaId));
    }

    private void validate() {
        if (!providerPhoneNumberId.matches("^[0-9]{5,30}$")) {
            throw new IllegalStateException("HELVOCA_META_WHATSAPP_TEST_PHONE_NUMBER_ID is invalid");
        }
        if (!wabaId.matches("^[0-9]{5,30}$")) {
            throw new IllegalStateException("HELVOCA_META_WHATSAPP_TEST_WABA_ID is invalid");
        }
        if (!credentialRef.matches("^[A-Z0-9_]{2,80}$")) {
            throw new IllegalStateException("HELVOCA_META_WHATSAPP_TEST_CREDENTIAL_REF is invalid");
        }
        if (anchorPhone.isBlank()) {
            throw new IllegalStateException("HELVOCA_WHATSAPP_SANDBOX_TENANT_PHONE_E164 is required");
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String lastFour(String value) {
        return value.length() <= 4 ? value : value.substring(value.length() - 4);
    }
}

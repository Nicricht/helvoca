package cl.helvoca.messaging.meta;

import cl.helvoca.jobs.PersistentJobProperties;
import cl.helvoca.messaging.outbound.OutboundMessagingProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MetaWhatsAppDeploymentReadinessServiceTest {

    @Test
    void blocksWhenMetaWebhookSecretsAreMissingEvenWithTrafficGatesClosed() {
        MetaWhatsAppProperties meta = new MetaWhatsAppProperties();
        OutboundMessagingProperties outbound = new OutboundMessagingProperties();
        PersistentJobProperties jobs = new PersistentJobProperties();

        var response = new MetaWhatsAppDeploymentReadinessService(meta, outbound, jobs).readiness();

        assertEquals("BLOCKED", response.state());
        assertFalse(response.readyForTenantStaging());
        assertTrue(response.webhookValidationEnabled());
        assertFalse(response.appSecretConfigured());
        assertFalse(response.verifyTokenConfigured());
        assertFalse(response.globalMetaEnabled());
        assertFalse(response.outboundDeliveryEnabled());
        assertEquals("NONE", response.outboundProvider());
        assertFalse(response.jobsEnabled());
        assertTrue(response.blockers().stream().anyMatch(item -> "APP_SECRET_MISSING".equals(item.code())));
        assertTrue(response.blockers().stream().anyMatch(item -> "VERIFY_TOKEN_MISSING".equals(item.code())));
    }

    @Test
    void reportsReadyOnlyWithWebhookSecurityConfiguredAndAllTrafficGatesClosed() {
        MetaWhatsAppProperties meta = new MetaWhatsAppProperties();
        meta.setWebhookValidationEnabled(true);
        meta.setAppSecret("secret-value");
        meta.setVerifyToken("verify-value");

        OutboundMessagingProperties outbound = new OutboundMessagingProperties();
        outbound.setDeliveryEnabled(false);
        outbound.setProvider("none");

        PersistentJobProperties jobs = new PersistentJobProperties();
        jobs.setEnabled(false);

        var response = new MetaWhatsAppDeploymentReadinessService(meta, outbound, jobs).readiness();

        assertEquals("READY_FOR_TENANT_STAGING", response.state());
        assertTrue(response.readyForTenantStaging());
        assertTrue(response.appSecretConfigured());
        assertTrue(response.verifyTokenConfigured());
        assertTrue(response.blockers().isEmpty());
        assertFalse(response.toString().contains("secret-value"));
        assertFalse(response.toString().contains("verify-value"));
    }

    @Test
    void blocksWhenAnyRealTrafficGateIsOpen() {
        MetaWhatsAppProperties meta = new MetaWhatsAppProperties();
        meta.setAppSecret("secret");
        meta.setVerifyToken("verify");
        meta.setEnabled(true);

        OutboundMessagingProperties outbound = new OutboundMessagingProperties();
        outbound.setDeliveryEnabled(true);
        outbound.setProvider("META_WHATSAPP_CLOUD");

        PersistentJobProperties jobs = new PersistentJobProperties();
        jobs.setEnabled(true);

        var response = new MetaWhatsAppDeploymentReadinessService(meta, outbound, jobs).readiness();

        assertFalse(response.readyForTenantStaging());
        assertTrue(response.blockers().stream().anyMatch(item -> "GLOBAL_META_MUST_BE_DISABLED".equals(item.code())));
        assertTrue(response.blockers().stream().anyMatch(item -> "OUTBOUND_DELIVERY_MUST_BE_DISABLED".equals(item.code())));
        assertTrue(response.blockers().stream().anyMatch(item -> "OUTBOUND_PROVIDER_MUST_BE_NONE".equals(item.code())));
        assertTrue(response.blockers().stream().anyMatch(item -> "JOBS_MUST_BE_DISABLED".equals(item.code())));
    }
}

package cl.helvoca.messaging.meta;

import cl.helvoca.messaging.WhatsAppReceptionistService;
import cl.helvoca.messaging.outbound.MetaWhatsAppDeliveryStatusService;
import cl.helvoca.security.TenantDatabaseContext;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class MetaWhatsAppWebhookStatusTest {

    @Test
    void routesMetaDeliveryStatusInsideResolvedTenantScope() {
        MetaWhatsAppProperties properties = new MetaWhatsAppProperties();
        properties.setEnabled(true);
        properties.setWebhookValidationEnabled(false);

        MetaWhatsAppTenantResolver resolver = mock(MetaWhatsAppTenantResolver.class);
        MetaWhatsAppDeliveryStatusService deliveryStatus = mock(MetaWhatsAppDeliveryStatusService.class);
        UUID businessId = UUID.randomUUID();
        UUID phoneRecordId = UUID.randomUUID();

        when(resolver.resolveRoute("PHONE-123"))
                .thenReturn(Optional.of(new MetaWhatsAppTenantRoute(
                        businessId, phoneRecordId, "+56955555555")));
        when(deliveryStatus.apply(
                eq(businessId),
                eq("wamid.STATUS-1"),
                eq("delivered"),
                eq(Instant.ofEpochSecond(1720000000L)),
                isNull()))
                .thenReturn(MetaWhatsAppDeliveryStatusService.Result.UPDATED);

        var controller = new MetaWhatsAppWebhookController(
                properties,
                resolver,
                new TenantDatabaseContext(),
                mock(WhatsAppReceptionistService.class));
        ReflectionTestUtils.setField(controller, "deliveryStatus", deliveryStatus);

        byte[] body = """
                {
                  "entry": [{
                    "changes": [{
                      "value": {
                        "metadata": {"phone_number_id": "PHONE-123"},
                        "statuses": [{
                          "id": "wamid.STATUS-1",
                          "status": "delivered",
                          "timestamp": "1720000000"
                        }]
                      }
                    }]
                  }]
                }
                """.getBytes(StandardCharsets.UTF_8);

        var response = controller.inbound(null, body);

        assertEquals(200, response.getStatusCode().value());
        verify(deliveryStatus).apply(
                businessId,
                "wamid.STATUS-1",
                "delivered",
                Instant.ofEpochSecond(1720000000L),
                null);
    }

    @Test
    void unknownProviderMessageReturnsRetryableWebhookResponse() {
        MetaWhatsAppProperties properties = new MetaWhatsAppProperties();
        properties.setEnabled(true);
        properties.setWebhookValidationEnabled(false);

        MetaWhatsAppTenantResolver resolver = mock(MetaWhatsAppTenantResolver.class);
        MetaWhatsAppDeliveryStatusService deliveryStatus = mock(MetaWhatsAppDeliveryStatusService.class);
        UUID businessId = UUID.randomUUID();
        UUID phoneRecordId = UUID.randomUUID();

        when(resolver.resolveRoute("PHONE-123"))
                .thenReturn(Optional.of(new MetaWhatsAppTenantRoute(
                        businessId, phoneRecordId, "+56955555555")));
        when(deliveryStatus.apply(any(), anyString(), anyString(), any(), any()))
                .thenReturn(MetaWhatsAppDeliveryStatusService.Result.NOT_FOUND);

        var controller = new MetaWhatsAppWebhookController(
                properties,
                resolver,
                new TenantDatabaseContext(),
                mock(WhatsAppReceptionistService.class));
        ReflectionTestUtils.setField(controller, "deliveryStatus", deliveryStatus);

        byte[] body = """
                {
                  "entry": [{
                    "changes": [{
                      "value": {
                        "metadata": {"phone_number_id": "PHONE-123"},
                        "statuses": [{
                          "id": "wamid.RACE-1",
                          "status": "sent",
                          "timestamp": "1720000000"
                        }]
                      }
                    }]
                  }]
                }
                """.getBytes(StandardCharsets.UTF_8);

        assertEquals(503, controller.inbound(null, body).getStatusCode().value());
    }
}

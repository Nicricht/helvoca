package cl.helvoca.operations;

import cl.helvoca.catalog.CatalogItemRepository;
import cl.helvoca.delivery.DeliveryCoverageMatcher;
import cl.helvoca.delivery.DeliveryZone;
import cl.helvoca.delivery.DeliveryZoneRepository;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Adds conservative address coverage validation on top of the universal
 * commercial operation engine. This bean is primary so voice and WhatsApp use
 * the validated path without coupling the core order calculator to address
 * matching policy.
 */
@Service
@Primary
public class ValidatedCommercialOperationToolService extends CommercialOperationToolService {
    private static final String VALIDATE_DELIVERY_ADDRESS = "validate_delivery_address";

    private final DeliveryZoneRepository deliveryZones;
    private final DeliveryCoverageMatcher coverageMatcher;

    public ValidatedCommercialOperationToolService(CatalogItemRepository catalog,
                                                   DeliveryZoneRepository deliveryZones,
                                                   BusinessOrderRepository orders,
                                                   BusinessOrderLineRepository orderLines,
                                                   BusinessQuoteRepository quotes,
                                                   BusinessLeadRepository leads,
                                                   BusinessOperationCapabilityService capabilities,
                                                   DeliveryCoverageMatcher coverageMatcher) {
        super(catalog, deliveryZones, orders, orderLines, quotes, leads, capabilities);
        this.deliveryZones = deliveryZones;
        this.coverageMatcher = coverageMatcher;
    }

    @Override
    public boolean supports(String toolName) {
        return VALIDATE_DELIVERY_ADDRESS.equals(toolName) || super.supports(toolName);
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public String execute(UUID businessId,
                          UUID customerId,
                          UUID sourceReferenceId,
                          String trustedPhone,
                          BusinessOrder.Source source,
                          String toolName,
                          String rawArguments) {
        JSONObject args;
        try {
            args = rawArguments == null || rawArguments.isBlank()
                    ? new JSONObject()
                    : new JSONObject(rawArguments);
        } catch (Exception e) {
            return error("INVALID_ARGUMENT", "Los datos de la operación no son válidos.").toString();
        }

        if (VALIDATE_DELIVERY_ADDRESS.equals(toolName)) {
            return validateAddress(businessId, args).toString();
        }
        if ("list_delivery_zones".equals(toolName)) {
            return listZonesWithCoverage(businessId).toString();
        }
        if (("quote_order".equals(toolName) || "create_order".equals(toolName))
                && "DELIVERY".equalsIgnoreCase(args.optString("fulfillmentType", ""))) {
            JSONObject validation = validateSelectedZone(businessId, args);
            if (!validation.optBoolean("success", false)) return validation.toString();
        }
        return super.execute(businessId, customerId, sourceReferenceId, trustedPhone, source, toolName, rawArguments);
    }

    private JSONObject validateAddress(UUID businessId, JSONObject args) {
        String address = required(args, "address");
        String requestedZone = optional(args, "deliveryZoneId");

        if (requestedZone != null) {
            UUID zoneId = uuid(requestedZone);
            DeliveryZone zone = deliveryZones.findByIdAndBusinessId(zoneId, businessId)
                    .filter(DeliveryZone::isActive)
                    .orElse(null);
            if (zone == null) {
                return error("DELIVERY_ZONE_NOT_FOUND", "La zona de despacho no existe o no está activa.");
            }
            boolean covered = coverageMatcher.matches(zone, address);
            return success(new JSONObject()
                    .put("covered", covered)
                    .put("address", address)
                    .put("deliveryZoneId", zone.getId().toString())
                    .put("deliveryZone", zone.getName())
                    .put("fee", zone.getFee())
                    .put("minimumOrder", nullable(zone.getMinimumOrder()))
                    .put("coverageConfirmedBy", covered ? "CONFIGURED_TERMS" : JSONObject.NULL));
        }

        JSONArray matches = new JSONArray();
        List<DeliveryZone> zones = deliveryZones.findAllByBusinessIdAndActiveTrueOrderByNameAsc(businessId);
        for (DeliveryZone zone : zones) {
            if (!coverageMatcher.matches(zone, address)) continue;
            matches.put(zoneData(zone));
        }
        return success(new JSONObject()
                .put("covered", matches.length() > 0)
                .put("address", address)
                .put("matches", matches)
                .put("unambiguous", matches.length() == 1));
    }

    private JSONObject validateSelectedZone(UUID businessId, JSONObject args) {
        String zoneIdRaw = optional(args, "deliveryZoneId");
        String address = optional(args, "address");
        if (zoneIdRaw == null || address == null) {
            return error("DELIVERY_ADDRESS_REQUIRED",
                    "Para despacho se necesita una zona configurada y una dirección completa.");
        }
        UUID zoneId = uuid(zoneIdRaw);
        DeliveryZone zone = deliveryZones.findByIdAndBusinessId(zoneId, businessId)
                .filter(DeliveryZone::isActive)
                .orElse(null);
        if (zone == null) {
            return error("DELIVERY_ZONE_NOT_FOUND", "La zona de despacho no existe o no está activa.");
        }
        if (!coverageMatcher.matches(zone, address)) {
            return error("DELIVERY_ADDRESS_NOT_COVERED",
                    "La dirección no coincide con la cobertura configurada para esa zona. Confirma otra zona o deriva a una persona.");
        }
        return success(new JSONObject()
                .put("covered", true)
                .put("deliveryZoneId", zone.getId().toString())
                .put("deliveryZone", zone.getName())
                .put("address", address));
    }

    private JSONObject listZonesWithCoverage(UUID businessId) {
        JSONArray zones = new JSONArray();
        for (DeliveryZone zone : deliveryZones.findAllByBusinessIdAndActiveTrueOrderByNameAsc(businessId)) {
            zones.put(zoneData(zone));
        }
        return success(new JSONObject().put("zones", zones));
    }

    private JSONObject zoneData(DeliveryZone zone) {
        return new JSONObject()
                .put("id", zone.getId().toString())
                .put("name", zone.getName())
                .put("coverageTerms", zone.getCoverageTerms())
                .put("fee", zone.getFee())
                .put("minimumOrder", nullable(zone.getMinimumOrder()));
    }

    private static String required(JSONObject args, String key) {
        String value = optional(args, key);
        if (value == null) throw new IllegalArgumentException("Falta el dato requerido: " + key + ".");
        return value;
    }

    private static String optional(JSONObject args, String key) {
        if (args == null || !args.has(key) || args.opt(key) == JSONObject.NULL) return null;
        String value = String.valueOf(args.get(key)).trim();
        return value.isBlank() ? null : value;
    }

    private static UUID uuid(String value) {
        try { return UUID.fromString(value.trim()); }
        catch (Exception e) { throw new IllegalArgumentException("Se recibió un identificador inválido."); }
    }

    private static Object nullable(Object value) {
        return value == null ? JSONObject.NULL : value;
    }

    private static JSONObject success(JSONObject data) {
        return new JSONObject().put("success", true).put("data", data).put("error", JSONObject.NULL);
    }

    private static JSONObject error(String code, String message) {
        return new JSONObject()
                .put("success", false)
                .put("data", JSONObject.NULL)
                .put("error", new JSONObject().put("code", code).put("message", message));
    }
}

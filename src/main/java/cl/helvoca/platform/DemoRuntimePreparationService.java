package cl.helvoca.platform;

import cl.helvoca.agent.AiAgent;
import cl.helvoca.agent.AiAgentRepository;
import cl.helvoca.agent.AiCapability;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessMode;
import cl.helvoca.business.BusinessProfile;
import cl.helvoca.business.BusinessProfileRepository;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.business.BusinessStatus;
import cl.helvoca.catalog.CatalogItem;
import cl.helvoca.catalog.CatalogItemRepository;
import cl.helvoca.knowledge.KnowledgeItem;
import cl.helvoca.knowledge.KnowledgeItemRepository;
import cl.helvoca.schedule.BusinessHour;
import cl.helvoca.schedule.BusinessHourRepository;
import cl.helvoca.servicecatalog.ServiceItem;
import cl.helvoca.servicecatalog.ServiceItemRepository;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;

@Service
public class DemoRuntimePreparationService {
    private final BusinessRepository businesses;
    private final BusinessProfileRepository businessProfiles;
    private final AiAgentRepository agents;
    private final CatalogItemRepository catalog;
    private final ServiceItemRepository services;
    private final BusinessHourRepository hours;
    private final KnowledgeItemRepository knowledge;

    public DemoRuntimePreparationService(BusinessRepository businesses,
                                         BusinessProfileRepository businessProfiles,
                                         AiAgentRepository agents,
                                         CatalogItemRepository catalog,
                                         ServiceItemRepository services,
                                         BusinessHourRepository hours,
                                         KnowledgeItemRepository knowledge) {
        this.businesses = businesses;
        this.businessProfiles = businessProfiles;
        this.agents = agents;
        this.catalog = catalog;
        this.services = services;
        this.hours = hours;
        this.knowledge = knowledge;
    }

    @Transactional
    public StageResult stage(UUID runtimeBusinessId, DemoProfile profile) {
        if (runtimeBusinessId == null) throw new IllegalArgumentException("Demo runtime business is required");
        if (profile == null || profile.getId() == null) throw new IllegalArgumentException("Approved demo profile is required");

        PreparedSnapshot snapshot = parse(profile);

        Business business = businesses.findById(runtimeBusinessId)
                .orElseThrow(() -> new IllegalStateException("Configured DEMO runtime business does not exist"));
        if (business.getMode() != BusinessMode.DEMO) {
            throw new IllegalStateException("Configured runtime is not in DEMO mode");
        }
        if (business.getStatus() != BusinessStatus.ACTIVE) {
            throw new IllegalStateException("Configured DEMO runtime is not active");
        }

        business.setName(snapshot.businessName());
        business.setTimezone(snapshot.timezone());
        business.setLanguage(snapshot.language());
        businesses.saveAndFlush(business);

        syncBusinessProfile(runtimeBusinessId, snapshot);
        syncAgent(runtimeBusinessId, snapshot);
        syncCatalogAndServices(runtimeBusinessId, snapshot);
        syncHours(runtimeBusinessId, snapshot.hours());
        syncKnowledge(runtimeBusinessId, snapshot.knowledge());

        return new StageResult(revision(profile));
    }

    private void syncBusinessProfile(UUID businessId, PreparedSnapshot snapshot) {
        BusinessProfile value = businessProfiles.findById(businessId).orElseGet(() -> {
            BusinessProfile created = new BusinessProfile();
            created.setBusinessId(businessId);
            return created;
        });
        value.setPresetKey("demo");
        value.setDefaultCurrency(snapshot.defaultCurrency());
        value.setSellsProducts(snapshot.catalog().stream().anyMatch(item -> item.kind() == CatalogItem.Kind.PRODUCT));
        value.setSellsServices(snapshot.catalog().stream().anyMatch(item -> item.kind() == CatalogItem.Kind.SERVICE));
        value.setUsesReservations(snapshot.capabilities().contains("BOOKING"));
        businessProfiles.saveAndFlush(value);
    }

    private void syncAgent(UUID businessId, PreparedSnapshot snapshot) {
        AiAgent agent = agents.findByBusinessId(businessId).orElseGet(() -> {
            AiAgent created = new AiAgent();
            created.setBusinessId(businessId);
            return created;
        });
        agent.setName(snapshot.businessName());
        agent.setLanguage(snapshot.language());
        agent.setGreeting(snapshot.greeting());
        agent.setInstructions(snapshot.instructions());
        agent.setCapabilities(aiCapabilities(snapshot));
        agent.setActive(true);
        // Intentionally preserve the runtime-owned voice selection. The approved
        // profile changes behavior/content, not certified provider voice routing.
        agents.saveAndFlush(agent);
    }

    private void syncCatalogAndServices(UUID businessId, PreparedSnapshot snapshot) {
        List<CatalogItem> currentCatalog = catalog.findAllByBusinessIdOrderByNameAsc(businessId);
        Map<String, CatalogItem> catalogByKey = new HashMap<>();
        for (CatalogItem item : currentCatalog) {
            catalogByKey.put(catalogKey(item.getKind(), item.getName()), item);
        }

        List<ServiceItem> currentServices = services.findAllByBusinessIdOrderByNameAsc(businessId);
        Map<String, ServiceItem> serviceByName = new HashMap<>();
        for (ServiceItem item : currentServices) {
            serviceByName.put(normalized(item.getName()), item);
        }

        Set<String> desiredCatalog = new HashSet<>();
        Set<String> desiredServices = new HashSet<>();

        for (CatalogSnapshot input : snapshot.catalog()) {
            String catalogKey = catalogKey(input.kind(), input.name());
            desiredCatalog.add(catalogKey);

            UUID serviceId = null;
            if (input.kind() == CatalogItem.Kind.SERVICE) {
                String serviceKey = normalized(input.name());
                desiredServices.add(serviceKey);
                ServiceItem service = serviceByName.get(serviceKey);
                if (service == null) {
                    service = new ServiceItem();
                    service.setBusinessId(businessId);
                }
                service.setName(input.name());
                service.setDescription(input.description());
                service.setDurationMinutes(input.durationMinutes());
                service.setPrice(input.price());
                service.setActive(true);
                service = services.save(service);
                serviceId = service.getId();
                serviceByName.put(serviceKey, service);
            }

            CatalogItem item = catalogByKey.get(catalogKey);
            if (item == null) {
                item = new CatalogItem();
                item.setBusinessId(businessId);
                item.setKind(input.kind());
            }
            item.setName(input.name());
            item.setDescription(input.description());
            item.setPrice(input.price());
            item.setCurrency(input.currency());
            item.setDurationMinutes(input.durationMinutes());
            item.setLegacyServiceId(serviceId);
            item.setMetadataJson(new JSONObject(Map.of(
                    "importMethod", "DEMO_PROFILE",
                    "demoProfileId", snapshot.profileId().toString())).toString());
            item.setActive(true);
            catalog.save(item);
            catalogByKey.put(catalogKey, item);
        }

        for (CatalogItem item : currentCatalog) {
            if (item.isActive() && !desiredCatalog.contains(catalogKey(item.getKind(), item.getName()))) {
                item.setActive(false);
                catalog.save(item);
            }
        }
        for (ServiceItem item : currentServices) {
            if (item.isActive() && !desiredServices.contains(normalized(item.getName()))) {
                item.setActive(false);
                services.save(item);
            }
        }
    }

    private void syncHours(UUID businessId, List<HourSnapshot> approved) {
        hours.deleteAllByBusinessId(businessId);
        for (HourSnapshot input : approved) {
            BusinessHour value = new BusinessHour();
            value.setBusinessId(businessId);
            value.setDayOfWeek(input.dayOfWeek());
            value.setOpenTime(input.openTime());
            value.setCloseTime(input.closeTime());
            hours.save(value);
        }
    }

    private void syncKnowledge(UUID businessId, List<KnowledgeSnapshot> approved) {
        List<KnowledgeItem> current = knowledge.findAllByBusinessIdOrderByTitleAsc(businessId);
        Map<String, KnowledgeItem> byTitle = new HashMap<>();
        for (KnowledgeItem item : current) byTitle.put(normalized(item.getTitle()), item);

        Set<String> desired = new HashSet<>();
        for (KnowledgeSnapshot input : approved) {
            String key = normalized(input.title());
            desired.add(key);
            KnowledgeItem value = byTitle.get(key);
            if (value == null) {
                value = new KnowledgeItem();
                value.setBusinessId(businessId);
            }
            value.setTitle(input.title());
            value.setCategory(input.category());
            value.setContent(input.content());
            value.setActive(true);
            value = knowledge.save(value);
            byTitle.put(key, value);
        }

        for (KnowledgeItem value : current) {
            if (value.isActive() && !desired.contains(normalized(value.getTitle()))) {
                value.setActive(false);
                knowledge.save(value);
            }
        }
    }

    private static PreparedSnapshot parse(DemoProfile profile) {
        String businessName = required(profile.getBusinessName(), "Demo business name is required");
        String timezone = required(profile.getTimezone(), "Demo timezone is required");
        try {
            ZoneId.of(timezone);
        } catch (Exception e) {
            throw new IllegalArgumentException("Demo timezone is invalid");
        }
        String language = required(profile.getLanguage(), "Demo language is required").toLowerCase(Locale.ROOT);
        String greeting = required(profile.getGreeting(), "Demo greeting is required");
        String instructions = blankToNull(profile.getInstructions());

        List<CatalogSnapshot> catalog = parseCatalog(profile.getCatalog());
        List<HourSnapshot> hours = parseHours(profile.getHours());
        List<KnowledgeSnapshot> knowledge = parseKnowledge(profile.getKnowledge());
        Set<String> capabilities = parseCapabilities(profile.getCapabilities());

        String defaultCurrency = catalog.stream()
                .map(CatalogSnapshot::currency)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("CLP");

        return new PreparedSnapshot(
                profile.getId(), businessName, timezone, language, greeting, instructions,
                catalog, hours, knowledge, capabilities, defaultCurrency);
    }

    private static List<CatalogSnapshot> parseCatalog(Map<String, Object> source) {
        Object raw = source == null ? null : source.get("items");
        if (raw == null) return List.of();
        if (!(raw instanceof Iterable<?> iterable)) {
            throw new IllegalArgumentException("Demo catalog items must be a list");
        }

        List<CatalogSnapshot> result = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (Object value : iterable) {
            Map<?, ?> item = requireMap(value, "Demo catalog item must be an object");
            CatalogItem.Kind kind;
            try {
                kind = CatalogItem.Kind.valueOf(required(item.get("kind"), "Catalog kind is required")
                        .toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Catalog kind must be PRODUCT or SERVICE");
            }
            String name = required(item.get("name"), "Catalog name is required");
            BigDecimal price = decimal(item.get("price"), BigDecimal.ZERO);
            if (price.signum() < 0) throw new IllegalArgumentException("Catalog price cannot be negative");
            String currency = optional(item.get("currency"), "CLP").toUpperCase(Locale.ROOT);
            if (!currency.matches("^[A-Z]{3}$")) throw new IllegalArgumentException("Catalog currency is invalid");
            Integer duration = integer(item.get("durationMinutes"));
            if (kind == CatalogItem.Kind.SERVICE && (duration == null || duration <= 0)) {
                throw new IllegalArgumentException("Demo services require a positive duration");
            }
            if (kind == CatalogItem.Kind.PRODUCT) duration = null;
            String key = catalogKey(kind, name);
            if (!keys.add(key)) throw new IllegalArgumentException("Demo catalog contains duplicate items");
            result.add(new CatalogSnapshot(
                    kind, name, blankToNull(string(item.get("description"))), price, currency, duration));
        }
        return List.copyOf(result);
    }

    private static List<HourSnapshot> parseHours(Map<String, Object> source) {
        Object raw = source == null ? null : source.get("intervals");
        if (raw == null) return List.of();
        if (!(raw instanceof Iterable<?> iterable)) {
            throw new IllegalArgumentException("Demo hours intervals must be a list");
        }
        List<HourSnapshot> result = new ArrayList<>();
        for (Object value : iterable) {
            Map<?, ?> item = requireMap(value, "Demo hour interval must be an object");
            Integer day = integer(item.get("dayOfWeek"));
            if (day == null || day < 1 || day > 7) throw new IllegalArgumentException("Demo dayOfWeek must be 1..7");
            LocalTime open = localTime(item.get("openTime"), "Demo opening time is invalid");
            LocalTime close = localTime(item.get("closeTime"), "Demo closing time is invalid");
            if (!open.isBefore(close)) throw new IllegalArgumentException("Demo opening time must be before closing time");
            result.add(new HourSnapshot(day, open, close));
        }
        for (int day = 1; day <= 7; day++) {
            int currentDay = day;
            List<HourSnapshot> sameDay = result.stream()
                    .filter(value -> value.dayOfWeek() == currentDay)
                    .sorted(Comparator.comparing(HourSnapshot::openTime))
                    .toList();
            for (int i = 1; i < sameDay.size(); i++) {
                if (sameDay.get(i).openTime().isBefore(sameDay.get(i - 1).closeTime())) {
                    throw new IllegalArgumentException("Demo business hour intervals cannot overlap");
                }
            }
        }
        return List.copyOf(result);
    }

    private static List<KnowledgeSnapshot> parseKnowledge(Map<String, Object> source) {
        Object raw = source == null ? null : source.get("items");
        if (raw == null) return List.of();
        if (!(raw instanceof Iterable<?> iterable)) {
            throw new IllegalArgumentException("Demo knowledge items must be a list");
        }
        List<KnowledgeSnapshot> result = new ArrayList<>();
        Set<String> titles = new HashSet<>();
        for (Object value : iterable) {
            Map<?, ?> item = requireMap(value, "Demo knowledge item must be an object");
            String title = required(item.get("title"), "Knowledge title is required");
            if (!titles.add(normalized(title))) {
                throw new IllegalArgumentException("Demo knowledge contains duplicate titles");
            }
            String content = required(item.get("content"), "Knowledge content is required");
            result.add(new KnowledgeSnapshot(title, blankToNull(string(item.get("category"))), content));
        }
        return List.copyOf(result);
    }

    private static Set<String> parseCapabilities(List<String> source) {
        if (source == null) return Set.of();
        Set<String> allowed = Set.of("ORDER", "BOOKING", "QUOTE", "LEAD", "REQUEST", "DELIVERY");
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : source) {
            if (value == null || value.isBlank()) continue;
            String normalized = value.trim().toUpperCase(Locale.ROOT);
            if (!allowed.contains(normalized)) {
                throw new IllegalArgumentException("Unsupported demo capability: " + normalized);
            }
            result.add(normalized);
        }
        return Set.copyOf(result);
    }

    private static EnumSet<AiCapability> aiCapabilities(PreparedSnapshot snapshot) {
        EnumSet<AiCapability> out = EnumSet.of(
                AiCapability.GET_BUSINESS_INFORMATION,
                AiCapability.LIST_SERVICES,
                AiCapability.SEARCH_KNOWLEDGE,
                AiCapability.FIND_CALLER,
                AiCapability.REGISTER_CALLER,
                AiCapability.RECORD_UNANSWERED_QUESTION);

        if (snapshot.catalog().stream().anyMatch(item -> item.kind() == CatalogItem.Kind.PRODUCT)) {
            out.add(AiCapability.LIST_CATALOG);
        }
        if (snapshot.capabilities().contains("BOOKING")) {
            out.addAll(EnumSet.of(
                    AiCapability.LIST_AVAILABLE_SLOTS,
                    AiCapability.CHECK_BOOKING_AVAILABILITY,
                    AiCapability.CREATE_BOOKING,
                    AiCapability.LIST_CUSTOMER_BOOKINGS,
                    AiCapability.RESCHEDULE_BOOKING,
                    AiCapability.CANCEL_BOOKING));
        }
        if (snapshot.capabilities().contains("ORDER")) {
            out.addAll(EnumSet.of(
                    AiCapability.LIST_CATALOG,
                    AiCapability.QUOTE_ORDER,
                    AiCapability.UPDATE_ORDER,
                    AiCapability.CREATE_ORDER,
                    AiCapability.GET_ORDER_STATUS,
                    AiCapability.CANCEL_ORDER));
        }
        if (snapshot.capabilities().contains("QUOTE")) {
            out.add(AiCapability.LIST_CATALOG);
            out.add(AiCapability.CREATE_QUOTE);
        }
        if (snapshot.capabilities().contains("LEAD")) out.add(AiCapability.CREATE_LEAD);
        if (snapshot.capabilities().contains("REQUEST")) out.add(AiCapability.CREATE_REQUEST);
        if (snapshot.capabilities().contains("DELIVERY")) {
            out.addAll(EnumSet.of(
                    AiCapability.LIST_DELIVERY_ZONES,
                    AiCapability.VALIDATE_DELIVERY_ADDRESS,
                    AiCapability.QUOTE_DELIVERY,
                    AiCapability.UPDATE_DELIVERY,
                    AiCapability.CREATE_DELIVERY,
                    AiCapability.GET_DELIVERY_STATUS,
                    AiCapability.CANCEL_DELIVERY));
        }

        // SEND_WHATSAPP_OPERATION and TRANSFER_TO_HUMAN are intentionally absent:
        // preparing a demo can never arm an external effect.
        return out;
    }

    private static String revision(DemoProfile profile) {
        String raw = profile.getId() + "|" + String.valueOf(profile.getUpdatedAt());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(64);
            for (byte value : digest) out.append(String.format("%02x", value));
            return out.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to fingerprint demo profile", e);
        }
    }

    private static Map<?, ?> requireMap(Object value, String message) {
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException(message);
        return map;
    }

    private static BigDecimal decimal(Object value, BigDecimal fallback) {
        if (value == null) return fallback;
        try {
            return value instanceof BigDecimal decimal
                    ? decimal
                    : new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Demo numeric value is invalid");
        }
    }

    private static Integer integer(Object value) {
        if (value == null) return null;
        try {
            return value instanceof Number number
                    ? number.intValue()
                    : Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Demo integer value is invalid");
        }
    }

    private static LocalTime localTime(Object value, String message) {
        try {
            return LocalTime.parse(required(value, message));
        } catch (Exception e) {
            throw new IllegalArgumentException(message);
        }
    }

    private static String required(Object value, String message) {
        String out = string(value);
        if (out == null || out.isBlank()) throw new IllegalArgumentException(message);
        return out.trim();
    }

    private static String optional(Object value, String fallback) {
        String out = string(value);
        return out == null || out.isBlank() ? fallback : out.trim();
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String normalized(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String catalogKey(CatalogItem.Kind kind, String name) {
        return kind.name() + ":" + normalized(name);
    }

    public record StageResult(String revision) {}

    private record PreparedSnapshot(
            UUID profileId,
            String businessName,
            String timezone,
            String language,
            String greeting,
            String instructions,
            List<CatalogSnapshot> catalog,
            List<HourSnapshot> hours,
            List<KnowledgeSnapshot> knowledge,
            Set<String> capabilities,
            String defaultCurrency) {}

    private record CatalogSnapshot(
            CatalogItem.Kind kind,
            String name,
            String description,
            BigDecimal price,
            String currency,
            Integer durationMinutes) {}

    private record HourSnapshot(int dayOfWeek, LocalTime openTime, LocalTime closeTime) {}

    private record KnowledgeSnapshot(String title, String category, String content) {}
}

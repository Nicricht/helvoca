package cl.helvoca.platform;

import cl.helvoca.agent.AiAgent;
import cl.helvoca.agent.AiAgentRepository;
import cl.helvoca.agent.AiCapability;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessMode;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.business.BusinessStatus;
import cl.helvoca.catalog.CatalogItem;
import cl.helvoca.catalog.CatalogItemRepository;
import cl.helvoca.knowledge.KnowledgeItem;
import cl.helvoca.knowledge.KnowledgeItemRepository;
import cl.helvoca.operations.BusinessOperationCapability;
import cl.helvoca.operations.BusinessOperationCapabilityGrant;
import cl.helvoca.operations.BusinessOperationCapabilityRepository;
import cl.helvoca.schedule.BusinessHour;
import cl.helvoca.schedule.BusinessHourRepository;
import cl.helvoca.security.TenantDatabaseContext;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.*;

@Service
public class DemoRuntimeStagingService {
    private final BusinessRepository businesses;
    private final CatalogItemRepository catalog;
    private final BusinessHourRepository hours;
    private final KnowledgeItemRepository knowledge;
    private final AiAgentRepository agents;
    private final BusinessOperationCapabilityRepository operationCapabilities;
    private final TenantDatabaseContext databaseContext;

    public DemoRuntimeStagingService(
            BusinessRepository businesses,
            CatalogItemRepository catalog,
            BusinessHourRepository hours,
            KnowledgeItemRepository knowledge,
            AiAgentRepository agents,
            BusinessOperationCapabilityRepository operationCapabilities,
            TenantDatabaseContext databaseContext) {
        this.businesses = businesses;
        this.catalog = catalog;
        this.hours = hours;
        this.knowledge = knowledge;
        this.agents = agents;
        this.operationCapabilities = operationCapabilities;
        this.databaseContext = databaseContext;
    }

    /**
     * Explicit platform-owned staging path. It never reuses tenant-authenticated
     * onboarding services and never touches phone/provider/payment configuration.
     */
    @Transactional
    public void stage(DemoProfile profile, UUID runtimeBusinessId) {
        if (profile == null) throw new IllegalArgumentException("Demo profile is required");
        if (runtimeBusinessId == null) throw new IllegalArgumentException("Demo runtime is required");

        try (TenantDatabaseContext.Scope ignored = databaseContext.useSystem()) {
            Business runtime = businesses.findById(runtimeBusinessId)
                    .orElseThrow(() -> new IllegalStateException("Configured DEMO runtime does not exist"));
            requireDemoRuntime(runtime);

            runtime.setName(profile.getBusinessName().trim());
            runtime.setTimezone(profile.getTimezone().trim());
            runtime.setLanguage(profile.getLanguage().trim().toLowerCase(Locale.ROOT));
            businesses.save(runtime);

            stageCatalog(profile, runtimeBusinessId);
            stageHours(profile, runtimeBusinessId);
            stageKnowledge(profile, runtimeBusinessId);
            stageAgentAndCapabilities(profile, runtimeBusinessId);
        }
    }

    private static void requireDemoRuntime(Business runtime) {
        if (runtime.getMode() != BusinessMode.DEMO) {
            throw new IllegalStateException("Configured runtime is not in DEMO mode");
        }
        if (runtime.getStatus() != BusinessStatus.ACTIVE) {
            throw new IllegalStateException("Configured DEMO runtime is not active");
        }
    }

    private void stageCatalog(DemoProfile profile, UUID businessId) {
        List<CatalogItem> current = new ArrayList<>(catalog.findAllByBusinessIdOrderByNameAsc(businessId));
        current.forEach(item -> item.setActive(false));

        Map<String, CatalogItem> byKey = new HashMap<>();
        for (CatalogItem item : current) byKey.put(catalogKey(item.getKind(), item.getName()), item);

        for (Map<String, Object> input : objectList(profile.getCatalog(), "items")) {
            String name = requiredText(input, "name", "Demo catalog item requires a name");
            CatalogItem.Kind kind = enumValue(CatalogItem.Kind.class, text(input.get("kind")), CatalogItem.Kind.PRODUCT);
            String key = catalogKey(kind, name);
            CatalogItem item = byKey.get(key);
            if (item == null) {
                item = new CatalogItem();
                item.setBusinessId(businessId);
                item.setKind(kind);
                byKey.put(key, item);
                current.add(item);
            }
            item.setName(name);
            item.setDescription(nullableText(input.get("description")));
            item.setPrice(decimal(input.get("price")));
            item.setCurrency(currency(input.get("currency")));
            item.setDurationMinutes(integer(input.get("durationMinutes")));
            item.setMetadataJson(metadataJson(input.get("metadata")));
            item.setActive(booleanValue(input.get("active"), true));
        }

        catalog.saveAll(current);
    }

    private void stageHours(DemoProfile profile, UUID businessId) {
        hours.deleteAllByBusinessId(businessId);
        hours.flush();

        List<BusinessHour> staged = new ArrayList<>();
        List<Map<String, Object>> structured = objectList(profile.getHours(), "items");
        if (!structured.isEmpty()) {
            for (Map<String, Object> input : structured) {
                int day = dayOfWeek(input.get("dayOfWeek"));
                LocalTime open = time(input.get("openTime"), "openTime");
                LocalTime close = time(input.get("closeTime"), "closeTime");
                validateWindow(open, close);
                staged.add(hour(businessId, day, open, close));
            }
        } else {
            for (Map.Entry<String, Object> entry : profile.getHours().entrySet()) {
                int day = dayOfWeek(entry.getKey());
                Object value = entry.getValue();
                if (value instanceof Iterable<?> iterable) {
                    for (Object window : iterable) addWindow(staged, businessId, day, String.valueOf(window));
                } else if (value != null) {
                    addWindow(staged, businessId, day, String.valueOf(value));
                }
            }
        }
        if (!staged.isEmpty()) hours.saveAll(staged);
    }

    private static void addWindow(List<BusinessHour> staged, UUID businessId, int day, String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank() || normalized.equalsIgnoreCase("closed") || normalized.equalsIgnoreCase("cerrado")) return;
        String[] parts = normalized.split("-", 2);
        if (parts.length != 2) throw new IllegalArgumentException("Demo business hour must use HH:mm-HH:mm");
        LocalTime open = LocalTime.parse(parts[0].trim());
        LocalTime close = LocalTime.parse(parts[1].trim());
        validateWindow(open, close);
        staged.add(hour(businessId, day, open, close));
    }

    private void stageKnowledge(DemoProfile profile, UUID businessId) {
        List<KnowledgeItem> current = new ArrayList<>(knowledge.findAllByBusinessIdOrderByTitleAsc(businessId));
        current.forEach(item -> item.setActive(false));
        Map<String, KnowledgeItem> byTitle = new HashMap<>();
        for (KnowledgeItem item : current) byTitle.put(item.getTitle().trim().toLowerCase(Locale.ROOT), item);

        List<Map<String, Object>> configured = objectList(profile.getKnowledge(), "items");
        if (!configured.isEmpty()) {
            for (Map<String, Object> input : configured) {
                upsertKnowledge(current, byTitle, businessId,
                        requiredText(input, "title", "Demo knowledge item requires a title"),
                        nullableText(input.get("category")),
                        requiredText(input, "content", "Demo knowledge item requires content"),
                        booleanValue(input.get("active"), true));
            }
        } else {
            Object faq = profile.getKnowledge().get("faq");
            if (faq instanceof Iterable<?> iterable) {
                int index = 1;
                for (Object value : iterable) {
                    String content = nullableText(value);
                    if (content != null) {
                        upsertKnowledge(current, byTitle, businessId,
                                "FAQ " + index++, "FAQ", content, true);
                    }
                }
            }
        }

        knowledge.saveAll(current);
    }

    private void upsertKnowledge(
            List<KnowledgeItem> current,
            Map<String, KnowledgeItem> byTitle,
            UUID businessId,
            String title,
            String category,
            String content,
            boolean active) {
        String key = title.toLowerCase(Locale.ROOT);
        KnowledgeItem item = byTitle.get(key);
        if (item == null) {
            item = new KnowledgeItem();
            item.setBusinessId(businessId);
            current.add(item);
            byTitle.put(key, item);
        }
        item.setTitle(title);
        item.setCategory(category);
        item.setContent(content);
        item.setActive(active);
    }

    private void stageAgentAndCapabilities(DemoProfile profile, UUID businessId) {
        AiAgent agent = agents.findByBusinessId(businessId).orElseGet(() -> {
            AiAgent created = new AiAgent();
            created.setBusinessId(businessId);
            created.setActive(true);
            return created;
        });

        agent.setName(profile.getBusinessName().trim());
        agent.setLanguage(profile.getLanguage().trim().toLowerCase(Locale.ROOT));
        agent.setGreeting(profile.getGreeting().trim());
        agent.setInstructions(nullableText(profile.getInstructions()));

        EnumSet<AiCapability> toolCapabilities = AiCapability.legacyDefaults();
        // Phase C owns real outbound WhatsApp. Prepare must not silently enable it.
        toolCapabilities.remove(AiCapability.SEND_WHATSAPP_OPERATION);
        toolCapabilities.remove(AiCapability.TRANSFER_TO_HUMAN);

        EnumSet<BusinessOperationCapability> businessCapabilities =
                EnumSet.noneOf(BusinessOperationCapability.class);

        for (String raw : profile.getCapabilities()) {
            if (raw == null || raw.isBlank()) continue;
            String capability = raw.trim().toUpperCase(Locale.ROOT);
            switch (capability) {
                case "BOOKING" -> toolCapabilities.addAll(Set.of(
                        AiCapability.LIST_AVAILABLE_SLOTS,
                        AiCapability.CHECK_BOOKING_AVAILABILITY,
                        AiCapability.CREATE_BOOKING,
                        AiCapability.LIST_CUSTOMER_BOOKINGS,
                        AiCapability.RESCHEDULE_BOOKING,
                        AiCapability.CANCEL_BOOKING));
                case "REQUEST" -> toolCapabilities.add(AiCapability.CREATE_REQUEST);
                case "ORDER" -> addBusinessCapability(
                        BusinessOperationCapability.ORDER, toolCapabilities, businessCapabilities);
                case "DELIVERY" -> addBusinessCapability(
                        BusinessOperationCapability.DELIVERY, toolCapabilities, businessCapabilities);
                case "QUOTE" -> addBusinessCapability(
                        BusinessOperationCapability.QUOTE, toolCapabilities, businessCapabilities);
                case "LEAD" -> addBusinessCapability(
                        BusinessOperationCapability.LEAD, toolCapabilities, businessCapabilities);
                case "CATALOG" -> addBusinessCapability(
                        BusinessOperationCapability.CATALOG, toolCapabilities, businessCapabilities);
                case "PAYMENT" -> throw new IllegalArgumentException(
                        "Live payment capability cannot be staged into a DEMO runtime");
                default -> throw new IllegalArgumentException("Unsupported demo capability: " + capability);
            }
        }

        if (!objectList(profile.getCatalog(), "items").isEmpty()
                || businessCapabilities.contains(BusinessOperationCapability.ORDER)
                || businessCapabilities.contains(BusinessOperationCapability.DELIVERY)) {
            addBusinessCapability(BusinessOperationCapability.CATALOG, toolCapabilities, businessCapabilities);
        }

        agent.setCapabilities(toolCapabilities);
        agents.save(agent);

        operationCapabilities.deleteAllByBusinessId(businessId);
        operationCapabilities.flush();
        if (!businessCapabilities.isEmpty()) {
            List<BusinessOperationCapabilityGrant> grants = new ArrayList<>();
            for (BusinessOperationCapability capability : businessCapabilities) {
                BusinessOperationCapabilityGrant grant = new BusinessOperationCapabilityGrant();
                grant.setBusinessId(businessId);
                grant.setCapability(capability);
                grants.add(grant);
            }
            operationCapabilities.saveAll(grants);
        }
    }

    private static void addBusinessCapability(
            BusinessOperationCapability capability,
            Set<AiCapability> tools,
            Set<BusinessOperationCapability> grants) {
        grants.add(capability);
        tools.addAll(capability.aiCapabilities());
    }

    private static List<Map<String, Object>> objectList(Map<String, Object> root, String key) {
        if (root == null || root.isEmpty()) return List.of();
        Object value = root.get(key);
        if (!(value instanceof Iterable<?> iterable)) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : iterable) {
            if (!(item instanceof Map<?, ?> raw)) {
                throw new IllegalArgumentException("Demo configuration " + key + " must contain objects");
            }
            LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
            raw.forEach((k, v) -> copy.put(String.valueOf(k), v));
            out.add(copy);
        }
        return out;
    }

    private static String catalogKey(CatalogItem.Kind kind, String name) {
        return kind.name() + ":" + name.trim().toLowerCase(Locale.ROOT);
    }

    private static String requiredText(Map<String, Object> input, String key, String error) {
        String value = nullableText(input.get(key));
        if (value == null) throw new IllegalArgumentException(error);
        return value;
    }

    private static String nullableText(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isBlank() ? null : text;
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static BigDecimal decimal(Object value) {
        if (value == null || String.valueOf(value).isBlank()) return null;
        BigDecimal result;
        try {
            result = new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Demo catalog price must be numeric");
        }
        if (result.signum() < 0) throw new IllegalArgumentException("Demo catalog price cannot be negative");
        return result;
    }

    private static Integer integer(Object value) {
        if (value == null || String.valueOf(value).isBlank()) return null;
        try {
            int result = Integer.parseInt(String.valueOf(value));
            if (result <= 0) throw new IllegalArgumentException("Demo duration must be positive");
            return result;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Demo duration must be an integer");
        }
    }

    private static String currency(Object value) {
        String result = nullableText(value);
        if (result == null) return "CLP";
        result = result.toUpperCase(Locale.ROOT);
        if (!result.matches("^[A-Z]{3}$")) throw new IllegalArgumentException("Demo currency must contain three letters");
        return result;
    }

    private static String metadataJson(Object value) {
        if (value == null) return null;
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("Demo catalog metadata must be an object");
        return new JSONObject(map).toString();
    }

    private static boolean booleanValue(Object value, boolean fallback) {
        if (value == null) return fallback;
        if (value instanceof Boolean bool) return bool;
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static int dayOfWeek(Object value) {
        if (value instanceof Number number) {
            int result = number.intValue();
            if (result >= 1 && result <= 7) return result;
        }
        String raw = nullableText(value);
        if (raw == null) throw new IllegalArgumentException("Demo business hour requires dayOfWeek");
        String normalized = raw.toUpperCase(Locale.ROOT);
        Map<String, DayOfWeek> aliases = Map.ofEntries(
                Map.entry("MONDAY", DayOfWeek.MONDAY), Map.entry("LUNES", DayOfWeek.MONDAY),
                Map.entry("TUESDAY", DayOfWeek.TUESDAY), Map.entry("MARTES", DayOfWeek.TUESDAY),
                Map.entry("WEDNESDAY", DayOfWeek.WEDNESDAY), Map.entry("MIERCOLES", DayOfWeek.WEDNESDAY),
                Map.entry("MIÉRCOLES", DayOfWeek.WEDNESDAY),
                Map.entry("THURSDAY", DayOfWeek.THURSDAY), Map.entry("JUEVES", DayOfWeek.THURSDAY),
                Map.entry("FRIDAY", DayOfWeek.FRIDAY), Map.entry("VIERNES", DayOfWeek.FRIDAY),
                Map.entry("SATURDAY", DayOfWeek.SATURDAY), Map.entry("SABADO", DayOfWeek.SATURDAY),
                Map.entry("SÁBADO", DayOfWeek.SATURDAY),
                Map.entry("SUNDAY", DayOfWeek.SUNDAY), Map.entry("DOMINGO", DayOfWeek.SUNDAY));
        DayOfWeek day = aliases.get(normalized);
        if (day == null) throw new IllegalArgumentException("Unsupported demo day: " + raw);
        return day.getValue();
    }

    private static LocalTime time(Object value, String field) {
        String raw = nullableText(value);
        if (raw == null) throw new IllegalArgumentException("Demo business hour requires " + field);
        return LocalTime.parse(raw);
    }

    private static void validateWindow(LocalTime open, LocalTime close) {
        if (!close.isAfter(open)) throw new IllegalArgumentException("Demo business hour close must be after open");
    }

    private static BusinessHour hour(UUID businessId, int day, LocalTime open, LocalTime close) {
        BusinessHour value = new BusinessHour();
        value.setBusinessId(businessId);
        value.setDayOfWeek(day);
        value.setOpenTime(open);
        value.setCloseTime(close);
        return value;
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String raw, T fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsupported demo catalog kind: " + raw);
        }
    }
}

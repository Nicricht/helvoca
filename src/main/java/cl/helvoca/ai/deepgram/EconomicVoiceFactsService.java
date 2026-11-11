package cl.helvoca.ai.deepgram;

import cl.helvoca.ai.realtime.RealtimeCallContext;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessProfile;
import cl.helvoca.business.BusinessProfileRepository;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.schedule.BusinessHour;
import cl.helvoca.schedule.BusinessHourRepository;
import cl.helvoca.security.TenantTransactionExecutor;
import cl.helvoca.servicecatalog.ServiceItem;
import cl.helvoca.servicecatalog.ServiceItemRepository;
import cl.helvoca.voice.VoiceEconomyIntentRouter;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.text.NumberFormat;
import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Read-only, tenant-scoped facts for the deterministic low-cost voice path.
 * A missing or ambiguous fact returns empty so the request stays with Gemini.
 */
@Service
public class EconomicVoiceFactsService {
    private static final Locale CHILEAN_SPANISH = Locale.forLanguageTag("es-CL");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final BusinessRepository businesses;
    private final BusinessProfileRepository profiles;
    private final BusinessHourRepository hours;
    private final ServiceItemRepository services;
    private final TenantTransactionExecutor tenantTransactions;

    public EconomicVoiceFactsService(BusinessRepository businesses,
                                     BusinessProfileRepository profiles,
                                     BusinessHourRepository hours,
                                     ServiceItemRepository services,
                                     TenantTransactionExecutor tenantTransactions) {
        this.businesses = businesses;
        this.profiles = profiles;
        this.hours = hours;
        this.services = services;
        this.tenantTransactions = tenantTransactions;
    }

    public Optional<String> answer(RealtimeCallContext context, String utterance) {
        if (context == null || context.businessId() == null) return Optional.empty();
        VoiceEconomyIntentRouter.Decision decision = VoiceEconomyIntentRouter.classify(utterance);
        if (!decision.cheaperPathCandidate()) return Optional.empty();
        String answer = tenantTransactions.read(context.businessId(),
                () -> answerInTenant(context, utterance, decision));
        return Optional.ofNullable(answer);
    }

    public Optional<String> businessName(RealtimeCallContext context) {
        if (context == null || context.businessId() == null) return Optional.empty();
        return tenantTransactions.read(context.businessId(), () -> businesses.findById(context.businessId())
                .map(Business::getName)
                .filter(value -> value != null && !value.isBlank()));
    }

    private String answerInTenant(RealtimeCallContext context,
                                  String utterance,
                                  VoiceEconomyIntentRouter.Decision decision) {
        return switch (decision.reason()) {
            case BUSINESS_HOURS -> businessHours(context.businessId());
            case BUSINESS_ADDRESS -> businessAddress(context.businessId());
            case SERVICE_PRICE -> servicePrice(context.businessId(), utterance);
            default -> null;
        };
    }

    private String businessHours(java.util.UUID businessId) {
        List<BusinessHour> configured = hours.findAllByBusinessIdOrderByDayOfWeekAscOpenTimeAsc(businessId);
        if (configured.isEmpty()) return null;

        Map<Integer, List<BusinessHour>> byDay = new LinkedHashMap<>();
        for (BusinessHour hour : configured) {
            int day = hour.getDayOfWeek();
            if (day >= 1 && day <= 7) byDay.computeIfAbsent(day, ignored -> new ArrayList<>()).add(hour);
        }
        if (byDay.isEmpty()) return null;

        List<String> days = new ArrayList<>();
        for (int day = 1; day <= 7; day++) {
            String dayName = DayOfWeek.of(day).getDisplayName(TextStyle.FULL, CHILEAN_SPANISH);
            List<BusinessHour> windows = byDay.get(day);
            if (windows == null || windows.isEmpty()) {
                days.add(dayName + " cerrado");
                continue;
            }
            String ranges = windows.stream()
                    .map(window -> window.getOpenTime().format(TIME) + " a " + window.getCloseTime().format(TIME))
                    .reduce((left, right) -> left + " y " + right)
                    .orElse("");
            days.add(dayName + " de " + ranges);
        }
        return "Nuestros horarios son: " + String.join("; ", days) + ".";
    }

    private String businessAddress(java.util.UUID businessId) {
        BusinessProfile profile = profiles.findById(businessId).orElse(null);
        if (profile == null || blank(profile.getAddressLine())) return null;
        List<String> parts = new ArrayList<>();
        add(parts, profile.getAddressLine());
        add(parts, profile.getCommune());
        add(parts, profile.getCity());
        add(parts, profile.getRegion());
        if (!blank(profile.getCountryCode())) {
            add(parts, "CL".equalsIgnoreCase(profile.getCountryCode().trim())
                    ? "Chile" : profile.getCountryCode().trim());
        }
        return "Estamos en " + String.join(", ", parts) + ".";
    }

    private String servicePrice(java.util.UUID businessId, String utterance) {
        BusinessProfile profile = profiles.findById(businessId).orElse(null);
        if (profile == null || blank(profile.getDefaultCurrency())) return null;
        String query = normalize(utterance);
        List<ServiceItem> matches = services.findAllByBusinessIdOrderByNameAsc(businessId).stream()
                .filter(ServiceItem::isActive)
                .filter(item -> !blank(item.getName()))
                .filter(item -> query.contains(normalize(item.getName())))
                .toList();
        if (matches.size() != 1) return null;

        ServiceItem service = matches.get(0);
        BigDecimal price = service.getPrice();
        if (price == null || price.signum() < 0) return null;
        try {
            Currency currency = Currency.getInstance(profile.getDefaultCurrency().trim().toUpperCase(Locale.ROOT));
            NumberFormat format = NumberFormat.getCurrencyInstance(CHILEAN_SPANISH);
            format.setCurrency(currency);
            format.setMaximumFractionDigits(currency.getDefaultFractionDigits());
            format.setMinimumFractionDigits(currency.getDefaultFractionDigits());
            return "El servicio de " + service.getName() + " cuesta " + format.format(price) + ".";
        } catch (IllegalArgumentException invalidCurrency) {
            return null;
        }
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static void add(List<String> parts, String value) {
        if (!blank(value) && !parts.contains(value.trim())) parts.add(value.trim());
    }
}

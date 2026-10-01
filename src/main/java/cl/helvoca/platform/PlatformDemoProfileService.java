package cl.helvoca.platform;

import cl.helvoca.audit.AuditService;
import cl.helvoca.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class PlatformDemoProfileService {
    private static final Set<String> FORBIDDEN_SECRET_FRAGMENTS = Set.of(
            "token", "secret", "credential", "password", "apikey", "authorization", "authheader");

    private final DemoProfileRepository profiles;
    private final AuditService audit;

    public PlatformDemoProfileService(DemoProfileRepository profiles, AuditService audit) {
        this.profiles = profiles;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<PlatformDemoProfileResponse> list() {
        return profiles.findAllByOrderByUpdatedAtDesc().stream().map(this::response).toList();
    }

    @Transactional
    public PlatformDemoProfileResponse create(PlatformDemoProfileRequest request) {
        validate(request);
        DemoProfile profile = new DemoProfile();
        apply(profile, request);
        profile = profiles.saveAndFlush(profile);
        audit.platformHumanSuccess(
                null,
                "DEMO_PROFILE_CREATE",
                "DEMO_PROFILE",
                profile.getId(),
                null,
                auditSummary(profile));
        return response(profile);
    }

    @Transactional
    public PlatformDemoProfileResponse update(UUID id, PlatformDemoProfileRequest request) {
        validate(request);
        DemoProfile profile = profiles.findById(id)
                .orElseThrow(() -> new NotFoundException("Demo profile not found"));
        Map<String, Object> before = auditSummary(profile);
        apply(profile, request);
        profile = profiles.saveAndFlush(profile);
        audit.platformHumanSuccess(
                null,
                "DEMO_PROFILE_UPDATE",
                "DEMO_PROFILE",
                profile.getId(),
                before,
                auditSummary(profile));
        return response(profile);
    }

    @Transactional
    public void delete(UUID id) {
        DemoProfile profile = profiles.findById(id)
                .orElseThrow(() -> new NotFoundException("Demo profile not found"));
        Map<String, Object> before = auditSummary(profile);
        profiles.delete(profile);
        audit.platformHumanSuccess(
                null,
                "DEMO_PROFILE_DELETE",
                "DEMO_PROFILE",
                id,
                before,
                null);
    }

    private static void validate(PlatformDemoProfileRequest request) {
        try {
            ZoneId.of(request.timezone().trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid demo profile timezone");
        }
        validateNoSecrets(request.catalog());
        validateNoSecrets(request.hours());
        validateNoSecrets(request.knowledge());
        validateNoSecrets(request.sourceMetadata());
    }

    private static void validateNoSecrets(Object value) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey())
                        .toLowerCase(Locale.ROOT)
                        .replaceAll("[^a-z0-9]", "");
                if (isSecretKey(key)) {
                    throw new IllegalArgumentException("Demo profiles cannot store provider credentials");
                }
                validateNoSecrets(entry.getValue());
            }
        } else if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) validateNoSecrets(item);
        }
    }

    private static boolean isSecretKey(String normalizedKey) {
        if (normalizedKey == null || normalizedKey.isBlank()) return false;
        return FORBIDDEN_SECRET_FRAGMENTS.stream().anyMatch(normalizedKey::contains);
    }

    private static void apply(DemoProfile profile, PlatformDemoProfileRequest request) {
        profile.setDisplayName(request.displayName().trim());
        profile.setBusinessName(request.businessName().trim());
        profile.setTimezone(request.timezone().trim());
        profile.setLanguage(request.language().trim().toLowerCase(Locale.ROOT));
        profile.setCatalog(copyMap(request.catalog()));
        profile.setHours(copyMap(request.hours()));
        profile.setKnowledge(copyMap(request.knowledge()));
        profile.setGreeting(request.greeting().trim());
        profile.setInstructions(blankToNull(request.instructions()));
        profile.setCapabilities(copyCapabilities(request.capabilities()));
        profile.setPresenterNotes(blankToNull(request.presenterNotes()));
        profile.setSourceMetadata(copyMap(request.sourceMetadata()));
    }

    private static Map<String, Object> copyMap(Map<String, Object> source) {
        return new LinkedHashMap<>(source == null ? Map.of() : source);
    }

    private static List<String> copyCapabilities(List<String> source) {
        if (source == null) return List.of();
        ArrayList<String> result = new ArrayList<>();
        for (String value : source) {
            if (value == null || value.isBlank()) continue;
            String normalized = value.trim().toUpperCase(Locale.ROOT);
            if (!result.contains(normalized)) result.add(normalized);
        }
        return result;
    }

    private PlatformDemoProfileResponse response(DemoProfile profile) {
        return new PlatformDemoProfileResponse(
                profile.getId(),
                profile.getDisplayName(),
                profile.getBusinessName(),
                profile.getTimezone(),
                profile.getLanguage(),
                copyMap(profile.getCatalog()),
                copyMap(profile.getHours()),
                copyMap(profile.getKnowledge()),
                profile.getGreeting(),
                profile.getInstructions(),
                List.copyOf(profile.getCapabilities()),
                profile.getPresenterNotes(),
                copyMap(profile.getSourceMetadata()),
                profile.getCreatedAt(),
                profile.getUpdatedAt());
    }

    private static Map<String, Object> auditSummary(DemoProfile profile) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("displayName", profile.getDisplayName());
        value.put("businessName", profile.getBusinessName());
        value.put("timezone", profile.getTimezone());
        value.put("language", profile.getLanguage());
        value.put("capabilities", List.copyOf(profile.getCapabilities()));
        return value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

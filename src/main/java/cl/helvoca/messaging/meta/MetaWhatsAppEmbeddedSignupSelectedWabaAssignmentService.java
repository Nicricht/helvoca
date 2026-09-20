package cl.helvoca.messaging.meta;

import cl.helvoca.common.ConflictException;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Service
public class MetaWhatsAppEmbeddedSignupSelectedWabaAssignmentService {
    private static final String ASSIGNMENT_TASK = "MANAGE";
    private static final int MAX_WABA_PAGES = 100;

    private final MetaWhatsAppEmbeddedSignupReadinessService readinessService;
    private final MetaWhatsAppEmbeddedSignupSharedWabaClient sharedWabaClient;
    private final MetaWhatsAppEmbeddedSignupAssignedUsersClient assignedUsersClient;
    private final MetaWhatsAppEmbeddedSignupAssignSystemUserClient assignSystemUserClient;
    private final MetaWhatsAppProperties metaProperties;

    public MetaWhatsAppEmbeddedSignupSelectedWabaAssignmentService(
            MetaWhatsAppEmbeddedSignupReadinessService readinessService,
            MetaWhatsAppEmbeddedSignupSharedWabaClient sharedWabaClient,
            MetaWhatsAppEmbeddedSignupAssignedUsersClient assignedUsersClient,
            MetaWhatsAppEmbeddedSignupAssignSystemUserClient assignSystemUserClient,
            MetaWhatsAppProperties metaProperties) {
        this.readinessService = readinessService;
        this.sharedWabaClient = sharedWabaClient;
        this.assignedUsersClient = assignedUsersClient;
        this.assignSystemUserClient = assignSystemUserClient;
        this.metaProperties = metaProperties;
    }

    public MetaWhatsAppEmbeddedSignupSelectedWabaAssignmentResult ensureAssigned(String wabaId) {
        if (!readinessService.readiness().readyForEmbeddedSignup()) {
            throw new ConflictException("META_EMBEDDED_SIGNUP_NOT_READY");
        }

        String cleanWabaId = requireWabaId(wabaId);
        if (!isSharedWaba(cleanWabaId)) {
            throw new ConflictException("META_EMBEDDED_SIGNUP_WABA_NOT_SHARED");
        }

        MetaWhatsAppEmbeddedSignupAssignedUsersResult assignedUsers =
                assignedUsersClient.fetch(
                        cleanWabaId,
                        metaProperties.getEmbeddedSignupBusinessId(),
                        metaProperties.getEmbeddedSignupSystemUserAccessToken());

        boolean alreadyAssigned = assignedUsers.users().stream()
                .anyMatch(user -> metaProperties.getEmbeddedSignupSystemUserId().equals(user.id()));

        if (alreadyAssigned) {
            return new MetaWhatsAppEmbeddedSignupSelectedWabaAssignmentResult(
                    "SYSTEM_USER_ALREADY_ASSIGNED",
                    true,
                    false);
        }

        MetaWhatsAppEmbeddedSignupAssignSystemUserResult assignment =
                assignSystemUserClient.assign(
                        cleanWabaId,
                        metaProperties.getEmbeddedSignupSystemUserId(),
                        ASSIGNMENT_TASK,
                        metaProperties.getEmbeddedSignupAdminSystemUserAccessToken());

        if (!assignment.success()) {
            throw new ConflictException("META_EMBEDDED_SIGNUP_SYSTEM_USER_ASSIGNMENT_FAILED");
        }

        return new MetaWhatsAppEmbeddedSignupSelectedWabaAssignmentResult(
                "SYSTEM_USER_ASSIGNED",
                true,
                true);
    }

    private boolean isSharedWaba(String selectedWabaId) {
        Set<String> seenCursors = new HashSet<>();
        String cursor = null;

        for (int pageNumber = 0; pageNumber < MAX_WABA_PAGES; pageNumber++) {
            MetaWhatsAppEmbeddedSignupSharedWabaPage page =
                    cursor == null
                            ? sharedWabaClient.list(
                                    metaProperties.getEmbeddedSignupBusinessId(),
                                    metaProperties.getEmbeddedSignupSystemUserAccessToken())
                            : sharedWabaClient.list(
                                    metaProperties.getEmbeddedSignupBusinessId(),
                                    metaProperties.getEmbeddedSignupSystemUserAccessToken(),
                                    cursor);

            if (page.wabas().stream().anyMatch(waba -> selectedWabaId.equals(waba.id()))) {
                return true;
            }

            String nextCursor = clean(page.afterCursor());
            if (nextCursor == null) {
                return false;
            }
            if (!seenCursors.add(nextCursor)) {
                throw new IllegalStateException(
                        "Meta Embedded Signup shared WABA pagination repeated a cursor");
            }
            cursor = nextCursor;
        }

        throw new IllegalStateException(
                "Meta Embedded Signup shared WABA pagination exceeded safe limit");
    }

    private static String requireWabaId(String wabaId) {
        String clean = clean(wabaId);
        if (clean == null) {
            throw new IllegalArgumentException("Meta WABA id is required");
        }
        if (!clean.matches("[0-9]{1,80}")) {
            throw new IllegalArgumentException("Meta WABA id is invalid");
        }
        return clean;
    }

    private static String clean(String value) {
        String clean = value == null ? "" : value.trim();
        return clean.isBlank() ? null : clean;
    }
}

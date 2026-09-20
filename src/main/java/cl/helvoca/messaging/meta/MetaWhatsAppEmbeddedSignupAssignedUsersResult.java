package cl.helvoca.messaging.meta;

import java.util.List;

public record MetaWhatsAppEmbeddedSignupAssignedUsersResult(
        List<AssignedUser> users
) {
    public MetaWhatsAppEmbeddedSignupAssignedUsersResult {
        users = users == null ? List.of() : List.copyOf(users);
    }

    public boolean hasAssignedUsers() {
        return !users.isEmpty();
    }

    public record AssignedUser(
            String id,
            String name,
            List<String> tasks
    ) {
        public AssignedUser {
            tasks = tasks == null ? List.of() : List.copyOf(tasks);
        }
    }
}

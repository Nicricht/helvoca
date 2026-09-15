package cl.helvoca.security;

import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Explicit database access context consumed by TenantAwareDataSource.
 *
 * HTTP requests always install a scope in TenantDatabaseContextFilter. Internal
 * non-HTTP work defaults to SYSTEM and should narrow to TENANT whenever the
 * business id is known.
 */
@Component
public class TenantDatabaseContext {
    private final ThreadLocal<Access> current = new ThreadLocal<>();

    public Access currentOrInternalSystem() {
        Access value = current.get();
        return value == null ? Access.system() : value;
    }

    public Scope useTenant(UUID businessId) {
        if (businessId == null) throw new IllegalArgumentException("businessId is required");
        return install(Access.tenant(businessId));
    }

    public Scope useSystem() {
        return install(Access.system());
    }

    public Scope deny() {
        return install(Access.denied());
    }

    public <T> T callAsTenant(UUID businessId, Supplier<T> work) {
        try (Scope ignored = useTenant(businessId)) {
            return work.get();
        }
    }

    public void runAsTenant(UUID businessId, Runnable work) {
        try (Scope ignored = useTenant(businessId)) {
            work.run();
        }
    }

    public <T> T callAsSystem(Supplier<T> work) {
        try (Scope ignored = useSystem()) {
            return work.get();
        }
    }

    public void runAsSystem(Runnable work) {
        try (Scope ignored = useSystem()) {
            work.run();
        }
    }

    private Scope install(Access next) {
        Access previous = current.get();
        current.set(next);
        return () -> {
            if (previous == null) current.remove();
            else current.set(previous);
        };
    }

    public enum Mode { TENANT, SYSTEM, DENIED }

    public record Access(Mode mode, UUID businessId) {
        public static Access tenant(UUID businessId) { return new Access(Mode.TENANT, businessId); }
        public static Access system() { return new Access(Mode.SYSTEM, null); }
        public static Access denied() { return new Access(Mode.DENIED, null); }
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}

package cl.helvoca.chaos;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public final class DeterministicFailureInjector {
    private final int failuresBeforeSuccess;
    private final Supplier<? extends RuntimeException> failureFactory;
    private final AtomicInteger attempts = new AtomicInteger();

    public DeterministicFailureInjector(
            int failuresBeforeSuccess,
            Supplier<? extends RuntimeException> failureFactory) {
        if (failuresBeforeSuccess < 0) {
            throw new IllegalArgumentException("failuresBeforeSuccess must be >= 0");
        }
        this.failuresBeforeSuccess = failuresBeforeSuccess;
        this.failureFactory = Objects.requireNonNull(failureFactory, "failureFactory");
    }

    public <T> T execute(Supplier<T> success) {
        int attempt = attempts.incrementAndGet();
        if (attempt <= failuresBeforeSuccess) {
            throw failureFactory.get();
        }
        return success.get();
    }

    public int attempts() {
        return attempts.get();
    }
}

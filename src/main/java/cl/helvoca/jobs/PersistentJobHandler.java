package cl.helvoca.jobs;

import java.time.Duration;

public interface PersistentJobHandler {
    PersistentJob.Type type();

    void handle(PersistentJob job);

    final class RetryableJobException extends RuntimeException {
        private final Duration retryDelay;

        public RetryableJobException(String message) {
            this(message, null, Duration.ZERO);
        }

        public RetryableJobException(String message, Throwable cause) {
            this(message, cause, Duration.ZERO);
        }

        public RetryableJobException(String message, Throwable cause, Duration retryDelay) {
            super(message, cause);
            this.retryDelay = retryDelay == null || retryDelay.isNegative()
                    ? Duration.ZERO
                    : retryDelay;
        }

        public Duration retryDelay() {
            return retryDelay;
        }
    }

    final class PermanentJobException extends RuntimeException {
        public PermanentJobException(String message) { super(message); }
        public PermanentJobException(String message, Throwable cause) { super(message, cause); }
    }
}

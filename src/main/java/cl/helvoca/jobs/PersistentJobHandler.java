package cl.helvoca.jobs;

public interface PersistentJobHandler {
    PersistentJob.Type type();

    void handle(PersistentJob job);

    final class RetryableJobException extends RuntimeException {
        public RetryableJobException(String message) { super(message); }
        public RetryableJobException(String message, Throwable cause) { super(message, cause); }
    }

    final class PermanentJobException extends RuntimeException {
        public PermanentJobException(String message) { super(message); }
        public PermanentJobException(String message, Throwable cause) { super(message, cause); }
    }
}

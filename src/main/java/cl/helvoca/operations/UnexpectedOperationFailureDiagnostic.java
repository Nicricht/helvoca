package cl.helvoca.operations;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Centralized, sanitized logging for unexpected operation failures.
 * Never logs tool arguments, customer data, tokens, phone numbers, or secrets.
 */
@Component
public class UnexpectedOperationFailureDiagnostic {
    private static final Logger log = LoggerFactory.getLogger(UnexpectedOperationFailureDiagnostic.class);

    public void record(String toolName, Throwable error) {
        Throwable root = rootCause(error);
        String type = root == null ? "Unknown" : safe(root.getClass().getSimpleName(), 80);
        String message = root == null ? "NONE" : safe(root.getMessage(), 180);
        log.error("OPERATION_UNEXPECTED_FAILURE toolName={} exceptionType={} message={}",
                safe(toolName, 80), type, message);
    }

    private static Throwable rootCause(Throwable error) {
        Throwable current = error;
        Throwable last = error;
        while (current != null) {
            last = current;
            current = current.getCause();
        }
        return last;
    }

    private static String safe(String value, int maxLength) {
        if (value == null || value.isBlank()) return "NONE";
        String clean = value.replaceAll("[^A-Za-z0-9_.:()\\[\\] /-]", "_").trim();
        return clean.length() <= maxLength ? clean : clean.substring(0, maxLength);
    }
}

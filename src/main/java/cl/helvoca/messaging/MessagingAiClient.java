package cl.helvoca.messaging;

import java.util.List;
import java.util.Set;

public interface MessagingAiClient {
    String respond(String instructions, List<Turn> history, Set<String> allowedToolNames, ToolInvoker tools);

    record Turn(String role, String content) {}

    @FunctionalInterface
    interface ToolInvoker {
        String execute(String name, String arguments);
    }
}

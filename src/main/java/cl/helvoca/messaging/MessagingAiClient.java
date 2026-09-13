package cl.helvoca.messaging;

import java.util.List;

public interface MessagingAiClient {
    String respond(String instructions, List<Turn> history, ToolInvoker tools);

    record Turn(String role, String content) {}

    @FunctionalInterface
    interface ToolInvoker {
        String execute(String name, String arguments);
    }
}

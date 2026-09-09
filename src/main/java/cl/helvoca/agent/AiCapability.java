package cl.helvoca.agent;

import java.util.Arrays;
import java.util.Optional;

public enum AiCapability {
    GET_BUSINESS_INFORMATION("get_business_information"),
    LIST_SERVICES("list_services"),
    SEARCH_KNOWLEDGE("search_knowledge"),
    FIND_CALLER("find_caller"),
    REGISTER_CALLER("register_caller"),
    CHECK_BOOKING_AVAILABILITY("check_booking_availability"),
    CREATE_BOOKING("create_booking");

    private final String toolName;

    AiCapability(String toolName) { this.toolName = toolName; }

    public String toolName() { return toolName; }

    public static Optional<AiCapability> fromToolName(String toolName) {
        return Arrays.stream(values()).filter(v -> v.toolName.equals(toolName)).findFirst();
    }
}

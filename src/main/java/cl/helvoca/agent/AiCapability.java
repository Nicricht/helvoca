package cl.helvoca.agent;

import java.util.Arrays;
import java.util.Optional;

public enum AiCapability {
    GET_BUSINESS_INFORMATION("get_business_information"),
    LIST_SERVICES("list_services"),
    SEARCH_KNOWLEDGE("search_knowledge"),
    FIND_CALLER("find_caller"),
    REGISTER_CALLER("register_caller"),
    LIST_AVAILABLE_SLOTS("list_available_slots"),
    CHECK_BOOKING_AVAILABILITY("check_booking_availability"),
    CREATE_BOOKING("create_booking"),
    LIST_CUSTOMER_BOOKINGS("list_customer_bookings"),
    RESCHEDULE_BOOKING("reschedule_booking"),
    CANCEL_BOOKING("cancel_booking"),
    CREATE_REQUEST("create_request"),
    RECORD_UNANSWERED_QUESTION("record_unanswered_question"),
    TRANSFER_TO_HUMAN("transfer_to_human");

    private final String toolName;

    AiCapability(String toolName) {
        this.toolName = toolName;
    }

    public String toolName() {
        return toolName;
    }

    public static Optional<AiCapability> fromToolName(String toolName) {
        return Arrays.stream(values()).filter(value -> value.toolName.equals(toolName)).findFirst();
    }
}

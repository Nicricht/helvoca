package cl.helvoca.agent;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;

public enum AiCapability {
    GET_BUSINESS_INFORMATION("get_business_information", false),
    LIST_SERVICES("list_services", false),
    SEARCH_KNOWLEDGE("search_knowledge", false),
    FIND_CALLER("find_caller", false),
    REGISTER_CALLER("register_caller", false),
    LIST_AVAILABLE_SLOTS("list_available_slots", false),
    CHECK_BOOKING_AVAILABILITY("check_booking_availability", false),
    CREATE_BOOKING("create_booking", false),
    LIST_CUSTOMER_BOOKINGS("list_customer_bookings", false),
    RESCHEDULE_BOOKING("reschedule_booking", false),
    CANCEL_BOOKING("cancel_booking", false),
    CREATE_REQUEST("create_request", false),
    RECORD_UNANSWERED_QUESTION("record_unanswered_question", false),
    TRANSFER_TO_HUMAN("transfer_to_human", false),

    LIST_CATALOG("list_catalog", true),
    LIST_DELIVERY_ZONES("list_delivery_zones", true),
    VALIDATE_DELIVERY_ADDRESS("validate_delivery_address", true),
    QUOTE_DELIVERY("quote_delivery", true),
    UPDATE_DELIVERY("update_delivery", true),
    CREATE_DELIVERY("create_delivery", true),
    GET_DELIVERY_STATUS("get_delivery_status", true),
    CANCEL_DELIVERY("cancel_delivery", true),
    QUOTE_ORDER("quote_order", true),
    UPDATE_ORDER("update_order", true),
    CREATE_ORDER("create_order", true),
    GET_ORDER_STATUS("get_order_status", true),
    CANCEL_ORDER("cancel_order", true),
    CREATE_QUOTE("create_quote", true),
    CREATE_LEAD("create_lead", true),
    QUOTE_PAYMENT("quote_payment", true),
    UPDATE_PAYMENT("update_payment", true),
    CREATE_PAYMENT("create_payment", true),
    GET_PAYMENT_STATUS("get_payment_status", true),
    CANCEL_PAYMENT("cancel_payment", true);

    private final String toolName;
    private final boolean commercialOperation;

    AiCapability(String toolName, boolean commercialOperation) {
        this.toolName = toolName;
        this.commercialOperation = commercialOperation;
    }

    public String toolName() { return toolName; }
    public boolean isCommercialOperation() { return commercialOperation; }

    public static Optional<AiCapability> fromToolName(String toolName) {
        return Arrays.stream(values()).filter(value -> value.toolName.equals(toolName)).findFirst();
    }

    public static EnumSet<AiCapability> legacyDefaults() {
        EnumSet<AiCapability> defaults = EnumSet.noneOf(AiCapability.class);
        for (AiCapability capability : values()) {
            if (!capability.commercialOperation) defaults.add(capability);
        }
        return defaults;
    }

    public static EnumSet<AiCapability> commercialOperations() {
        EnumSet<AiCapability> commercial = EnumSet.noneOf(AiCapability.class);
        for (AiCapability capability : values()) {
            if (capability.commercialOperation) commercial.add(capability);
        }
        return commercial;
    }
}

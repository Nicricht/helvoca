package cl.helvoca.messaging.meta;

public record MetaWhatsAppEmbeddedSignupSelectedWabaSubscriptionResult(
        String state,
        boolean systemUserAssigned,
        boolean assignmentChanged,
        boolean appSubscribed
) {
}

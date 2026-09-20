package cl.helvoca.messaging.meta;

import java.util.List;

public record MetaWhatsAppEmbeddedSignupSharedWabaPage(
        List<Waba> wabas,
        String afterCursor
) {
    public MetaWhatsAppEmbeddedSignupSharedWabaPage {
        wabas = wabas == null ? List.of() : List.copyOf(wabas);
    }

    public record Waba(
            String id,
            String name,
            String currency,
            String timezoneId,
            String messageTemplateNamespace
    ) {
    }
}

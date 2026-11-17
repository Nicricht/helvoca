package cl.helvoca.onboarding;

/**
 * Read-only truth about whether this industry requires authoritative resource
 * availability beyond the generic service booking calendar. This is NOT a
 * capability grant, capacity lookup, booking confirmation or provider check.
 *
 * Until a tenant-scoped resource allocator has been certified, supported
 * industry presets fail closed with NOT_CERTIFIED.
 */
public record SectorResourceReadiness(
        String resourceKind,
        String verificationState,
        boolean requiresDedicatedResource,
        String nextStep) {

    private static final String PROFESSIONAL =
            "Configura y verifica la disponibilidad real por profesional antes de ofrecer cupos individuales.";
    private static final String TABLE =
            "Configura mesas, capacidad y turnos con disponibilidad comprobada antes de ofrecer reservas de mesa.";
    private static final String SERVICE_BAY =
            "Configura bahías, técnicos y capacidad real antes de ofrecer una recepción con recurso asignado.";
    private static final String PROPERTY =
            "Verifica cada inmueble y la agenda de visitas antes de ofrecer un horario específico.";
    private static final String CLASS =
            "Registra clases, aforo y cupos verificados antes de ofrecer inscripciones.";
    private static final String ROOM =
            "Configura habitaciones, ocupación y disponibilidad por noche antes de ofrecer una estadía.";
    private static final String STOCK =
            "Configura catálogo y stock verificable por variante; no garantices disponibilidad sin consultar inventario.";
    private static final String GENERAL =
            "Define los recursos que utiliza este negocio y verifica su fuente antes de prometer una reserva.";

    public static SectorResourceReadiness forPreset(String presetKey) {
        return switch (presetKey == null ? "" : presetKey) {
            case "clinic", "dental", "veterinary", "salon", "legal" ->
                    missing("PROFESSIONAL", PROFESSIONAL);
            case "restaurant" -> missing("TABLE", TABLE);
            case "workshop" -> missing("SERVICE_BAY", SERVICE_BAY);
            case "real_estate" -> missing("PROPERTY_VISIT", PROPERTY);
            case "gym" -> missing("CLASS_SLOT", CLASS);
            case "hospitality" -> missing("ROOM_NIGHT", ROOM);
            case "store", "hardware_store" ->
                    new SectorResourceReadiness("CATALOG_INVENTORY", "NOT_APPLICABLE", false, STOCK);
            default -> new SectorResourceReadiness("UNSPECIFIED", "NOT_VERIFIED", false, GENERAL);
        };
    }

    private static SectorResourceReadiness missing(String resourceKind, String nextStep) {
        return new SectorResourceReadiness(resourceKind, "NOT_CERTIFIED", true, nextStep);
    }
}

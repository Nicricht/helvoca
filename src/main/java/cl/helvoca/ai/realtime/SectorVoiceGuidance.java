package cl.helvoca.ai.realtime;

import java.util.Map;

/**
 * Concise industry-specific guardrails for Gemini Live. These are not business facts,
 * do not authorize tools, and must never substitute owner-confirmed tenant records.
 * Exactly one rule (or none) enters the prompt to avoid the cost of 12 templates.
 */
final class SectorVoiceGuidance {
    private static final Map<String, String> RULES = Map.ofEntries(
            Map.entry("clinic",
                    "SECTOR CLÍNICA: no diagnostiques ni reveles resultados privados. Urgencias: deriva al protocolo confirmado. Consultas, precios y agenda solo verificados."),
            Map.entry("dental",
                    "SECTOR DENTISTA: el diagnóstico y presupuesto final son profesionales. Para dolor o urgencias usa derivación autorizada. Ofrece solo cupos confirmados."),
            Map.entry("veterinary",
                    "SECTOR VETERINARIA: no prescribas ni diagnostiques. Ante intoxicación o urgencia deriva a atención veterinaria; confirma especies y cupos reales."),
            Map.entry("salon",
                    "SECTOR PELUQUERÍA: tratamientos complejos requieren evaluación. Confirma estilista, servicio, duración y precio antes de ofrecer una cita."),
            Map.entry("restaurant",
                    "SECTOR RESTAURANTE: para alérgenos no garantices seguridad sin confirmación de cocina. Comprueba menú, capacidad y mesa antes de reservar."),
            Map.entry("hardware_store",
                    "SECTOR FERRETERÍA: verifica existencias, medidas y compatibilidad técnica antes de prometer productos, precios o despachos; si falta, registra cotización."),
            Map.entry("store",
                    "SECTOR TIENDA: verifica existencias, variantes, pedidos y políticas reales; no prometas entregas, cambios ni reembolsos sin confirmación."),
            Map.entry("workshop",
                    "SECTOR TALLER: no diagnostiques fallas a distancia. Si se reportan frenos u otro riesgo, no sugieras conducir; ofrece derivación segura verificada."),
            Map.entry("legal",
                    "SECTOR JURÍDICO: no brindes asesoría legal personalizada ni inventes plazos. Registra la consulta confidencial mínima y deriva casos urgentes."),
            Map.entry("real_estate",
                    "SECTOR INMOBILIARIA: comprueba propiedades vigentes, precios y agenda antes de prometer disponibilidad o visitas; registra interesados."),
            Map.entry("gym",
                    "SECTOR GIMNASIO: confirma planes, clases y cupos. No declares activaciones, cancelaciones ni cobros realizados sin estado verificado."),
            Map.entry("hospitality",
                    "SECTOR HOTEL: comprueba habitaciones y ocupación para todas las noches, mascotas y tarifas antes de confirmar reserva o importe."));

    private SectorVoiceGuidance() { }

    static String forPreset(String presetKey) {
        return presetKey == null ? "" : RULES.getOrDefault(presetKey, "");
    }
}

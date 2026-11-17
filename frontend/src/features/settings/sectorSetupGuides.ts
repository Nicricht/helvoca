/**
 * Issue #879: data-collection guides, NOT operational presets.
 * No values here are authoritative facts of any real business.
 * Never turn these questions into provider tools, AI instructions, bookings or prices.
 */
export interface SectorSetupGuide {
  readonly title: string;
  readonly questions: readonly string[];
  readonly missingInformation: string;
}

const sectorSetupGuides: Readonly<Record<string, SectorSetupGuide>> = {
  clinic: {
    title: "Clínica / centro médico",
    questions: [
      "¿Cuáles son las especialidades y profesionales, las prestaciones, sus duraciones y aranceles?",
      "¿Qué convenios, requisitos, exámenes, direcciones y horarios están confirmados?",
      "¿Qué protocolo y contacto de urgencias ha autorizado el centro médico?"
    ],
    missingInformation: "No diagnosticar, inventar horas ni exponer resultados privados; registrar la consulta o derivar según protocolo verificado."
  },
  dental: {
    title: "Dentista",
    questions: [
      "¿Qué tratamientos y odontólogos atienden, y cuánto dura cada evaluación?",
      "¿Cuáles son los precios de evaluación, las condiciones de presupuesto y los cupos?",
      "¿Qué síntomas requieren derivación odontológica urgente y a quién?"
    ],
    missingInformation: "No prometer tratamientos, resultados ni presupuestos definitivos sin evaluación profesional; derivar urgencias."
  },
  veterinary: {
    title: "Veterinaria",
    questions: [
      "¿Qué especies atendidas, vacunas, consultas y profesionales están autorizados?",
      "¿Cuáles son las tarifas, requisitos, horarios y disponibilidad real de citas?",
      "¿Dónde se deriva una emergencia veterinaria, incluidos horarios fuera de atención?"
    ],
    missingInformation: "No diagnosticar ni prescribir medicamentos; ante posibles intoxicaciones o emergencias, derivar a atención profesional."
  },
  salon: {
    title: "Peluquería / barbería",
    questions: [
      "¿Cuáles son los estilistas y servicios, precios, duración y horarios?",
      "¿Qué tratamientos requieren evaluación previa y tienen condiciones especiales?",
      "¿Cómo se gestionan retrasos, cancelaciones y cambios de profesional?"
    ],
    missingInformation: "No garantizar resultados estéticos sin una evaluación; ofrecer cita disponible verificada o registrar solicitud."
  },
  restaurant: {
    title: "Restaurante",
    questions: [
      "¿Dónde está el menú y alérgenos confirmados, incluidos riesgos de contaminación cruzada?",
      "¿Cuáles son los horarios, capacidades de mesas y políticas de reservas para grupos?",
      "¿Se aceptan pedidos, retiro o delivery y con qué zonas, precios y plazos reales?"
    ],
    missingInformation: "No garantizar un plato libre de alérgenos ni una mesa sin confirmación; consultar al responsable y registrar seguimiento."
  },
  hardware_store: {
    title: "Ferretería",
    questions: [
      "¿Dónde está el catálogo y existencias actuales, SKU, medidas, compatibilidades y precios?",
      "¿Qué reglas tienen para cotizaciones, encargos, despachos, tiempos y garantías?",
      "¿Cómo se consultará stock en cada sucursal o se escalará una compatibilidad técnica?"
    ],
    missingInformation: "No afirmar stock, compatibilidad ni entrega sin datos vigentes; registrar cotización o consulta técnica."
  },
  store: {
    title: "Tienda / comercio",
    questions: [
      "¿Qué tallas, variantes, colores, precios y stock verificado ofrece cada producto?",
      "¿Cómo se comprueba un pedido, la entrega, el retiro o su estado?",
      "¿Cuáles son los cambios, devoluciones, garantías y pagos autorizados?"
    ],
    missingInformation: "No asegurar disponibilidad, reembolso ni entrega sin consulta real de pedidos, inventario y políticas."
  },
  workshop: {
    title: "Taller mecánico",
    questions: [
      "¿Qué vehículos admitidos, marcas, reparaciones y diagnósticos realiza el taller?",
      "¿Cómo se reciben citas, órdenes de trabajo, cotizaciones, repuestos y plazos?",
      "¿Qué situaciones exigen detener el vehículo o coordinar asistencia segura?"
    ],
    missingInformation: "No diagnosticar una falla ni aconsejar conducir un vehículo inseguro; registrar asistencia o derivar."
  },
  legal: {
    title: "Estudio jurídico",
    questions: [
      "¿Cuáles son las áreas legales atendidas, abogados disponibles y tarifas?",
      "¿Cómo se solicita una reunión y qué datos/documentos mínimos se necesitan?",
      "¿Cuál es el procedimiento autorizado para plazos urgentes y datos confidenciales?"
    ],
    missingInformation: "No interpretar notificaciones, inventar plazos ni dar asesoría jurídica personalizada; solicitar atención profesional."
  },
  real_estate: {
    title: "Inmobiliaria",
    questions: [
      "¿Dónde están las propiedades vigentes, sus precios, estado y características?",
      "¿Cómo se verifican visitas, horarios, requisitos y encargados de cada inmueble?",
      "¿Cómo se registrarán interesados y se ofrecerán alternativas confirmadas?"
    ],
    missingInformation: "No prometer una propiedad ni una visita sin disponibilidad actualizada; registrar al interesado."
  },
  gym: {
    title: "Gimnasio",
    questions: [
      "¿Cuáles son los planes y cupos, cuotas, clases, entrenadores y horarios?",
      "¿Cómo se comprueban inscripciones, pagos y membresías existentes?",
      "¿Qué reglas aplican para cancelaciones, congelaciones y reclamos?"
    ],
    missingInformation: "No confirmar altas, cobros, cupos ni cancelaciones sin operación verificada; registrar el caso."
  },
  hospitality: {
    title: "Hotel / hostal",
    questions: [
      "¿Qué habitaciones y ocupación máxima están permitidas, incluidas mascotas y menores?",
      "¿Cómo se consulta disponibilidad por noche, tarifas, impuestos y reservas?",
      "¿Qué reglas hay de check-in, check-out, llegadas tardías y cancelación?"
    ],
    missingInformation: "No garantizar habitaciones, precios totales ni reservas sin inventario validado para fechas concretas."
  },
  services: {
    title: "Servicios",
    questions: [
      "¿Cuál es la actividad principal, qué servicios ofrecen y cuáles son sus precios y duraciones?",
      "¿Qué horarios, zonas de cobertura y profesionales están disponibles?",
      "¿Cuáles son las condiciones para reservas, presupuestos y seguimiento?"
    ],
    missingInformation: "No prometer atención, cobertura ni presupuestos sin información confirmada; registrar la solicitud."
  },
  professional: {
    title: "Profesional / oficina",
    questions: [
      "¿Qué especialidades y servicios atiende la oficina y con qué profesionales?",
      "¿Qué modalidad, tarifas, horarios y requisitos tienen las reuniones?",
      "¿Cómo se reciben consultas confidenciales y se derivan casos urgentes?"
    ],
    missingInformation: "No prestar asesoría fuera de la competencia del negocio; registrar consulta y solicitar confirmación."
  },
  general: {
    title: "Otro negocio",
    questions: [
      "¿Cuál es la actividad principal y qué necesitan resolver quienes llaman?",
      "¿Qué servicios o productos, horarios, precios y políticas puede confirmar el propietario?",
      "¿Qué solicitudes debe registrar la recepcionista y a quién derivará excepciones?"
    ],
    missingInformation: "No inventar políticas, respuestas ni servicios; recoger la necesidad y dejar seguimiento verificable."
  }
};

export function getSectorSetupGuide(presetKey: string | null | undefined): SectorSetupGuide {
  return sectorSetupGuides[presetKey ?? "general"] ?? sectorSetupGuides.general;
}

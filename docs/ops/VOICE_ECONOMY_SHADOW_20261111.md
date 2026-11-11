# RecepVoz | Enrutamiento económico de voz, fase 1 (shadow)

**Estado:** prototipo seguro integrado, **no** sustituto de Gemini Live. PR DRAFT dependiente de #872 y #871. No usar en producción comercial.

## Por qué no se activa un proveedor barato directamente

El flujo actual recibe audio Twilio PCMU, lo envía a Gemini Live y devuelve audio nativo. En esta fase la clasificación ocurre **después** de que Gemini ya transcribió al paciente. **Activar el modo shadow no evita ningún token de Gemini** y tampoco cambia los minutos de Twilio.

Cambiar voz o cortar sesiones sin un motor STT+TTS que cumpla requisitos de latencia, privacidad, interrupciones y calidad podría provocar reservas equivocadas. No se incorpora Twilio Gather, voces Polly ni otra ruta que eluda autorizaciones.

## Implementación

- Clasificador determinista VoiceEconomyIntentRouter, sin llamadas externas ni cobro.
- RULES_CANDIDATE solo para consultas cortas inequívocas y no personalizadas sobre horario, dirección o precio del catálogo demostrativo. **No genera respuesta.** Para servir respuestas futuras se necesitará consulta autenticada y vigente al negocio/tenant.
- GEMINI_LIVE para reservas, reprogramación, cancelación, disponibilidad, urgencias, identidad, WhatsApp, pagos, transferencias, fechas relativas y múltiples peticiones o texto ambiguo.
- Flag GEMINI_LIVE_ECONOMY_SHADOW_ENABLED=false por defecto. Opcional en Aurora aislado para futuras llamadas ya autorizadas. No conmuta el proveedor.
- Observación en GeminiLiveVoiceSession.flush: transcripción del turno terminado -> clasificación local -> registro solo call_id, path, reason, mode=ADVISORY_ONLY; sin audio, texto de paciente ni identidad.
- GeminiLiveVoiceProvider copia el flag a la sesión; ningún cambio de herramientas ni permisos.

## Benchmark sin telefonía

JUnit usa **18 mensajes ficticios**. **6/18 (33,3 %)** son candidatos a ruta barata y **12/18** requieren Gemini. Esa proporción es sintética; **no es ahorro de pesos ni ahorro de llamadas**. Clasificación pura: cero Twilio, cero solicitudes Gemini, cero cobros y cero consultas a la agenda.

## Gates

- Java 21 y JUnit de clasificación segura, privacidad y copia del flag por sesión.
- Prueba de integración del shadow: sin envío de audio, invocación de herramientas ni transcripción en logs; off significa no clasificación.
- Fast Gate, Golden Journey, seguridad multi-tenant, PostgreSQL 16/18 y CI.
- No modificar main, producción, Meta ni cobros. No originar llamadas reales en estas pruebas.

## Para ahorrar dinero de verdad en fase 2

Prototipar STT alternativo y TTS de calidad comparable a Sulafat, con acceso autenticado de solo lectura al catálogo y horarios vigentes. Probar con audio sintético de Chile, interrupciones, fechas y nombres. Ante duda, preferir Gemini. Activar el nuevo motor solo cuando sea mediblemente más barato y no baje tasa de reservas correctas. Requiere trabajo y autorización separados para una ruta telefónica real.
## Comprobante de contexto previo

Receipt autenticado anterior al HEAD final: PR #874, comentario 6803853419, validando el padre exacto, rama, base main temporal y evidencia del piloto. Este commit únicamente añade la referencia de trazabilidad; no desactiva controles ni habilita rutas telefónicas.

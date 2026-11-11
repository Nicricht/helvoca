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

## Fase 2: PR #875 (Nova-3 + Aura-2), estado NO-GO hasta certificación

- Rama `feat/deepgram-economy-voice-isolated-20261111`, apilada sobre #874; PR DRAFT https://github.com/Nicricht/helvoca/pull/875.
- Se añadió proveedor `deepgram-economy` con reconocimiento Deepgram Nova-3 multilingüe en tiempo real (PCMU 8 kHz), síntesis Aura-2 en español (PCMU 8 kHz sin contenedor) y adaptador a Twilio media stream, sin alterar el proveedor `gemini` ni Sulafat.
- La selección económica es **opt-in y OFF**: nunca figura en `HELVOCA_VOICE_PROVIDER_ORDER` predeterminado, requiere `HELVOCA_DEEPGRAM_ECONOMY_ENABLED=true`, credencial segura no registrada en Git, negocio UUID y teléfono exactos. Incluso habilitado exige fallback Gemini configurado. `configured()` sin contexto devuelve false.
- Sólo se responde un precio cuyo nombre coincide exactamente con un servicio activo del catálogo del tenant mediante la capa existente de permisos. Horarios y dirección, reservas, datos personales, consultas ambiguas y falla del backend escalan a Gemini; nunca se inventan datos ni se habilitan mutaciones en la ruta económica.
- Si falla STT, TTS, el costo estimado, la conexión o la respuesta autorizada, la ruta intenta transferir el control a la sesión Gemini de la misma llamada; un fallo adicional termina la conexión de forma segura. Una transición puede requerir repetir la solicitud, por eso **no se acepta la ruta para pacientes reales sin un ensayo de continuidad y latencia**.
- Modelo de costos: referencia provisional Nova-3 streaming USD 0,0092/min + Aura-2 USD 0,03/1.000 caracteres frente a Gemini Live USD 0,023/min. Son unidades de referencia, no un ahorro facturado; Twilio, latencia, contenido y consumo adicional quedan fuera. Aceptación obligatoria: ahorro extremo a extremo **medido**, no por porcentajes inventados.
- Pruebas unitarias sintéticas en `DeepgramEconomyVoiceTest` y CI del SHA final; NO se realizaron llamadas, HTTP al proveedor, pagos, recargas, despliegues ni cambios de variables de Railway. La calidad de voz chilena, VAD, interrupciones y continuidad del fallback siguen **NO VERIFICADAS EN PROVEEDOR REAL**. El PR debe seguir DRAFT hasta cerrar el Full Gate y la verificación de un piloto autorizado.

- Integración de entrada Twilio: `TwilioMediaStreamHandler` valida `configuredFor(businessPhone)` para el proveedor económico igual que `VoiceCallRouter`, sin cambiar la firma del stream, el chequeo de binding ni el control RLS. Prueba firmada positiva/negativa `TwilioDeepgramEconomyMediaRoutingTest` con cero red externa.

- Cobertura de contratos sin red en `DeepgramEconomyVoiceSessionTest`: transcripción interina ignorada; fragmentos finales unidos antes de fallback; precio oficial y bytes PCMU enviados a Twilio; fallo de STT sin Gemini cierra una sola vez; callbacks tardíos tras close no cambian estado. El test TTS inyecta una respuesta HTTP sintética en `HttpClient`, nunca contacta Deepgram.

### Fallo de saludo del negocio (regresión, 11 nov 2026)

Si una lectura autorizada de `agentGreeting` arroja una excepción (p. ej., error temporal de base de datos), el callback de Deepgram `onOpen` ahora entrega el control una sola vez a Gemini si está configurado para el número exacto; si Gemini también falla, finaliza el transporte de forma segura. No inventa información del negocio, no reproduce audio TTS tras ese fallo y no registra contenido del paciente. Test `DeepgramEconomyVoiceSessionTest.failingTenantGreetingHandsOffToGeminiOnceWithoutSendingTtsOrHangingUp` ejecuta el caso con mocks, sin credenciales ni uso real de Deepgram.

**La verificación con proveedor real sigue sin acreditarse:** Railway Aurora no muestra `DEEPGRAM_API_KEY` ni habilitación `HELVOCA_DEEPGRAM_ECONOMY_ENABLED`; sin credenciales y una prueba costeada autorizada no existe evidencia de calidad en chileno, tarifas cobradas, ni ahorro real.

### Certificación GitHub sobre rama apilada

El workflow `.github/workflows/ci.yml` tiene activador de PR restringido a `main`. Para certificar el HEAD de la nueva corrección, el PR DRAFT #875 se basó temporalmente en `main` sin fusionar y se creó un recibo nuevo para su base real. Tras observar el Fast/Full Gate exacto debe regresar a la base `perf/aurora-economy-intent-shadow-20261111` (#874). La prueba nueva comprueba también que, cuando fallan **el saludo y Gemini**, la telefonía se cierra una sola vez sin invocar Deepgram TTS. Ninguno de estos cambios acredita autenticidad o ahorro del proveedor real.

### 11 nov 2026 · Reconciliación de cobertura y control de audio STT

El workflow `43089205213` terminó con conclusión global **FAILURE** en el job `test`, paso `Enforce differential Java coverage`. No es fallo de la batería JUnit: el reporte `backend-test-diagnostics` de 8 MB muestra **2.702 Java tests PASS**. La cobertura en JaCoCo del código completo del prototipo, no sólo del último commit, es insuficiente: `DeepgramEconomyVoiceSession` 111/180 líneas y 54/130 ramas, `DeepgramEconomyVoiceProvider` 7/15 líneas y 0/10 ramas. Prohibido declarar DONE o modificar el umbral de 100 %.

Se añadió un límite conservador de **240.000 bytes de audio PCMU 8 kHz** (equivalente a 30 segundos de audio) por sesión STT económica. Al excederlo la sesión deja de remitir nuevos bytes a Deepgram y deriva la llamada a Gemini configurado para el negocio, sin colgar. **No es un límite estricto de facturación**: duración de WebSocket inactivo, tarifas de proveedor, TTS y continuidad todavía deben certificarse. También se añadieron pruebas offline del handshake, pinning, agente inactivo, audio inválido, cierre temprano y sobreconsumo de audio. El provider sigue **OFF**.

Referencias de formato verificadas: https://developers.deepgram.com/docs/nova-3-language-prompting , https://developers.deepgram.com/docs/encoding/ y https://developers.deepgram.com/developer-tools/cli/text-to-speech .

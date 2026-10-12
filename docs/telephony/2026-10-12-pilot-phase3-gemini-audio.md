# Helvoca · Piloto de llamadas reales · Parte 3/6, Gemini Live y audio bidireccional

**Fecha:** 2026-10-12. **Base exacta:** PR #800, HEAD `522b4cd9eaae8edc1e2ebf3fba78dda50c0ed70e`. **Rama:** `feat/voice-real-pilot-3of6-gemini-audio-20261012`. **Producción:** sin cambios.

## Hallazgo de ciclo de vida

En `GeminiLiveVoiceSession.fail()` un error del proveedor marcaba `closed=true` y cerraba la conexión del carrier, **pero no cerraba el WebSocket upstream de Gemini**. Como `close()` comienza con el mismo compare-and-set, una llamada posterior a `close()` ya no podía liberar esa conexión. El riesgo es mantener una sesión de IA innecesariamente abierta después de una falla telefónica. Además el log escribía un `reason` que podía contener texto controlado por el proveedor, sin sanitización.

## Corrección aplicada

- Tras un fallo único, limpiar buffer de audio previo al setup, ensamblador de audio saliente y parser de frames.
- Si hay socket upstream, invocar `WebSocket.abort()` inmediatamente. El cierre normal continúa usando `sendClose()` de forma gradual, sin abort abrupto.
- Si el cliente cuelga **antes de completar** `buildAsync`, bloquear cualquier `onOpen()` tardío: abortar ese WebSocket sin solicitar mensajes ni enviar el setup. Esta carrera podía dejar conexión upstream sin uso, aunque el cierre normal no dispusiera aún del socket.
- Mantener idempotencia de fallo con `closed.compareAndSet(false, true)`. Evitar repetición en devolución de callbacks del WebSocket o cierre de Twilio.
- Si abort upstream o cierre carrier lanzan excepciones, cerrar lo restante igualmente y ejecutar `finalizeCall()` exactamente una vez mediante `finally`.
- Registrar únicamente tipo de fallo y Call ID. No interpolar mensajes del proveedor, credenciales o datos del interlocutor en logs de este camino.
- Conservación de señales de salud/circuit breaker, estado terminal, generación de resumen y verificación de certificación con alcance tenant intacto.

## Contratos automatizados

`GeminiLiveFailureCleanupPhase3Test` prueba:
1. error en WebSocket Gemini ya abierto, abort upstream, liberación de carrier y finalización una vez incluso ante eventos duplicados;
2. cierre inesperado del proveedor;
3. fallos simultáneos de abort upstream, actualización del estado de llamada y cierre carrier;
4. fallo previo a establecer WebSocket;
5. cierre normal mantiene `sendClose()` y no utiliza `abort()`;
6. cierre local antes de conexión upstream seguido de `onOpen()` tardío, con abort inmediato y cero mensajes enviados;
7. error de conexión previo a `onOpen()` y callback tardío, sin recrear sesión ni duplicar finalización.

Workflow `.github/workflows/voice-live-pilot-phase3-gemini-audio.yml`: ejecuta sin credenciales ni redes externas las suites de Gemini Live, codecs PCMU, framing 20 ms, Hybrid VAD, interrupción/barge-in, WebSocket Media Stream Twilio, reintentos y circuito de salud. El workflow de siete gates seguros se extiende a esta rama con JaCoCo diferencial 100 % frente al HEAD exacto de Parte 2.

## Dependencias pendientes

- Las Partes 1 y 2 tienen tests especializados verdes pero la certificación integral de siete gates se verifica por separado. Esta Parte 3 **no** debe declararse integralmente certificada antes de comprobar el mismo SHA en ambos workflows y `completed/success`.
- Los PR #796 a #800 están fuera de `main` en el momento de implementar esta fase; la versión de producción anterior **no ejecuta estas correcciones**.
- No se ha oído ni probado audio desde un teléfono real; eso requiere prueba carrier end-to-end de Parte 6.
- La existencia de `GEMINI_LIVE_ENABLED=true`, de `voiceReady=true` en logs y de un `external_id=PN...` correcto **no certifica conversación audible**.

**Estado: LIVE_CALL_CERTIFIED=PENDING, COMMERCIAL_GO=NO-GO.** Sin tráfico Twilio/Gemini facturable, sin cambios en Railway, PostgreSQL, subcuentas, webhooks, claves o flags. **No iniciar Parte 4 en esta sesión.**

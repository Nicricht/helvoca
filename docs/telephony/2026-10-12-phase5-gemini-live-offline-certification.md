# Helvoca · Parte 5/8 · Gemini Live offline: aislamiento de configuración y audio

**12 de octubre de 2026. Alcance técnico local, sin credenciales reales ni llamadas. Estado operativo: NO-GO.**

## Hallazgo confirmado en el HEAD de Parte 4

`GeminiLiveVoiceProvider.sessionProperties(context)` creaba `new GeminiLiveProperties()` y propagaba `enabled`, API key, model, voice, endpoint y simulación de certificación, **pero omitía** los valores configurados de VAD, barge-in local y presupuestos de latencia. Las sesiones nuevas usaban defaults de Java incluso cuando el operador había configurado otros valores. El valor expuesto por el readiness global no garantizaba los parámetros efectivos de una sesión.

La infraestructura previa ya implementa:

- Twilio PCMU 8 kHz → Gemini PCM16 16 kHz y Gemini PCM16 24 kHz → Twilio PCMU 8 kHz, con `GeminiTwilioAudioFramer` y bloques de 20 ms.
- `interrupted` y barge-in local para descartar audio anterior a la interrupción.
- Funciones de reserva protegidas con `behavior: BLOCKING` y controles backend.
- `GeminiLiveModelPolicy` con allowlist y URL oficial; `GeminiLiveRuntimeReadiness` comprueba **solo configuración local**. Una cadena de modelo permitida **no** demuestra que el proyecto Google tenga acceso, ni que el servicio esté conectado.

## Corrección de Parte 5

`GeminiLiveVoiceProvider.sessionProperties` toma una **instantánea por sesión** de los trece ajustes omitidos: sensibilidades de inicio/fin, prefix padding, silencio, hybrid VAD, barge-in, amplitud, frames de entrada/liberación, ventana reciente de audio del asistente y tres límites de latencia. Los setters reciben valores **ya normalizados y acotados mediante getters**. La instantánea no depende de cambios posteriores en el objeto global y no altera ninguna otra sesión.

El código **no habilita** Gemini Live, ni añade probes, ni cambia modelo o credenciales. Los ajustes originales de privacidad, autorización de herramientas y cierre de la llamada permanecen intactos.

## Pruebas offline exigidas

1. Valores no predeterminados para **cada uno de los trece campos**, incluyendo VAD deshabilitado y barge-in deshabilitado: comprobar que se mantienen al crear la sesión.
2. Independencia: modificar un snapshot no debe modificar la configuración global ni otro snapshot.
3. Valores fuera de rango: comprobar que el snapshot adopta los límites validados y el `buildSetup` JSON contiene la configuración real de VAD.
4. Si el proveedor está apagado o no tiene API key, `session.start()` rechaza localmente antes de intentar WebSocket; no se usan tokens reales.
5. Ejecutar suites simuladas existentes de `GeminiLiveVoiceSessionTest`, `GeminiLiveAudioInterruptionContractTest`, `GeminiLiveToolReplayContractTest`, `GeminiTwilioAudioFramerTest`, `LocalBargeInDetectorTest`, `GeminiLiveModelPolicyTest`, `GeminiLiveRuntimeReadinessTest` y `GeminiLiveVoiceProviderBakeOffTest` sin realizar llamadas a proveedores.
6. GitHub Actions por HEAD exacto: Fast Gate, backend con 100 % de cobertura diferencial aplicable de líneas/ramas/métodos, Golden Journey, Home, Inventario, System E2E y Playwright completo. Usar controles **alternativos** offline, no el smoke HTTPS contra producción.

## Límites y dependencias

- Rama apilada sobre [PR #794](https://github.com/Nicricht/helvoca/pull/794) (Parte 4), aún DRAFT, sobre #793, #792, #791 y #790. Nada se fusiona ni despliega en esta fase.
- Las simulaciones no validan modelo real disponible en Google, cuota, identidad de API key, métricas de consumo, llamadas reales, Twilio subcuenta, webhooks externos, audio audible ni facturas.
- Mantener por defecto `HELVOCA_TWILIO_EXISTING_VOICE_ROUTE_ENABLED=false` y no modificar `GEMINI_LIVE_ENABLED` de producción. El CI normal posee `meta-webhook-public-smoke` que consulta dominios públicos; no invocarlo sin autorización.
- Si cualquier gate termina FAIL o está PENDING, la Parte 5 **no está certificada**, aunque las suites específicas pasen.
- **PRODUCTION / COMMERCIAL NO-GO.** Certificar el HEAD solo cuando exista evidencia completa. No iniciar Parte 6 automáticamente.

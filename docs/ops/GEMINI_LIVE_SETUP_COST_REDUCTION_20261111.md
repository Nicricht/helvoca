# Gemini Live: reducción segura del setup (Aurora, 11-nov-2026)

## Alcance

Cambios exclusivamente al **texto** de descripciones de herramientas y al suplemento de voz en la rama DRAFT `perf/aurora-gemini-lean-prompt-20261111`, apilada sobre PR #871. No se altera el backend, el aislamiento por tenant ni los argumentos y validaciones de la agenda. Las mejoras se aplican solo con los flags ya activos `GEMINI_LIVE_COMPACT_TOOL_DESCRIPTIONS_ENABLED=true` y `GEMINI_LIVE_LEAN_SYSTEM_INSTRUCTIONS_ENABLED=true`.

## Evidencia de base (antes del cambio)

Llamada real 11-nov, CallSid anonimizado: Gemini Live `gemini-3.8-live`, voz Sulafat. Setup anterior: `system_chars=23681`, `declarations_chars=11457`, `tool_count=16`, `message_chars=36095`, cifras **en caracteres, NO tokens ni USD**. Gemini reportó 17 snapshots de uso; estos no se suman directamente para determinar factura.

## Medidas implementadas

- Concisión de descripción de las herramientas de voz, incluidos catálogo, disponibilidad, reserva en dos fases, identidad, cancelación, reprogramación y solicitudes.
- Permanecen los mismos nombres, tipos, campos obligatorios y campos de parámetros (incluidas reglas de semana), sin quitar herramientas.
- Descripciones originales de `end_call` y `send_whatsapp_operation` se conservan completas por seguridad.
- Instrucciones oficiales del tenant se anteponen **verbatim**; solo se compacta el suplemento de estilo vocal. Se mantiene idioma chileno, continuidad de timbre, saludo único, interrupciones, confirmación de reservas, fecha real, transferencias y cierre tras audio completo.
- Se mantiene el ajuste de compresión existente en demo: umbral 20.000, objetivo 10.000, aplicado previamente en Railway. Este PR no cambia esos valores.

## Gates y criterio de aceptación

`GeminiLiveLowLatencySetupTest` compara esquemas original/compacto y parámetros, asegura que las descripciones operacionales se acorten, verifica no inventar causa, fecha o identificadores, y comprueba que el texto de voz quede por debajo de 3.000 caracteres adicionales a las instrucciones oficiales del negocio. Pasar gates de GitHub CI y Golden Journey antes del despliegue aislado; **sin llamada de pago**. Después del despliegue, un futuro uso ya autorizado podrá mostrar tamaños en `GEMINI_LIVE_SETUP_SIZE` y facturación con latencia. No afirmar reducción de pesos ni porcentaje real sin conciliación oficial.

## Seguridad

No fusionar este PR sobre main antes de #871. No originar llamadas, reservas, cobros ni recargas para medir. Producción comercial y otros tenants fuera de alcance. En caso de error de funcionamiento en demo, revertir únicamente los cambios de prompt o flags opt-in.

## Trazabilidad del gate

Se añadió un comprobante autenticado de contexto previo a este commit en el PR #872, con `AUDITED_HEAD` explícito, identificador de la rama, línea base y evidencia #823. El gate `scripts/ci/verify_prechange_receipt.py` debe validar el **padre exacto** del nuevo HEAD; no se ha debilitado ni desactivado ese control. Este commit solo añade la trazabilidad al documento operativo.

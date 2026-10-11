# Helvoca: Parte 4/8, puente Twilio y Gemini Live en tiempo real

## Objetivo

Construir sobre la autorización carrier-to-tenant certificada en Parte 3, sin llamar a números reales ni cambiar Railway, Twilio o PostgreSQL de producción.

## Auditoría concreta

- Twilio Media Streams envía PCMU a 8 kHz. Gemini Live recibe PCM16 little-endian a 16 kHz y emite PCM16 little-endian a 24 kHz.
- El decodificador anterior convertía cada `modelTurn.parts[].inlineData.data` independientemente, descartando hasta dos muestras de 24 kHz por fragmento cuando su longitud no era divisible por tres. Esto podía introducir discontinuidades de audio.
- El transporte anterior enviaba directamente chunks de Gemini con longitudes arbitrarias, en lugar de bloques de 20 ms.
- El proveedor emite `serverContent.interrupted` cuando una intervención debe cortar la reproducción. Además de `clear` en Twilio, hay que desechar cualquier audio todavía no emitido localmente.
- Gemini 3.8 Live usa llamadas a herramientas `NON_BLOCKING` por defecto, lo que permite que la generación avance antes de terminar una operación. Para este adaptador transaccional se configura `behavior: BLOCKING` en cada declaración de herramienta.

## Implementación

1. `GeminiTwilioAudioFramer` conserva bytes PCM16 incompletos, grupos de remuestreo 3:1 e incluso fragmentos de audio menores que un bloque. Emite PCMU en bloques completos de 160 muestras (20 ms) y descarga la cola residual al recibir `generationComplete` o `turnComplete`.
2. `GeminiLiveVoiceSession` usa el framer en cada evento de audio, lo vacía al recibir `interrupted`, no reproduce partes del modelo contenidas en ese mismo evento y también lo reinicia con una interrupción local validada por el detector de voz.
3. Las herramientas se publican en modo `BLOCKING`, conservando las verificaciones de permisos y el control de resultados antes de confirmar reservas.
4. Las nuevas pruebas abarcan fragmentación en límites arbitrarios de byte, grupos de 3 muestras, cola parcial, turnos consecutivos, interrupciones con contenido contradictorio, vaciado de audio y modo bloqueante.

## Fuentes de contrato

- https://ai.google.dev/gemini-api/docs/live-api/capabilities
- https://ai.google.dev/gemini-api/docs/live-api/best-practices
- https://ai.google.dev/gemini-api/docs/models/gemini-3.8-live

## Criterios de certificación

- CI exacto del HEAD: fast-gate, test Java completo con JaCoCo diferencial **100% líneas, ramas y métodos**, PostgreSQL/Testcontainers y E2E Playwright, además de gates de frontend pertinentes.
- Cualquier test fallido, cobertura inferior a 100% o ejecución pendiente: **NO-GO**, aunque las unidades focalizadas pasen.
- Sin llamadas reales, gastos de Twilio/Gemini, despliegues, fusiones, activación de números ni mutaciones de bases de datos de producción.
- El estado operativo comercial sigue **NO-GO** mientras número, cuenta/subcuenta, SID y webhooks de producción no estén conciliados y certificados mediante pruebas autorizadas.
- Dependencia de PR #783 y PR #784: no fusionar esta parte antes de integrar y recertificar aquellas ramas.

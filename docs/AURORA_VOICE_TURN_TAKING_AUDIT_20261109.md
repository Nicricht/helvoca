# Aurora 2026-11-09: telefonía Gemini, turno de voz y transcripción

Auditoría de una llamada real en entorno aislado. Fuente: registros Railway del 9 nov 2026, CallSid `CA396caafc443c679352c000b473237d36`. El cliente confirmó que la recepcionista le interrumpía y que la transcripción contiene palabras que nunca dijo. La transcripción **no** es una transcripción literal verificada contra el audio.

## Hallazgo verificable

- El WebSocket conectado comunicó `model=gemini-3.8-live` y `certification_simulation=false`; la voz seleccionada fue Sulafat.
- `GeminiLiveVoiceSession` usaba simultáneamente la detección automática de actividad de Gemini y un detector local por amplitud con 2 tramas de 20 ms, además de `audioStreamEnd` generado tras 25 tramas silenciosas.
- `onLocalSpeechStarted` borra audio del operador en Twilio cuando hay actividad local. Se observó `VOICE_LOCAL_BARGE_IN`.
- Se observaron 96 entradas de transcripción, de ellas 22 intervenciones de usuario de nueve caracteres o menos. Se registraron diez intentos rechazados de `end_call`.
- La certificación terminó FAILED; no equiparar conexión de Gemini ni un build verde con certificación de conversación.

## Hipótesis a comprobar

Detección falsa por eco, ruido, pausas o volumen local; competencia entre dos decisiones de fin de turno; reconocimiento inexacto con audio telefónico comprimido. Ni el log ni el texto por sí solos demuestran qué se dijo realmente. La comparación de audio con consentimiento debe preceder a atribuir una causa de transcripción concreta.

## Reparación acotada

- Dejar a Gemini como autoridad de interrupción y fin de turno por defecto; mantener opción de barge-in local como ajuste explícito para posteriores ensayos controlados.
- Ampliar el umbral de silencio para pausas naturales. Si el detector local se habilita explícitamente, exigir una duración de voz más conservadora.
- Conservar transmisión de audio entrante, incluidas confirmaciones breves como «sí» y «no»; no descartar palabras basándose solo en la longitud de la transcripción.
- Añadir pruebas de pausa, fragmentos espurios, confirmación breve y evento de interrupción remota.
- No modificar la voz Sulafat, otras voces, `main`, producción comercial, ni efectuar llamadas automáticamente.

## Aceptación

Pruebas y gates completos en rama DRAFT; despliegue aislado solamente tras CI verde y autorización de su alcance; certificación acústica real independiente de los tests. Documentar explícitamente cualquier bloqueo.

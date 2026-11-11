# RecepVoz | Enrutamiento económico de voz: fase de observación

Fecha: 2026-11-11. Dependencia de código: PR #872, sin fusionar a main.

## Cómo se reflejaría al atender pacientes

1. Una llamada entra por el mismo número que el cliente conoce. No se fuerza ningún menú DTMF ni se cambia de voz.
2. Una petición pública y breve, como «¿Dónde quedan?» o «¿Cuál es el horario de atención?», es **candidata** para una ruta de menor costo, siempre consultando información real y actual del negocio.
3. Una petición para reservar, reprogramar, cancelar, comunicar información médica, consultar datos privados o hablar con una persona permanece en **Gemini Live**, con las herramientas y permisos del tenant.
4. En una futura fase, un ejecutor económico certificado podría procesar las preguntas públicas, pero solo si su reconocimiento de voz + síntesis + telefonía + transferencia cuestan menos que Gemini Live y no degradan la experiencia.
5. El paciente no debería distinguir las rutas. **No se sustituye la voz Sulafat por una grabación ni se corta el turno en curso**.

## Implementado en esta rama

- `EconomicVoiceRouteAdvisor`: evaluador determinista sin llamadas externas, clasificador estricto de dos preguntas públicas (horario, dirección). Cualquier duda, frase compuesta, cambio de reserva, información clínica o privacidad se trata como compleja. Usa una whitelist de utterance completo, no coincidencia parcial.
- El asesor de costo solo devuelve candidato si existe **ejecutor de voz alternativo listo**, precios estrictamente positivos, documentados, de la **misma moneda/unidad** y ruta alternativa objetivamente más barata. De lo contrario vuelve a Gemini.
- En `GeminiLiveVoiceSession`, **solo si** se habilita el modo de observación, se analiza una transcripción que ya devolvió Gemini en el turno. El log contiene únicamente `call_id`, tipo de intención y `selected=GEMINI_LIVE`, **nunca texto de conversación ni PII**. No se hacen peticiones adicionales a Gemini, Twilio u otro proveedor.
- El flag `GEMINI_LIVE_ECONOMIC_ROUTING_SHADOW_ENABLED` está **OFF por defecto**, aislado en la copia de propiedades por llamada. No modifica rutas de telefonía, TwiML, disponibilidad, herramientas ni reservas.
- Pruebas Java offline de clasificación segura, privacidad, fallos cerrados y precios. Sin cambiar credenciales, presupuestos ni límites operacionales.

## Lo que todavía NO está hecho

**No hay segundo motor de conversación con síntesis de voz listo**. Este PR es **fase de observación**, no una reducción real de audio facturado. Si el primer audio sigue entrando por Gemini, su costo inicial no desaparece por clasificarlo después. Activar el flag solo permitiría contar potenciales preguntas públicas futuras, sin cambiar la experiencia ni los cobros.

Antes de conectar un segundo proveedor habría que:
1. Elegir canal de STT/TTS para español chileno con facturación real y privacidad contractual aceptables.
2. Comparar costo **integral** por minuto: STT, TTS, cómputo, telefonía, streaming y transferencia; no usar un precio unitario aislado.
3. Garantizar que datos de horarios y ubicación salen de fuentes oficiales del tenant, no respuestas inventadas.
4. Mantener la misma identidad de voz o detener la alternativa cuando cambie audiblemente.
5. Probar barge-in, silencio, interrupción, frases mixtas, idioma, transferencia, fallos externos, aislamiento y capacidad de reserva.
6. Desplegar solo a demo-isolated con autorización; habilitar clientes reales únicamente tras A/B de calidad, costos y privacidad.

## Política de seguridad y gasto

- No generar llamadas reales para medir una reducción provisional. No modificar main o producción comercial.
- No usar un precio de Twilio saliente como tarifa entrante. Las tarifas de voz dependen del sentido de la llamada y su país/origen.
- No anunciar ahorros financieros hasta contar con dos comparables facturados, no con proyecciones del modo shadow.

## CI: pre-change receipt

El PR #873 dispone de comprobante de evidencia previa autenticado que incluye el SHA exacto dc9c6eece260fd0b383247a2d09a34f03fb30aa0, rama y base main en el momento del test. Este último commit documental desencadena la verificación de ese padre; no se debilita el gate. Una vez verificados los checks, el PR vuelve a apilarse sobre #872.

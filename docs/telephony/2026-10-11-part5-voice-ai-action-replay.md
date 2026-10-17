# Helvoca Parte 5/8: acciones seguras de la recepcionista IA

## Riesgos encontrados y corregidos

El adaptador Gemini Live mantenía un set de identificadores de function-call completadas y, cuando recibía de nuevo el mismo ID, omitía el mensaje entero. Así no volvía a ejecutar la mutación, pero tampoco respondía al proveedor. En el modo BLOCKING introducido en Parte 4, la repetición podía dejar una acción de reserva/cancelación sin su respuesta. Era necesario garantizar **una ejecución, tantas respuestas idénticas como reintentos válidos**.

El mismo adaptador cacheaba durante 30 segundos las consultas `find_caller`, `list_available_slots` y `check_booking_availability`. Una segunda consulta válida con un ID nuevo podía recibir información obsoleta, incluso después de actualizar una reserva o identificar al cliente.

## Cambios

- `GeminiToolCallLedger`: ledger **por sesión**, sincronizado, de resultados de herramientas vinculados a `functionCall.id`, nombre de herramienta y argumentos JSON canónicos.
- Un reintento con el **mismo ID, nombre y argumentos** recibe una copia de la respuesta original, sin ejecutar de nuevo la acción, registrar un segundo evento de certificación ni producir doble reserva.
- Un ID reutilizado con nombre o argumentos distintos recibe `TOOL_CALL_ID_CONFLICT`; nunca se ejecuta la acción contradictoria.
- Límite conservador de 512 IDs por llamada, sin desalojo de evidencias de idempotencia. A partir del límite, herramientas nuevas devuelven `TOOL_CALL_LIMIT_REACHED` y no generan efectos secundarios.
- Las llamadas de `find_caller`, `list_available_slots` y `check_booking_availability` con IDs distintos consultan siempre al backend. Se conserva una deduplicación corta exclusivamente para `list_services`.
- IDs vacíos y nombres de herramienta vacíos no disparan ejecución.
- Tests de replay de creación de reservas, cancelación contradictoria bloqueada, frescura de disponibilidad, concurrencia, capacidad, argumentos JSON reordenados y llamadas malformadas.

La lógica de autorización por tenant, confirmación de reservas en dos fases y revalidación de disponibilidad antes de crear la reserva **no se sustituye ni debilita**. El ledger evita solo la repetición del *mismo* ID de Gemini: solicitudes diferentes siguen bajo las garantías de idempotencia y transacción del backend.

## Criterio de certificación

- CI verde asociado al HEAD exacto.
- 100% de cobertura diferencial en Java (líneas, ramas y métodos), sin modificar umbrales.
- Java/PostgreSQL/Testcontainers, Playwright E2E, cobertura de frontend y gates de integración verde.
- Sin llamadas reales, pruebas con coste de proveedor, compras, migraciones de producción, despliegues ni merges.
- Dependencias sin fusionar: PR #783, PR #784 y PR #785. Se requiere recertificación de integración contra main antes de producción.
- El número piloto de Twilio y su AccountSid/PhoneNumberSid/subcuenta/webhooks siguen sujetos a verificación independiente: por ello, incluso con CI exitoso, la habilitación comercial permanece NO-GO.

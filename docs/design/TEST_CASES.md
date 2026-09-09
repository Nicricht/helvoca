# Helvoca - Casos de prueba críticos

| ID | Caso | Resultado esperado |
|---|---|---|
| CP-001 | Login correcto de BUSINESS_ADMIN | 200 y JWT con roles y business_id. |
| CP-002 | Usuario de negocio A intenta leer cliente de negocio B | 403/404 sin fuga de datos. |
| CP-003 | Webhook Twilio con firma inválida | 403 y no se crea llamada. |
| CP-004 | Llamada a número no registrado | Respuesta controlada, sin asignar tenant incorrecto. |
| CP-005 | Webhook de llamada repetido | Se procesa una sola vez. |
| CP-006 | Pregunta de conocimiento existente | Respuesta basada en knowledge_item activo del tenant. |
| CP-007 | Pregunta no conocida | IA no inventa; ofrece alternativa/transferencia. |
| CP-008 | Reserva en horario disponible | 201, booking CONFIRMED. |
| CP-009 | Dos llamadas intentan reservar simultáneamente mismo recurso y franja | Solo una reserva se confirma; la otra recibe 409. |
| CP-010 | Reserva fuera del horario del negocio | 422/409 según regla; no se crea reserva. |
| CP-011 | Reserva en día marcado como cerrado por excepción | Se rechaza. |
| CP-012 | Tool `create_order` deshabilitada para el agente | Acción DENIED, no se persiste pedido. |
| CP-013 | API externa devuelve error al crear reserva | IA informa fallo real; jamás confirma reserva. |
| CP-014 | Cliente solicita operador disponible | Se crea transfer, contexto se entrega y estado pasa a ACCEPTED. |
| CP-015 | Operador no disponible | Se crea callback_request PENDING. |
| CP-016 | Cliente interrumpe a la IA mientras habla | Se limpia audio pendiente y la IA escucha la nueva intervención. |
| CP-017 | Fin de llamada | Se actualiza duración/estado y se persiste resumen. |
| CP-018 | Transcripción llega fuera de orden | Se almacena/consulta usando sequence_number. |
| CP-019 | Integración caída | Circuit breaker/fallback; plataforma sigue atendiendo preguntas que no dependen de ella. |
| CP-020 | Refresh token revocado | No emite nuevo access token. |
| CP-021 | BUSINESS_ADMIN intenta endpoint PLATFORM_ADMIN | 403. |
| CP-022 | Pedido repetido con misma idempotency_key | Se devuelve resultado existente; no duplica pedido. |

## Pirámide mínima
- Unitarias: reglas de disponibilidad, permisos, cálculo de totales, decisiones de fallback.
- Integración: repositorios, exclusión de solapamientos, seguridad tenant, idempotencia.
- Contrato: OpenAPI y proveedores externos simulados.
- End-to-end: llamada simulada → tool → persistencia → cierre.

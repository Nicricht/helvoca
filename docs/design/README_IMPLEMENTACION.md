# Helvoca - Diseño técnico de implementación MVP

## Stack decidido
- Backend: Java + Spring Boot, monolito modular.
- Base de datos: PostgreSQL.
- Cache: Redis, opcional para sesiones, rate limiting y datos efímeros.
- Mensajería: RabbitMQ, opcional para notificaciones, webhooks y trabajos no críticos del camino de audio.
- Telefonía inicial: Twilio Programmable Voice + Media Streams bidireccional.
- IA de voz: OpenAI Realtime API mediante WebSocket.
- Frontend: React recomendado para el panel web.
- Contrato: REST `/api/v1`; webhooks separados en `/webhooks/v1`; WebSocket de audio en `/ws/v1`.

## Principios no negociables
1. BUSINESS es el tenant.
2. El `business_id` privado sale del JWT, nunca de un parámetro confiado del frontend.
3. La IA no accede directamente a PostgreSQL.
4. La IA solo ejecuta tools habilitadas en `ai_agent_capability`.
5. El backend determina el resultado real de reservas, pedidos e integraciones.
6. Webhooks y comandos críticos usan idempotencia.
7. Las reservas evitan solapamientos en la propia base de datos, no solo mediante una consulta previa.
8. Los secretos se almacenan en un secret manager o referencia segura, no en texto plano.
9. La ruta de audio no debe depender de RabbitMQ.
10. Toda acción sensible genera auditoría.

## Flujo real de llamada
1. Cliente llama al número del negocio.
2. Twilio envía webhook HTTP al backend.
3. Backend valida firma y localiza `phone_number`.
4. Se resuelve `business_id` y agente activo.
5. Se crea `call_session`.
6. Backend devuelve TwiML con `<Connect><Stream>`.
7. Twilio abre WebSocket y envía audio en tiempo real.
8. El gateway de audio conecta con OpenAI Realtime.
9. La IA conversa y, si requiere una acción, emite una tool call.
10. `AiToolDispatcher` valida capability y llama al servicio de dominio.
11. El servicio valida tenant y reglas, ejecuta transacción y devuelve resultado.
12. La IA comunica exactamente ese resultado.
13. Al terminar se persisten transcripción, resumen, resultado y métricas.

## Módulos Spring Boot
com.helvoca
├── auth
├── tenant
├── business
├── user
├── customer
├── catalog
├── resource
├── booking
├── call
├── ai
├── knowledge
├── telephony
├── transfer
├── integration
├── notification
├── order
├── audit
└── shared

Cada módulo:
- api: controllers y DTOs
- application: casos de uso
- domain: entidades y reglas
- infrastructure: JPA, clientes HTTP, proveedores

## Seguridad
- Spring Security como Resource Server.
- Access JWT corto.
- Refresh token rotatorio y revocable.
- Claims mínimas: `sub`, `business_id`, `roles`, `permissions`, `iss`, `aud`, `exp`.
- `PLATFORM_ADMIN` puede no tener `business_id`.
- `BUSINESS_ADMIN` y `OPERATOR` deben tener `business_id`.
- Filtro/interceptor construye `TenantContext` desde el token.
- Repositorios privados filtran siempre por `business_id`.
- `@PreAuthorize` valida roles/permisos.
- Webhooks no usan JWT: validan firma del proveedor.
- WebSocket de audio queda asociado a una llamada ya validada.

## Tools del agente
- `get_business_information`
- `search_knowledge`
- `find_customer_by_phone`
- `search_services`
- `check_booking_availability`
- `create_booking`
- `cancel_booking`
- `reschedule_booking`
- `create_order`
- `get_order_status`
- `request_human_transfer`
- `send_notification`

## Códigos de error de dominio
- TENANT_ACCESS_DENIED
- BUSINESS_NOT_ACTIVE
- AGENT_NOT_ACTIVE
- CAPABILITY_NOT_ALLOWED
- CUSTOMER_NOT_FOUND
- SERVICE_NOT_FOUND
- RESOURCE_NOT_AVAILABLE
- BOOKING_SLOT_UNAVAILABLE
- BOOKING_OUTSIDE_BUSINESS_HOURS
- BOOKING_NOT_FOUND
- OPERATOR_UNAVAILABLE
- INTEGRATION_UNAVAILABLE
- PROVIDER_SIGNATURE_INVALID
- DUPLICATE_WEBHOOK
- RATE_LIMIT_EXCEEDED

## Respuesta REST estándar
Éxito:
`{"success": true, "data": {...}, "error": null}`

Error:
`{"success": false, "data": null, "error": {"code": "...", "message": "...", "correlationId": "..."}}`

## Regla de concurrencia
No basta con:
1. consultar disponibilidad,
2. luego insertar.

Dos llamadas pueden ver el mismo hueco al mismo tiempo. La restricción `booking_no_resource_overlap` en PostgreSQL actúa como última barrera. El servicio captura la violación y responde `409 BOOKING_SLOT_UNAVAILABLE`.

## Barge-in
Si el cliente interrumpe mientras la IA habla:
1. se detecta nueva voz,
2. se cancela/interrumpe la respuesta del modelo,
3. se limpia el audio pendiente del proveedor telefónico,
4. la nueva intervención pasa a ser prioritaria.

## Observabilidad
Cada llamada tendrá `correlation_id`.
Logs estructurados:
- call_id
- business_id
- provider_call_id
- tool
- latency_ms
- result
- error_code

Métricas:
- active_calls
- ai_response_latency
- tool_latency
- transfer_rate
- booking_conversion
- provider_errors
- websocket_disconnects

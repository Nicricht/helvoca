# Helvoca - Product Backlog MVP

## Objetivo
Atender llamadas entrantes de negocios con un agente de voz con IA que pueda responder preguntas, consultar información del negocio, crear reservas mediante acciones controladas, transferir a una persona y registrar el resultado sin mezclar datos entre negocios.

## Historias de usuario y criterios de aceptación

| ID | Historia | Prioridad | Criterios de aceptación |
|---|---|---|---|
| HU-001 | Como administrador de negocio quiero iniciar sesión para administrar únicamente mi negocio. | P0 | Token válido; `business_id` se obtiene del token; otro tenant devuelve 403/404; roles aplicados. |
| HU-002 | Como administrador quiero configurar los datos básicos, horarios y excepciones de mi negocio. | P0 | Permite horarios semanales; feriados/excepciones; valida horas; cambios quedan auditados. |
| HU-003 | Como administrador quiero configurar el agente de IA. | P0 | Nombre, saludo, idioma, voz, instrucciones y capacidades; solo capacidades habilitadas pueden ejecutarse. |
| HU-004 | Como negocio quiero que una llamada entrante sea atendida automáticamente. | P0 | Se valida firma del proveedor; se identifica número destino; se crea `call_session`; se inicia audio bidireccional. |
| HU-005 | Como cliente quiero conversar por voz sin usar menús telefónicos rígidos. | P0 | Audio entrante se procesa en tiempo real; respuesta vuelve por voz; permite interrupción del cliente. |
| HU-006 | Como cliente quiero preguntar horarios, dirección, servicios y políticas. | P0 | IA responde solo con conocimiento del tenant; si no sabe, reconoce la limitación o transfiere. |
| HU-007 | Como sistema quiero identificar al cliente por teléfono. | P0 | Busca dentro del tenant; si no existe puede crearlo; nunca cruza clientes de otro negocio. |
| HU-008 | Como cliente quiero consultar disponibilidad antes de reservar. | P0 | Considera horario, excepciones, servicio, recurso y reservas existentes. |
| HU-009 | Como cliente quiero crear una reserva durante la llamada. | P0 | Confirma fecha/hora; backend es fuente de verdad; evita solapamientos concurrentes; devuelve 201 o 409. |
| HU-010 | Como cliente quiero cancelar o reprogramar una reserva. | P1 | Valida pertenencia y reglas del negocio; registra auditoría; devuelve nuevo estado real. |
| HU-011 | Como cliente quiero pedir hablar con una persona. | P0 | Crea solicitud; envía contexto; transfiere si hay operador; crea callback si no lo hay. |
| HU-012 | Como administrador quiero ver las llamadas realizadas. | P0 | Lista paginada por tenant con estado, duración, resultado y fecha. |
| HU-013 | Como administrador quiero revisar transcripción y resumen. | P0 | Segmentos ordenados; resumen asociado a una sola llamada; acceso restringido al tenant. |
| HU-014 | Como administrador quiero gestionar una base de conocimiento. | P0 | CRUD; activación/desactivación; IA solo consulta elementos activos del tenant. |
| HU-015 | Como administrador quiero ver métricas básicas. | P1 | Total llamadas, duración media, resueltas, transferidas, reservas y fallos. |
| HU-016 | Como negocio quiero enviar confirmaciones luego de una llamada. | P1 | Canal configurado; estado PENDING/SENT/FAILED; reintento controlado. |
| HU-017 | Como negocio quiero conectar calendarios/CRM/ERP. | P1 | Credenciales fuera del código; prueba de conexión; fallos externos no inventan éxito. |
| HU-018 | Como restaurante/comercio quiero que la IA cree pedidos. | P1 | Pedido e ítems persistidos; total validado por backend; idempotencia; estado consultable. |
| HU-019 | Como administrador de plataforma quiero administrar negocios. | P1 | Crear, suspender, reactivar y consultar negocio; no expone secretos. |
| HU-020 | Como plataforma quiero procesar webhooks de forma idempotente. | P0 | Evento duplicado no ejecuta dos veces la operación; firma validada; resultado persistido. |

## Orden de implementación

### Sprint 0 - Fundaciones
HU-001, HU-002, HU-003, HU-019, HU-020.
Entregables: PostgreSQL, migraciones, tenant context, JWT, roles/permisos, auditoría, Docker.

### Sprint 1 - Llamada e IA
HU-004, HU-005, HU-006, HU-007, HU-014.
Entregables: webhook de voz, WebSocket de audio, Realtime AI, knowledge tools, registro de llamada.

### Sprint 2 - Reservas
HU-008, HU-009, HU-010.
Entregables: disponibilidad, recursos, prevención de solapamiento, tools de reserva.

### Sprint 3 - Operación
HU-011, HU-012, HU-013, HU-015.
Entregables: transferencia, callback, historial, detalle, transcripción, resumen y dashboard.

### Sprint 4 - Extensiones
HU-016, HU-017, HU-018.
Entregables: notificaciones, integraciones y pedidos.

## Definition of Done
- Pruebas unitarias del dominio.
- Pruebas de integración con PostgreSQL.
- Contrato OpenAPI actualizado.
- Validación de tenant en cada endpoint privado.
- Logs con `correlation_id`.
- Ningún secreto en repositorio.
- Errores de proveedor traducidos a códigos de dominio.
- IA nunca declara éxito sin respuesta exitosa del backend.

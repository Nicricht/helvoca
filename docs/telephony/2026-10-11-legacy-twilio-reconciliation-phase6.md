# Fase 6 de 10: autorización humana, doble aprobación y auditoría Twilio

## Estado de despliegue

Solo se implementa en el PR #791, **draft y sin fusionar**. Las rutas nuevas están **DESACTIVADAS POR DEFECTO** por `app.telephony.twilio.existing-enrollment-admin-enabled`; únicamente el valor explícito `true` registra el controlador. No habilitar la propiedad en Railway sin GO comercial, auditoría externa y autorización expresa. No se realizan compras, llamadas, conexiones de voz, cambios a webhooks ni migraciones en producción.

## Compromisos de seguridad

- Controlador `/api/v1/platform/twilio-existing-enrollments`, cubierto por el rol `PLATFORM_ADMIN` en la cadena HTTP **y** en `@PreAuthorize`. El servicio valida adicionalmente el JWT autenticado y firmado por la infraestructura Spring Security: subject UUID, email, claim de rol y autoridad; consulta en cada petición el usuario activo y su rol **vigente** en PostgreSQL. Se exige que el operador de plataforma no pertenezca a un tenant (`app_user.business_id IS NULL`). Los roles revocados no mantienen permisos por JWT obsoletos.
- El primer administrador solo solicita un cambio; el segundo, autenticado con un token personal diferente, **aprueba por separado**. No se acepta un `requester_id` o `approver_id` en el cuerpo del navegador ni un SID PN autoafirmado. El Account SID solicitado es solo expectativa para comprobación posterior mediante la API Twilio, no evidencia de titularidad.
- Las solicitudes vencen como máximo a los **15 minutos**. Solo el aprobador puede ejecutar y se confirma nuevamente que el solicitante siga activo y autorizado. La evidencia Twilio se verifica nuevamente con el servicio de Fase 5 justo al ejecutar.
- `request`: body {businessId,phoneId,ownerAccountSid}; devuelve {changeId,state}. `approve`: POST /{changeId}/approve, body vacío; `execute`: POST /{changeId}/execute, body vacío; `status`: GET /{changeId}. Nunca se devuelve el SID PN, Account SID, número, identidad o token en las respuestas.
- Se impiden solicitudes activas duplicadas del mismo número mediante `SELECT ... FOR UPDATE` de la fila telefónica y verificación del estado; los cambios de estado se serializan mediante `FOR UPDATE` de la solicitud.
- `execute` llama al reconciliador de Fase 5, que usa una transacción propia y solamente acepta SYSTEM, revalida el operador, el tenant, el número y el propietario; la atribución no activa llamadas, no cambia `active`, no inicia sesiones IA.

## PostgreSQL V103

`V103__twilio_existing_enrollment_admin_approval.sql` agrega:

- `twilio_existing_enrollment_request` con claves UUID y FK de tenant/teléfono y de humanos de plataforma, estado `PENDING/APPROVED/APPLIED`, tiempos coherentes y caducidad máxima de 15 minutos.
- Trigger que solo permite transición `PENDING → APPROVED → APPLIED`, bloquea reescrituras de cuenta, identidad, tenant, teléfono, instante y vencimiento.
- `twilio_existing_enrollment_audit`: historial **append-only** con UUID del actor, cambio, acción `REQUESTED/APPROVED/EXECUTED/EXECUTION_BLOCKED`, resultado seguro y sello temporal. No contiene secretos ni SID de proveedor.
- `FORCE ROW LEVEL SECURITY` en ambas tablas, política exclusivamente SYSTEM y grants explícitos: request SELECT/INSERT/UPDATE, audit SELECT/INSERT; **ningún rol tenant puede leer o modificar** y ni siquiera el rol de aplicación SYSTEM puede borrar el historial.

La auditoría de solicitud y aprobación se guarda en la misma transacción de cada transición. La ejecución de reconciliación realiza su propio commit idempotente de Fase 5; si se interrumpe el registro de ejecución después de un commit exitoso, una repetición dentro de la vigencia retorna `ALREADY_APPLIED` y completa el estado/auditoría, evitando una segunda vinculación.

## Pruebas

`TwilioExistingEnrollmentAdminPostgresIntegrationTest` usa PostgreSQL 16 + Testcontainers + todas las migraciones Flyway y un reconciliador simulado (sin carrier HTTP real). Verifica roles frescos, separación entre dos tokens, solicitudes fraudulentas, expiración, permisos RLS, inmutabilidad, bloqueos, auditoría, impedimento a otros tenants, replays y revocación de roles. Las pruebas previas de Fase 5 ejercitan la transacción real con Twilio simulado.

`TwilioExistingEnrollmentAdminControllerContractTest` comprueba que el controlador sigue desactivado por defecto, requiere rol de plataforma y expone exactamente las rutas administrativas de alcance reducido.

**No confundir esto con una autorización de despliegue real:** el GO comercial, la certificación del SID de la cuenta/subcuenta piloto y los flujos de llamadas siguen pendientes de Fases 7 a 10. No iniciar Fase 7 sin petición del usuario.

Criterio de cierre de la Fase 6: 7/7 CI sobre el SHA final, 100 % cobertura diferencial Java de líneas, ramas y métodos y ausencia de efectos externos.

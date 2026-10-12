# Helvoca: Fase 5/10, reconciliación atómica de número Twilio existente

Estado: **implementación interna en PR en borrador**, sin operación administrativa pública, sin llamadas reales y sin despliegue.

## Operación interna protegida

`TwilioExistingEnrollmentReconciler` ejecuta exclusivamente bajo `TenantDatabaseContext.Mode.SYSTEM`, establecido previamente por un código de plataforma confiable. **No se eleva automáticamente un tenant a sistema.** Su entrada es el `Intent` interno de la Fase 3; NO es un endpoint ni una credencial de autorización. La Fase 6 deberá validar criptográficamente las identidades, la aprobación independiente y el alcance, registrar el consentimiento y la auditoría durable, y solo entonces conceder el contexto SYSTEM. Mientras eso no exista, **no llamar a este componente desde rutas HTTP o flujos de tenant**.

Flujo:
1. Rechazar de inmediato contextos sin SYSTEM, identidades nulas, roles ajenos o mismo revisor.
2. Comprobar primero, con acceso restringido, un eventual claim técnico del mismo `change_id`. Solo devolver `ALREADY_APPLIED` cuando todos los campos originales y el estado actual del número coincidan. Un replay divergente es `REPLAY_CONFLICT`.
3. Leer el número real por UUID desde la base de datos bajo scope SYSTEM; `TwilioExistingEnrollmentPlanner.preview` vuelve a validar tenant, E.164, procedencia, PN/AC históricos, roles separados y **llama de nuevo al verificador autenticado de Twilio**. No acepta un candidato `REVIEWABLE_ONLY` cacheado.
4. Iniciar una nueva transacción de PostgreSQL `READ COMMITTED`, volver a comprobar el claim y actualizar mediante un solo `UPDATE ... WHERE` condicional el tenant, teléfono, estado activo, proveedor, `updated_at`, SID anterior, cuenta anterior, procedencia NULL y voice_verified_at NULL. Un cambio concurrente causa `ROW_CONFLICT`, no un reemplazo.
5. Dentro de la **misma transacción**, insertar un claim técnico único de `change_id`, teléfono y PN, con solicitante/revisor y momento. Un conflicto de unicidad revierte **ambos** la vinculación y el claim.
6. No modificar `active` ni el modelo de rutas, webhooks, Twilio, Gemini, tickets u operaciones pagadas. `TwilioCallBindingGuard` sigue permitiendo solo `MANAGED_PURCHASE`.

Los resultados públicos potenciales son exclusivamente códigos diagnósticos cerrados, sin SID, cuenta, número, tenant ni contenido de excepciones.

## V102: registro técnico de idempotencia

`V102__twilio_existing_enrollment_claim.sql` agrega `twilio_existing_enrollment_claim`:

- Primary key de `change_id`, unicidad de `phone_number_id` y SID, FK compuesta (`phone_number_id`, `business_id`) a la fila original.
- Dos sujetos diferentes, formato PN/AC/E.164 y instante de inscripción requeridos.
- `FORCE ROW LEVEL SECURITY`, política solo para `helvoca_system`, y `REVOKE` explícito de privilegios heredados de defaults de Flyway. A `helvoca_system` se le concede solo `SELECT, INSERT`; los tenants no pueden consultar, alterar ni eliminar el ledger.
- Es únicamente una **traza técnica de idempotencia**, no constituye prueba de la identidad humana ni aprobación administrativa auditable. Lo segundo corresponde a la Fase 6.
- No modifica filas existentes, sin backfills ni activaciones.

## Pruebas

`TwilioExistingEnrollmentReconcilerPostgresIntegrationTest`: PostgreSQL 16 real, Flyway V1..V102, tenant RLS, prueba de relectura fresca con Twilio HTTP **simulado**, rechazo de rol tenant y sin scope, idempotencia y replays falsificados, rollback por SID duplicado, cambio de fila entre evidencia y transacción, concurrencia de dos cambios iguales y distintos, verificación de FK, privilegios y aislamiento, y guard de llamadas aún fail-closed.

**Ninguna prueba llama a Twilio real** ni altera PostgreSQL de producción. Esto demuestra la corrección de la operación frente a eventos simulados, no la titularidad real del número piloto.

## Límites de seguridad y continuidad

- No existe todavía un endpoint de plataforma con autorización/auditoría humana real: **NO utilizar en producción** hasta cerrar Fase 6.
- La foto de estado de Twilio es instantánea: cualquier cambio posterior en la cuenta, subcuenta o webhooks exige una nueva verificación antes de autorizar llamadas. Fases 7 y 10.
- No aplicar V102 en Railway ni combinar con despliegue sin permiso expreso. PR #791 permanece draft y sin merge sobre historial PR #790.
- Fase 6: compuerta administrativa, doble aprobación auténtica, expiración de evidencia, auditoría, permisos, diagnóstico sin datos privados. No iniciar sin orden del usuario.

Criterio de salida: CI completo 7/7 SUCCESS sobre el SHA final, cobertura diferencial Java aplicable 100 % de líneas/ramas/métodos, y pruebas reales Testcontainers sin llamadas.

# Importación con IA: cuotas comerciales por suscripción (Parte 4/5)

## Estado de seguridad
**NO se habilitó la importación pagada ni se desplegó esta rama.** El interruptor
`HELVOCA_BUSINESS_IMPORT_AI_ENABLED=false` sigue apagado en producción; presupuestos
y número de intentos autorizados continúan en cero por defecto. La presencia de
una clave de proveedor o un plan de Railway no equivale a presupuesto de OpenAI.

## Sistema reutilizado
- `V42 commercial_plan`, `commercial_plan_entitlement` y `business_subscription`
  siguen siendo la única autoridad para planes y períodos.
- `V99` agrega el derecho `AI_IMPORT_REQUESTS` (USAGE, REQUESTS, hard limit)
  con límite **0 para todos los planes existentes**. No se inventan beneficios,
  precios, consumos contratados ni recargos. Para un plan nuevo, el derecho ausente
  también falla cerrado. Un operador deberá aprobar y ajustar el derecho, fuera
  de este cambio, antes de ofrecer importaciones pagadas.
- `V41 usage_meter_event` persiste reservas atómicas de 1 solicitud por intento,
  limitadas al período `[current_period_start, current_period_end)` de la
  suscripción, con `business_id`, idempotencia y RLS. No se crea otro ledger.
- `V98 business_import_ai_provider_usage_event` continúa registrando STARTED,
  RESPONSE y UNCERTAIN y tokens reales **cuando el proveedor los entrega**. No
  se combinan token counts con contadores de solicitudes, ni se inventan
  costos facturados. La conciliación real sigue en issue #773.

## Autorización y ejecución
La importación CSV/TSV/XLS/XLSX local no usa proveedor ni consume derecho.
Las imágenes/PDF pasan por la bandera maestra y validaciones de tamaño;
antes de intentar OpenAI, el backend verifica:
1. Derecho activo `AI_IMPORT_REQUESTS` para el plan comercial real, y negocio
   `ACTIVE`. Suscripción solo `ACTIVE` o `TRIALING`, dentro del período actual.
   PAST_DUE no autoriza nuevo gasto incluso si existe gracia para otros servicios.
2. Preflight comercial read-only para no permitir que un plan sin derecho agote
   el presupuesto global.
3. Guardas financieras heredadas: máximos por negocio, intentos y presupuesto
   reservado global en PostgreSQL. Un cero en cualquier control niega la llamada.
4. Reserva final transaccional: `SELECT ... FOR UPDATE OF s` sobre la
   suscripción del negocio, suma V41 dentro del período, compara saldo, y añade
   una única fila V41 con UUID e idempotencia persistente. El lock serializa
   solicitudes simultáneas en diferentes réplicas. La reserva no se revierte
   después de errores inciertos. Una solicitud denegada no llama a OpenAI.
5. Evidencia STARTED V98 confirmada antes de enviar la solicitud. Si falla la
   evidencia, no se llama al proveedor.

**Conservadurismo:** las reservas V41 cuantifican intentos autorizados, no
importaciones exitosas ni token counts. Los límites de gasto preventivos son
secuenciales e independientes: en una carrera el control global puede reservar
capacidad antes de que una reserva final de plan resulte rechazada. No se
reembolsa esa capacidad global. Jamás declarar estas reservas gasto real.
No prometer una cota de facturación exacta: validar límites del proveedor,
precios efectivos, permisos y facturas con presupuesto explícito antes de piloto.

## Contrato de consulta y UX
`GET /api/v1/onboarding/import/ai-quota` exige `BUSINESS_ADMIN`, deriva
siempre el negocio de su JWT, y devuelve estado, plan, limit, used, remaining,
inicio/fin de ciclo y estado del interruptor maestro. No expone consumos
ni documentos de otros negocios. Los estados incluyen AVAILABLE, DISABLED,
BUSINESS_INACTIVE, SUBSCRIPTION_INACTIVE, PERIOD_EXPIRED, NOT_INCLUDED,
LIMIT_REACHED y UNAVAILABLE. Si no hay datos seguros, devuelve UNAVAILABLE.
El frontend usa la pantalla de importación existente, conserva los flujos
gratuitos y muestra un mensaje de cupo o fallo de verificación sin ofrecer
activación ni prometer pagos.

## Operación y despliegue
Mantener ambos PR en DRAFT hasta lograr CI exact-head, diferencial Java 100%
de líneas/ramas/métodos, e2e Playwright y PostgreSQL real. Si se fusiona
la rama base #774, retargetear PR de Parte 4 a main y verificar de nuevo.
Ningún despliegue o gasto debe ejecutarse en la Parte 4 por defecto.
Antes de habilitar: aprobar beneficios reales por plan y presupuesto
separado de OpenAI, realizar conciliación de invoice según
`BUSINESS_IMPORT_AI_USAGE_RECONCILIATION.md`, verificar límite de proveedor
y Railway Wait for CI. Solo Parte 5, previa orden explícita, evaluará
la transición a producción y piloto.

## Nota de certificación CI (2026-10-10)
El workflow `.github/workflows/ci.yml` solo escucha `pull_request`
contra `main`. Por ello el PR #775 se dirige a `main` **como DRAFT**,
manteniendo su HEAD descendiente directo del PR #774 hasta que este último
sea fusionado. La certificación no autoriza fusionar ni desplegar #775; el
diff contra `main` temporalmente incluye la dependencia V98 de #774.

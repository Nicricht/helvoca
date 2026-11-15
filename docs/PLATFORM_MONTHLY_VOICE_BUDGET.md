# Presupuesto mensual global de voz: admisión estimada, no límite de facturación

RecepVoz es multiempresa. La aprobación del titular de **$20.000 CLP/mes** corresponde al presupuesto inicial de operación de **Twilio + Gemini en conjunto**, no a una cuota gratis ni a un límite de gasto independiente por cliente.

Este PR implementa una barrera **opt-in** sobre el comienzo de nuevas llamadas entrantes:

- `HELVOCA_MAX_ESTIMATED_PLATFORM_COST_PER_UTC_MONTH_CLP`: techo global estimado en CLP, **0 por defecto, desactivado**. Para el presupuesto autorizado el objetivo sería 20000, pero no aplicar aún sin las demás condiciones.
- `HELVOCA_ESTIMATED_NEW_CALL_RESERVATION_USD`: reserva conservadora por nueva llamada que contemple el máximo de sesión y riesgo de redondeo del carrier.
- `HELVOCA_COST_USD_TO_CLP`: tipo de cambio verificado con fecha, nunca usar cero.
- `HELVOCA_TELEPHONY_COST_PER_MINUTE_USD` / `HELVOCA_TWILIO_COST_PER_MINUTE_USD` / `HELVOCA_AI_COST_PER_MINUTE_USD` y overrides de Gemini: estimaciones verificadas con proveedor; cualquier tarifa aplicable cero hace fallar la admisión.
- `HELVOCA_VERIFIED_UNTRACKED_PLATFORM_SPEND_THIS_MONTH_CLP`: saldo **verificado expresamente** del consumo externo, cargos fijos, impuestos/ajustes y llamadas ajenas a `call_session`, descontando solo costes ya incluidos en el estimado local. Cuando falta, el control activado rechaza nuevas llamadas. **Debe actualizarse según la facturación real del mes**.

El control serializa la admisión entre negocios y réplicas PostgreSQL con un único advisory transaction lock, bajo **SYSTEM** (nunca calcula un agregado global dentro del RLS de un tenant). Suma todas las llamadas persistidas del mes UTC, incluidas las incompletas y de certificación, reservando al menos un valor positivo a cada una. Considera la reserva de la nueva llamada y lo no contabilizado antes de aceptar.

Las llamadas de proveedor con el mismo CallSid mantienen la idempotencia. Ninguna configuración ni bandera de Gemini/Twilio LIVE se modifica en este PR.

**No es un hard cap financiero:** la estimación puede diferir de los cargos de Twilio, tokens de Gemini, tipo de cambio, impuestos, latencia de facturas y llamadas en curso. El corte de sesión Twilio de 600 segundos ya se activó separadamente en Railway production pero sigue siendo una salvaguarda, no un límite de facturación garantizado. Antes de abrir llamadas comerciales hace falta conciliar proveedor, activar sus límites operativos disponibles y verificar el STOP de llamadas en vuelo.

**NO desplegar ni habilitar por el mero hecho de aprobar CI:** comprobar contratos/QA del HEAD, saldo inicial del mes vigente, tarifas vigentes, autorización por tenant, límite/alertas del proveedor y respaldo/rollback. Mantener este PR en DRAFT hasta completar esas verificaciones.

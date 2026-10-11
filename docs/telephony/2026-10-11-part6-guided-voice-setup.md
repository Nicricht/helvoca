# Helvoca: Parte 6/8 | configuración guiada y verificación telefónica segura

## Objetivo y riesgo auditado

La pantalla anterior de Canales mostraba el estado `active` del registro telefónico pero no su evidencia de propiedad, coincidencia de cuenta Twilio/SID, fecha de verificación ni advertencia de que la **activación registrada no equivale a una llamada exitosa**. Un usuario podía interpretar un número activo como una recepcionista IA lista comercialmente. Además, la compra de un número nuevo devuelve deliberadamente una fila **inactiva** hasta la verificación con el carrier.

## Contrato backend

`GET /api/v1/phone-numbers/provisioning/readiness` (autenticado, `BUSINESS_ADMIN` o `OPERATOR`) consulta exclusivamente `findAllByBusinessIdOrderByCreatedAtDesc(tenantProvider.requireBusinessId())`. No recibe `businessId` desde el navegador.

La proyección `PhoneVoiceReadinessResponse` expone solo el ID del registro, teléfono, estado, elegibilidad para intentar activar, última verificación histórica y explicación. **NO expone tokens, el SID de la cuenta ni el PhoneNumberSid**. Nunca consulta Twilio ni muta PostgreSQL.

Estados:
- `UNSUPPORTED_PROVIDER`: proveedor distinto de Twilio.
- `UNVERIFIED_BINDING`: falta evidencia `MANAGED_PURCHASE`, SID `PN...` válido o cuenta registrada.
- `ACCOUNT_MISMATCH`: la cuenta guardada difiere de la cuenta administrada configurada.
- `NEEDS_VERIFICATION`: vinculación registrable, pero nunca certificada.
- `ACTIVATION_REQUIRED`: verificación histórica registrada, teléfono inactivo.
- `PREVIOUSLY_VERIFIED`: verificación registrada y teléfono activo; **NO implica operatividad actual ni acceso comercial**.

El endpoint existente `PATCH /phone-numbers/{id}/active` conserva todas sus validaciones live de Twilio y el acceso admin. No se activa ni se compra al cargar la pantalla.

## Interfaz

`Configuración → Canales → Telefonía` presenta tres pasos: número registrado, identidad/procedencia telefónica y certificación pendiente de llamada. La explicación siempre distingue datos históricos de la prueba real y ofrece activación solo si la vinculación es elegible y el usuario es administrador. Al hacer clic, un diálogo explícito describe el alcance de la consulta a Twilio y requiere aprobación.

Se corrigieron las notificaciones de aprovisionamiento: un número adquirido se muestra **registrado e inactivo**, no «conectado» como si estuviera listo. El diagnóstico se invalida tras altas, bajas y activaciones; ante errores, se bloquea la presunción de readiness y se permite reintentar solo lectura.

La interfaz usa controles semánticos, mensajes `role=status/alert`, selector con etiqueta, distribución adaptable a 390px, foco visible y movimiento reducido.

## Certificación sin gasto

Pruebas unitarias de cada causa de bloqueo, cuentas cruzadas, estado histórico y tenant, verificando ausencia de `twilio.verifyManagedPhone()`, `twilio.provision()` y escrituras. Pruebas reales de navegador con respuestas mock, errores, propietario vs operador, cancelación de activación, compra bloqueada y 390px.

Para cerrar: GitHub Actions verde en SHA exacto, 100% diferencial (líneas, ramas, métodos), suite backend PostgreSQL/Testcontainers y Playwright E2E, frontend, gates de integración.

**Fuera de alcance:** llamadas reales, consumo Gemini/Twilio, compra de números, actualización de configuración o datos productivos, merge/deploy. Los PR #783–#786 están apilados y pendientes. Certificación comercial sigue NO-GO hasta conciliar el piloto y autorizar una prueba real end-to-end.

# Helvoca | Parte 6/8: guía de configuración autónoma de telefonía IA

## Hallazgo de seguridad y experiencia

El onboarding existente (`OnboardingService.buildStatus`) considera el teléfono configurado cuando existe **cualquier fila activa**. Este dato no garantiza cuenta Twilio correcta, PhoneNumberSid, evidencia de aprovisionamiento, webhooks vigentes ni una llamada real terminada correctamente. El nuevo diagnóstico distingue explícitamente configuración local de certificación comercial, evitando interpretar `phoneConfigured` o `readyForCalls` como validación real de producción.

## Endpoint GET de solo lectura

`GET /api/v1/onboarding/voice-readiness`, autorizado solo para `BUSINESS_ADMIN` y `OPERATOR` mediante `@PreAuthorize`; toma el businessId del JWT validado con `TenantProvider.requireBusinessId`, nunca del cliente ni de query parameters.

Devuelve:

- `status`: `CONFIGURATION_INCOMPLETE` o `AWAITING_LIVE_CERTIFICATION`.
- `configurationReady`: true **solo** cuando todos los pasos locales necesarios estén completos.
- `liveCertified`: **false** hasta implementar y acreditar una prueba real autorizada; no se inventa certificación por un test simulado.
- `readyForCommercialCalls`: **false** por defecto conservador.
- `activePhoneNumber`: número activo visible para ese tenant, **sin** cuenta SID ni credenciales.
- `steps`: BUSINESS, SERVICES y SCHEDULE según rubro, AGENT, CARRIER, PHONE, LIVE_CALL. Cada uno ofrece estado, explicación y una ruta al editor si se puede corregir desde UI.

La comprobación de PHONE falla cerrada si falta cualquiera de estos requisitos: número activo, proveedor TWILIO, `twilio_binding_source=MANAGED_PURCHASE`, `twilio_account_sid` coincidente con la cuenta administrada, PhoneNumberSid `PN` más 32 dígitos hexadecimales, `voice_verified_at` existente y backend carrier configurado. **No invoca a Twilio** ni realiza comprobaciones remotas, compras, llamadas o mutaciones. Esta evidencia local confirma solamente un último chequeo registrado, no que el número siga coincidiendo hoy. Si hay una fila activa heredada, no puede pasar por “listo”.

## Frontend

`Configuración → Canales → Telefonía` muestra la guía, un enlace contextual a cada paso pendiente y un botón **Actualizar diagnóstico**, que realiza exclusivamente un GET. Maneja carga, autorización de lectura y fallo de API sin tratar un error como señal de disponibilidad. Una configuración completa todavía muestra explícitamente “Falta certificar una llamada real autorizada”. El mensaje de conexión de números ya no asegura que pueden atender llamadas.

## Tests y criterio de salida

- Unitarios sobre rutas opcionales de servicios y horarios, inexistencia de SID, SID ajeno, cuenta errónea, proveedor incorrecto, filas legadas, tenant scoped, fecha de verificación y bloqueo de configuración remota.
- Test de contrato endpoint con `BUSINESS_ADMIN` y `OPERATOR`.
- Playwright de UI en escenario incompleto, local completo (sigue NO-GO), error GET fail closed y operador sin controles de pago.
- Gates: 100% diferencial Java (líneas, ramas, métodos); suite de integración Postgres/Testcontainers; Playwright y frontend. Sin relajar umbrales.
- PR apilado sobre Partes 2–5 (#783 a #786), por lo que NO MERGE y NO DEPLOY sin reconciliar main.
- Hasta verificar propiedad actual del número, webhooks y llamada real autorizada: **NO-GO comercial**.

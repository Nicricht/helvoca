# Helvoca · Parte 8 de 10 · Identidad Gemini Live, consentimiento y presupuesto: NO-GO offline

**Fecha:** 2026-10-13. Base apilada: PR #817, HEAD
`0574feb80c8e4c20ab699f598f060fa57f5564af`.
**Clasificación:** certificación local de configuración, simulaciones de presupuesto
y regresión integral sin conexión a proveedores. Nunca es un permiso de llamada.

## 1. Verificación del modelo, sin consumir la API

Revisión de documentación pública de Google AI for Developers realizada el
13 de octubre de 2026:

- https://ai.google.dev/gemini-api/docs/models
- https://ai.google.dev/gemini-api/docs/models/gemini-3.8-live
- https://ai.google.dev/gemini-api/docs/deprecations/
- https://ai.google.dev/gemini-api/docs/changelog

Google identifica `gemini-3.8-live` como modelo **estable**, disponible
en la API Live, lanzado el 15 de septiembre de 2026. El preview
`gemini-3.1-flash-live-preview` se considera legado y tiene retirada
programada para noviembre de 2026 según la página de deprecaciones.

**Límite esencial:** disponibilidad en documentación pública NO acredita
autorización del proyecto Google vinculado a `GEMINI_API_KEY`, identidad
de la clave productiva, cuota, saldo, factura, token consumido, selección
actual de proveedor en Railway ni que una llamada se haya conectado. Esas
son verificaciones independientes, aún pendientes y potencialmente
facturables si se usaran peticiones o WebSocket. Esta fase NO lo hace.

## 2. Qué demuestra el código existente

- `GeminiLiveModelPolicy`: allowlist local del modelo estable y legacy,
  endpoint `wss://generativelanguage.googleapis.com/...BidiGenerateContent`.
- `GeminiLiveProperties`: `ready()` exige flag, clave no vacía, modelo
  permitido, voz y endpoint. No hace handshake.
- `GeminiLiveRuntimeReadiness`: publica solo metadatos locales seguros;
  nunca clave real ni URL arbitraria y no llama a Google.
- `application.yml`: fallback de `GEMINI_LIVE_MODEL` es
  `gemini-3.8-live`, pero un valor real del entorno puede prevalecer.
- `GeminiLiveStartupProbe`: puede abrir WebSocket a Google y se activa
  solamente con `GEMINI_LIVE_PROBE_ON_STARTUP=true`. Permanece
  **deshabilitado** en esta fase.
- `app.commercial.calls`: admite costes por minuto configurables;
  valores 0 o estimaciones no prueban límites duros del proveedor ni
  tarifas oficiales.

El test de configuración y los tests de audio verifican únicamente rutas
locales, mocks, codificación/decodificación, barge-in, redacción y cierres
seguros sin iniciar sesiones reales.

## 3. Nuevo ensayo de decisión fail-closed

Archivo `scripts/ci/voice_part8_model_cost_consent_gate.py`.

Consume solo un snapshot **sintético no confiable**. Si todos los valores
son estructuralmente correctos, devuelve `SYNTHETIC_REVIEW_ONLY`,
jamás `GO`, con:
- `may_call=false`, `may_deploy=false`, `may_merge=false`;
- `provider_handshake_performed=false`;
- `carrier_request_performed=false`;
- `charged_calls_performed=false`;
- `source_trusted=false` y `commercial_go=false`.

Reglas adversariales:
1. Modelo exacto `gemini-3.8-live`, categoría local `STABLE`;
   no preview ni extended-thinking en este ensayo, endpoint oficial exacto
   sin query ni fragment que puedan desviar credenciales.
2. Configuración local y router `GEMINI` coherentes. NO se atribuye
   conexión a Google ni verificación de clave.
3. Consentimiento de operador y participante, política de grabación revisada
   y dos revisores estructuralmente distintos. Los identificadores de
   ejemplo NO son autorizaciones reales y no se confía en un JSON aportado.
4. Propiedad Twilio, cuenta y tenant, acceso de proyecto Google, backup
   reciente, RLS y copia cifrada independientes declarados en el fixture,
   pero NUNCA autenticados por esa declaración.
5. Todas las banderas de llamada, salida, arranque probe y cambios Twilio
   apagadas; piloto PAUSED y otros tenants aislados.
6. Simulación económica: como máximo UNA llamada y 180 segundos;
   tarifa positiva de Twilio y Gemini por minuto, números finitos como
   string decimal, cupo simulado no mayor a 5 USD. Redondea minutos hacia
   arriba, aplica **reserva conservadora 2x** y rechaza importes estimados
   superiores al tope. Exige además atestaciones de límite duro externo
   y límite interno, SIN asumir que son reales.
7. La muestra sintética usa tarifas de **0.20 y 0.50 USD/min** y cap de
   **2.00 USD** exclusivamente para pruebas matemáticas. No son tarifas
   oficiales ni una recomendación de gasto. El buffer 2x no garantiza
   que un proveedor vaya a detener cargos al llegar a ese importe.
8. No imprime contenido arbitrario del snapshot, claves, teléfonos,
   tenant, SIDs, datos de participantes ni URL alterada.

Pruebas adversariales cubren límites falsos, negativos, NaN/infinito,
valores booleanos disfrazados de enteros, sobrecoste 61 segundos, ausencia
de consentimientos, revisores duplicados, URLs maliciosas y modelo
desconocido. Una salida sintética positiva jamás anula NO-GO.

## 4. Cuatro gates de GitHub Actions

Workflow: `.github/workflows/voice-release-part8-gemini-cost-consent-offline.yml`.

| Job | Evidencia esperada |
| --- | --- |
| `gemini-financial-consent-stop-contract` | Python adversarial, contrato de workflow, Parte 7 STOP y V100–V103 inmutables |
| `gemini-twilio-audio-redaction-unit` | Java 21, modelo, readiness, redacción Twilio, codec, interrupción, control de llamadas y clases comerciales |
| `pg18-carrier-tenant-rls-regression` | Testcontainers PostgreSQL 18, administrador de enrolamiento, propiedad/ruta Twilio, RLS entre empresas |
| `react-spring-pg18-local-integration` | React + Spring Boot + PostgreSQL 18 efímero y Playwright local; sin público ni efectos externos |

Permisos sólo `contents:read`. El workflow sólo admite la rama Parte 8 y
PR apilado Parte 7. Todas las banderas costosas/permisivas OFF y
sin acceso a secretos productivos. No modificar producción ni fusionar.
Cerrar solamente tras SUCCESS de todos los jobs push y PR sobre el SHA final.

## 5. Evidencia REAL aún necesaria antes de siquiera solicitar un piloto

**No se ejecuta durante la Parte 8:**

- Confirmar por un mecanismo autenticado y de **solo lectura** identidad
  del proyecto Google asociado a la clave presente en Railway, sin exponer
  la clave y sin activar prueba de Live API. Comprobar cuota/ajustes de
  facturación y existencia de presupuesto límite real, si el proveedor
  ofrece enforcement. En caso contrario, declarar que NO existe hard cap
  técnico garantizado.
- Verificar el modelo seleccionado **en runtime productivo** con sólo
  metadatos; no confundir default Java, configuración en GitHub, nombre
  del modelo ni presencia de API key con llamadas realizadas.
- Mantener Twilio y Gemini apagados hasta comprobar propiedad real del PN SID
  y Account SID, aislamiento del tenant, webhooks, costes actuales y
  respaldos actualizados.
- Definir participantes informados, consentimiento, fechas, coste máximo
  que se acepta asumir incluso ante desbordes, plan de cancelación de
  sesión y dos aprobadores distintos antes de autorizar una prueba real.
- Nunca realizar un WebSocket probe, llamada de voz ni cambiar flags de
  Railway automáticamente a partir de CI.

## 6. Estado y siguiente fase

La Parte 8 termina cuando se certifican pruebas **OFFLINE** exactas y
se anota evidencia en el PR. GO sigue denegado para producción y
telefonía. No empezar Parte 9 sin la instrucción explícita del operador.

`PROD_DEPLOY=NO-GO`
`LIVE_CALL_CERTIFIED=false`
`COMMERCIAL_GO=NO-GO`

# Mercado Pago SaaS Sandbox Provider Gate

**Plan maestro:** [C5 · Issue #823](https://github.com/Nicricht/helvoca/issues/823). **Entrega:** [etapa 1 de 3, C5 TEST](https://github.com/Nicricht/helvoca/issues/823). Este documento describe únicamente la suscripción SaaS que paga el negocio a RecepVoz, no los pagos del cliente final del negocio.

## Tres etapas: ejecución y criterio de cierre

| Etapa | Alcance | Evidencia necesaria |
| --- | --- | --- |
| **1 · Seguridad offline** | Auditar workflow existente, bloquear credenciales productivas por identidad, validar correo y vendedor TEST, pruebas deterministas sin secretos/proveedor, documentación | CI exacto verde, ningún POST al proveedor; NO implica pago/test real |
| **2 · Sandbox autenticado** | Validar identidad real de vendedor TEST en consulta GET, crear **una** suscripción TEST con permiso explícito, comprador TEST auténtico, webhook firmado de preapproval y pago aprobado, replay/idempotencia | Enlaces privados a evidencia auténtica y estados del proveedor, sin registrar tokens/links en GitHub |
| **3 · Certificación comercial** | Renovación aprobada/rechazada, suspensión/reactivación y onboarding integral; después aprobación expresa para un pago LIVE acotado y seguimiento | Pago comprobado, entitlement correcto, incidentes/rollback, autorización y NO-GO/GO auditado |

**STOP tras cada etapa:** no comenzar la siguiente por aprobación del CI anterior. C1/C2 mantienen sus puertas independientes antes de merge/despliegue de producción; el V1 comercial sigue NO-GO.

## Riesgo resuelto en etapa 1: APP_USR no certifica TEST

La versión anterior del workflow permitía invocar al proveedor cuando el Access Token tenía prefijo `APP_USR`. **Ese prefijo existe también en producción**. Un usuario podía equivocarse de credencial y provocar una creación remota real, aun cuando el nombre del secreto dijera `TEST`. Por eso nunca aceptar el prefijo como prueba de entorno.

El flujo ahora **falla cerrado antes de cualquier escritura al proveedor**:

1. Se inicia manualmente con confirmación `confirm_sandbox=true`, además de la frase exacta `I_VERIFIED_TEST_CREDENTIALS` que el titular introduce tras verificar el origen de las credenciales en la consola de Mercado Pago.
2. Se proporciona el **User ID y el usuario TEST generado del vendedor** leídos de la misma fila del panel, y el correo **del comprador de prueba real** en formato `testuser123...@testuser.com` (no el marcador genérico `test@testuser.com`). Los datos de identidad se ingresan en el workflow; **el token jamás va en los inputs**.
3. El script `scripts/ops/c5_mercadopago_sandbox_preflight.py` verifica formatos, rechaza `RAILWAY_ENVIRONMENT=production` y ejecuta **solo** `GET https://api.mercadopago.com/users/me` autenticado y con tiempo limitado.
4. Se comprueba que `id` y `nickname` coinciden exactamente con el **User ID y usuario TEST generado que el titular verificó en la misma fila del panel Cuentas de prueba**, y `site_id=MLC`. Un `test_user=false` explícito o una cuenta/email incompatible bloquean. La ausencia del campo no documentado `test_user` no invalida por sí sola la evidencia de dos identificadores del panel.
5. Solo después de ese resultado positivo el workflow habilita localmente `HELVOCA_C5_PROVENANCE_GATE_PASSED=true` para `MercadoPagoSaasSandboxProviderIT`. Este test también rechaza ejecutarse sin el gate.
6. El test de proveedor puede crear un checkout y consultarlo de vuelta, pero **no muestra la URL de pago ni identificadores remotos en logs públicos de CI**. Debe establecerse un canal privado seguro para recuperar el enlace en la etapa 2, no imprimirlo en GitHub.

Un bloqueo `C5_MP_TEST_SELLER_NICKNAME_MISMATCH` o `C5_MP_TEST_ACCOUNT_CONTRADICTED` es un **resultado seguro**, no una autorización para deshabilitar validaciones. Esta verificación no utiliza producción ni hace ningún cargo; la llamada GET solo será posible cuando la etapa 2 se ejecute deliberadamente con las credenciales privadas.

## Alcance de la prueba automatizada de la etapa 1

El workflow `.github/workflows/c5-sandbox-provenance-offline.yml` comprueba `scripts/ops/test_c5_mercadopago_sandbox_preflight.py` con respuestas falsas, testea rechazo de confirmación ausente, correos reales, vendedor equivocado, vendedor no TEST, país no chileno, alias TEST distinto, bandera `test_user=false` explícita, errores del API y respuestas demasiado grandes. En paralelo comprueba los tests de contrato Java existentes y que el workflow manual **exija la verificación de identidad antes del test que crea una suscripción**. Se ejecuta sin secretos, sin red contra Mercado Pago y sin gastos.

El workflow manual original `.github/workflows/saas-billing-sandbox.yml` **no se ejecuta automáticamente** por push ni PR. Solo se puede invocar explícitamente y después de incorporarlo al repositorio bajo sus reglas de release.

## Reglas para etapa 2, no ejecutada aquí

El vendedor TEST y comprador TEST pertenecen a la misma prueba autorizada. El script requiere un token en GitHub Actions Secret `MERCADOPAGO_TEST_ACCESS_TOKEN`, y **no puede distinguir token TEST de LIVE por prefijo**; por eso compara **ID y nombre generado del vendedor** con el panel oficial, comprueba datos de país/email y exige confirmación de origen TEST.

NO guardar o enviar por chat, GitHub, PR o logs: Access Token, claves, credenciales del comprador, `checkoutUrl` accionable, datos de facturación reales, datos del cliente final o resultados privados identificables. No crear ni simular test user con cuentas productivas. No activar automáticamente `MERCADOPAGO_ENABLED` en Railway. El flujo de devolución de checkout del test tendrá que implementarse de forma privada y probada en etapa 2.

**Solo si el titular autoriza una operación real de TEST con cuenta validada** podremos verificar creación/readback/remesas firmadas. La creación de una suscripción TEST no demuestra por sí sola el pago autorizado, webhook entregado, segundo pago o capacidad de cobrar clientes reales.

### Registro de finalización y reanudación

La evidencia de **etapa 1** se registra en el PR de trabajo y se refleja en [Issue #823](https://github.com/Nicricht/helvoca/issues/823), incluyendo HEAD y CI **del commit final**. Aún sin esa evidencia, estado `IN_PROGRESS`. Prohibido repetir los tests CI ya válidos para el mismo SHA solo por cambio de chat.


## Etapa 2/3 · Contrato HMAC preparado, prueba con proveedor todavía pendiente (2026-10-14)

**Control del alcance:** esta entrega no es una nueva fase general. Continúa C5 del [Issue maestro #823](https://github.com/Nicricht/helvoca/issues/823). Implementación de las verificaciones offline en [PR #832](https://github.com/Nicricht/helvoca/pull/832), apilado sobre #831.

### Contratos reusados y añadidos

El backend ya tenía `MercadoPagoWebhookController` (`/webhooks/v1/mercadopago`), que usa la validación de firma oficial del SDK, consulta los estados del proveedor por ID mediante `BillingSubscriptionService` y no habilita el plan simplemente porque el JSON de la notificación diga `approved`.

**Pruebas existentes reutilizadas, no reimplementadas:** `BillingSubscriptionServiceTest` valida que una suscripción `authorized` no active los entitlements sin factura efectivamente aprobada, que una misma factura aprobada no duplique la operación, que un rechazo no extienda el período de gracia por repetición y que los ID/referencias de otros tenants sean bloqueados. `BillingSubscriptionServiceEdgeCasesTest` cubre pagos `pending`, invoice sin ID de suscripción y respuestas inesperadas.

**Brecha nueva abordada:** `MercadoPagoWebhookSignatureRoundTripTest.java` crea una firma HMAC-SHA256 **de verdad** con datos ficticios, conforme al manifiesto publicado por Mercado Pago `id:<data.id>;request-id:<x-request-id>;ts:<timestamp>;`, y llama al controlador real sin mockear el validador. Comprueba evento de suscripción, aviso de factura, firma alterada, `request-id` alterado, firma inválida, tópico ajeno y entrega duplicada. Que un evento firmado sea aceptado prueba **solo el contrato local**, no que el proveedor lo haya enviado ni que exista una factura pagada. <https://www.mercadopago.cl/developers/es/docs/links-and-debts/additional-content/your-integrations/notifications/webhooks?scope=prod>

CI propuesto: `.github/workflows/c5-sandbox-webhook-contract-offline.yml` con Java 21 y pruebas de firma y facturación focalizadas. Evidencia final, HEAD y resultados se registran en el mismo PR #832 y el Issue #823 cuando terminen.

### Bloqueos de la prueba sandbox auténtica

**No hay certificación real de sandbox por disponer de un CI verde.** Se requiere, antes de una única creación TEST explícita:

- Cuenta de desarrollador con aplicación Mercado Pago Chile y **credenciales de prueba** almacenadas de forma privada en GitHub Actions (jamás publicarlas); vendedor y comprador TEST distintos ya creados, bajo control del titular.
- Confirmación verificable de procedencia TEST del token. **La API no garantiza `test_user` en GET `/users/me`**. En la política V2 el titular debe verificar ambos campos del vendedor TEST en el panel; el script exige que User ID y nickname generado coincidan con la identidad autenticada. Si hay marca explícita falsa, bloquea. El prefijo `APP_USR` por sí mismo jamás demuestra TEST. Si las cuentas o su origen siguen sin verificarse, permanece `NO-GO` y no se ejecuta POST.
- Endpoint de notificaciones en un **entorno de pruebas separado y no desplegado en producción**, con secret de firma propio de TEST. Asegurar TLS, validación real de firma, no compartición del tenant productivo, logs minimizados y recuperación ante duplicados.
- Canal **privado** de recuperación de URL de checkout y evidencia de la factura. El test de la etapa 1 dejó de imprimir estos valores en logs CI, por lo que todavía no existe un método seguro certificado para completar el pago TEST desde la prueba automatizada.
- Prueba de principio a fin: ID checkout remoto autenticado, comprador TEST completa autorización en web oficial, webhook `subscription_preapproval`, webhook `subscription_authorized_payment` de factura con estado aprobado leído en proveedor, status/plan local correcto, idempotencia ante doble notificación, ausencia de lecturas cruzadas, resultado NO-GO ante error o falta de prueba.

**STOP:** No crear webhooks fingidos para aparentar entrega real del proveedor. No usar cuentas reales, `MERCADOPAGO_ENABLED` de Railway, LIVE ni pago de verdad; no modificar `main` sin C1 recuperable. Ante ausencia de secreto/entorno autónomo o identidad no concluyente, registrar `BLOCKED_EXTERNAL_PROVIDER` y dejar el software offline certificado sin afirmar cierre de la etapa 2.

**Fuentes oficiales de límites:** [Credenciales TEST y producción](https://www.mercadopago.cl/developers/es/docs/mp-point/resources/credentials) y [topics de webhooks de suscripciones y facturas](https://www.mercadopago.cl/developers/es/docs/links-and-debts/additional-content/your-integrations/notifications/webhooks?scope=prod).

### Checkpoint de continuidad de la etapa 2

- **Rama:** `test/c5-webhook-signed-sandbox-contract-20261014`; PR #832; base PR #831.
- **Estado del contrato offline:** `IN_PROGRESS` hasta CI del último commit, después registrar número de tests reales.
- **Estado de sandbox proveedor:** `BLOCKED_EXTERNAL_PROVIDER` hasta obtener autorización, vendedor/comprador y secretos TEST verificados sin revelar datos. Este estado **no** se convierte en `DONE` por pasar las pruebas con mocks.
- **Acción siguiente para quien retome:** leer el PR sucesor con la política V2 y su CI exacto, verificar la fila real de vendedor TEST (ID + usuario generado) y origen privado de credencial, después preparar webhook sandbox aislado. No ejecutar POST sin todas las condiciones ni iniciar etapa 3.



### Hallazgo y corrección del SDK Java 3.7.0: unidades de tiempo en firmas

**Bug reproducido en el primer CI de etapa 2:** [run #38529327212](https://github.com/Nicricht/helvoca/actions/runs/38529327212) **4 fallos en 7 pruebas HMAC** (los demás 26 tests Java pasaron). Firmas HMAC válidas con `ts` de 13 dígitos fueron rechazadas por el controlador. **No se usó una firma inventada para declarar GO**. Se leyó el código fuente del SDK Java tag `3.7.0`: su overload `WebhookSignatureValidator.validate(..., Duration)` calcula `Long.parseLong(ts) * 1000` y por tanto asume segundos; la documentación oficial muestra además `ts` de 13 dígitos en milisegundos. [Fuente SDK](https://github.com/mercadopago/sdk-java/blob/3.7.0/src/main/java/com/mercadopago/webhook/WebhookSignatureValidator.java), [firma y timestamp oficiales](https://www.mercadopago.cl/developers/es/docs/mp-point-v2/notifications?scope=prod).

**Corrección aislada y cerrada a fallos:** `MercadoPagoWebhookController` sigue llamando a la **verificación criptográfica HMAC del SDK**, usando su overload sin tolerancia temporal. Solo después de una HMAC correcta, `MercadoPagoWebhookTimestampGuard` exige **un único timestamp de 10 dígitos (segundos) o 13 dígitos (milisegundos)** dentro de una ventana máxima de 5 minutos (o configuración positiva); rechaza los falsos, vencidos, futuros, repetidos y malformados. La firma corresponde al mismo header original. Para IDs alfanuméricos, usa minúsculas según manifiesto de Mercado Pago; el ID remoto original no se reemplaza en la reconciliación.

**Verificación ampliada:** `MercadoPagoWebhookSignatureRoundTripTest`, `MercadoPagoWebhookTimestampGuardTest`, controlador existente con firma HMAC auténtica en lugar de SDK mockeado, y tests ya certificados de estados/duplicados de `BillingSubscriptionService`. Las pruebas HMAC reales y la ventana estricta no necesitan credenciales del proveedor, ni realizan escrituras a PostgreSQL productivo. Una prueba firmada correctamente puede probar el **contrato local** pero no acredita recepción de webhook de Mercado Pago TEST.

**Estado:** hasta CI del último commit, `IN_PROGRESS`. C5 etapa 2 completa seguirá `BLOCKED_EXTERNAL_PROVIDER` sin entrega real, invoice aprobada y evidencia autenticada. Conservar la barrera de etapa 1 para credenciales TEST y no activar cobros reales. Próximo paso: confirmar CI exacto y documentar evidencia en PR #832 y #823 sin re-ejecutar pruebas ya válidas.


### 2026-10-14 · Mejora de certificación de identidad del vendedor TEST (V2)

El preflight inicial del PR #831 exigía `GET /users/me → test_user=true`, pero Mercado Pago no documenta que esta propiedad esté siempre presente. **No se infiere su valor ni se fuerza artificialmente.** En la V2, el operador confirma en el panel oficial de `Cuentas de prueba` dos datos relacionados con el vendedor: `User ID` numérico y **usuario generado que comienza `TEST`**. El workflow solicita ambos y consulta `GET /users/me` con el token almacenado como Actions Secret. Deben coincidir ambos datos, el sitio `MLC`, la dirección `@testuser.com` si el servicio la suministra y ninguna marca `test_user=false`. Se mantiene `CONFIRM_TEST_CREDENTIAL_SOURCE=I_VERIFIED_TEST_CREDENTIALS`. Este control compara evidencia de origen independiente con la identidad real de la credencial, **sin depender del prefijo `APP_USR` ni de propiedades no documentadas**. Nunca registrar secreto, contraseña, respuesta completa o token.

Esta decisión se fundamenta en [Cuentas de prueba de Mercado Pago](https://www.mercadopago.cl/developers/es/docs/automatic-payments-orders/resources/test-accounts?scope=prod) y en el [endpoint de creación de test user](https://www.mercadopago.cl/developers/es/reference/test_user/_users_test/post), que muestran el identificador y nickname generados. Mercado Pago indica asimismo que la prueba de **suscripciones** emplea una aplicación del vendedor TEST, cuya credencial puede tener aspecto de producción; ver [guía oficial de suscripciones](https://www.mercadopago.cl/developers/es/news/2023/11/16/Questions-on-how-to-test-your-integration--). Por ello se requiere **verificación del vendedor real de prueba** y no basta el nombre del secreto o APP_USR.

**Estado:** aún no es certificación auténtica de sandbox. Faltan las cuentas del titular, credencial TEST privada y receptor de webhook aislado. Hasta entonces `BLOCKED_EXTERNAL_PROVIDER`; etapa 3 no iniciada. Evidencia CI exacta y siguiente paso en PR V2 e Issue #823.

## Etapa 2: PostgreSQL temporal, firmas y transacciones sin proveedor real

**Razón:** las pruebas anteriores verificaban HMAC y estados con repositorios simulados; ahora también se exige evidencia de persistencia real en PostgreSQL con Flyway, separación de tenants e idempotencia en transacciones.

**Implementación:** PR #835 (https://github.com/Nicricht/helvoca/pull/835), test Java MercadoPagoSignedInvoicePostgresIntegrationTest y workflow c5-signed-invoice-real-postgres-offline.yml. PostgreSQL 16 Testcontainers y Flyway se ejecutan en runner temporal. El único componente simulado es la API externa SubscriptionPaymentGateway; el HMAC del SDK, el controlador de webhook, el servicio, JPA y PostgreSQL son reales.

**Criterios:** notificación de autorización sin factura NO activa plan; factura aprobada con HMAC válido actualiza negocio A y no B; repetir factura no modifica el período; firma falsa se rechaza sin consulta remota; factura de suscripción no vinculada no modifica otro tenant; factura rechazada no activa plan ni amplía gracia con replay. Hasta CI final verde no declarar estos criterios certificados.

**Diagnóstico inicial:** CI https://github.com/Nicricht/helvoca/actions/runs/38539820855 falló con cinco respuestas 404 porque el workflow imponía la variable de entorno MERCADOPAGO_ENABLED=false, que tiene prioridad sobre la propiedad del test. Eliminamos esa variable del job; el gateway sigue siendo un mock y la credencial del controlador una cadena ficticia sin proveedor real.

**NO-GO externa:** Mercado Pago TEST real no se ha llamado con POST en esta entrega. Railway solo tiene production, por lo que no se montó receptor webhook allí. Una suscripción pending creada en /preapproval NO equivale a pago aprobado; para cerrar C5 etapa 2 faltan checkout con comprador TEST, webhook entregado por proveedor en receptor separado, factura aprobada consultada desde Mercado Pago y repetición real. No confundir tests con mocks o PostgreSQL temporal con certificación productiva.

**Siguiente paso:** verificar CI en el SHA final del PR #835, registrar el resultado exacto, comprobar si ya existe un entorno receptor aislado reutilizable y solo solicitar acción externa al titular si se demuestra indispensable. La etapa 3 queda intacta.

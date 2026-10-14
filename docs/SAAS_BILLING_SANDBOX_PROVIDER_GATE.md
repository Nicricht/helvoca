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
2. Se proporciona el User ID **del vendedor de prueba** y el correo **del comprador de prueba real** en formato `testuser123...@testuser.com` (no el marcador genérico `test@testuser.com`). Todo queda en entradas de ejecución, **nunca el token**.
3. El script `scripts/ops/c5_mercadopago_sandbox_preflight.py` verifica formatos, rechaza `RAILWAY_ENVIRONMENT=production` y ejecuta **solo** `GET https://api.mercadopago.com/users/me` autenticado y con tiempo limitado.
4. Se comprueba que `id` coincide exactamente con el vendedor esperado, `site_id=MLC` y **`test_user` es literalmente `true`**. Si Mercado Pago omite `test_user`, devuelve otro esquema o no confirma la identidad, el flujo **bloquea** la creación, aunque pudiera existir una credencial válida; en la etapa 2 se debe demostrar y documentar un mecanismo alternativo equivalente antes de levantar este bloqueo.
5. Solo después de ese resultado positivo el workflow habilita localmente `HELVOCA_C5_PROVENANCE_GATE_PASSED=true` para `MercadoPagoSaasSandboxProviderIT`. Este test también rechaza ejecutarse sin el gate.
6. El test de proveedor puede crear un checkout y consultarlo de vuelta, pero **no muestra la URL de pago ni identificadores remotos en logs públicos de CI**. Debe establecerse un canal privado seguro para recuperar el enlace en la etapa 2, no imprimirlo en GitHub.

Un bloqueo `C5_MP_TEST_ACCOUNT_UNVERIFIED` es un **resultado seguro**, no una autorización para deshabilitar validaciones. Esta verificación no utiliza producción ni hace ningún cargo; la llamada GET solo será posible cuando la etapa 2 se ejecute deliberadamente con las credenciales privadas.

## Alcance de la prueba automatizada de la etapa 1

El workflow `.github/workflows/c5-sandbox-provenance-offline.yml` comprueba `scripts/ops/test_c5_mercadopago_sandbox_preflight.py` con respuestas falsas, testea rechazo de confirmación ausente, correos reales, vendedor equivocado, vendedor no TEST, país no chileno, ausencia de la marca `test_user`, errores del API y respuestas demasiado grandes. En paralelo comprueba los tests de contrato Java existentes y que el workflow manual **exija la verificación de identidad antes del test que crea una suscripción**. Se ejecuta sin secretos, sin red contra Mercado Pago y sin gastos.

El workflow manual original `.github/workflows/saas-billing-sandbox.yml` **no se ejecuta automáticamente** por push ni PR. Solo se puede invocar explícitamente y después de incorporarlo al repositorio bajo sus reglas de release.

## Reglas para etapa 2, no ejecutada aquí

El vendedor TEST y comprador TEST pertenecen a la misma prueba autorizada. El script requiere un token en GitHub Actions Secret `MERCADOPAGO_TEST_ACCESS_TOKEN`, y **no puede distinguir token TEST de LIVE por prefijo**; por eso verifica la identidad.

NO guardar o enviar por chat, GitHub, PR o logs: Access Token, claves, credenciales del comprador, `checkoutUrl` accionable, datos de facturación reales, datos del cliente final o resultados privados identificables. No crear ni simular test user con cuentas productivas. No activar automáticamente `MERCADOPAGO_ENABLED` en Railway. El flujo de devolución de checkout del test tendrá que implementarse de forma privada y probada en etapa 2.

**Solo si el titular autoriza una operación real de TEST con cuenta validada** podremos verificar creación/readback/remesas firmadas. La creación de una suscripción TEST no demuestra por sí sola el pago autorizado, webhook entregado, segundo pago o capacidad de cobrar clientes reales.

### Registro de finalización y reanudación

La evidencia de **etapa 1** se registra en el PR de trabajo y se refleja en [Issue #823](https://github.com/Nicricht/helvoca/issues/823), incluyendo HEAD y CI **del commit final**. Aún sin esa evidencia, estado `IN_PROGRESS`. Prohibido repetir los tests CI ya válidos para el mismo SHA solo por cambio de chat.

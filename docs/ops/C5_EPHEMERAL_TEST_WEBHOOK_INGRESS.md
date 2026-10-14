# C5 etapa 2 · receptor HTTPS temporal aislado y sin cobros

**Plan rector:** [C0–C7, Issue #823](https://github.com/Nicricht/helvoca/issues/823). **Rama de trabajo:** `feat/c5-temporary-webhook-ingress-20261014`, basada en el [PR #835](https://github.com/Nicricht/helvoca/pull/835). Esta prueba se realiza **sin MERCADOPAGO_TEST_ACCESS_TOKEN**, sin backend productivo y sin dinero.

## Por qué hace falta

Los tests anteriores de C5 ya certifican 43 casos HMAC/estado y 5 casos reales de PostgreSQL Testcontainers, pero **no** que un `POST` firmado enviado desde Internet alcance un receptor HTTPS independiente. Se necesitaba comprobar esa frontera sin intentar desplegar Helvoca en el entorno `production` de Railway.

Inventario 2026-10-14: Railway Helvoca dispone únicamente del entorno `production`, donde están la API, PostgreSQL y un cron de respaldo vacío. Render muestra únicamente servicios de **otros proyectos** (no se reutilizan ni modifican). Los proyectos Supabase conectados no corresponden a RecepVoz y están inactivos (tampoco se alteran). No crear un servicio pagado para una comprobación sintética.

## Implementación efímera

- `scripts/ops/c5_ephemeral_webhook_ingress.py`: servidor Python de prueba, escucha exclusivamente `127.0.0.1` en el runner; procesa solo `POST /webhooks/v1/mercadopago` y `GET /health`. Para cada solicitud válida exige `x-signature` HMAC-SHA256, `x-request-id`, marca temporal dentro de ±5 min, asunto permitido, ID con forma válida, cuerpos menores de 4 KiB y rechazo de campos duplicados/conflictivos.
- Solo mantiene contadores de recepción **volátiles**, no almacena bodies, identidad, URLs de pago, transcripciones ni datos de clientes. No imprime solicitudes ni realiza operaciones sobre facturas/tenants. **No** es un receptor para cobros en producción, ni reemplaza al controlador Java, al servicio de facturación o a PostgreSQL.
- `scripts/ops/test_c5_ephemeral_webhook_ingress.py`: pruebas deterministas de firma, manipulación del ID/request-id, expiración y fecha futura, tópicos no admitidos, cabeceras repetidas y recepción HTTP local.
- `scripts/ops/c5_ephemeral_ingress_smoke.py`: envía por HTTPS un **evento ficticio firmado** y exige HTTP 204; el mismo evento **sin firma** debe ser rechazado con HTTP 401. Nunca imprime la URL efímera.
- `.github/workflows/c5-ephemeral-public-webhook-ingress.yml`: prepara un túnel público efímero de Cloudflare Quick Tunnel con cliente **fijado a la versión 2026.10.0** y SHA-256 oficial verificado, secreto HMAC aleatorio exclusivo del runner, timeout estricto y limpieza de procesos/archivos al terminar. No carga secretos del proveedor, crea cuentas, actualiza Mercado Pago ni compra infraestructura. Esta dirección se destruye al acabar el CI y **no** debe registrarse como webhook persistente.

## Límites y criterios

1. CI de unit/integration local con todos los casos verdes, no solo importación del código.
2. Dirección HTTPS de Internet, certificado TLS del túnel y prueba efectiva de HTTP 204 para firma válida y rechazo 401 para firma ausente.
3. Permisos GitHub `contents: read`; secreto HMAC efímero y URL aleatoria ocultos de la salida pública; cero efectos externos sobre proveedor.
4. Repetir solo el scope afectado si se demuestra fallo. **No declarar GO** hasta tener el CI del HEAD exacto.
5. Conservar el límite **C5 etapa 2 pendiente**: el túnel de GitHub es efímero y no puede quedarse escuchando indefinidamente por una factura de suscripción.

## Lo que esta prueba NO puede acreditar

- **No** fue una notificación real de Mercado Pago. Para ello se requiere un receptor TEST **persistente durante el checkout**, asociado al flujo oficial de Suscripciones, una clave de firma TEST real, un comprador TEST que termine la autorización y una factura `approved` consultada desde Mercado Pago. El comprador y el vendedor TEST y la credencial TEST **ya existen** y se verificaron con GET en PR #834; no pedir crearlos nuevamente.
- La documentación oficial de Webhooks indica que la configuración general desde `Tus integraciones` **no está disponible para Suscripciones** y recomienda configurar notificaciones en el flujo de creación del pago. Sin embargo, `PreapprovalCreateRequest` del SDK Java 3.7.0 actualmente utilizado **no expone `notificationUrl`**. Este es un punto de integración real aún por investigar: no asumir que la URL configurada en el panel puede recibir automáticamente eventos de preapproval.
- No invocar `POST /preapproval` antes de resolver el destino de notificaciones, controlar la posible creación remota y tener una forma segura y privada de continuar el checkout. Un recurso remoto creado como `pending` **no es un pago aprobado**.
- **No se habilitaron servicios nuevos de Railway/Render, ni se modificaron las variables de producción.** C1 respaldos y C2 despliegue siguen siendo puertas independientes.

Referencias: [Mercado Pago Webhooks](https://www.mercadopago.cl/developers/es/docs/links-and-debts/additional-content/your-integrations/notifications/webhooks?scope=prod), [API suscripciones preapproval](https://www.mercadopago.cl/developers/es/reference/online-payments/subscriptions/create-preapproval/post), [SDK Java 3.7.0 PreapprovalCreateRequest](https://github.com/mercadopago/sdk-java/blob/3.7.0/src/main/java/com/mercadopago/client/preapproval/PreapprovalCreateRequest.java).

## Checkpoint

**Estado `DONE_VERIFIED_EPHEMERAL_HTTPS_ONLY / C5-2_PROVIDER_NO-GO`.** [CI #38542133558](https://github.com/Nicricht/helvoca/actions/runs/38542133558) **completed/success** en código del HEAD `18281542c07960e70cd38ea58d7a52d21cf265ad`: 17/17 pruebas Python, cliente del túnel con SHA256 certificado, evento ficticio con firma a través de HTTPS público 204, solicitud sin firma 401, sin secretos del proveedor. El túnel murió con el runner. La documentación del último commit no modifica este comportamiento; consultar el CI del HEAD final del PR antes de informar un GO. El siguiente paso técnico tras la prueba HTTPS es resolver **un receptor de larga duración y el mecanismo oficial de notificaciones de Suscripciones** sin reutilizar infraestructura ajena ni tocar Railway production. Solo pedir al titular una intervención si es realmente indispensable para autenticar al comprador TEST, autorizar un recurso con coste o una acción que no se pueda completar por el conector. Mantener etapa 3 NO INICIADA.

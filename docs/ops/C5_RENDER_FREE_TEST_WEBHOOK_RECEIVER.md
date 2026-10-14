# C5 etapa 2 · receptor de webhooks TEST en Render Free (2026-10-14)

**Fuente de verdad:** [plan maestro C0–C7, Issue #823](https://github.com/Nicricht/helvoca/issues/823). **Estado:** `TEST_INGRESS_DEPLOYED_AND_HTTPS_VERIFIED / REAL_MP_WEBHOOK_NOT_YET_CERTIFIED`.

## Alcance autorizado y resultado

El titular autorizó expresamente **crear un servicio exclusivo de RecepVoz en Render solo con plan gratuito**. Se comprobó primero el inventario de la cuenta Render: estaban servicios `tiempojusto` y `SIGAO`, no servicios Helvoca. No se tocaron ni reutilizaron; Railway continúa únicamente con environment `production`.

Servicio nuevo exclusivo:

- **Nombre:** `helvoca-c5-test-webhook-ingress`
- **ID Render:** `srv-db7ku5ivcj2c73e1cv40`
- **Plan:** `free` (no `starter`, ni suscripción paga para el servicio)
- **Dashboard:** https://dashboard.render.com/web/srv-db7ku5ivcj2c73e1cv40
- **Origen HTTPS:** https://helvoca-c5-test-webhook-ingress.onrender.com
- **Salud:** `GET /health` → 204
- **Receptor TEST:** `POST /webhooks/v1/mercadopago` → 401 sin firma HMAC válida
- **Workspace y región:** workspace existente del titular, Virginia
- **Repositorio:** `Nicricht/helvoca`, rama `feat/c5-render-free-test-ingress-20261014`
- **AutoDeploy:** `no` intencional. Los commits en rama no despliegan código ni cambian producción sin revisión.
- **Despliegue creado:** `dep-db7ku62vcj2c73e1d110`, `live`, SHA real **`2f04b0a0fd566c90f03559aa2bbb137db91c6792`**. Últimas adiciones posteriores a este SHA son workflows y documentación: **no se debe afirmar** que estén desplegadas.
- **Comandos de build/start:** `python3 -m py_compile scripts/ops/c5_render_test_webhook_entrypoint.py scripts/ops/c5_ephemeral_webhook_ingress.py`, `python3 scripts/ops/c5_render_test_webhook_entrypoint.py`.

## Barreras de seguridad

- `C5_RENDER_TEST_ONLY=isolated-no-payment` y `C5_EPHEMERAL_MODE=test-only` son obligatorios. Sin ambos, la app **no escucha**.
- `C5_EPHEMERAL_HMAC_SECRET` es una clave **aleatoria interna del receptor**, generada para esta prueba, almacenada solo en **Render Environment**; su valor **no está** en GitHub, registros, chat ni este documento. **NO es la firma real entregada por Mercado Pago**, no reutilizar el Access Token de prueba de la cuenta.
- La app Python procesa firmas HMAC, tiempo de ±5 minutos, tópicos permitidos y tamaño del body. Solo mantiene contadores volátiles; no guarda facturas, identidades, credenciales ni datos personales. No llama a Mercado Pago, no modifica suscripciones, no tiene conexión a Railway ni PostgreSQL ni endpoint de pagos.
- Permite `0.0.0.0:$PORT` **únicamente** a través de `c5_render_test_webhook_entrypoint.py` condicionado al modo TEST. Las pruebas locales previas siguen usando `127.0.0.1`.
- Nadie debe registrar este receptor como webhook de pagos comerciales. No hay checkout autorizado ni mecanismo de persistencia/reconciliación conectado a la facturación de Helvoca.
- Repositorio de otros proyectos y servicios externos permanecen intactos.

## Verificación exacta

1. [CI código y regresión #38544511970](https://github.com/Nicricht/helvoca/actions/runs/38544511970) **completed/success** en SHA `2f04b0a0fd566c90f03559aa2bbb137db91c6792`: **11/11 tests nuevo entrypoint Render y 17/17 tests receptor**, gate `C5_RENDER_FREE_NO_PROVIDER_NO_PAYMENT_CONTRACT=PASS`.
2. [Deploy Render `dep-db7ku62vcj2c73e1d110`](https://dashboard.render.com/web/srv-db7ku5ivcj2c73e1cv40) **`live`**, confirmado mediante integración Render. Build y startup definidos explícitamente.
3. [CI público #38544761946](https://github.com/Nicricht/helvoca/actions/runs/38544761946) **completed/success**, `C5_RENDER_STABLE_TLS_HEALTH=PASS`, `C5_RENDER_STABLE_HTTPS_UNSIGNED_REJECTED_401=PASS`, `C5_RENDER_NO_PROVIDER_OR_PAYMENT=PASS`. Probó HTTPS **en el dominio estable** ya servido por Render, desde el runner.
4. [CI de código final antes de esta documentación #38544761855](https://github.com/Nicricht/helvoca/actions/runs/38544761855) **completed/success**, mismas 28 pruebas y contrato no-payment. Una adición exclusivamente documental obliga a comprobar estado de CI exacto posterior, sin presumir que el SHA de documentación corresponde al deploy.

## Advertencias del plan gratuito

Según [Render Free](https://render.com/docs/free), los servicios gratuitos se suspenden tras ~15 minutos sin tráfico y el siguiente request puede tardar aproximadamente un minuto en despertarlos. También consumen **horas gratuitas, minutos de build y ancho de banda compartidos con otros servicios del workspace**. El plan del servicio es `free`, pero el consumo que supere determinadas cuotas incluidas puede generar cargos adicionales si la cuenta tiene método de pago: **no autorizamos gastos ni cambio a plan pagado**. No mantener tráfico artificial para evitar el apagado y no usar este servicio gratuito para webhooks productivos o SLAs.

El filesystem de Render Free es efímero y se pierde al reiniciar; por eso el receptor solo guarda contadores en RAM y **NO** certifica entrega durable ni reintentos a través de reinicios. En una prueba real el despertar de un servicio dormido puede interferir con los plazos de reintento del proveedor: debe ensayarse antes de afirmar recepción fiable.

## Brecha que sigue pendiente en C5, etapa 2

**No se ha realizado un checkout ni un pago auténtico TEST con Mercado Pago.** El receptor responde a HMAC sintético y rechaza eventos sin firma; la clave configurada actualmente no fue entregada/configurada en Mercado Pago.

Para certificar un evento de Suscripciones, aún se necesita:

1. Confirmar procedimiento **oficial** para inscribir el webhook `subscription_preapproval` y `subscription_authorized_payment` de la aplicación de pruebas. La documentación del proveedor señala que la configuración por panel tiene excepciones para Suscripciones, y el SDK Java 3.7.0 usado en Helvoca no expone `notificationUrl` en `PreapprovalCreateRequest`. No inventar integración basada en panel si no hay prueba de que se usará.
2. Obtener o vincular **clave real de firma de notificaciones TEST** mediante el canal autorizado del proveedor. No se encontró disponible en los nombres consultados por GitHub Actions/Render producción, **no afirmar que no exista universalmente**.
3. Completar un checkout TEST con la cuenta **comprador TEST ya existente** y verificar entrega remota auténtica y factura efectivamente aprobada mediante GET de Mercado Pago. Una creación de suscripción `pending` o HMAC sintético **NO** lo prueba.
4. Recibir/gestionar con persistencia de pruebas e idempotencia conforme al backend Java, con evidencia privada sin guardar tokens ni checkout URLs en logs. El receptor Python aislado aquí no implementa esa persistencia.
5. Mantener el merge/deploy production sujeto a C1 backup recuperable y C2 release. **C5 etapa 3 no iniciada.**

**No pedir al titular** crear cuentas TEST, token de vendedor ni configurar otra app: ya existen, se comprobó el token mediante GET autenticado en [PR #834](https://github.com/Nicricht/helvoca/pull/834). Solo escalar una acción específica del panel de Mercado Pago si se comprueba que el agente no puede ejecutarla mediante conectores ya disponibles. No añadir costes sin autorización.

**Reanudación:** primero PR de esta rama, Issue #823, CI exacto y deployment Render. Los siguientes cambios de aplicación deben certificarse y desplegarse **manualmente** en el servicio aislado; no activar AutoDeploy por comodidad.

## Recepción TEST con evidencia mínima y limitación de clave externa

**2026-10-14, corrección posterior al despliegue inicial:** el receptor dedicado de Render exige ahora adicionalmente que cada notificación firmada incluya `live_mode: false` **booleano**. Rechaza `true`, cadenas de texto y ausencia del indicador incluso cuando el HMAC sea válido. No hay posibilidad de aceptar un webhook productivo en este servicio TEST por accidente. Solo tras una firma criptográfica válida y `live_mode=false` se registra un mensaje seguro del tipo `C5_AUTHENTICATED_TEST_RECEIPT_TOPIC=subscription_preapproval` o `...subscription_authorized_payment`, **sin IDs, emails, importes, payloads, timestamps, tokens ni URL**. El registro permite a Render Logs acreditar recepción y tipo del evento durante una prueba, pero NO acredita factura aprobada, persiste únicamente durante la retención operativa de Render y NO mueve dinero ni planes.

Pruebas de regresión [#38547673097](https://github.com/Nicricht/helvoca/actions/runs/38547673097) **completed/success**: la regla de arranque Render y el rechazo de `live_mode` incorrecto pasan junto con la prueba HMAC/HTTP local; [#38547673206](https://github.com/Nicricht/helvoca/actions/runs/38547673206) **completed/success** en el mismo SHA `c4f3483664dd61c478098af233e51953461f3801`: comprobación HTTPS del servicio existente y rechazo de solicitud anónima. **No atribuir esta actualización a la versión desplegada antes de verificar el nuevo deployment real en Render**; AutoDeploy continúa desactivado.

**Investigación de contrato de notificaciones de Suscripciones:**

- Documentación oficial: [Webhooks en Mercado Pago](https://www.mercadopago.cl/developers/es/docs/links-and-debts/additional-content/your-integrations/notifications/webhooks?scope=prod) indica explícitamente que la configuración general por el panel `Tus integraciones` **no está disponible para Suscripciones**, y remite a configuración durante creación del pago.
- Contrato público [Mercado Pago OpenAPI, SubscriptionRequest](https://github.com/mercadopago/openapi/blob/main/by-product/subscriptions/spec3.yaml) y SDK Java 3.7.0 de `preapproval`: **no incluyen `notification_url`** como campo documentado para crear una suscripción. No inventar un atributo o activar una ruta de recepción asumiendo que Mercado Pago la usará.
- El panel de Webhooks permite generar/ver una clave de firma en ciertas integraciones, pero **no tenemos evidencia de que la clave de esas notificaciones se aplique a Suscripciones de la aplicación TEST**. El receptor exige clave privada de firma compartida con el verdadero emisor. La clave aleatoria interna existente en Render sirve solo para pruebas locales y jamás prueba el origen de Mercado Pago.
- Un webhook firmado que llega con `live_mode=false` sigue siendo solo aviso; el backend debe GET de `authorized_payments/{id}` y validar pago aprobado, tenant y replay en un entorno TEST con PostgreSQL aislado. El receptor actual no consulta al proveedor ni altera entitlements.

**Bloqueo realmente externo:** no hay un conector Mercado Pago Developers con acceso autenticado a la configuración de Webhooks de esta app ni la clave de firma. No se encontraron `MERCADOPAGO_TEST_WEBHOOK_SECRET` ni la URL estable TEST en GitHub Actions bajo los nombres comprobados en PR #837. La URL de Render **sí** existe ahora. Sin el mecanismo oficial de callback y su clave auténtica, no realizar un POST `/preapproval` ni afirmar que se certificó cobro TEST. El próximo operador debe comprobar la interfaz real/configuración de la aplicación, y solo después vincular la clave en el entorno Render **sin ponerla en GitHub ni en el chat**. Si la plataforma no permite la configuración de Suscripciones, registrar bloqueo proveedor y buscar soporte oficial, no falsificar el resultado. C5-3 sigue NO INICIADA.


## Evidencia visual del panel Mercado Pago TEST (2026-10-14)

El titular aportó una captura del formulario **Configurar notificaciones Webhooks**, con la pestaña **Modo de prueba** seleccionada. Evidencia visual confirmada:

- Hay una **URL de prueba preexistente**, que apunta al webhook de pagos por negocio en `recepvoz.cl` con una clave de ruta. **No reproducir, registrar ni copiar esta ruta/UUID completa en documentación o logs**. No modificar la pestaña **Modo productivo**, que tiene configuración distinta.
- En esa misma configuración TEST el tópico **Order (Mercado Pago)** está marcado, mientras **Planes y suscripciones** aparece desmarcado. Al seleccionar este último, los eventos que enumera el proveedor son `subscription_preapproval`, `subscription_authorized_payment`, `subscription_preapproval_plan`. **La existencia de la casilla NO prueba que el proveedor la utilice para todos los flujos de `/preapproval`**, dado que la propia documentación de MP contiene una excepción para Suscripciones. Verificar con evento auténtico y no con checkbox solamente.
- La **Clave secreta** existe en la pestaña de pruebas, **enmascarada**. Ni valor ni metadatos privados fueron copiados al repo. Cambia el estado anterior de “no hay evidencia de secret” por “secret MP TEST visible pero **no configurado aún en el receptor Render**”. Nunca usar ni rotar la clave de producción.
- El receptor Render `https://helvoca-c5-test-webhook-ingress.onrender.com/webhooks/v1/mercadopago` tiene actualmente una **clave interna de ensayo independiente**. Un webhook realmente firmado por Mercado Pago con otra clave devolverá HTTP 401 hasta igualarlas. Por tanto, **NO cambiar la URL TEST del proveedor todavía**.

**Intervención indispensable confirmada por accesos:** no existe conector autenticado de Mercado Pago Developers ni acción para leer/gestionar secretos de su pantalla. El propietario debe, **sin enviarlo por chat**, copiar la `Clave secreta` de **Modo de prueba** y pegarla directamente en el valor de `C5_EPHEMERAL_HMAC_SECRET` de **Render Environment** del servicio `srv-db7ku5ivcj2c73e1cv40`, sobrescribiendo únicamente la clave interna de simulación. La actualización puede generar un redeploy del receptor TEST. Tras confirmación, el agente debe verificar salud HTTPS y 401 para solicitudes no firmadas, inspeccionar estado del deploy sin revelar secretos, y **solo entonces** pedir la siguiente acción para el panel MP: cambiar **solamente** URL para prueba al endpoint de Render, activar **Planes y suscripciones**, y gestionar el tópico Order sin afectar pruebas de otros productos (anotar/restaurar URL TEST anterior por canal seguro). Nunca tocar la pestaña **Modo productivo**, pulsar `Restablecer` ni exponer clave en capturas.

**Importante:** la URL TEST previa apunta a otra integración de pagos, de modo que reemplazarla incluso en pruebas **puede interrumpir eventos de prueba de ese otro flujo**. No asumir múltiples URLs por evento; plantear ventana acotada y restauración de configuración, o aplicación TEST separada solo si se aprueba. El objetivo es reducir cambios y preservar lo existente. Verificar recepción auténtica y factura mediante API aislada antes de cualquier `GO`.

**Fuentes:** [MP Webhooks y restricciones de Suscripciones](https://www.mercadopago.cl/developers/es/docs/links-and-debts/additional-content/your-integrations/notifications/webhooks?scope=prod). La captura del usuario **no** está en el repositorio para proteger la URL sensible y la clave.


## Corrección de ACK oficial y despliegue comprobado

**2026-10-14:** la [documentación oficial de notificaciones MP](https://www.mercadopago.cl/developers/es/docs/links-and-debts/additional-content/your-integrations/notifications/webhooks?scope=prod) especifica que para confirmar la entrega un receptor debe devolver **HTTP 200 o 201**. El servidor de test devolvía 204 tras HMAC válida, respuesta que otros sistemas aceptan pero que MP puede interpretar como ausencia de confirmación.

**TDD y resolución:**
- [CI RED #38549537160](https://github.com/Nicricht/helvoca/actions/runs/38549537160): nuevos asserts 200 reprodujeron el bug: 2 fallos, resultado real 204, sin modificar credenciales ni producción.
- Se cambió **solo el ACK del POST firmado y aceptado**, de 204 a **200**. `GET /health` continúa 204, el POST sin HMAC sigue 401, y los eventos con `live_mode` incompatible siguen rechazados. Se actualizó el smoke sintético para que exija 200.
- [CI GREEN #38549622427](https://github.com/Nicricht/helvoca/actions/runs/38549622427) **35/35 pruebas Python PASS** y contrato sin proveedor/efecto de cobro. [CI HTTPS #38549622492](https://github.com/Nicricht/helvoca/actions/runs/38549622492) success.
- **Render Free real:** deployment manual `dep-db7li1k9v7es73cg70c0` terminó en estado **`live`** sobre SHA ejecutable `12a7e99b27c74771ac307e9155475c2f491a8a61`. Servicio sigue en plan `free` y AutoDeploy desactivado. Ningún despliegue a Railway ni token de MP revelado.

**La siguiente acción requiere al titular:** copiar la `Clave secreta` existente de la pestaña `Modo de prueba` de Mercado Pago, sin restablecerla ni pegarla en chat, directamente sobre la variable existente `C5_EPHEMERAL_HMAC_SECRET` del servicio Render Free `helvoca-c5-test-webhook-ingress`, seleccionando `Save and deploy`. **No actualizar `MERCADOPAGO_TEST_ACCESS_TOKEN`**, no editar el entorno Railway, no exponer secret por screenshot y no editar aún la URL del proveedor. El agente comprobará Render live + HTTPS, y luego guiará el cambio temporal/supervisado de URL y tópico `Planes y suscripciones` en Modo de prueba, preservando la configuración anterior del webhook TEST de pagos de negocio. La documentación oficial contiene una salvedad sobre notificaciones de Suscripciones: una casilla visible no garantiza entrega hasta ver un callback MP verdadero.


## C5 · diagnóstico de 401 del simulador de Mercado Pago (2026-10-14)

**Evidencia visual del titular (2026-10-14, sin subir captura ni secretos):** la acción `Simular notificación` desde Mercado Pago Developers para tipo `subscription_preapproval` devolvió **HTTP 401 Unauthorized** en la URL HTTPS del receptor TEST Render Free. El panel mostraba un payload simulado `action=updated`, `entity=preapproval`, `type=subscription_preapproval`, `id=123456`, `data.id=123456`, `version=8` y `date=2021-11-01T02:02:02Z`, sin atributo `live_mode`. No copiar IDs ni credenciales originales de la app. **Esta es evidencia de fallo de la simulación, no de un pago real.**

**Diagnóstico de código:** en ese momento `require_sandbox_event` rechazaba cualquier payload sin `live_mode=false` incluso si el HMAC ya había sido validado. Ese control basta para causar 401 con el formato visible del simulador, pero no podemos afirmar que la firma realmente haya coincidido: la versión anterior no registraba la etapa de rechazo. El `date` antiguo en el BODY del simulador no equivale a la marca de tiempo del encabezado `x-signature`, que sigue exigiendo actualidad ±5 minutos.

**Corrección minimalista y cerrada a producción:**
- `verify` continúa verificando primero **HMAC-SHA256 con secret configurado y cabeceras reales**, request-id, timestamps y tipo/ID permitidos. No se admite ninguna petición sin firma, con HMAC incorrecto ni timestamp caducado.
- Después de HMAC se aceptan eventos reales de pruebas **solo con `live_mode: false` booleano**. La única excepción para un payload sin esa propiedad es el **fixture sintético exacto** del simulador mostrado, con `action=updated`, `entity=preapproval`, `type=subscription_preapproval`, versión 8, fecha fija 2021-11-01, IDs ficticios `123456` y `application_id` decimal. Esto responde `200` pero etiqueta explícitamente `C5_AUTHENTICATED_SIMULATOR_RECEIPT_TOPIC=subscription_preapproval` para que nadie lo confunda con factura pagada, y no realiza escrituras en DB ni MP.
- Si falla antes/durante validación, se registra únicamente `C5_TEST_WEBHOOK_REJECT_STAGE=HMAC_OR_ENVELOPE`; si firma pasa pero no cumple el modo TEST, se registra `C5_TEST_WEBHOOK_REJECT_STAGE=TEST_MODE`. **Jamás imprimir header, valor de secret, payload, IDs, email ni request-id**.
- Una petición con `live_mode=true`, campo faltante que no coincida exactamente con ese fixture, HMAC incorrecto, body alterado o tiempo de firma caducado continúa bloqueada.
- **Regresión GitHub:** [Actions #38553247628](https://github.com/Nicricht/helvoca/actions/runs/38553247628) `completed/success` 11 pruebas de entrypoint Render + 32 pruebas HMAC/HTTP = **43/43**, [Actions HTTPS #38553247538](https://github.com/Nicricht/helvoca/actions/runs/38553247538) PASS. Estos CI fueron ejecutados antes del nuevo deploy, con código SHA `0cd0822661ba44e459f1476fa8866a7ae7525f52`.
- **Render manual:** deploy `dep-db7m0ne7bikc73bc2ajg` `live`, SHA ejecutada `0cd0822661ba44e459f1476fa8866a7ae7525f52`, plan `free` y AutoDeploy desactivado. Sin nuevas variables, cargos, escrituras en Railway ni integración contra API externa.
- **Pendiente, sin falso GO:** repetir **solo una** simulación `subscription_preapproval` desde el panel TEST ya configurado; consultar a continuación los logs Render para distinguir `AUTHENTICATED_SIMULATOR_RECEIPT_TOPIC`, `HMAC_OR_ENVELOPE` y `TEST_MODE`. Si continúa 401 de HMAC, investigar la igualdad de firmas **sin jamás exigir copiar el secret al chat**; no reemplazar credenciales a ciegas. Incluso un 200 del simulador solo confirma transporte y firma de un fixture, **no** una suscripción ni invoice `approved` auténticas.
- **Seguridad de configuración:** preservar Modo productivo, no regenerar claves TEST, preservar/restaurar URL TEST previa de pagos de negocio cuando concluya el ensayo. Mantener C5 etapa 3 NO INICIADA.


## Simulador: 502 al despertar Render Free y diagnóstico privado de HMAC

**2026-10-14, segundo intento del titular:** el simulador de `subscription_preapproval` mostró código interno `1111` y **HTTP 502** con cuerpo de solicitud visible como `{}`. No se adjunta captura ni datos de cuenta. **Esto no demuestra que firma HMAC coincida** ni que un pago exista.

**Evidencia correlacionada en Render MCP:** el servicio `helvoca-c5-test-webhook-ingress` plan Free estaba desplegado `live`, pero a la hora del intento registró un **nuevo arranque de la aplicación** `C5_RENDER_TEST_INGRESS_LISTENING_NO_PAYMENT_ACTIONS` (~2026-10-14 11:17:53 UTC), tras más de 15 min desde la última actividad registrada. No constaban registros de petición HTTP/aplicación que demostraran la recepción del segundo intento. Esto es **compatible con el despertar tardío de Render Free y el timeout del simulador**, no es demostración inequívoca de la causa remota. La primera simulación había dejado `C5_TEST_WEBHOOK_REJECT_STAGE=HMAC_OR_ENVELOPE` en Render (~10:59:57 UTC): hay una **segunda barrera** de firma/forma por diagnosticar y no se debe afirmar que la clave MP y Render ya coincidan.

**Mejora sin exposición de secretos:** `scripts/ops/c5_ephemeral_webhook_ingress.py` registra una etiqueta fija de `C5_TEST_WEBHOOK_REJECT_CAUSE` (por ejemplo `HMAC_MISMATCH`, `SIGNATURE_FORMAT`, `REQUEST_HEADER`, `SIGNATURE_TIMESTAMP`, `EVENT_SHAPE`, `ENVELOPE`, `UNKNOWN`). El cliente siempre recibe HTTP 401 genérico sin diagnóstico; logs nunca contienen payload, headers, valores de firma, request IDs o secret. El control HMAC previo y la restricción TEST **no se han debilitado**. Se añadieron pruebas para que una firma incorrecta sea `HMAC_MISMATCH`, para diferenciar evento mal formado y para que nunca se imprima un mensaje desconocido del proveedor.

**Certificación:** [GitHub Actions #38555933016](https://github.com/Nicricht/helvoca/actions/runs/38555933016) completed/success (**11 pruebas de arranque Render y 35 pruebas de receptor = 46/46**) y [CI HTTPS #38555932991](https://github.com/Nicricht/helvoca/actions/runs/38555932991) success en el código `594cf5db52f7717e559ce101ef470483aa32b505`. **Render deployment manual `dep-db7mbftg1s2s73b2b030` `live`**, SHA ejecutada idéntica, sin AutoDeploy ni nueva infraestructura. Este cambio documental posterior requiere la verificación específica de CI del HEAD documental, que no altera el binario ya desplegado.

**Próximo único experimento:** desde un runner externo comprobar primero `GET /health=204` y `POST` sin firma `401` para **despertar el servicio antes** de que el usuario pulse `Nueva prueba` una sola vez. Pedir repetir el simulador **inmediatamente tras confirmar que Render está caliente**. Consultar logs en el momento posterior. Si aparece `HMAC_MISMATCH`, verificar de forma segura coincidencia de secreto de modo TEST sin pedir copiarlo al chat; si aparece `SIGNATURE_FORMAT` o `EVENT_SHAPE`, corregir el parser/contrato. Si Render vuelve a dormirse tras 15 min, el 502 puede repetirse, por lo que no es infraestructura apta para webhooks comerciales de suscripciones.


## C5-2: nuevo diagnóstico granular del 502

**2026-10-14.** El arranque nuevo de Render Free ~11:17:53 UTC, posterior al período de inactividad, es compatible con timeout del simulador Mercado Pago al despertar la instancia, pero NO demuestra causalidad exclusiva. Render no produjo logs HTTP por request para esa ventana. Luego se observaron SIGNATURE_HEADER (~11:23:20 UTC) y EVENT_SHAPE (~11:24:34 UTC), sin identificador seguro que permita asociarlos inequívocamente al intento del titular. No existe aceptación autenticada del simulador documentada.

**Corrección de observabilidad:** todo POST que alcanza el handler del webhook emite solo C5_TEST_WEBHOOK_HANDLER_ENTERED, sin IDs ni payload. Los errores previamente agrupados en EVENT_SHAPE se separan en TOPIC_SHAPE, EVENT_ID_MISSING, EVENT_ID_CONFLICT y EVENT_ID_FORMAT. El cliente sigue recibiendo un 401 genérico si falla la autenticación o el contrato, sin filtrar secreto, firma, encabezados, cuerpos ni identificadores; siguen intactos HMAC, ventana temporal, modo TEST y exclusión de producción.

**RED y GREEN:** prueba nueva RED [Actions #38557191984](https://github.com/Nicricht/helvoca/actions/runs/38557191984) en SHA 1ff6592d3016aa157d7fd396c7fbb0cdfc4bd826. Regresión GREEN [Actions #38557253518](https://github.com/Nicricht/helvoca/actions/runs/38557253518) y HTTPS GREEN [Actions #38557253815](https://github.com/Nicricht/helvoca/actions/runs/38557253815) en SHA exacta 07d2b474725b49fdd4269e4210e28ea8c1e5dd1c. El HTTPS público prueba salud y rechazo sin HMAC, NO la autenticidad de Mercado Pago.

**Render TEST:** despliegue manual dep-db7mgrek1f9s7389hhvg confirmado live, commit ejecutable 07d2b474725b49fdd4269e4210e28ea8c1e5dd1c, plan free, AutoDeploy desactivado. El commit documental posterior no cambia el binario; verificar su propio CI. Sin cambios Railway production, main, secretos ni pagos.

**Falta:** obtener una sola repetición oficial de Mercado Pago Developers TEST con servicio despierto, después de comprobar GET /health=204 y POST sin HMAC=401. Revisar logs inmediatamente y distinguir si llegó al handler, si fue rechazada su firma o su formato. Ningún CI sintético reemplaza ese resultado. El plan gratuito con suspensión no garantiza entregas de webhook permanentes; no instalar un ping recurrente ni contratar un plan sin autorización. C5 etapa 2 NO-GO como prueba de pago real.


## C5-2 · Simulador TEST a las 11:37 UTC: rechazo de formato previo al HMAC

**Hallazgo real (2026-10-14):** el titular ejecutó de nuevo la simulación oficial Mercado Pago Developers TEST, topic visible `subscription_preapproval`, y recibió **HTTP 401**. Los registros Render de esa hora muestran `C5_TEST_WEBHOOK_HANDLER_ENTERED` (11:37:20 UTC), `C5_TEST_WEBHOOK_REJECT_STAGE=HMAC_OR_ENVELOPE` y `C5_TEST_WEBHOOK_REJECT_CAUSE=TOPIC_SHAPE`. Esto confirma que esta solicitud llegó al proceso Python; por tanto **el 401 observado no es el 502 anterior por pérdida de conectividad**. El clasificador anterior rechazaba temas antes de verificar la firma, así que **HMAC todavía estaba SIN VERIFICAR** para ese intento. El 401 no se debe atribuir a secreto incorrecto. Tampoco existen datos suficientes para distinguir conflicto query/body frente a tópico desconocido, porque eran una misma categoría.

**Cambio mínimo HIGH RISK, sin aceptar nuevos temas:** verificar firma HMAC primero, utilizando exclusivamente identificadores de longitud/forma restringida, y después rechazar topics ausentes, de URL no soportados, de body no soportados y de query/body contradictorios con categorías fijas `TOPIC_MISSING`, `TOPIC_QUERY_UNSUPPORTED`, `TOPIC_BODY_UNSUPPORTED` y `TOPIC_CONFLICT`. Siguen obligatorios HMAC, timestamp reciente, allowlist de dos tópicos, entorno TEST y bloqueo de producción. Respuesta externa sigue 401 genérico y logging solo de valores categóricos, nunca rutas completas, headers, ID, app IDs, importes, clave ni bodies.

**RED → GREEN:** [Actions #38558141012](https://github.com/Nicricht/helvoca/actions/runs/38558141012) failure por tests adversariales nuevos en SHA `2629322661bab09934af532ceae529d9b1576131`. Corrección en SHA `2a0c0ec7667d0a91366a484c5b0349f83ab04350`; [regresión #38558230974](https://github.com/Nicricht/helvoca/actions/runs/38558230974) completed/success y [HTTPS externo #38558231390](https://github.com/Nicricht/helvoca/actions/runs/38558231390) completed/success. La nueva versión **no equivale** a un callback auténtico recibido ni a una suscripción facturada.

**Despliegue manual TEST:** `dep-db7mkos9v7es73cjk4qg` iniciado desde GitHub SHA `2a0c0ec7667d0a91366a484c5b0349f83ab04350`, mantener estado pendiente de confirmar hasta comprobar `live` en Render. El último HEAD posterior por documentación no debe confundirse con el código desplegado. No tocar Railway production, `main`, secretos ni recursos adicionales.

**Continuidad:** después de comprobar deploy `live` y humo HTTPS en nuevo HEAD, queda solo un envío oficial TEST desde Mercado Pago para saber si el HMAC coincide o si el tópico proviene de query/body incompatibles. No seguir cambiando el parser a ciegas. C5-2 continúa NO-GO proveedor hasta demostrar firma, recepción y factura `approved`. **Precaución:** una captura nueva del titular mostró el valor de la clave de firma TEST sin enmascarar; NO copiarla al repositorio, logs ni comentarios. Recomendar rotarla y sincronizarla por canales seguros una vez finalizado el diagnóstico. 

**Verificación posterior del despliegue, 2026-10-14 11:42 UTC:** Render confirmó `dep-db7mkos9v7es73cjk4qg` estado **live**, código ejecutable exacto `2a0c0ec7667d0a91366a484c5b0349f83ab04350`. Registros confirman arranque `C5_RENDER_TEST_INGRESS_LISTENING_NO_PAYMENT_ACTIONS` y rechazo seguro `SIGNATURE_HEADER` del POST sin firma del smoke público. En el commit documental `ade8eda6d1191b95a573ec5fc6742032bcf73e38`, CI [regresión #38558463099](https://github.com/Nicricht/helvoca/actions/runs/38558463099) **completed/success** y [HTTPS #38558463052](https://github.com/Nicricht/helvoca/actions/runs/38558463052) **completed/success**. No se recibió todavía una nueva prueba oficial del proveedor con este código ni una factura TEST aprobada.


## C5-2 · raíz del HTTP 401 en simulador oficial, solucionada en código (2026-10-14)

**Evidencia verificable:** en el envío del propietario a las 07:48:01 Chile (11:48:01 UTC), el simulador `subscription_preapproval` respondió HTTP 401 y el receptor Render registró `C5_TEST_WEBHOOK_HANDLER_ENTERED` seguido de `C5_TEST_WEBHOOK_REJECT_CAUSE=TOPIC_CONFLICT`. El chequeo HMAC previo estaba situado **antes** de esta validación, por lo que para este envío la HMAC fue **válida**. La URL/solicitud codificaba dos nombres de tópico permitidos, pero contradictorios entre query y cuerpo; el servidor anterior siempre los rechazaba, incluso al tratarse del ejemplo inofensivo de Mercado Pago Developers. Este 401 NO se debe a falta de firma ni suspensión de Render.

**Corrección mínima (HIGH RISK):** `verify(... allow_simulator_topic_mismatch=False)` mantiene por defecto el rechazo de cualquier conflicto de tópicos. Exclusivamente en el receptor Render TEST, **después de autenticar HMAC, timestamp y ID**, se permite que un query `subscription_authorized_payment` contradiga el body `subscription_preapproval` SOLO si `require_sandbox_event()` clasifica el body como el **fixture sintético exacto** `SIMULATOR_ONLY` (fecha fija histórica, action/entity/version/ID/estructura). En ese único caso, usa el tópico del body para el registro, sin procesar ni cobrar ningún pago. Solicitudes sin HMAC, con HMAC inválida, eventos normales TEST con temas contradictorios, eventos productivos, fixtures alterados y cualquier otra pareja de tópicos continúan prohibidos.

**TDD RED → GREEN:** [CI deliberadamente rojo #38559402105](https://github.com/Nicricht/helvoca/actions/runs/38559402105) SHA `bb4ba7684df53d7e9ffb26cf0d00bff6d35218d5`, luego [CI regresión verde #38559474707](https://github.com/Nicricht/helvoca/actions/runs/38559474707) y [HTTPS preliminar verde #38559474701](https://github.com/Nicricht/helvoca/actions/runs/38559474701) SHA código `1231d72010b4dbdf430a3276d4c2098c70371b5b`. Cinco pruebas nuevas incluyen aceptación del simulador con conflicto, rechazo de evento normal/fixture alterado/firma errónea y HTTP real local 200 después de HMAC correcta. El HTTPS preliminar verificó la instancia anterior a este deploy y **no** certifica la nueva hasta otro smoke.

**Nuevo deploy manual exclusivo de Render TEST:** `dep-db7mphuk1f9s738abs9g` **live**, SHA ejecutable `1231d72010b4dbdf430a3276d4c2098c70371b5b`; build y arranque correctos. Plan gratuito, AutoDeploy OFF; sin tocar Railway, main, configuraciones de MP, claves ni cuentas, sin transacciones ni dinero real.

**Resultado aún no afirmable:** tras el cambio no se ha registrado **otro** evento oficial autenticado del simulador, por lo que no declarar su HTTP 200 remoto como certificado. C5-2 tampoco está cerrada sin una suscripción TEST real, notificación real validada, factura approved e idempotencia del backend. La última captura expuso una clave secreta TEST: nunca copiarla a GitHub/logs; rotar y sincronizar mediante canales privados antes de uso confiable de la integración.

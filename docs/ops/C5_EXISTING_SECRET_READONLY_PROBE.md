# C5 · comprobar el secreto existente ANTES de solicitar configuración al titular

**Plan único:** [Issue #823](https://github.com/Nicricht/helvoca/issues/823), puerta C5 etapa 2/3. **Trabajo:** rama `probe/c5-existing-secret-readonly-20261014`, apilada sobre PR #833.

## Error operativo que no debe repetirse

El titular ya tenía el **repository Actions secret** `MERCADOPAGO_TEST_ACCESS_TOKEN` en GitHub, con fecha de modificación anterior al inicio de la etapa 2. Antes se le indicó crear uno sin revisar su captura ni estado. **No volver a pedir creación de secret, cuenta, variable, servicio o aplicación sin comprobar primero si existe y si su valor cumple el contrato**, usando los accesos y evidencias disponibles.

El conector de GitHub no permite leer valores de secretos ni las APIs de administración de secrets. Esto **no justifica pedir al usuario que revele un token ni que lo vuelva a crear**. En su lugar se creó una prueba de **solo lectura** en GitHub Actions:

- `scripts/ops/c5_existing_secret_readonly_probe.py` únicamente hace **GET** autenticado a `https://api.mercadopago.com/users/me`, con 8 segundos de timeout. No hay POST, suscripción, cobro, checkout, migración ni cambio de base de datos.
- Si falta el secret, el token no funciona o la cuenta es distinta, devuelve códigos **categóricos**. No expone Access Token, cuentas, correo ni respuesta completa. Github Actions ya tiene acceso seguro al secreto existente mediante `secrets.MERCADOPAGO_TEST_ACCESS_TOKEN`, **sin copiarlo ni volver a crear uno**.
- La identidad esperada del vendedor de prueba y la cuenta compradora, ya evidenciadas en capturas previas, se comparan usando **huellas SHA-256**, sin colocar valores completos en código ni logs.
- `.github/workflows/c5-existing-secret-readonly-probe.yml` se ejecuta solamente en push de la rama de diagnóstico, con permiso `contents: read`. Corre primero pruebas unitarias en memoria (sin HTTP), y solo después la consulta GET real de diagnóstico, si se mantiene el control estático de ausencia de POST.
- Un fallo real de identidad es un **bloqueo seguro** y dejará rojo el job a propósito; NO se debe convertir ese bloqueo en `PASS` quitando verificaciones.

## Qué significa el diagnóstico

- `C5_EXISTING_SECRET_VERIFIED_READONLY_MATCHES_TEST_SELLER`: secreto disponible, credencial autenticada, cuenta TEST/Chile coincidente con vendedor del panel de esta integración. **Aún no** acredita checkout/webhook/pago aprobado.
- `C5_EXISTING_SECRET_SELLER_ID_MATCH_BUT_ALIAS_MISMATCH`: el mismo ID no coincide en nombre; comprobar si la captura/documentación de nickname cambió, sin comprar nada.
- `C5_EXISTING_SECRET_IS_SCREENSHOT_BUYER_NOT_SELLER`: el token del secreto corresponde al comprador en vez de vendedor; no utilizar para iniciar una suscripción vendedor.
- `C5_EXISTING_SECRET_IS_DIFFERENT_TEST_ACCOUNT`: la credencial funciona y declara una cuenta de TEST Chile, pero no es el vendedor ni comprador de las capturas actuales. **No** iniciar suscripciones sin comprobar procedencia.
- `C5_EXISTING_SECRET_REJECTED_BY_PROVIDER`: token existente desactualizado/inválido o sin permisos para la consulta. Hay que actualizarlo por el valor TEST actual.
- `C5_EXISTING_REPOSITORY_SECRET_UNAVAILABLE_TO_ACTIONS`: puede existir en UI pero no estar disponible en el workflow. Revisar permisos de ejecución.
- Otros códigos `C5_MP_READONLY_...`: infraestructura/respuesta indeterminada; NO supone token inválido ni autoriza creación.

## Regla de siguiente intervención

**Primero** averiguar la categoría exacta de la ejecución real de GitHub Actions. Solo si confirma token distinto al vendedor TEST actual o token rechazado, pedir **una única acción**: editar el **secreto existente** (icono lápiz en GitHub repository Secrets), pegar directamente **Access Token de Credenciales de prueba** para el vendedor TEST cuya identidad se verificó en el panel de Mercado Pago, y guardar. **No crear duplicado ni enviar token por el chat.**

Sin el cambio real, mantener `C5-2=BLOCKED_EXTERNAL_PROVIDER`, `C5-3=NO INICIADA`. Incluso si la identidad GET pasa, sigue pendiente una integración con webhook firmado y factura TEST aprobada en un **entorno aislado**. El merge a `main` y cambios productivos continúan sujetos a C1/C2.

## Evidencia exacta y handoff

- Primer run de consulta de secreto [#38535515523](https://github.com/Nicricht/helvoca/actions/runs/38535515523): tests offline OK; consulta GET a proveedor devolvió **categoría segura de identidad no coincidente**. No se crearon suscripciones.
- Ejecuciones de refinamiento de clasificación posteriores no reemplazan evidencia final. Consultar primero el último HEAD de rama y su GitHub Actions antes de afirmar estado.
- Cierre documental obligatorio: actualizar descripción del PR y [Issue #823](https://github.com/Nicricht/helvoca/issues/823) con SHA/run exactos, categoría real y siguiente acción comprobada. Nunca registrar los identificadores TEST completos, las contraseñas visibles en capturas ni el token de GitHub.

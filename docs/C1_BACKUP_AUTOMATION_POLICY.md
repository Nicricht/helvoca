# Helvoca C1: política operativa de respaldos cifrados y recuperación

**Estado:** preparación en rama de PR #828. **No implementado como programación activa; no autoriza despliegue.** Este documento complementa exclusivamente C1 del [tablero maestro #823](https://github.com/Nicricht/helvoca/issues/823), no abre nuevas fases.

## Evidencia ya adquirida y que NO se repite

- Un respaldo nuevo de PostgreSQL 18 de producción fue exportado mediante acceso de solo lectura y cifrado con `age`. La consola del operador informó la restauración aislada inicial completada.
- Google Drive institucional entregó un objeto remoto cifrado de **673001 bytes** con SHA-256 **`fdf281efa0ff75dac61e00353229d2e8f61f55c8c434a876c75d33d7449a7ce8`**. Se descargó independientemente vía conector y el operador confirmó que coincide con el cifrado local.
- El operador ejecutó la restauración PostgreSQL 18 aislada desde descarga autenticada de Drive, con salida `C1_REMOTE_DOWNLOAD_DECRYPT_AND_OFFLINE_PG18_RESTORE_VERIFIED` y evidencia privada fechada 2026-10-14.
- **Nuevo destino personal:** el operador comunicó que creó y cargó una carpeta personal restringida de Google Drive. En 2026-10-14 el conector de Drive disponible en esta conversación devolvió **HTTP 404/no acceso** a esa carpeta. **No hay lectura autenticada ni hash de su copia personal.** El enlace o ID personal no se guarda en este repositorio.

Los comandos de pruebas de recuperación de los scripts de `scripts/ops/c1_*` no programan tareas ni tienen autorización para transferir datos productivos a otros servicios.

## Política propuesta (pendiente de aprobación y medición)

| Variable | Propuesta inicial | Puerta de aceptación |
| --- | --- | --- |
| RPO | Máximo **24 horas** de datos perdidos | Hora de cada backup tomada en origen y copia remota verificadas; no deducir RPO solo del cron |
| RTO | Máximo **4 horas** hasta servicio recuperado | Simulacro cronometrado desde descarga limpia, provisión, restore y reanudación funcional autorizada |
| Frecuencia | Una vez al día, objetivo local 03:30 `America/Santiago` | Dos ejecuciones automáticas consecutivas con upload y readback correctos |
| Retención | 30 copias diarias; 12 copias mensuales opcionales | Ajuste según privacidad, tamaño, costes y requisitos legales |
| Ensayo | Integridad remota diaria; restauración PostgreSQL 18 aislada semanal | Evidencia privada de última restauración con hora y resultados |
| Alertas | Backup vencido >26 h, upload incompleto, hash diferente, restore fallido, almacenamiento insuficiente | Destinatario de alertas real, prueba de alerta y acuse |
| Ubicación | Cuenta de servicio/organización no institucional, credenciales mínimas y MFA | Propiedad del destino, cuotas, permisos y recuperación de cuenta comprobados |

Los valores son **objetivos provisionales**, no compromisos existentes ni métricas observadas. Un backup diario por sí solo NO prueba RPO/RTO contractuales. Se recomienda revisar la necesidad de PITR antes de prometer RPO inferior a 24 horas.

## Arquitectura de automatización segura

1. **Ejecución 24/7 independiente del PC.** Un job dedicado y limitado (cron gestionado/cloud worker) obtiene un snapshot PostgreSQL usando credenciales con privilegios mínimos de lectura/backup. **No crear job productivo ni reutilizar la cuenta personal del operador de Railway para un cron hasta verificar el mecanismo de autenticación no interactivo.**
2. **Cifrado antes del envío.** La versión manual usa `age --passphrase`, interactivo. Para cron, migrar a `age` con **recipient público**; guardar clave privada de recuperación **fuera del job**, en bóveda segura con recuperación de acceso ensayada. Nunca enviar un `.dump` sin cifrar a Drive, GH, logs o servicios de terceros.
3. **Almacenamiento externo.** Usar API de Drive con OAuth de alcance mínimo y refresh token protegido, o almacenamiento de objetos offsite equivalente. Confirmar identidad y titularidad de cuenta, cuota, 2FA, permisos privados y longevidad. **No exponer la carpeta al público** para que el conector pueda leerla.
4. **Verificación remota.** Registrar `run_id`, timestamp UTC, hash/size del ciphertext, tamaño/hora de origen, ID remoto y readback de bytes desde el proveedor; descargar y comparar SHA-256 real, no tratar `upload 200` ni existencia como suficiente.
5. **Fail-closed.** Cuando falla cualquier paso, alerta y se conserva backup anterior. Nunca borrar una copia sin que exista una sucesora **remota, cifrada y validada**. Operaciones de borrado/retención inicialmente solo `dry-run`; políticas reales de eliminación requieren destino estable y permisos verificados.
6. **Restauración aislada.** Programar ensayos separados de la exportación: PostgreSQL 18 desechable sin acceso a Internet, datos en volumen efímero privado, chequeos de tablas/migraciones y eliminación segura de plaintext temporal.
7. **Protección de secretos y privacidad.** Secrets en gestor del entorno del job, no CLI argument/log/CI artifacts; alcance al backup solo el mínimo indispensable. No incluir PII, base de datos, `private.json`, key material ni tokens en GitHub. Preferir que los dumps de recuperación queden localizados y con TTL breve.
8. **Pruebas de fallo.** Simular Drive desconectado, token revocado, cuota llena, CLI/Railway inaccesible, backup inválido, cifra corrupta, eliminación equivocada, job atrasado y alerta perdida, sin tocar producción.

## Puertas de habilitación (todas obligatorias)

- [x] Backup nuevo, cifrado, copia externa institucional y restauración independiente reportada por operador.
- [ ] Copia del **destino personal** validada desde la cuenta titular con readback y hash remoto coincidente; la conexión disponible no tiene acceso.
- [ ] Guardar y recuperar la passphrase manual existente en bóveda separada; verificar el procedimiento sin revelar la clave.
- [ ] Elegir destino de ejecución automática independiente del PC y comprobar autenticación PostgreSQL backup read-only, Drive y mecanismo de cifrado no interactivo.
- [ ] Implementar cron, métricas, alertas, retención solo dry-run inicialmente; verificar tests y dos ejecuciones reales sin cambios productivos.
- [ ] Medir RPO y RTO desde origen y simulacro de recuperación autorizado.
- [ ] Incorporar permisos y presupuestos operativos aprobados antes de activar un cron de producción.

**STOP absoluto:** sin cuenta permanente verificable, sin credenciales de recovery custodiadas, sin método seguro de `age` no interactivo, sin prueba real de alertas o sin destino remoto, mantener `C1=NO-GO`, `PROD_DEPLOY=NO-GO`, `COMMERCIAL_GO=NO-GO`. No volver a exportar solo para repetir pruebas.

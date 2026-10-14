# Helvoca · Piloto de voz · Parte 5/6: predespliegue productivo seguro

**Fecha:** 2026-10-12, 21:30 Chile (inicio del trabajo). **Repositorio:** `Nicricht/helvoca`. **Rama de Parte 5:** `chore/voice-real-pilot-5of6-production-gate-20261012`, derivada del **PR #802, `92f430ba92b772e0109bdb2f4a337ddbfc463d33`**. El código que se valida en esta etapa **no está desplegado**.

## 1. Inventario observado directamente, sin alterar producción

- Railway proyecto `41988ee6-f9f6-48eb-b726-abb973bb5740`, entorno `production` `8bbc755a-58b5-46e8-8227-eefa4adbbce4`.
- API `helvoca-api`, servicio `edbfc4d2-3487-4cfb-ab7c-52fdb4060c4a`, un contenedor en región `iad`, `source=Nicricht/helvoca`, rama `main`, despliegue `SUCCESS` con SHA **`ca58693e167752bd6eab8ff4e9be770391fe4293`**.
- PostgreSQL: servicio `107bb612-f25d-4c57-98a4-f494ea6b9cb4`, imagen Postgres-SSL 18, región `iad`, nodo único y volumen `b28c1046-168e-4f2e-bf02-18a2a2f1e611`, 500 MB, montado en `/var/lib/postgresql/data`. Sin cambios `staged` conocidos en API o DB.
- La ruta API/Twilio y la fila `phone_number.external_id` fueron verificadas previamente con capturas del usuario; la tabla de producción **no** fue consultada directamente en esta etapa.
- Los secretos de Railway se mantienen protegidos. No se han leído, copiado, expuesto o modificado.

**Importante:** `docs/POSTGRES_PRODUCTION_RECOVERY.md` afirma una copia de volumen Railway diaria al 2026-10-01, pero no proporciona un identificador actual de backup, una prueba de restauración aislada ni un RTO probado. Esta conexión Railway no expone endpoints de backup/PITR y no ejecuta SQL directo contra PostgreSQL. No confundir documentos previos con evidencia vigente.

## 2. Diferencia real de esquema contra producción

La rama candidata añade estos archivos Flyway:

1. `V100__twilio_managed_voice_binding_provenance.sql`: añade `twilio_binding_source`, `twilio_account_sid`, `voice_verified_at`; restricciones CHECK y un índice único.
2. `V101__twilio_verified_existing_binding_provenance.sql`: amplía las restricciones a `VERIFIED_EXISTING`, índice único y trigger de bloqueo de escrituras no-sistema.
3. `V102__twilio_existing_enrollment_claim.sql`: tabla de reclamos internos, FKs, UNIQUE, política FORCE RLS y privilegios `helvoca_system`.
4. `V103__twilio_existing_enrollment_admin_approval.sql`: solicitud de doble aprobación, auditoría, constraints, triggers, privilegios y RLS.

**Ninguna de las cuatro incluye backfill masivo ni UPDATE de los números existentes.** Sin embargo, hay DDL que altera estructura, constraints y permisos productivos. `spring.flyway.enabled=true` en `application.yml`: iniciar el nuevo backend dispararía migraciones automáticamente. Hacer rollback solo del contenedor **NO** revierte de manera segura el esquema ni los datos. No aplicar migraciones manuales, no desactivar RLS, ni eliminar constraints por tanteo.

## 3. Puerta de liberación creada en Parte 5

- `scripts/ci/voice_pilot_phase5_gate.py` comprueba un inventario explícito de SHA de producción/candidato, 7/7 CI, conjunto exacto de migraciones, mantenimiento, verificación read-only de Flyway, backup actual identificado, **restauración terminada y verificada en destino aislado**, aislamiento de tenants, kill switch global, responsable de rollback.
- `docs/telephony/evidence/2026-10-12-phase5-production-no-go.json` registra `null/false` para verificaciones **no probadas**; ninguna se presume positiva.
- `scripts/ci/test_voice_pilot_phase5_gate.py` prueba que fallan cerrados los supuestos positivos, faltantes o corruptos. `--expect-blocked` impide que el workflow reporte una habilitación productiva inadvertida.
- Este validador usa **evidencia proporcionada por el operador**, no es prueba criptográfica de backups ni permiso autónomo de desplegar. Aun si llega a `READY_FOR_HUMAN_DEPLOYMENT_REVIEW`, devuelve `is_deployment_authorization=false`, `live_call_certified=false` y `commercial_go=false`. La confirmación debe provenir de herramientas oficiales y comprobaciones independientes.

## 4. Resultado actual y bloqueos concretos

**Estado de despliegue: NO-GO.** Faltan pruebas independientes de:

- último backup de volumen realmente existente, reciente e íntegro;
- restauración aislada exitosa sin tocar el volumen origen, con `flyway_schema_history` y tablas operativas;
- esquema productivo y última versión Flyway (consulta read-only) antes de migración;
- revisión de compatibilidad de restricciones V100–V103 con **datos reales**, sin alterar tablas;
- completa conclusión de CI del SHA final de Parte 5;
- corroboración actual de que el global kill switch está apagado, el tenant piloto protegido, los demás tenants intactos y existe ventana de mantenimiento/propietario de recuperación.

El acceso Railway utilizado expone estado de despliegue, variables por **nombre**, observabilidad y recursos; **no** respaldo/PITR ni consultas SQL directas en esta sesión. La estrategia segura es dejar la migración **sin aplicar** hasta obtener las pruebas mediante medios autorizados.

## 5. Procedimiento de despliegue propuesto una vez satisfechos los bloqueos (NO ejecutado)

1. Certificar Full CI 7/7 del SHA exacto de Parte 5, dejando todos los PR apilados consistentes y sin reescribir historial.
2. Verificar backup/restauración **en un PostgreSQL distinto** y capturar IDs de evidencia. No restaurar sobre el volumen de producción solo para probarlo.
3. Consultar la historia Flyway y esquema read-only. Ensayar V100–V103 y su compatibilidad de datos en una copia aislada con volumen restaurado, y validar RLS, índices y tiempos de bloqueo.
4. Comprobar `HELVOCA_CONTROLLED_PILOT_EXTERNAL_EFFECTS_ENABLED=false` efectivamente, y desactivar rutas pagadas hasta certificar canales. Mantener los demás tenants inaccesibles al piloto. No cambiar variables sin trazabilidad y rollback.
5. Identificar SHA candidato exacto y ventana aprobada. Desplegar de manera controlada, observar las cuatro migraciones, el estado Railway `SUCCESS`, `/actuator/health`, errores, latencia, RLS, métricas de disponibilidad e integridad por tenant.
6. Si falla: mantener efectos externos detenidos, evaluar rollback del **código** al SHA conocido, conservar el esquema hasta que se compruebe compatibilidad; jamás `DROP` o reemplazar volumen a ciegas. Exigir plan distinto para revertir datos/schema.
7. Tras un despliegue realmente verificado, ejecutar el nuevo endpoint protegido con la identidad real del negocio para constatar la admisión local y propiedad Twilio. El endpoint no existe en producción hasta que se despliegue el commit candidato.

## 6. Límite de responsabilidad de esta etapa

La autorización general para desarrollar **no** hace verificable un backup que esta conexión no puede inspeccionar. Por tanto **no se realizó deploy, merge, migración, compra, transferencia, llamada ni cambio de variables**.

En Parte 6, una llamada solo puede certificarse con resultado real Twilio/Gemini, control de gasto, interlocutor y finalización medible. Mientras no exista esa evidencia: `LIVE_CALL_CERTIFIED=PENDING`, `COMMERCIAL_GO=NO-GO`.

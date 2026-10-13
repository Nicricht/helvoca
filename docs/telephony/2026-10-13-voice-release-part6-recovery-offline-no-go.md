# Helvoca · Parte 6/10 · Backup, restauración, rollback y ventana controlada (NO-GO)

**Fecha:** 2026-10-13. **Base:** PR #815 (Parte 5) y SHA
5a78ec252a6133574dedc4b1b55df2c5d650b219. **PR nuevo:** rama apilada
test/voice-release-part6-recovery-offline-20261013, borrador; nunca fusionar directamente a main.

## Decisión ejecutiva

La Parte 6 desarrolla y prueba exclusivamente **preparación de recuperación offline**.
No aprueba despliegue, no ejecuta Flyway en Railway, no cambia Twilio ni habilita
llamadas. Los tests con datos sintéticos no son sustituto de respaldo actualizado
de producción, copia independiente ni una restauración de datos reales.

- PART6_OFFLINE_RECOVERY_CI: sujeto a resultados del HEAD exacto.
- PART6_SYNTHETIC_PG18_RESTORE: sujeto a la prueba Docker, no equivale a recuperación productiva.
- FRESH_PRODUCTION_LOGICAL_BACKUP: PENDIENTE DE VERIFICACIÓN INDEPENDIENTE.
- FRESH_PRODUCTION_RESTORE: PENDIENTE. Hay evidencia anterior del operador, no renovación automática.
- ENCRYPTED_REDUNDANT_OFFSITE_COPY: NO CERTIFICADA.
- PROD_DEPLOY=NO-GO; LIVE_CALL_CERTIFIED=false; COMMERCIAL_GO=NO-GO.

## 1. Evidencia existente y sus límites

- El documento docs/telephony/2026-10-13-stage-a-postgres18-recovery.md
  recoge un respaldo privado de PostgreSQL 18 y una restauración local
  reportada por el operador, sin acceso de GitHub al archivo original.
- docs/telephony/2026-10-13-stage-b-pg18-flyway-rls-rehearsal.md registra
  ensayo de psql V100–V103 sobre una copia restaurada del operador y CI con
  Flyway sobre bases sintéticas. No demostró Flyway sobre el dump privado.
- La Parte 2 y la Parte 5 certificaron compatibilidad PostgreSQL 16/18,
  migraciones inmutables V100–V103 y CI integrado para ese candidato.
- Última versión de Flyway productiva reportada: V99 (lectura anterior);
  no volver a presentar esta versión como lectura actual sin otra consulta.
- Las tres carencias de telefonía documentadas siguen sin resolverse
  con escritura productiva: DB_ACCOUNT_SID_MISSING_OR_MISMATCH,
  DB_PROVENANCE_UNVERIFIED y DB_VOICE_TIMESTAMP_MISSING.

## 2. Nuevo ensayo 100 % sintético y sin red

Script: scripts/ci/voice_part6_synthetic_pg18_recovery.sh

1. Deniega variables que podrían dirigirlo a conexiones productivas.
2. Crea DOS contenedores postgres:18-alpine completamente desconectados
   de red (--network none), sin puertos publicados y con claves aleatorias.
3. Inserta dos empresas y dos reservas ficticias en el primero.
4. Genera un pg_dump -Fc local, verifica tamaño y SHA-256.
5. Cifra DOS copias efímeras separadas mediante OpenSSL AES-256-CBC,
   PBKDF2 con 200 000 iteraciones y salt, y compara ambas descifradas
   byte a byte y por SHA-256. Es una demostración de cifrado local:
   **no son dos ubicaciones independientes, no existe copia offsite**.
   El descifrado más verificación SHA-256 detecta alteraciones; AES-CBC
   no proporciona por sí solo autenticación. No usarlo sin un esquema
   de integridad autenticada y gestión de claves para respaldos reales.
6. Restaura en el SEGUNDO contenedor con pg_restore --exit-on-error,
   compara filas y relaciones de claves foráneas con la fuente.
7. Destruye contenedores, dumps, copias y clave de prueba al salir.
   No sube artefactos, archivos SQL, credenciales ni datos.
8. Mide segundos de restauración **solo de este fixture pequeño**:
   no son RTO ni RPO de producción.

La prueba no puede certificar la copia real del operador ni validar todos
los datos, RLS, red o rendimiento de una restauración productiva.

## 3. CI y compatibilidad

Workflow: .github/workflows/voice-release-part6-recovery-offline.yml

- Gate offline-safety-contract: pruebas adversariales Python, contrato de
  definiciones fail-closed e integridad de archivos V100–V103 de Parte 2.
- Gate synthetic-pg18-restore-and-encryption: ejecuta el ensayo anterior,
  y debe incluir línea PART6_SYNTHETIC_PG18_RESTORE=PASS en los logs.
- Gate pg18-flyway-rls-regression: Java 21, Testcontainers PostgreSQL 18
  y pruebas existentes del upgrade Flyway V99→V103, RLS y dos aprobadores.
- El SHA del PR debe ser idéntico al de todos los jobs verdes. No declarar
  PASS si queda algún job pendiente o si cambia la rama base.
- No se ejecuta el workflow general de main que incluye smoke HTTP público.
- Solo contents: read; sin secrets; switches de telefonía, OpenAI, Gemini
  y trabajos automatizados deshabilitados.

## 4. Lista operacional de respaldo real, SIN EJECUTAR AHORA

Un operador autorizado debe completar cada condición en el entorno controlado
AN TES de siquiera evaluar una ventana de cambios (la evidencia existente no
convalida indefinidamente la frescura):

1. Confirmar lectura actual de main, commit desplegado, nombre y versión
   PostgreSQL, Flyway, RLS, volumen y backup schedule; registro sin secretos.
2. Obtener backup PostgreSQL 18 nuevo de producción mediante mecanismo
   autorizado y de solo lectura. Mantenerlo en almacenamiento privado fuera
   de GitHub/CI/chat/OneDrive; nunca ejecutar scripts que hagan dropdb,
   createdb ni pg_restore contra el host de origen.
3. Medir SHA-256, tamaño, hora UTC, ID opaco de versión y código de salida
   sin incorporar datos sensibles en el expediente público.
4. Restaurar el backup en Docker LOCAL aislado y sin red usando el script
   de recuperación privado de Stage A; cotejar esquema y conteos y conservar
   reporte privado. Una evidencia local previa no equivale a backup nuevo.
5. Guardar al menos una copia cifrada autenticada y otra copia redundante
   en un destino realmente independiente, con control de acceso separado,
   llave disponible a responsables y pruebas de descifrado y verificación
   de hash de ambas. Validar restauración desde una de las copias.
6. Verificar que el backup y su cadena de custodia sean recientes respecto
   al inicio exacto de la ventana. Si hay cambios posteriores, detener
   preparación y refrescar backup.
7. Medir tiempo real de restauración con datos representativos, no extrapolar
   el tiempo del fixture. Declarar RPO/RTO solo con evidencias medidas.

El mismo plan aplica al inventario de backups Railway/PITR: consultar
schedules/estado primero, sin habilitar PITR ni iniciar restores productivos
por automatismo. Restaurar un backup Railway in-place sobre la fuente real
está explícitamente prohibido como ensayo.

## 5. Secuencia de liberación FUTURA (solo plan, NO autorizada)

**Preflight T-antes:** dos aprobadores distintos identificados y
autorizados, aviso a afectados, mantenimiento/control de tráfico,
presupuesto/tasas y piloto PAUSED, backups de arriba certificados, estado
Flyway actual V99 con checksums previstos sin drift, separación tenants
probada, lista de SHA/imagen exactos, rollback practicado en entorno
aislado, responsables on-call, ventana y criterio de abortar documentados.

**Fase esquema, bajo autorización nueva:** una sola aplicación controlada
de V100→V103 por Flyway sobre Railway solo si preflight sigue válido.
Registrar cada versión y checksum, duración, lock y errores sin divulgar
secretos. No poblar automáticamente procedencia de números antiguos.
Mantener flags de voz OFF y ninguna llamada externa.

**Fase aplicación, autorización adicional:** desplegar artefacto
explícitamente aprobado compatible con nuevo esquema. Evaluar salud,
latencias, RLS, tenant ID, fallos, locks, errores y coste. Sin dar GO de
telefonía ni alterar webhooks. El enlace Twilio exige auditoría real,
identidad verificada y las dos aprobaciones de la Parte 4.

**Condiciones de parada inmediatas:** backup/descifrado inválido, checksum
distinto, deriva de SHA o de Flyway, problema de roles/RLS, lectura entre
tenants, incoherencia de SID/procedencia, signos de pérdida de datos,
error de migración, efectos externos imprevistos o alarmas no evaluadas.

## 6. Decisiones de rollback y límites

- Si aún no se migró ni se desplegó: ABORTAR sin escrituras.
- Si se aplicó esquema y aún no se habilitó voz: mantener switches OFF,
  mantener piloto PAUSED y evaluar compatibilidad del código. Revertir
  versión de aplicación SOLO si es compatible; revertir aplicación NO revierte
  automáticamente V100–V103 ni sus datos.
- Si alguna migración falló: suspender cambios, preservar logs y consultar
  historial/checksums; NO ejecutar flyway repair para ocultar un error,
  ni DROP manual de objetos V100–V103.
- Restaurar una base real puede perder escrituras posteriores y requerir
  downtime. Solo plantear restauración productiva con declaración de
  incidente, pérdida de datos aceptada, nueva autorización humana,
  snapshot de resguardo, plan de comunicaciones, comprobación RLS y rollback.
  No automatizarla en CI.
- Si fallan políticas multiempresa o procedencia Twilio, congelar
  exposición externa, impedir llamadas y denegar activación comercial.

## 7. Pendientes y frontera con Parte 7

La Parte 6 puede concluir como **certificación de preparación offline**
cuando los tres jobs pasen sobre HEAD exacto y se actualice el PR. El
**GO productivo continúa denegado** hasta obtener evidencia privada reciente,
copia redundante cifrada, autorización y plan operativo validado.

La Parte 7 NO se inicia ni se fusiona a main durante esta etapa.

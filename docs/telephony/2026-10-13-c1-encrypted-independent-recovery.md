# C1/8 | Segunda copia cifrada independiente y restauración PostgreSQL 18

Fecha: 2026-10-13. Tablero único: [Issue #823](https://github.com/Nicricht/helvoca/issues/823), frente C1.
Rama de trabajo: feat/c1-offsite-pg18-encrypted-restore-20261013, apilada sobre PR #822.

## Qué ya se verificó, no repetir

El 13-10-2026 el operador hizo un dump lógico read-only de Railway y lo restauró con éxito en Docker PostgreSQL 18 aislado, según su propia salida de PowerShell. La evidencia redactada ya está en docs/telephony/evidence/2026-10-13-stage-a-local-pg18-restore-attestation.md.

- Archivo privado inmutable: %LOCALAPPDATA%\Helvoca\private-backups\helvoca-postgres18-20261013T041250Z-355b64.dump
- SHA-256 esperado: 46f1ce16c694d22c7650ab2f26f6c96ded0cd0f1c4e5a3aa1fedd0be88cfbe18
- Restore PG18 verificado previamente por salida del operador, no mediante acceso directo del asistente al PC.
- Railway tiene API y Postgres 18 online y un volumen único configurado en 500 MB. Este tamaño no es el espacio usado ni certifica backups.
- El documento histórico docs/POSTGRES_PRODUCTION_RECOVERY.md indica backup diario en Railway a 01-10-2026; **no hay lectura actual autenticada de la lista de copias o PITR desde el conector Railway**.
- Existe script read-only previo scripts/ops/railway_postgres_recovery_audit.py para consultar estado y lista con Railway CLI local. No repetirlo ni activar PITR sin permiso, ya que PITR puede generar gastos.

## Qué añade este PR

El script scripts/ops/c1_offsite_pg18_encrypted_restore.py:
1. Reutiliza exactamente el archivo local anterior; no hace otro pg_dump ni contacta Railway.
2. Exige formato PGDMP, tamaño y SHA-256 ya certificado.
3. Usa el programa de cifrado establecido [age](https://github.com/FiloSottile/age) con contraseña introducida interactivamente. No implementa cifrado propio y nunca recibe contraseñas por argumentos, archivos Git, logs o variables.
4. Guarda el archivo .dump.age en una segunda ubicación existente elegida por el titular, excluyendo repositorios Git, rutas simbólicas y el directorio original.
5. Descifra **desde el archivo del destino final** hacia una carpeta temporal local privada, verifica el SHA-256 y reutiliza el restaurador anterior en un Docker postgres:18 con red deshabilitada y sin puertos publicados.
6. Elimina el archivo descifrado temporal; mantiene sin sobrescribir el original. Emite solo un JSON privado en %LOCALAPPDATA%\Helvoca\private-evidence.
7. Siempre deja tres estados sin certificar por máquina: independencia verdadera del almacenamiento, sincronización remota durable y custodia independiente de la contraseña. Medir restore local NO equivale a RTO completo de producción, ni el archivo antiguo satisface un RPO para operaciones posteriores.

## Única acción local necesaria

1. Elegir una unidad externa o almacenamiento privado sincronizado realmente independiente; el directorio destino debe existir previamente. La ruta E:\HelvocaOffsite del ejemplo **no existe necesariamente y no debe copiarse sin comprobarla**.
2. Confirmar que Docker Desktop esté en funcionamiento. Instalar age la primera vez en PowerShell, según la instrucción oficial de su autor:

~~~powershell
winget install --id FiloSottile.age --exact
age --version
docker --version
~~~

3. Con el repositorio local situado en esta rama actualizada, ejecutar solo una vez:

~~~powershell
py -3 .\scripts\ops\c1_offsite_pg18_encrypted_restore.py --offsite-dir "E:\HelvocaOffsite"
~~~

4. Introducir una contraseña robusta únicamente en la consola cuando age lo pida, conservar el método de recuperarla fuera de la unidad cifrada. No compartir contraseña ni dump con ChatGPT o GitHub.
5. La salida de éxito contiene C1_ENCRYPTED_COPY_DECRYPT_AND_OFFLINE_PG18_RESTORE_VERIFIED. La ruta del informe privado se debe conservar en el PC, nunca publicarla con su contenido.

**Importante sobre nube:** si la segunda ubicación es sincronizada, esperar a completar la subida del archivo CIFRADO y después demostrar que puede descargarse desde una sesión o equipo distinto y descifrarse/restaurarse desde ESA copia. El script no comprueba por sí solo esa independencia. Una carpeta distinta en el mismo disco no basta.

## RPO, RTO y seguridad comercial

- RPO: definir cuánto tiempo de escrituras podría perder Helvoca y demostrar frecuencia real de respaldos que cumpla esa política. Sin política y medición, RPO NO VERIFICADO.
- RTO: medir extremo a extremo cómo traer la copia desde el destino independiente, aprovisionar PostgreSQL, descifrar, restaurar, validar los tenants y devolver API saludable. El script solo mide la fase local de descifrar/restaurar. Hasta el ensayo total, RTO NO VERIFICADO.
- Frecuencia: esta herramienta se limita al dump de SHA conocido para evitar duplicar trabajo; no sustituye respaldos futuros, rotación ni comprobación periódica de disponibilidad remota.
- No habilitar PITR automáticamente: Railway explica que utiliza almacenamiento/bucket y egress facturable. Tampoco restaurar una copia de volumen sobre la base activa solo para practicar. Fuentes: https://docs.railway.com/volumes/point-in-time-recovery y https://docs.railway.com/cli .

## Evidencia de CI sin datos reales

Workflow: .github/workflows/c1-encrypted-offsite-pg18-restore-contract.yml.
20 tests adversariales y reutilización de Stage A, más un roundtrip real de age con datos sintéticos.
Sin tráfico de proveedor, sin producción y sin secretos.

**C1 permanece bloqueado hasta una copia externa cifrada restaurable, custodia independiente de contraseña y objetivos RPO/RTO aceptados.** Mantener PROD_DEPLOY=NO-GO, LIVE_CALL_CERTIFIED=false, COMMERCIAL_GO=NO-GO.

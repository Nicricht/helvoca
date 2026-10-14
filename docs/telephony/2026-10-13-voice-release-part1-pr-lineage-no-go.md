# HELVOCA · Plan de integración en 10 partes · Parte 1/10
## Auditoría final de PR, ancestros y riesgos de merge (13 de octubre de 2026)

**Dictamen: cadena estructuralmente coherente en la observación read-only; liberación NO-GO.**
Este documento certifica solo la **Parte 1**. No fusiona, despliega, activa servicios,
ejecuta migraciones, gasta Twilio, inicia Gemini ni modifica Railway.

### 1. Referencia real de GitHub y Railway

| Comprobación | Hecho observado |
| --- | --- |
| Repositorio | `Nicricht/helvoca` |
| `main` confirmado | `ca58693e167752bd6eab8ff4e9be770391fe4293` |
| Railway API `main` desplegado | SUCCESS, **mismo SHA** |
| `helvoca-api` / `Postgres` | Ambos online; Railway `pendingWork=[]` |
| Candidato final de esta auditoría | PR #810, `fa58a1852499ce728554d20f76c1f4b7bb033267` |
| Comparación `main..PR#810` | `ahead_by=300`, `behind_by=0`, 173 archivos cambiados |
| PR apilados | 18: #792–#804 y #806–#810; todos `open`, `draft`, `merged=false`, `mergeable=true` en la foto |
| Ancestro | #791 (`7851f1f0868ee8a84634ab0fe08063b41b55dd24`) **es ancestro del HEAD #792**; `compare` muestra 109 commits adelante, cero detrás |
| Rama paralela | #805 (`42b045623e0b2985521d2236ee0137433b09c352`) **NO está en esta cadena**; comparada con #810 presenta divergencia: +69 en candidato, 4 solo en #805 |

Las pruebas de ancestros y divergencia se consultaron con GitHub `compare`,
**no** se infirieron de los nombres de ramas. Los PR `mergeable=true` describen
el estado de merge contra **sus propias ramas base**, no garantizan que una
combinación futura sobre `main` esté libre de conflictos o efectos de despliegue.

### 2. Orden certificado del historial y alcance por bloques

La cadena descendente desde `main` es:

```text
main ca58693e
  │
  ├─ #791 (ancestro YA presente en #792: NO merge separado)
  │
  └─ #792 integración candidata base
       └─ #793 auditoría offline
          └─ #794 ruta Twilio VERIFIED_EXISTING y gates
             └─ #795 Gemini Live, VAD, barge-in
                └─ #796 RLS y aislamiento multiempresa
                   └─ #797 lifecycle voz y privacidad
                      └─ #798 evidencia offline final
                         └─ #799 preflight piloto 1/6
                            └─ #800 propiedad Twilio 2/6
                               └─ #801 audio y recuperación 3/6
                                  └─ #802 validación integrada 4/6
                                     └─ #803 gate producción 5/6
                                        └─ #804 gate real-pilot 6/6
                                           ├─ #805 (PARALELO; NO es ancestro de #806)
                                           └─ #806 respaldo PG18 privado (A)
                                              └─ #807 migraciones y aislamiento PG18 (B)
                                                 └─ #808 auditoría integral (C)
                                                    └─ #809 Flyway productivo read-only (D)
                                                       └─ #810 Twilio GET/PG read-only (D)
                                                          └─ este informe Parte 1
```

La tabla íntegra de **bases, ramas, HEAD SHA y estados exactos** se
mantiene en `docs/telephony/evidence/2026-10-13-part1-pr-lineage-readonly-snapshot.json`.
Un auditor Python aislado la valida sin acceso a sistemas externos.

### 3. La bifurcación #805 y el riesgo de cambios solapados

**No fusionar #805 en bloque ni descartarlo sin revisión.**

Comparación de cuatro archivos afectados:

- #805 añade `.github/workflows/voice-pilot-postgres18-compatibility.yml`,
  workflow aislado de V100–V103/RLS sobre PostgreSQL 18, **no presente**
  como archivo exacto en #810.
- #805 modifica dos pruebas JUnit para aceptar
  `-Dhelvoca.test.postgres.image=postgres:18-alpine` con default
  `postgres:16-alpine`; la cadena #810 ya fija explícitamente
  `postgres:18-alpine` en ambas pruebas.
- En `.github/workflows/twilio-railway-safe-internal-gates.yml`, #805
  añade un disparador para su propia rama y un BASE_SHA histórico;
  #810 usa otros disparadores y un BASE_SHA distinto.

**Consecuencia:** existe solapamiento funcional de tests PostgreSQL 18,
pero **no equivalencia completa** de workflows. La Parte 2 debe
revisar si merece incorporar el workflow único de #805 y la
configuración parametrizable, preservando las pruebas efectivas,
sin cherry-pick automático ni reemplazar gates por pruebas menos estrictas.

### 4. CI: qué está certificado y qué NO

- **Último CI completo seguro de 7 jobs**: éxito en el HEAD del PR #808,
  SHA `5e75cc87f824312b363e89c24cf269677677e6de`,
  run <https://github.com/Nicricht/helvoca/actions/runs/38348597042>.
  Esto incluye 2.230 tests Java + 615 Playwright, pero corresponde
  a **ese SHA**, NO al candidato final #810.
- **Último CI focalizado de la auditoría de carrier**: éxito sobre
  `fa58a1852499ce728554d20f76c1f4b7bb033267`,
  run <https://github.com/Nicricht/helvoca/actions/runs/38354260073>,
  **18 tests Python**. NO reemplaza el CI completo de siete jobs.
- El smoke público de webhook en `.github/workflows/ci.yml` realiza
  peticiones HTTP reales si se apunta el PR a `main`; esta fase
  **no abre un PR con base `main` ni ejecuta ese smoke**.
- El HEAD exacto del candidato consolidado **no tiene aún 7/7 CI
  certificado**. Antes de una futura integración habrá que ejecutar
  gates seguros sobre el SHA final exacto, además de comprobar los
  controles públicos pertinentes con autorización y plan.

### 5. Bloqueos que importan a la integración

1. El respaldo real PG18 de Etapa A fue restaurado de forma aislada.
   Los bytes y pruebas privadas permanecen en Windows. Falta una copia
   cifrada independiente y un respaldo **nuevo** justo antes de cualquier
   cambio productivo.
2. El ensayo de V100–V103 contra la copia restaurada fue mediante
   **psql**, NO fue una ejecución Flyway sobre esa copia.
   Las pruebas oficiales Testcontainers usan Flyway sobre PG18.
   La **Parte 2** debe cerrar esta diferencia de evidencia sin
   migrar una sola tabla de producción.
3. Flyway productivo fue certificado como **V99** por salida del
   operador read-only. V100–V103 **no están desplegadas**.
4. El auditor real GET Twilio + SELECT PostgreSQL se ejecutó
   y el operador reportó exactamente:
   `DB_ACCOUNT_SID_MISSING_OR_MISMATCH`,
   `DB_PROVENANCE_UNVERIFIED`,
   `DB_VOICE_TIMESTAMP_MISSING`.
   Las columnas de prueba se introducen en V100, y su procedencia
   `VERIFIED_EXISTING` se habilita con restricciones en V101.
   El SQL de migración **no confirma propiedad ni rellena campos**.
   La vinculación requiere reconciliación controlada y doble aprobación.
5. Fusionar a `main` hace que Railway construya/despliegue el backend.
   Un lote de 18 merges secuenciales podría producir despliegues
   intermedios no deseados, especialmente con migraciones Flyway
   automáticas y cambios de compatibilidad.
   Un rollback del código **no revierte** el esquema automáticamente.
6. Ni configuración Gemini ni pruebas sintéticas certifican todavía
   una llamada bidireccional real, factura, coste o permisos comerciales.

### 6. Decisión formal de Parte 1

**Orden correcto para revisión:** #792 → #793–#804 → #806–#810
(con #791 ya incluido por ascendencia).

**#805:** preservar como paralelo; su tratamiento definitivo se decide en
Parte 2, comparando tests y workflows. **No cerrar el PR como duplicado**
hasta esa revisión.

**Estrategia para despliegue futuro:** preparar candidato consolidado y
revisado en rama separada; solo tras la autorización formal planear la
integración en `main`, evitando fusionar automáticamente cada PR apilado
en producción. Reconfirmar `main`, el SHA desplegado, todas las bases,
checks y contratos antes de tomar esa decisión.

**Resultados auditables de Parte 1:**
`PART1_PR_LINEAGE=PASS_OFFLINE_SNAPSHOT`;
`PART1_PARALLEL_PR805=REVIEW_REQUIRED`;
`PART1_EXACT_CANDIDATE_7_GATE_CI=PENDING`;
`PART1_RELEASE=NO_GO`.

Gates inmutables: `PROD_DEPLOY=NO-GO`,
`LIVE_CALL_CERTIFIED=false`, `COMMERCIAL_GO=NO-GO`.
Ninguna compra, llamada, SQL productivo, merge o despliegue autorizados.

### 7. Entrega a Parte 2 de 10

**Siguiente objetivo, NO comenzado aquí:** auditar V100–V103 y su
compatibilidad hacia atrás con la base V99, revisar aportes de #805,
cotejar checksums/historial, ensayar estrategia de reversión en
PostgreSQL 18 aislado y documentar condiciones de despliegue.
Detenerse al terminar Parte 1.

La instantánea es evidencia de un instante, no una consulta viva. El
script `scripts/ci/voice_part1_stacked_pr_lineage_audit.py` está
diseñado para negar cualquier `GO` aun con snapshot consistente.
